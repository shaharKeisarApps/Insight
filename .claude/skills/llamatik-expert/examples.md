# Llamatik Production Examples (v0.15.0)

Real-world examples demonstrating Llamatik integration in KMP applications.

## Example 1: AI-Powered Code Review Assistant

**Use Case**: Local code review suggestions without sending code to cloud services.

### Architecture

```
:feature:code-review:api
  - CodeReviewService (interface)
  - ReviewResult (data class)

:feature:code-review:impl
  - LlamatikCodeReviewer (implements CodeReviewService)
  - Model: CodeLlama-7B-Q5_1.gguf
```

### Implementation

```kotlin
// :feature:code-review:api/CodeReviewService.kt
interface CodeReviewService {
    suspend fun reviewCode(
        code: String,
        language: String,
        focus: ReviewFocus
    ): Result<ReviewResult>
}

enum class ReviewFocus {
    BEST_PRACTICES,
    SECURITY,
    PERFORMANCE,
    READABILITY
}

data class ReviewResult(
    val suggestions: List<Suggestion>,
    val severity: Severity,
    val summary: String
)

data class Suggestion(
    val line: Int?,
    val issue: String,
    val recommendation: String,
    val severity: Severity
)

enum class Severity { INFO, WARNING, ERROR, CRITICAL }

// :feature:code-review:impl/LlamatikCodeReviewer.kt
@Inject
@SingleIn(AppScope::class)
class LlamatikCodeReviewer(
    private val modelPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) : CodeReviewService {

    private val reviewMutex = Mutex()

    init {
        // Initialize model on background thread
        CoroutineScope(ioDispatcher).launch {
            LlamaBridge.initGenerateModel(modelPath)
        }
    }

    override suspend fun reviewCode(
        code: String,
        language: String,
        focus: ReviewFocus,
    ): Result<ReviewResult> = withContext(ioDispatcher) {
        reviewMutex.withLock {
            runCatching {
                val prompt = buildReviewPrompt(code, language, focus)
                val jsonSchema = buildReviewSchema()

                val json = LlamaBridge.generateJson(prompt, jsonSchema)
                parseReviewResult(json)
            }
        }
    }

    private fun buildReviewPrompt(code: String, language: String, focus: ReviewFocus): String {
        val focusInstructions = when (focus) {
            ReviewFocus.BEST_PRACTICES -> "Focus on idiomatic $language patterns, naming conventions, and code style."
            ReviewFocus.SECURITY -> "Focus on security vulnerabilities: SQL injection, XSS, hardcoded secrets, path traversal."
            ReviewFocus.PERFORMANCE -> "Focus on performance issues: O(n²) algorithms, unnecessary allocations, blocking calls."
            ReviewFocus.READABILITY -> "Focus on readability: complex logic, unclear names, missing documentation."
        }

        return """
        Review the following $language code. $focusInstructions

        Code:
        ```$language
        $code
        ```

        Provide structured feedback as JSON.
        """.trimIndent()
    }

    private fun buildReviewSchema(): String = """
    {
      "type": "object",
      "properties": {
        "suggestions": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "line": {"type": ["number", "null"]},
              "issue": {"type": "string"},
              "recommendation": {"type": "string"},
              "severity": {"type": "string", "enum": ["INFO", "WARNING", "ERROR", "CRITICAL"]}
            },
            "required": ["issue", "recommendation", "severity"]
          }
        },
        "summary": {"type": "string"}
      },
      "required": ["suggestions", "summary"]
    }
    """.trimIndent()

    private fun parseReviewResult(json: String): ReviewResult {
        val parsed = Json.decodeFromString<ReviewResultDto>(json)
        return ReviewResult(
            suggestions = parsed.suggestions.map { dto ->
                Suggestion(
                    line = dto.line,
                    issue = dto.issue,
                    recommendation = dto.recommendation,
                    severity = Severity.valueOf(dto.severity)
                )
            },
            severity = parsed.suggestions.maxByOrNull { it.severity }?.severity?.let { Severity.valueOf(it) }
                ?: Severity.INFO,
            summary = parsed.summary
        )
    }

    fun shutdown() {
        LlamaBridge.shutdown()
    }
}

@Serializable
private data class ReviewResultDto(
    val suggestions: List<SuggestionDto>,
    val summary: String
)

@Serializable
private data class SuggestionDto(
    val line: Int? = null,
    val issue: String,
    val recommendation: String,
    val severity: String
)
```

### Presenter (Circuit)

```kotlin
@CircuitInject(CodeReviewScreen::class, AppScope::class)
@AssistedInject
class CodeReviewPresenter(
    @Assisted private val navigator: Navigator,
    private val reviewService: CodeReviewService,
) : Presenter<CodeReviewScreen.State> {

    @Composable
    override fun present(): CodeReviewScreen.State {
        var codeInput by remember { mutableStateOf("") }
        var reviewResult by remember { mutableStateOf<ReviewResult?>(null) }
        var isReviewing by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        return CodeReviewScreen.State(
            codeInput = codeInput,
            reviewResult = reviewResult,
            isReviewing = isReviewing,
            error = error,
        ) { event ->
            when (event) {
                is CodeReviewScreen.Event.CodeChanged -> {
                    codeInput = event.code
                    error = null
                }
                is CodeReviewScreen.Event.ReviewCode -> {
                    isReviewing = true
                    error = null
                    reviewService.reviewCode(
                        code = codeInput,
                        language = "kotlin",
                        focus = ReviewFocus.BEST_PRACTICES
                    ).fold(
                        onSuccess = {
                            reviewResult = it
                            isReviewing = false
                        },
                        onFailure = {
                            error = "Review failed: ${it.message}"
                            isReviewing = false
                        }
                    )
                }
                CodeReviewScreen.Event.ClearResults -> {
                    reviewResult = null
                    error = null
                }
            }
        }
    }

    @AssistedFactory
    @CircuitInject(CodeReviewScreen::class, AppScope::class)
    fun interface Factory {
        fun create(navigator: Navigator): CodeReviewPresenter
    }
}
```

---

## Example 2: Offline RAG System for Documentation

**Use Case**: Semantic search over local documentation using embeddings.

### Architecture

```
:feature:docs-search:api
  - DocumentationSearchService

:feature:docs-search:impl
  - LlamatikEmbeddingService
  - VectorDatabase (in-memory or SQLite)
  - Model: all-MiniLM-L6-v2.gguf (384-dim embeddings)
```

### Implementation

```kotlin
// :feature:docs-search:api/DocumentationSearchService.kt
interface DocumentationSearchService {
    suspend fun indexDocument(id: String, content: String)
    suspend fun search(query: String, topK: Int = 5): List<SearchResult>
    suspend fun answerQuestion(question: String): String
}

data class SearchResult(
    val id: String,
    val content: String,
    val score: Float
)

// :feature:docs-search:impl/LlamatikEmbeddingService.kt
@Inject
@SingleIn(AppScope::class)
class LlamatikEmbeddingService(
    private val modelPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val embeddingMutex = Mutex()

    init {
        CoroutineScope(ioDispatcher).launch {
            LlamaBridge.initModel(modelPath)
        }
    }

    suspend fun embed(text: String): FloatArray = withContext(ioDispatcher) {
        embeddingMutex.withLock {
            LlamaBridge.embed(text)
        }
    }

    fun shutdown() {
        LlamaBridge.shutdown()
    }
}

// :feature:docs-search:impl/InMemoryVectorDatabase.kt
@Inject
@SingleIn(AppScope::class)
class InMemoryVectorDatabase(
    private val embeddingService: LlamatikEmbeddingService,
) {
    private val documents = mutableMapOf<String, Pair<String, FloatArray>>()

    suspend fun index(id: String, content: String) {
        val embedding = embeddingService.embed(content)
        documents[id] = content to embedding
    }

    suspend fun search(queryEmbedding: FloatArray, topK: Int): List<SearchResult> {
        return documents.entries
            .map { (id, pair) ->
                val (content, embedding) = pair
                val score = cosineSimilarity(queryEmbedding, embedding)
                SearchResult(id, content, score)
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        val dotProduct = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
        val magnitudeA = sqrt(a.sumOf { (it * it).toDouble() }.toFloat())
        val magnitudeB = sqrt(b.sumOf { (it * it).toDouble() }.toFloat())
        return dotProduct / (magnitudeA * magnitudeB)
    }
}

// :feature:docs-search:impl/LlamatikDocumentationSearch.kt
@Inject
@SingleIn(AppScope::class)
class LlamatikDocumentationSearch(
    private val embeddingService: LlamatikEmbeddingService,
    private val vectorDb: InMemoryVectorDatabase,
    private val generativeModelPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) : DocumentationSearchService {

    private val generateMutex = Mutex()

    override suspend fun indexDocument(id: String, content: String) {
        vectorDb.index(id, content)
    }

    override suspend fun search(query: String, topK: Int): List<SearchResult> {
        val queryEmbedding = embeddingService.embed(query)
        return vectorDb.search(queryEmbedding, topK)
    }

    override suspend fun answerQuestion(question: String): String = withContext(ioDispatcher) {
        generateMutex.withLock {
            // Retrieve relevant docs
            val relevantDocs = search(question, topK = 3)
            val context = relevantDocs.joinToString("\n\n") { it.content }

            // Initialize generative model
            LlamaBridge.initGenerateModel(generativeModelPath)
            try {
                val prompt = """
                Answer the question based ONLY on the context below. If the answer is not in the context, say "I don't know."

                Context:
                $context

                Question: $question

                Answer:
                """.trimIndent()

                LlamaBridge.generate(prompt)
            } finally {
                LlamaBridge.shutdown()
            }
        }
    }
}
```

### Usage in UI

```kotlin
@CircuitInject(DocsSearchScreen::class, AppScope::class)
@AssistedInject
class DocsSearchPresenter(
    @Assisted private val navigator: Navigator,
    private val docsSearch: DocumentationSearchService,
) : Presenter<DocsSearchScreen.State> {

    @Composable
    override fun present(): DocsSearchScreen.State {
        var query by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf<String?>(null) }
        var isSearching by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Index documentation on startup
            docsSearch.indexDocument("kotlin-docs-1", "Kotlin is a modern, statically-typed language...")
            docsSearch.indexDocument("kotlin-docs-2", "Coroutines provide asynchronous programming...")
            // ... index more docs
        }

        return DocsSearchScreen.State(
            query = query,
            answer = answer,
            isSearching = isSearching,
        ) { event ->
            when (event) {
                is DocsSearchScreen.Event.QueryChanged -> query = event.query
                DocsSearchScreen.Event.Search -> {
                    isSearching = true
                    launch {
                        answer = docsSearch.answerQuestion(query)
                        isSearching = false
                    }
                }
            }
        }
    }
}
```

---

## Example 3: Streaming Chatbot with Circuit

**Use Case**: Real-time conversational interface with streaming responses.

### Implementation

```kotlin
// :feature:chatbot:impl/LlamatikChatbot.kt
@Inject
@SingleIn(AppScope::class)
class LlamatikChatbot(
    private val modelPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val conversationHistory = mutableListOf<String>()
    private val chatMutex = Mutex()

    init {
        CoroutineScope(ioDispatcher).launch {
            LlamaBridge.initGenerateModel(modelPath)
        }
    }

    suspend fun streamResponse(
        userMessage: String,
        onDelta: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ) = withContext(ioDispatcher) {
        chatMutex.withLock {
            val fullResponse = StringBuilder()

            LlamaBridge.generateStreamWithContext(
                systemPrompt = "You are a helpful Kotlin programming assistant. Be concise.",
                conversationHistory = conversationHistory.toList(),
                userMessage = userMessage,
                stream = object : GenStream {
                    override fun onDelta(delta: String) {
                        fullResponse.append(delta)
                        onDelta(delta)
                    }

                    override fun onComplete() {
                        // Save to history
                        conversationHistory.add("User: $userMessage")
                        conversationHistory.add("Assistant: ${fullResponse}")

                        // Trim history to last 10 exchanges
                        if (conversationHistory.size > 20) {
                            conversationHistory.removeAt(0)
                            conversationHistory.removeAt(0)
                        }

                        onComplete()
                    }

                    override fun onError(error: String) {
                        onError(error)
                    }
                }
            )
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    fun shutdown() {
        LlamaBridge.shutdown()
    }
}

// Presenter
@CircuitInject(ChatbotScreen::class, AppScope::class)
@AssistedInject
class ChatbotPresenter(
    @Assisted private val navigator: Navigator,
    private val chatbot: LlamatikChatbot,
) : Presenter<ChatbotScreen.State> {

    @Composable
    override fun present(): ChatbotScreen.State {
        var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
        var inputText by remember { mutableStateOf("") }
        var isStreaming by remember { mutableStateOf(false) }
        var currentStreamedText by remember { mutableStateOf("") }

        return ChatbotScreen.State(
            messages = messages,
            inputText = inputText,
            isStreaming = isStreaming,
            currentStreamedText = currentStreamedText,
        ) { event ->
            when (event) {
                is ChatbotScreen.Event.InputChanged -> inputText = event.text
                ChatbotScreen.Event.SendMessage -> {
                    if (inputText.isBlank()) return@State

                    val userMessage = Message.User(inputText)
                    messages = messages + userMessage
                    val sentText = inputText
                    inputText = ""
                    isStreaming = true
                    currentStreamedText = ""

                    launch {
                        chatbot.streamResponse(
                            userMessage = sentText,
                            onDelta = { delta ->
                                currentStreamedText += delta
                            },
                            onComplete = {
                                messages = messages + Message.Assistant(currentStreamedText)
                                currentStreamedText = ""
                                isStreaming = false
                            },
                            onError = { error ->
                                messages = messages + Message.Error(error)
                                isStreaming = false
                            }
                        )
                    }
                }
                ChatbotScreen.Event.ClearHistory -> {
                    chatbot.clearHistory()
                    messages = emptyList()
                }
            }
        }
    }
}

sealed interface Message {
    data class User(val text: String) : Message
    data class Assistant(val text: String) : Message
    data class Error(val message: String) : Message
}
```

### UI with Streaming Display

```kotlin
@Composable
fun ChatbotUi(state: ChatbotScreen.State, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { message ->
                when (message) {
                    is Message.User -> UserMessageBubble(message.text)
                    is Message.Assistant -> AssistantMessageBubble(message.text)
                    is Message.Error -> ErrorMessageBubble(message.message)
                }
            }

            // Show streaming message
            if (state.isStreaming && state.currentStreamedText.isNotEmpty()) {
                item {
                    AssistantMessageBubble(
                        text = state.currentStreamedText,
                        isStreaming = true
                    )
                }
            }
        }

        // Input field
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = state.inputText,
                onValueChange = { state.eventSink(ChatbotScreen.Event.InputChanged(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask a question...") },
                enabled = !state.isStreaming
            )
            IconButton(
                onClick = { state.eventSink(ChatbotScreen.Event.SendMessage) },
                enabled = !state.isStreaming && state.inputText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun AssistantMessageBubble(text: String, isStreaming: Boolean = false) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text)
            if (isStreaming) {
                Spacer(modifier = Modifier.width(4.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
```

---

## Example 4: Automated Test Case Generation

**Use Case**: Generate unit tests from function signatures.

### Implementation

```kotlin
// :feature:test-generator:impl/LlamatikTestGenerator.kt
@Inject
@SingleIn(AppScope::class)
class LlamatikTestGenerator(
    private val modelPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val genMutex = Mutex()

    init {
        CoroutineScope(ioDispatcher).launch {
            LlamaBridge.initGenerateModel(modelPath)
        }
    }

    suspend fun generateTests(
        functionCode: String,
        testFramework: TestFramework = TestFramework.JUNIT5,
    ): Result<String> = withContext(ioDispatcher) {
        genMutex.withLock {
            runCatching {
                val prompt = """
                Generate comprehensive unit tests for this Kotlin function using ${testFramework.name}:

                ```kotlin
                $functionCode
                ```

                Include:
                - Happy path tests
                - Edge cases (empty, null, boundary values)
                - Error cases
                - Property-based tests if applicable

                Use descriptive test names following the pattern: `fun \`should do X when Y\`()`

                Output only the test code, no explanations.
                """.trimIndent()

                LlamaBridge.generate(prompt)
            }
        }
    }

    fun shutdown() {
        LlamaBridge.shutdown()
    }
}

enum class TestFramework { JUNIT5, KOTEST, SPEK }
```

### Integration with IDE Plugin (Hypothetical)

```kotlin
// IDE plugin action
class GenerateTestsAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val project = event.project ?: return

        val selectedText = editor.selectionModel.selectedText ?: return
        val testGenerator = project.service<LlamatikTestGenerator>()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Tests") {
            override fun run(indicator: ProgressIndicator) {
                runBlocking {
                    testGenerator.generateTests(selectedText).fold(
                        onSuccess = { generatedTests ->
                            ApplicationManager.getApplication().invokeLater {
                                // Insert generated tests into test file
                                insertTestsIntoEditor(editor, generatedTests)
                            }
                        },
                        onFailure = { error ->
                            Notifications.Bus.notify(
                                Notification("Test Generation", "Failed", error.message ?: "Unknown error", NotificationType.ERROR)
                            )
                        }
                    )
                }
            }
        })
    }
}
```

---

## Example 5: Hybrid Local/Remote Architecture

**Use Case**: Try local inference first, fall back to cloud if device is low on resources.

### Implementation

```kotlin
// :feature:inference:api/InferenceService.kt
interface InferenceService {
    suspend fun generate(prompt: String): Result<String>
}

// :feature:inference:impl/HybridInferenceService.kt
@Inject
@SingleIn(AppScope::class)
class HybridInferenceService(
    private val localInference: LocalInferenceService,
    private val remoteInference: RemoteInferenceService,
    private val deviceCapabilities: DeviceCapabilities,
) : InferenceService {

    override suspend fun generate(prompt: String): Result<String> {
        return if (deviceCapabilities.canRunLocalInference()) {
            localInference.generate(prompt)
                .recoverCatching { error ->
                    // Fall back to remote on local failure
                    remoteInference.generate(prompt).getOrThrow()
                }
        } else {
            // Skip local if device resources are low
            remoteInference.generate(prompt)
        }
    }
}

// :feature:inference:impl/LocalInferenceService.kt
@Inject
class LocalInferenceService(
    private val modelPath: String,
    private val ioDispatcher: CoroutineDispatcher,
) : InferenceService {
    private val mutex = Mutex()

    override suspend fun generate(prompt: String): Result<String> = withContext(ioDispatcher) {
        mutex.withLock {
            runCatching {
                if (!isModelLoaded) {
                    LlamaBridge.initGenerateModel(modelPath)
                    isModelLoaded = true
                }
                LlamaBridge.generate(prompt)
            }
        }
    }

    private var isModelLoaded = false
}

// :feature:inference:impl/RemoteInferenceService.kt
@Inject
class RemoteInferenceService(
    private val httpClient: HttpClient,
) : InferenceService {
    override suspend fun generate(prompt: String): Result<String> = runCatching {
        val response: RemoteResponse = httpClient.post("https://api.example.com/generate") {
            contentType(ContentType.Application.Json)
            setBody(RemoteRequest(prompt))
        }.body()
        response.text
    }
}

// :core:platform/DeviceCapabilities.kt
@Inject
@SingleIn(AppScope::class)
expect class DeviceCapabilities {
    fun canRunLocalInference(): Boolean
    fun availableMemoryMB(): Long
}

// androidMain
actual class DeviceCapabilities {
    actual fun canRunLocalInference(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory() / (1024 * 1024) // MB
        return freeMemory > 1000 // Need at least 1GB free
    }

    actual fun availableMemoryMB(): Long {
        return Runtime.getRuntime().freeMemory() / (1024 * 1024)
    }
}

// iosMain
actual class DeviceCapabilities {
    actual fun canRunLocalInference(): Boolean {
        val freeMemory = NSProcessInfo.processInfo.physicalMemory / (1024 * 1024)
        return freeMemory > 2000 // iOS needs more headroom
    }

    actual fun availableMemoryMB(): Long {
        return NSProcessInfo.processInfo.physicalMemory / (1024 * 1024)
    }
}
```

---

## Best Practices Demonstrated

1. **Thread Safety**: All examples use `Mutex` to serialize access to `LlamaBridge`
2. **Resource Management**: `shutdown()` called in cleanup paths
3. **Coroutines**: All blocking calls wrapped in `withContext(Dispatchers.IO)`
4. **Error Handling**: `Result` types and `runCatching` for graceful failures
5. **Streaming UX**: Real-time feedback via `GenStream` callbacks
6. **Context Windows**: History trimming to stay within token limits
7. **Hybrid Fallback**: Graceful degradation from local to remote
8. **Metro DI**: `@Inject`, `@SingleIn`, `@CircuitInject` for dependency management
9. **Platform Abstraction**: `expect`/`actual` for platform-specific paths

## Performance Tips

- **Model Quantization**: Use Q4_0 for chat, Q5_1/Q8_0 for quality tasks
- **Lazy Loading**: Initialize models only when needed, not on app startup
- **Background Threads**: Always run inference on `Dispatchers.IO`, never `Main`
- **Streaming**: Use `generateStream()` for perceived responsiveness
- **Batch Processing**: If generating multiple outputs, consider batching (future API)

## Security Considerations

- **Local Data**: Models run offline — no user data leaves device
- **Model Integrity**: Verify model checksums before loading
- **Prompt Injection**: Sanitize user inputs, especially in RAG contexts
- **License Compliance**: Check model licenses (LLaMA, Mistral, etc.)
