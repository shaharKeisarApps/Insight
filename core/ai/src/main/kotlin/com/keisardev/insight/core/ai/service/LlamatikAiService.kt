package com.keisardev.insight.core.ai.service

import android.app.Application
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.Category
import com.llamatik.library.platform.LlamaBridge
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
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
) : AiService {

    private val modelPath: String? = run {
        val modelDir = File(application.filesDir, "models")
        val modelFile = modelDir.listFiles()?.firstOrNull { it.extension == "gguf" }
        modelFile?.absolutePath
    }

    private val mutex = Mutex()
    private var isModelLoaded = false

    override val isEnabled: Boolean
        get() = modelPath != null

    private suspend fun ensureModelLoaded() {
        if (isModelLoaded) return
        val path = modelPath ?: return
        withContext(Dispatchers.IO) {
            LlamaBridge.initGenerateModel(path)
        }
        isModelLoaded = true
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

        val historyContext = if (history.isNotEmpty()) {
            history.joinToString("\n") { msg ->
                val role = when (msg.role) {
                    ChatRole.USER -> "User"
                    ChatRole.ASSISTANT -> "Assistant"
                }
                "$role: ${msg.content}"
            }
        } else {
            ""
        }

        val systemPrompt = buildString {
            append("You are a helpful financial assistant. Today is $today. ")
            append("Be concise and helpful. Format money as ${'$'}X.XX.")
        }

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    ensureModelLoaded()
                    LlamaBridge.generateWithContext(
                        systemPrompt,
                        historyContext,
                        message,
                    )
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
