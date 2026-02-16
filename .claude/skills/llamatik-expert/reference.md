# Llamatik API Reference (v0.15.0)

Complete API documentation for Llamatik, the KMP library for on-device LLM inference.

## Core API: LlamaBridge

All Llamatik operations go through the `LlamaBridge` object, a singleton providing access to llama.cpp functionality.

### Model Initialization

#### `initGenerateModel(modelPath: String)`

Initializes a model for text generation tasks.

```kotlin
fun initGenerateModel(modelPath: String)
```

**Parameters**:
- `modelPath`: Absolute file path to the GGUF model file

**Behavior**:
- Loads model into memory
- Configures generation parameters (temperature, top-k, etc.)
- Throws exception if model file not found or invalid format

**Example**:
```kotlin
val modelPath = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")
LlamaBridge.initGenerateModel(modelPath)
```

**Notes**:
- Only one model can be loaded at a time
- Call `shutdown()` before loading a different model
- First call takes 1-10 seconds depending on model size

#### `initModel(modelPath: String)`

Initializes a model for embedding tasks.

```kotlin
fun initModel(modelPath: String)
```

**Parameters**:
- `modelPath`: Absolute file path to the GGUF model file

**Behavior**:
- Loads model in embedding mode (no text generation)
- Optimized for `embed()` calls
- Typically faster initialization than `initGenerateModel()`

**Example**:
```kotlin
val modelPath = LlamaBridge.getModelPath("all-minilm-L6-v2.gguf")
LlamaBridge.initModel(modelPath)
val embedding = LlamaBridge.embed("Kotlin Multiplatform")
```

**Notes**:
- Use dedicated embedding models (BERT, Sentence Transformers, etc.)
- Generation models can also embed, but may be inefficient

### Text Generation (Non-Streaming)

#### `generate(prompt: String): String`

Generates text from a prompt and returns the complete result.

```kotlin
fun generate(prompt: String): String
```

**Parameters**:
- `prompt`: Input text to generate from

**Returns**: Generated text as a single `String`

**Example**:
```kotlin
val output = LlamaBridge.generate("Explain Kotlin sealed classes in one sentence.")
// Output: "Sealed classes in Kotlin restrict inheritance to a finite set of subclasses..."
```

**Notes**:
- Synchronous — blocks until generation completes
- May take 5-60+ seconds depending on model and output length
- Use `generateStream()` for incremental output

#### `generateWithContext(systemPrompt: String, conversationHistory: List<String>, userMessage: String): String`

Generates text with system prompt and conversation history for context-aware responses.

```kotlin
fun generateWithContext(
    systemPrompt: String,
    conversationHistory: List<String>,
    userMessage: String
): String
```

**Parameters**:
- `systemPrompt`: System instruction defining assistant behavior
- `conversationHistory`: List of prior exchanges (format: `"User: ...\nAssistant: ..."`)
- `userMessage`: Current user message

**Returns**: Assistant's response as a `String`

**Example**:
```kotlin
val systemPrompt = "You are a Kotlin expert. Answer concisely."
val history = listOf(
    "User: What is a data class?\nAssistant: A data class in Kotlin...",
    "User: Can it be abstract?\nAssistant: No, data classes cannot be abstract..."
)
val response = LlamaBridge.generateWithContext(
    systemPrompt,
    history,
    "What about sealed classes?"
)
```

**Notes**:
- Total tokens (system + history + user) must fit in model's context window
- Older messages may be truncated if exceeding limit
- Format history consistently with model's training format

### Text Generation (Streaming)

#### `generateStream(prompt: String, stream: GenStream)`

Generates text incrementally, invoking callbacks for each token.

```kotlin
fun generateStream(
    prompt: String,
    stream: GenStream
)
```

**Parameters**:
- `prompt`: Input text to generate from
- `stream`: Callback interface receiving incremental output

**Example**:
```kotlin
LlamaBridge.generateStream(
    prompt = "Write a haiku about coding.",
    stream = object : GenStream {
        override fun onDelta(delta: String) {
            print(delta) // Print each token as it arrives
        }
        override fun onComplete() {
            println("\n[Generation complete]")
        }
        override fun onError(error: String) {
            println("Error: $error")
        }
    }
)
```

**Notes**:
- Callbacks run on a background thread
- Use `withContext(Dispatchers.Main)` for UI updates
- `onDelta()` called multiple times per second during generation

#### `generateStreamWithContext(systemPrompt: String, conversationHistory: List<String>, userMessage: String, stream: GenStream)`

Streaming generation with conversation context.

```kotlin
fun generateStreamWithContext(
    systemPrompt: String,
    conversationHistory: List<String>,
    userMessage: String,
    stream: GenStream
)
```

**Parameters**: Same as `generateWithContext()` + `stream` callback

**Example**:
```kotlin
LlamaBridge.generateStreamWithContext(
    systemPrompt = "You are a helpful assistant.",
    conversationHistory = listOf("User: Hello!\nAssistant: Hi! How can I help?"),
    userMessage = "Tell me a joke.",
    stream = object : GenStream {
        val buffer = StringBuilder()
        override fun onDelta(delta: String) {
            buffer.append(delta)
            updateUI(buffer.toString())
        }
        override fun onComplete() {
            saveMessage(buffer.toString())
        }
        override fun onError(error: String) {
            showError(error)
        }
    }
)
```

### Schema-Constrained JSON Generation

#### `generateJson(prompt: String, jsonSchema: String): String`

Generates JSON conforming to a specified schema.

```kotlin
fun generateJson(
    prompt: String,
    jsonSchema: String
): String
```

**Parameters**:
- `prompt`: Instruction for what JSON to generate
- `jsonSchema`: JSON Schema (draft-07) defining the expected structure

**Returns**: JSON string conforming to the schema

**Example**:
```kotlin
val schema = """
{
  "type": "object",
  "properties": {
    "title": {"type": "string"},
    "priority": {"type": "string", "enum": ["high", "medium", "low"]},
    "tags": {"type": "array", "items": {"type": "string"}}
  },
  "required": ["title", "priority"]
}
"""

val json = LlamaBridge.generateJson(
    prompt = "Create a task for reviewing the codebase.",
    jsonSchema = schema
)
// Output: {"title": "Code Review", "priority": "high", "tags": ["review", "quality"]}
```

**Notes**:
- Uses llama.cpp's grammar-based sampling for guaranteed schema compliance
- More reliable than instructing model to "output JSON"
- Supports nested objects, arrays, enums, and required fields
- Refer to JSON Schema spec for full syntax

#### `generateJsonWithContext(systemPrompt: String, conversationHistory: List<String>, userMessage: String, jsonSchema: String): String`

Schema-constrained JSON generation with conversation context.

```kotlin
fun generateJsonWithContext(
    systemPrompt: String,
    conversationHistory: List<String>,
    userMessage: String,
    jsonSchema: String
): String
```

**Parameters**: Combines `generateWithContext()` parameters + `jsonSchema`

**Example**:
```kotlin
val schema = """{"type": "object", "properties": {"sentiment": {"type": "string"}}}"""
val json = LlamaBridge.generateJsonWithContext(
    systemPrompt = "Analyze sentiment of user messages.",
    conversationHistory = listOf(),
    userMessage = "I love this app!",
    jsonSchema = schema
)
// Output: {"sentiment": "positive"}
```

### Embeddings

#### `embed(text: String): FloatArray`

Generates a vector embedding for the input text.

```kotlin
fun embed(text: String): FloatArray
```

**Parameters**:
- `text`: Input text to embed

**Returns**: Float array of model-specific dimension (e.g., 384, 768, 1024)

**Example**:
```kotlin
LlamaBridge.initModel("all-minilm-L6-v2.gguf")
val embedding = LlamaBridge.embed("Kotlin Multiplatform")
println("Dimension: ${embedding.size}") // e.g., 384

// Use for vector search
val query = LlamaBridge.embed("Android iOS development")
val similarity = cosineSimilarity(embedding, query)
```

**Notes**:
- Embedding models (BERT, Sentence Transformers) produce higher-quality vectors than generative models
- Common dimensions: 384 (MiniLM), 768 (BERT), 1536 (OpenAI ada-002)
- Use with vector databases (Chroma, Qdrant, Pinecone) for RAG systems

### Resource Management

#### `shutdown()`

Releases the loaded model and frees native memory.

```kotlin
fun shutdown()
```

**Behavior**:
- Unloads the current model
- Releases GPU/CPU memory
- Makes `LlamaBridge` ready to load a different model

**Example**:
```kotlin
try {
    LlamaBridge.initGenerateModel(modelPath)
    val result = LlamaBridge.generate(prompt)
    return result
} finally {
    LlamaBridge.shutdown() // Always clean up
}
```

**Notes**:
- **Critical**: Always call `shutdown()` to prevent memory leaks
- Safe to call multiple times (no-op if no model loaded)
- Required before loading a different model

#### `getModelPath(filename: String): String`

Platform-specific helper to locate model files.

```kotlin
fun getModelPath(filename: String): String
```

**Parameters**:
- `filename`: Model filename (e.g., `"phi-2.Q4_0.gguf"`)

**Returns**: Absolute path to the model file

**Platform Behavior**:
- **Android**: Returns path in `assets/` or `filesDir/models/`
- **iOS**: Returns path in app bundle or documents directory
- **Desktop**: Returns path in working directory or `~/.llamatik/models/`

**Example**:
```kotlin
// Android
val path = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")
// Returns: "/data/data/com.app/files/models/phi-2.Q4_0.gguf"

// iOS
val path = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")
// Returns: "/var/mobile/Containers/Bundle/phi-2.Q4_0.gguf"
```

**Notes**:
- Abstracts platform differences
- May require copying models to expected locations during app initialization

## Callback Interface: GenStream

Interface for receiving streaming generation events.

```kotlin
interface GenStream {
    fun onDelta(delta: String)
    fun onComplete()
    fun onError(error: String)
}
```

### `onDelta(delta: String)`

Called for each generated token.

**Parameters**:
- `delta`: The newly generated text fragment

**Behavior**:
- Invoked repeatedly during generation (10-50+ times per second)
- `delta` may be a single character, word, or token

**Example**:
```kotlin
override fun onDelta(delta: String) {
    outputBuffer.append(delta)
    updateUI(outputBuffer.toString())
}
```

### `onComplete()`

Called when generation finishes successfully.

**Behavior**:
- Invoked once at the end of generation
- No more `onDelta()` calls after this

**Example**:
```kotlin
override fun onComplete() {
    println("Generation finished.")
    saveToDatabase(outputBuffer.toString())
}
```

### `onError(error: String)`

Called if generation fails.

**Parameters**:
- `error`: Error message describing the failure

**Behavior**:
- Invoked once if an error occurs
- No `onComplete()` call after this

**Example**:
```kotlin
override fun onError(error: String) {
    Log.e("Llamatik", "Generation failed: $error")
    showUserError("Failed to generate response")
}
```

**Common Errors**:
- `"Model not initialized"` — forgot to call `initGenerateModel()`
- `"Context length exceeded"` — prompt + history too long
- `"Invalid model file"` — GGUF file corrupted or incompatible version
- `"Out of memory"` — device RAM insufficient for model

## Configuration (Advanced)

Llamatik exposes llama.cpp sampling parameters via environment variables or platform-specific APIs (undocumented in v0.15.0 — may require source inspection or future API updates).

**Typical Parameters** (not directly exposed in current API):
- `temperature`: Randomness (0.0 = deterministic, 1.0 = default, 2.0 = very random)
- `top_k`: Sample from top-k tokens (default: 40)
- `top_p`: Nucleus sampling threshold (default: 0.9)
- `repeat_penalty`: Penalize repeated tokens (default: 1.1)
- `max_tokens`: Max output length (default: model-specific, e.g., 2048)

**Workaround** (until official API):
```kotlin
// Modify prompt to guide behavior
val deterministicPrompt = "Answer in exactly one sentence. Be concise."
val creativePrompt = "Be creative and verbose."
```

## Platform-Specific Notes

### Android

**Model Loading**:
```kotlin
// Copy from assets to internal storage on first run
context.assets.open("phi-2.Q4_0.gguf").use { input ->
    val outputFile = File(context.filesDir, "models/phi-2.Q4_0.gguf")
    outputFile.parentFile?.mkdirs()
    outputFile.outputStream().use { output ->
        input.copyTo(output)
    }
}
val modelPath = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")
```

**Permissions**: No special permissions required (models stored in app-private storage).

**ProGuard**: Add keep rules if using R8/ProGuard:
```pro
-keep class com.llamatik.** { *; }
```

### iOS

**Model Bundling**:
1. Add `.gguf` files to Xcode project
2. Set "Copy Bundle Resources" in Build Phases
3. Access via `NSBundle`:

```kotlin
// iosMain
actual fun getModelPath(filename: String): String {
    return NSBundle.mainBundle.pathForResource(
        filename.substringBeforeLast("."),
        ofType = filename.substringAfterLast(".")
    ) ?: throw FileNotFoundException("Model $filename not in bundle")
}
```

**Memory Warnings**: Monitor via `NSNotificationCenter` and call `shutdown()` if low memory.

### Desktop

**Model Discovery**:
```kotlin
// desktopMain
actual fun getModelPath(filename: String): String {
    val userDir = System.getProperty("user.home")
    val modelDir = File(userDir, ".llamatik/models")
    modelDir.mkdirs()
    return File(modelDir, filename).absolutePath
}
```

**JNI**: Llamatik bundles native libraries for Windows, macOS, and Linux. No manual setup required.

## Model Recommendations

| Use Case | Model | Size | Quantization | Notes |
|----------|-------|------|--------------|-------|
| **Chatbot** | Phi-2 | 2.7B | Q4_0 | Fast, good quality |
| **Code Gen** | CodeLlama-7B | 7B | Q5_1 | Better syntax accuracy |
| **Summarization** | Mistral-7B | 7B | Q4_0 | Excellent compression |
| **Embeddings** | all-MiniLM-L6-v2 | 22M | F16 | 384-dim vectors |
| **RAG** | E5-small-v2 | 33M | F16 | 384-dim, high quality |
| **JSON Extraction** | LLaMA-2-13B | 13B | Q8_0 | Complex schema support |

**Download Sources**:
- Hugging Face: [https://huggingface.co/models?library=gguf](https://huggingface.co/models?library=gguf)
- TheBloke: [https://huggingface.co/TheBloke](https://huggingface.co/TheBloke) (pre-quantized GGUF models)
- Official repos: LLaMA, Mistral, Phi (convert with llama.cpp tools)

## Version Compatibility

| Llamatik Version | llama.cpp Version | Kotlin Version | Compose Version |
|------------------|-------------------|----------------|-----------------|
| 0.15.0 | ~b2267 | 1.9.0+ | 1.5.0+ |
| 0.14.x | ~b1900 | 1.8.0+ | 1.4.0+ |
| 0.13.x | ~b1700 | 1.7.20+ | 1.3.0+ |

**Breaking Changes**:
- v0.15.0: Updated llama.cpp backend, improved GGUF compatibility
- v0.14.0: Added `generateJson()` with schema support
- v0.13.0: Initial stable release

## Troubleshooting

### Model Fails to Load

**Error**: `"Invalid model file"` or `"Unsupported model format"`

**Solutions**:
- Verify GGUF format (not `.bin`, `.safetensors`, or old GGML)
- Check llama.cpp compatibility (newer models may require Llamatik update)
- Re-download model (file may be corrupted)

### Out of Memory

**Error**: App crashes or `"Out of memory"` exception

**Solutions**:
- Use smaller model (7B → 3B → 1B)
- Use higher quantization (F16 → Q8_0 → Q4_0)
- Increase Android `largeHeap` in manifest
- Profile with Android Studio Memory Profiler

### Slow Generation

**Symptom**: Taking 10+ seconds per response

**Solutions**:
- Use quantized model (Q4_0 fastest)
- Reduce prompt length
- Enable GPU acceleration (if supported in future Llamatik versions)
- Profile with Instruments (iOS) or Android Profiler

### iOS Build Errors

**Error**: `"Undefined symbols for architecture arm64"`

**Solutions**:
- Ensure Llamatik framework is linked in Xcode
- Check "Embed & Sign" in General → Frameworks
- Clean build folder (Cmd+Shift+K)

## Future API (Tentative)

Planned features for upcoming Llamatik versions:

- **Configuration API**: `setTemperature()`, `setTopK()`, `setMaxTokens()`
- **Batch Inference**: `generateBatch(prompts: List<String>)`
- **Fine-Tuning**: On-device LoRA adapter loading
- **GPU Acceleration**: Metal (iOS), Vulkan (Android), CUDA (Desktop)
- **Model Management**: Download and cache models from Hugging Face
- **Multi-Model**: Load multiple models simultaneously

Check Llamatik GitHub releases for updates: [https://github.com/ferranpons/Llamatik/releases](https://github.com/ferranpons/Llamatik/releases)
