package com.keisardev.insight.core.ai.service

import android.app.Application
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.data.repository.ExpenseRepository
import com.keisardev.insight.core.data.repository.IncomeRepository
import com.keisardev.insight.core.model.Category
import com.llamatik.library.platform.LlamaBridge
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File

/**
 * Implementation of [AiService] using Llamatik for on-device LLM inference.
 *
 * Uses llama.cpp under the hood via Llamatik's [LlamaBridge] to run models
 * locally on the device without requiring network access or API keys.
 *
 * Thread safety is ensured via [Mutex] since LlamaBridge uses native resources
 * that are not thread-safe.
 *
 * Place a GGUF model file at `{filesDir}/models/model.gguf` to enable.
 */
@SingleIn(AppScope::class)
@Inject
class LlamatikAiService(
    application: Application,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
) : AiService {

    private val modelsDir = File(application.filesDir, "models")

    private val modelPath: String?
        get() = modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }?.absolutePath

    private val mutex = Mutex()
    private var isModelLoaded = false
    private var loadedModelPath: String? = null

    override val isEnabled: Boolean
        get() = modelPath != null

    private suspend fun ensureModelLoaded() {
        val path = modelPath ?: return
        // Reload if model path changed (e.g. new model downloaded)
        if (isModelLoaded && loadedModelPath != path) {
            LlamaBridge.shutdown()
            isModelLoaded = false
            loadedModelPath = null
        }
        if (isModelLoaded) return
        withContext(Dispatchers.IO) {
            LlamaBridge.initGenerateModel(path)
        }
        isModelLoaded = true
        loadedModelPath = path
    }

    override suspend fun suggestCategory(
        description: String,
        availableCategories: List<Category>,
    ): Category? {
        if (!isEnabled || description.isBlank()) return null

        val categoryNames = availableCategories.joinToString(", ") { it.name }
        val prompt = buildString {
            append("Given these expense categories: [$categoryNames]\n")
            append("Which category best fits this expense: \"$description\"?\n")
            append("Respond with ONLY the category name, nothing else.")
        }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    ensureModelLoaded()
                    val result = LlamaBridge.generate(prompt)
                    availableCategories.find { category ->
                        category.name.equals(result.trim(), ignoreCase = true)
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private suspend fun buildFinancialSummary(today: LocalDate): String {
        return try {
            val monthStart = LocalDate(today.year, today.month, 1)
            val monthEnd = today

            val monthlyExpenseTotal = expenseRepository.observeMonthlyTotal(monthStart, monthEnd).first()
            val monthlyIncomeTotal = incomeRepository.observeMonthlyTotal(monthStart, monthEnd).first()
            val categoryBreakdown = expenseRepository.observeTotalByCategory(monthStart, monthEnd).first()
            val recentExpenses = expenseRepository.observeAllExpenses().first().take(10)
            val recentIncomes = incomeRepository.observeAllIncome().first().take(5)

            buildString {
                appendLine("Here is your financial data:")
                appendLine("This month (${today.month} ${today.year}) you spent $${String.format("%.2f", monthlyExpenseTotal)} total.")
                appendLine("This month you earned $${String.format("%.2f", monthlyIncomeTotal)} total.")
                val net = monthlyIncomeTotal - monthlyExpenseTotal
                if (net >= 0) {
                    appendLine("You saved $${String.format("%.2f", net)} this month.")
                } else {
                    appendLine("You overspent by $${String.format("%.2f", -net)} this month.")
                }

                if (categoryBreakdown.isNotEmpty()) {
                    appendLine("Spending by category this month:")
                    categoryBreakdown.entries
                        .sortedByDescending { it.value }
                        .forEach { (category, amount) ->
                            appendLine("- ${category.name}: $${String.format("%.2f", amount)}")
                        }
                }

                if (recentExpenses.isNotEmpty()) {
                    appendLine("Your recent expenses:")
                    recentExpenses.forEach { expense ->
                        val desc = if (expense.description.isNotBlank()) " - ${expense.description}" else ""
                        appendLine("- ${expense.date}: ${expense.category.name}, $${String.format("%.2f", expense.amount)}$desc")
                    }
                }

                if (recentIncomes.isNotEmpty()) {
                    appendLine("Your recent income:")
                    recentIncomes.forEach { income ->
                        val desc = if (income.description.isNotBlank()) " - ${income.description}" else ""
                        appendLine("- ${income.date}: ${income.category.name}, $${String.format("%.2f", income.amount)}$desc")
                    }
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun chat(
        message: String,
        history: List<ChatMessage>,
    ): String {
        if (!isEnabled) {
            return "On-device AI is not available. No model file found."
        }

        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

        val financialSummary = buildFinancialSummary(today)

        // Build history with financial data injected as the first exchange
        val historyContext = buildString {
            if (financialSummary.isNotBlank()) {
                appendLine("User: What is my financial data?")
                appendLine("Assistant: $financialSummary")
            }
            if (history.isNotEmpty()) {
                history.forEach { msg ->
                    val role = when (msg.role) {
                        ChatRole.USER -> "User"
                        ChatRole.ASSISTANT -> "Assistant"
                    }
                    appendLine("$role: ${msg.content}")
                }
            }
        }

        val systemPrompt = buildString {
            append("You are a helpful financial assistant. Today is $today. ")
            append("You have access to the user's expense and income data. ")
            append("Answer questions using the financial data from the conversation. ")
            append("Be concise. Format money as ${'$'}X.XX.")
        }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    ensureModelLoaded()
                    val raw = LlamaBridge.generateWithContext(
                        systemPrompt,
                        historyContext,
                        message,
                    )
                    // Strip any leaked conversation template from the response
                    raw.substringBefore("\nUser:")
                        .substringBefore("\nAssistant:")
                        .trim()
                } catch (e: Exception) {
                    "Sorry, I encountered an error: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    fun shutdown() {
        if (isModelLoaded) {
            LlamaBridge.shutdown()
            isModelLoaded = false
        }
    }
}
