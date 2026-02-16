---
name: llamatik-expert
description: Expert guidance on Llamatik for on-device LLM inference in KMP. Use for local AI models, offline-first LLM integration, text generation, embeddings, RAG systems, and hybrid local/remote inference.
---

# Llamatik Expert Skill (v0.15.0)

## Overview

Llamatik is a Kotlin Multiplatform library that enables on-device Large Language Model (LLM) inference through llama.cpp integration. It provides a unified API for running AI models locally across Android, iOS, and Desktop platforms with optional remote inference fallback.

**Key Capabilities**:
- Fully offline LLM inference (no network required)
- GGUF model format support (Mistral, Phi, LLaMA, etc.)
- Native C++ performance via Kotlin/Native bindings
- Streaming and non-streaming text generation
- Schema-constrained JSON generation
- Embeddings for vector search and RAG applications
- Hybrid local/remote architecture with seamless API

## When to Use

- **On-Device AI**: Running AI models locally for privacy and offline-first apps
- **Chatbots & Assistants**: Building conversational interfaces without cloud dependency
- **Code Generation**: Local code completion, documentation generation, or refactoring suggestions
- **Test Generation**: LLM-powered test case generation and validation (integrate with testing-expert)
- **Content Moderation**: Offline content analysis and classification
- **RAG Systems**: Local retrieval-augmented generation with embeddings
- **Game AI**: Procedural dialogue generation and NPC behavior
- **Hybrid Apps**: Apps that gracefully degrade from remote to local inference
- **Privacy-Critical Apps**: Healthcare, finance, or legal apps requiring data locality

## Quick Reference

For detailed API reference, see [reference.md](reference.md).
For production examples, see [examples.md](examples.md).

## Core Concepts

### Model Loading

Llamatik uses GGUF-format models loaded from the device filesystem:

```kotlin
// Get model path (platform-specific)
val modelPath = LlamaBridge.getModelPath("phi-2.Q4_0.gguf")

// Initialize model for generation
LlamaBridge.initGenerateModel(modelPath)

// Or initialize for embeddings
LlamaBridge.initModel(modelPath)
```

**Model Storage**:
- Android: `assets/` or internal storage
- iOS: App bundle or documents directory
- Desktop: Working directory or specified path

### Text Generation

**Non-Streaming**:
```kotlin
val output = LlamaBridge.generate("Explain Kotlin coroutines in one sentence.")
```

**Streaming**:
```kotlin
LlamaBridge.generateStream(
    prompt = "Write a haiku about coding.",
    stream = object : GenStream {
        override fun onDelta(delta: String) {
            print(delta) // Incremental output
        }
        override fun onComplete() {
            println("\nDone!")
        }
        override fun onError(error: String) {
            println("Error: $error")
        }
    }
)
```

### Context-Aware Generation

Use system prompts and conversation history:

```kotlin
LlamaBridge.generateWithContext(
    systemPrompt = "You are a helpful Kotlin expert.",
    conversationHistory = listOf(
        "User: What is a sealed class?",
        "Assistant: A sealed class restricts which classes can inherit from it...",
        "User: Show me an example."
    ),
    userMessage = "Now explain when objects in Kotlin?"
)
```

### Schema-Constrained JSON

Generate structured output with JSON schema validation:

```kotlin
val jsonSchema = """
{
  "type": "object",
  "properties": {
    "name": {"type": "string"},
    "age": {"type": "number"},
    "skills": {"type": "array", "items": {"type": "string"}}
  },
  "required": ["name", "age"]
}
"""

val json = LlamaBridge.generateJson(
    prompt = "Generate a developer profile.",
    jsonSchema = jsonSchema
)
// Output: {"name": "Alice", "age": 28, "skills": ["Kotlin", "Swift"]}
```

### Embeddings

Generate vector embeddings for semantic search and RAG:

```kotlin
val embedding = LlamaBridge.embed("Kotlin Multiplatform mobile development")
// Returns: FloatArray of model-specific dimension (e.g., 384 or 768)

// Use with vector databases for RAG
val similarDocs = vectorDb.search(embedding, topK = 5)
```

### Resource Management

Always clean up models when done:

```kotlin
try {
    LlamaBridge.initGenerateModel(modelPath)
    val result = LlamaBridge.generate(prompt)
    // Use result
} finally {
    LlamaBridge.shutdown() // Release native resources
}
```

## Platform Support

| Platform | Min Version | Notes |
|----------|-------------|-------|
| Android | API 26 (8.0) | AAR with native libs bundled |
| iOS | 16.6+ | Kotlin/Native with llama.cpp integration |
| Desktop | JVM 17+ | JNI bindings to native library |

**Model Compatibility**:
- GGUF format (unified format for llama.cpp)
- Quantization levels: Q4_0, Q4_1, Q5_0, Q5_1, Q8_0, F16, F32
- Model families: LLaMA, Mistral, Phi, GPT-J, Falcon, etc.

## Gradle Setup

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// build.gradle.kts (shared module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.llamatik:library:0.15.0")
        }
    }
}
```

**No additional KSP or compiler plugins required** — Llamatik is a pure runtime library.

## Integration with KMP Skills

### With testing-expert

Use Llamatik to generate test cases:

```kotlin
val testPrompt = """
Generate unit tests for this Kotlin function:
fun calculateDiscount(price: Double, percent: Int): Double {
    return price * (1 - percent / 100.0)
}
"""

val tests = LlamaBridge.generate(testPrompt)
// AI generates test cases covering edge cases
```

### With quality-expert

Code review suggestions:

```kotlin
val codeReviewPrompt = """
Review this code for Kotlin best practices:
${sourceCode}
Provide actionable suggestions.
"""

val review = LlamaBridge.generate(codeReviewPrompt)
```

### With security-expert

Analyze code for vulnerabilities:

```kotlin
val securityPrompt = """
Analyze this Kotlin code for security issues:
${code}
Focus on: SQL injection, XSS, path traversal, hardcoded secrets.
"""

val securityReport = LlamaBridge.generate(securityPrompt)
```

### With architecture-patterns-expert

Generate architecture documentation:

```kotlin
val archPrompt = """
Given this codebase structure: ${fileTree}
Generate a Mermaid diagram showing the module dependencies.
"""

val mermaid = LlamaBridge.generateJson(archPrompt, mermaidSchema)
```

## Hybrid Local/Remote Architecture

Llamatik supports optional remote inference via HTTP:

```kotlin
// Use llamatik-backend (https://github.com/ferranpons/llamatik-backend)
// or any compatible OpenAI-style endpoint

// Same API, different backend:
val localResult = LlamaBridge.generate(prompt)   // On-device
val remoteResult = remoteClient.generate(prompt) // Cloud fallback
```

**Use Cases**:
- Start with local inference for fast responses
- Fall back to remote for complex queries exceeding local model capacity
- A/B test local vs. cloud performance
- Graceful degradation when device resources are constrained

## Core Rules

1. **Model Lifecycle Management**: Always call `shutdown()` to release native memory when done with a model.
2. **One Model at a Time**: Llamatik loads one model per bridge instance. To switch models, shut down and reinitialize.
3. **Thread Safety**: `LlamaBridge` is **not thread-safe**. Use from a single thread or synchronize access (e.g., via `Mutex` or `Actor`).
4. **Context Length Limits**: Models have max context windows (e.g., 2048, 4096, 8192 tokens). Exceeding this causes truncation or errors.
5. **Streaming Callbacks**: `GenStream` callbacks run on a background thread. Use `withContext(Dispatchers.Main)` for UI updates.
6. **GGUF Version Compatibility**: Use models compatible with your llama.cpp version. Llamatik 0.15.0 uses llama.cpp ~b2267 (verify from release notes).
7. **Quantization Trade-offs**: Lower quantization (Q4_0) = smaller models but lower quality. F16/F32 = highest quality but larger size.
8. **Platform-Specific Paths**: Model paths differ by platform. Use `getModelPath()` or platform `expect`/`actual` to abstract.
9. **No Network by Default**: Llamatik has **zero network dependencies** by design. Remote inference requires separate HTTP client setup.
10. **License Compliance**: Llamatik is MIT. Verify model licenses (LLaMA, Mistral, etc.) allow your use case (commercial, redistribution).

## Common Pitfalls

### 1. Forgetting to Call `shutdown()`

**Problem**: Native memory leaks accumulate across model loads.

```kotlin
// BAD
fun runModel(prompt: String): String {
    LlamaBridge.initGenerateModel(modelPath)
    return LlamaBridge.generate(prompt)
    // shutdown() never called — memory leak!
}
```

**Solution**: Use `try-finally` or Kotlin's `use`-like pattern.

```kotlin
// GOOD
fun runModel(prompt: String): String {
    try {
        LlamaBridge.initGenerateModel(modelPath)
        return LlamaBridge.generate(prompt)
    } finally {
        LlamaBridge.shutdown()
    }
}
```

### 2. Concurrent Access to `LlamaBridge`

**Problem**: `LlamaBridge` is not thread-safe. Concurrent calls cause crashes.

```kotlin
// BAD
GlobalScope.launch { LlamaBridge.generate("prompt1") }
GlobalScope.launch { LlamaBridge.generate("prompt2") }
// Race condition — undefined behavior!
```

**Solution**: Serialize access via `Mutex` or use separate instances (if supported in future versions).

```kotlin
// GOOD
private val llamaMutex = Mutex()

suspend fun safeGenerate(prompt: String): String = llamaMutex.withLock {
    LlamaBridge.generate(prompt)
}
```

### 3. Exceeding Context Window

**Problem**: Passing more tokens than the model supports truncates context or fails.

```kotlin
// BAD — prompt + history > 4096 tokens
val longHistory = List(100) { "User: Question $it\nAssistant: Answer $it" }
LlamaBridge.generateWithContext("system", longHistory, "user message")
// Older messages silently dropped or error thrown
```

**Solution**: Implement sliding window or summarization for long conversations.

```kotlin
// GOOD
fun trimHistory(history: List<String>, maxTokens: Int): List<String> {
    // Keep only recent messages that fit in context
    return history.takeLast(maxTokens / 50) // Rough token estimate
}
```

### 4. Blocking UI Thread with Generation

**Problem**: `generate()` is synchronous and blocks for seconds/minutes.

```kotlin
// BAD — freezes UI
Button(onClick = {
    val result = LlamaBridge.generate(longPrompt) // BLOCKS!
    displayResult(result)
}) { Text("Generate") }
```

**Solution**: Use `withContext(Dispatchers.IO)` or coroutines.

```kotlin
// GOOD
Button(onClick = {
    coroutineScope.launch {
        val result = withContext(Dispatchers.IO) {
            LlamaBridge.generate(longPrompt)
        }
        displayResult(result)
    }
}) { Text("Generate") }
```

### 5. Not Handling Streaming Errors

**Problem**: `GenStream.onError()` ignored, silent failures.

```kotlin
// BAD
LlamaBridge.generateStream(prompt, object : GenStream {
    override fun onDelta(delta: String) { /* ... */ }
    override fun onComplete() { /* ... */ }
    override fun onError(error: String) {
        // Empty — error swallowed
    }
})
```

**Solution**: Log errors, show user feedback, or retry.

```kotlin
// GOOD
override fun onError(error: String) {
    logger.error("LLM generation failed: $error")
    showSnackbar("Failed to generate response. Try again?")
}
```

### 6. Using Incompatible Model Formats

**Problem**: Loading non-GGUF models (e.g., PyTorch `.bin`, HuggingFace `.safetensors`).

```kotlin
// BAD
LlamaBridge.initGenerateModel("model.bin") // Not GGUF!
// Crashes or "invalid model" error
```

**Solution**: Convert models to GGUF using `llama.cpp` tools or download pre-converted models.

```bash
# Convert with llama.cpp
python convert.py model.bin --outfile model.gguf --outtype q4_0
```

### 7. Ignoring Quantization Impact

**Problem**: Using highly quantized models (Q4_0) for quality-sensitive tasks.

```kotlin
// BAD for code generation
val model = "codellama-7b-Q4_0.gguf" // Low quality
val code = LlamaBridge.generate("Write a production-ready REST API in Kotlin")
// Output has syntax errors, incomplete logic
```

**Solution**: Use F16 or Q8_0 for quality-critical tasks, Q4_0 for speed.

| Quantization | Size | Quality | Speed | Use Case |
|--------------|------|---------|-------|----------|
| F32 | 100% | Highest | Slowest | Benchmarking |
| F16 | 50% | Very High | Slow | Production quality |
| Q8_0 | 25% | High | Medium | Balanced |
| Q5_1 | 17% | Medium | Fast | General use |
| Q4_0 | 12% | Lower | Fastest | Chatbots, summaries |

### 8. Not Validating JSON Schema Output

**Problem**: Assuming `generateJson()` always produces valid JSON.

```kotlin
// BAD
val json = LlamaBridge.generateJson(prompt, schema)
val data = Json.decodeFromString<MyData>(json) // May throw!
```

**Solution**: Wrap in `try-catch` and validate structure.

```kotlin
// GOOD
val json = LlamaBridge.generateJson(prompt, schema)
val data = try {
    Json.decodeFromString<MyData>(json)
} catch (e: SerializationException) {
    logger.error("Invalid JSON from LLM: $json", e)
    null
}
```

### 9. Hardcoding Model Paths

**Problem**: Model path differs across platforms, breaks iOS or Desktop builds.

```kotlin
// BAD
val modelPath = "/data/models/phi-2.gguf" // Android-specific!
```

**Solution**: Use `expect`/`actual` or `getModelPath()`.

```kotlin
// commonMain
expect fun getModelPath(filename: String): String

// androidMain
actual fun getModelPath(filename: String): String {
    return context.filesDir.resolve("models/$filename").absolutePath
}

// iosMain
actual fun getModelPath(filename: String): String {
    return NSBundle.mainBundle.pathForResource(filename, ofType = null) ?: ""
}
```

### 10. Not Profiling Model Performance

**Problem**: Using large models on low-end devices without performance testing.

```kotlin
// BAD — 13B model on 4GB RAM phone
LlamaBridge.initGenerateModel("llama-2-13b-Q4_0.gguf") // OOM!
```

**Solution**: Profile memory and latency, use smaller models for resource-constrained devices.

```kotlin
// GOOD
val modelPath = if (availableRAM > 6_000_000_000) {
    "llama-2-13b-Q4_0.gguf" // High-end devices
} else {
    "phi-2-Q4_0.gguf" // Low-end devices (2.7B params)
}
```

**Performance Targets**:
- **Mobile**: Aim for <5s first token, <50ms/token streaming
- **Desktop**: <2s first token, <20ms/token streaming
- **Memory**: Model size × 1.2 should fit in free RAM

## See Also

- [testing-expert](../testing-expert/SKILL.md) — LLM-powered test generation
- [quality-expert](../quality-expert/SKILL.md) — AI-assisted code review
- [security-expert](../security-expert/SKILL.md) — Security analysis with LLMs
- [performance-expert](../performance-expert/SKILL.md) — Profiling on-device AI performance
- [architecture-patterns-expert](../architecture-patterns-expert/SKILL.md) — Integrating AI into KMP architecture
- [ios-interop-expert](../ios-interop-expert/SKILL.md) — Platform-specific model loading on iOS
- [android-platform-expert](../android-platform-expert/SKILL.md) — Android asset management for models
