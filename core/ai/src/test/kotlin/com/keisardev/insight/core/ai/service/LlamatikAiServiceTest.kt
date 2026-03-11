package com.keisardev.insight.core.ai.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Method

/**
 * Unit tests for LlamatikAiService pure functions.
 *
 * Tests the following private methods via reflection:
 * - classifyIntent(message: String): UserIntent?
 * - cleanResponse(raw: String): String
 * - classifyQuestionType(message: String): QuestionType
 *
 * These are instance methods, so we invoke them on a mock-free instance
 * allocated without calling the constructor (bypasses DI dependencies).
 */
class LlamatikAiServiceTest {

    @Suppress("DEPRECATION")
    private val instance: Any by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(theUnsafe, LlamatikAiService::class.java)
    }

    private fun getClassifyIntentMethod(): Method {
        val method = LlamatikAiService::class.java.getDeclaredMethod(
            "classifyIntent",
            String::class.java,
        )
        method.isAccessible = true
        return method
    }

    private fun getCleanResponseMethod(): Method {
        val method = LlamatikAiService::class.java.getDeclaredMethod(
            "cleanResponse",
            String::class.java,
        )
        method.isAccessible = true
        return method
    }

    private fun getClassifyQuestionTypeMethod(): Method {
        val method = LlamatikAiService::class.java.getDeclaredMethod(
            "classifyQuestionType",
            String::class.java,
        )
        method.isAccessible = true
        return method
    }

    // ===== classifyIntent() Tests =====

    @Test
    fun classifyIntent_withAddExpenseAndAmount_returnsAddExpense() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "add expense \$50 for groceries")

        assertThat(result).isNotNull()
        assertThat(result.toString()).contains("ADD_EXPENSE")
        assertThat(result.toString()).contains("50.0")
    }

    @Test
    fun classifyIntent_withRecordIncomeAndAmount_returnsAddIncome() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "record income 2000 salary")

        assertThat(result).isNotNull()
        assertThat(result.toString()).contains("ADD_INCOME")
        assertThat(result.toString()).contains("2000.0")
    }

    @Test
    fun classifyIntent_withGotPaidPhrase_returnsAddIncome() {
        val method = getClassifyIntentMethod()
        // "got paid $3000" uses strict regex ($ prefix) since "got paid" is implicit, not explicit
        val result = method.invoke(instance, "got paid \$3000")

        assertThat(result).isNotNull()
        assertThat(result.toString()).contains("ADD_INCOME")
        assertThat(result.toString()).contains("3000.0")
    }

    @Test
    fun classifyIntent_withIPaidPhrase_returnsAddExpense() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "I paid \$25 for lunch")

        assertThat(result).isNotNull()
        assertThat(result.toString()).contains("ADD_EXPENSE")
        assertThat(result.toString()).contains("25.0")
    }

    @Test
    fun classifyIntent_withQueryOverride_returnsNull() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "how much did I spend?")

        assertThat(result).isNull()
    }

    @Test
    fun classifyIntent_withShowMePhrase_returnsNull() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "show me my expenses")

        assertThat(result).isNull()
    }

    @Test
    fun classifyIntent_withListMyPhrase_returnsNull() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "list my transactions")

        assertThat(result).isNull()
    }

    @Test
    fun classifyIntent_withAmountButNoAction_returnsNull() {
        val method = getClassifyIntentMethod()
        val result = method.invoke(instance, "some random text with \$100")

        assertThat(result).isNull()
    }

    // ===== cleanResponse() Tests =====

    @Test
    fun cleanResponse_withUserContinuation_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Hello\nUser: next question"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Hello")
    }

    @Test
    fun cleanResponse_withAssistantContinuation_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Answer\nAssistant: more text"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Answer")
    }

    @Test
    fun cleanResponse_withStopToken_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Answer<|im_end|>rest"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Answer")
    }

    @Test
    fun cleanResponse_withEndOfTextToken_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Response<|endoftext|>more"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Response")
    }

    @Test
    fun cleanResponse_withHashmarksMarker_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Complete\n###"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Complete")
    }

    @Test
    fun cleanResponse_withHumanAIPrefix_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Answer\nHuman: follow up"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Answer")
    }

    @Test
    fun cleanResponse_withAIPrefix_stripsItCorrectly() {
        val method = getCleanResponseMethod()
        val input = "Response\nAI: next"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Response")
    }

    @Test
    fun cleanResponse_withExtraSpaces_trimsThem() {
        val method = getCleanResponseMethod()
        val input = "  spaces  "
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("spaces")
    }

    @Test
    fun cleanResponse_withCleanText_returnsUnchanged() {
        val method = getCleanResponseMethod()
        val input = "Clean response text"
        val result = method.invoke(instance, input)

        assertThat(result).isEqualTo("Clean response text")
    }

    // ===== classifyQuestionType() Tests =====

    @Test
    fun classifyQuestionType_withHowMuchSpent_returnsExpenseQuery() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "how much did I spend")

        assertThat(result.toString()).contains("EXPENSE_QUERY")
    }

    @Test
    fun classifyQuestionType_withShowCategories_returnsCategoryBreakdown() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "show me categories")

        assertThat(result.toString()).contains("CATEGORY_BREAKDOWN")
    }

    @Test
    fun classifyQuestionType_withBiggestCategory_returnsCategoryBreakdown() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "what was my biggest expense")

        assertThat(result.toString()).contains("CATEGORY_BREAKDOWN")
    }

    @Test
    fun classifyQuestionType_withRecentExpenses_returnsRecentItems() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "recent expenses")

        assertThat(result.toString()).contains("RECENT_ITEMS")
    }

    @Test
    fun classifyQuestionType_withLatestTransactions_returnsRecentItems() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "latest transactions")

        assertThat(result.toString()).contains("RECENT_ITEMS")
    }

    @Test
    fun classifyQuestionType_withComparisonPhrase_returnsComparison() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "compared to last month")

        assertThat(result.toString()).contains("COMPARISON")
    }

    @Test
    fun classifyQuestionType_withVersusKeyword_returnsComparison() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "this month vs last month")

        assertThat(result.toString()).contains("COMPARISON")
    }

    @Test
    fun classifyQuestionType_withEarnedKeyword_returnsIncomeQuery() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "how much did I earn")

        assertThat(result.toString()).contains("INCOME_QUERY")
    }

    @Test
    fun classifyQuestionType_withSalaryKeyword_returnsIncomeQuery() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "show my salary info")

        assertThat(result.toString()).contains("INCOME_QUERY")
    }

    @Test
    fun classifyQuestionType_withIncomeKeyword_returnsIncomeQuery() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "what is my income")

        assertThat(result.toString()).contains("INCOME_QUERY")
    }

    @Test
    fun classifyQuestionType_withBreakdownKeyword_returnsCategoryBreakdown() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "expense breakdown")

        assertThat(result.toString()).contains("CATEGORY_BREAKDOWN")
    }

    @Test
    fun classifyQuestionType_withUnrecognizedQuestion_returnsGeneral() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "tell me something random")

        assertThat(result.toString()).contains("GENERAL")
    }

    @Test
    fun classifyQuestionType_withTotalSpent_returnsExpenseQuery() {
        val method = getClassifyQuestionTypeMethod()
        val result = method.invoke(instance, "total spent this month")

        assertThat(result.toString()).contains("EXPENSE_QUERY")
    }
}
