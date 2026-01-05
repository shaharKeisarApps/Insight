---
name: koog-expert
description: Elite expertise for Koog - JetBrains' Kotlin-based AI agent framework. Use when creating AI agents, defining tools, configuring LLM providers, implementing workflows, or integrating MCP servers.
category: ai-agents
---

You are an expert in Koog, JetBrains' Kotlin-based framework for building and running AI agents.

## When to Use This Skill

Invoke this skill when:
- Creating AI agents with Koog framework
- Defining custom tools using `@Tool` and `@LLMDescription` annotations
- Configuring LLM providers (OpenAI, Anthropic, Google, Ollama, DeepSeek)
- Implementing agent strategies and workflows
- Setting up MCP (Model Context Protocol) integration
- Building multi-provider or multi-agent systems
- Handling structured outputs and tool registries

## Core Concepts

### 1. AI Agent Creation

Basic agent setup with tools:

```kotlin
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val toolRegistry = ToolRegistry {
    tools(MyCustomTools())
}

val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    toolRegistry = toolRegistry
)

val result = agent.run("Your query here")
```

### 2. Custom Tool Definition

Tools are defined using annotated classes:

```kotlin
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Mathematical operations")
class MathTools : ToolSet {
    @Tool
    @LLMDescription("Multiplies two numbers and returns the result")
    fun multiply(
        @LLMDescription("First number") a: Int,
        @LLMDescription("Second number") b: Int
    ): Int = a * b

    @Tool
    @LLMDescription("Adds two numbers and returns the result")
    fun add(a: Int, b: Int): Int = a + b
}
```

### 3. LLM Provider Configuration

**OpenAI:**
```kotlin
val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
// Models: OpenAIModels.Chat.GPT4o, GPT4oMini, etc.
```

**Anthropic:**
```kotlin
val executor = simpleAnthropicExecutor(System.getenv("ANTHROPIC_API_KEY"))
// Models: AnthropicModels.Sonnet_4, Claude_3_5_Sonnet, etc.
```

**Google:**
```kotlin
val executor = simpleGoogleExecutor(System.getenv("GOOGLE_API_KEY"))
// Models: GoogleModels.Gemini2_5Pro, etc.
```

**Ollama (Local):**
```kotlin
val executor = simpleOllamaAIExecutor() // No API key needed
// Models: OllamaModels.Meta.LLAMA_3_2, etc.
```

**Multi-Provider:**
```kotlin
val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.OpenAI to OpenAILLMClient(openaiKey),
    LLMProvider.Anthropic to AnthropicLLMClient(anthropicKey)
)
```

### 4. Agent Strategies

**Functional Strategy (Simple):**
```kotlin
import ai.koog.agents.core.agent.functionalStrategy

val agent = AIAgent<String, String>(
    // ... config
    strategy = functionalStrategy { input ->
        var responses = requestLLMMultiple(input)
        while (responses.containsToolCalls()) {
            val pendingCalls = extractToolCalls(responses)
            val results = executeMultipleTools(pendingCalls)
            responses = sendMultipleToolResults(results)
        }
        responses.single().asAssistantMessage().content
    }
)
```

**Graph-Based Strategy (Complex Workflows):**
```kotlin
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy

val agentStrategy = strategy("calculator") {
    val nodeSendInput by nodeLLMRequest()
    val nodeExecuteTool by nodeExecuteTool()
    val nodeSendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo nodeSendInput)
    edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
}
```

**Chat Strategy (Pre-built):**
```kotlin
import ai.koog.agents.ext.agent.chatAgentStrategy
val agent = AIAgent(strategy = chatAgentStrategy(), /* ... */)
```

### 5. Agent Configuration

```kotlin
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.params.LLMParams

val agentConfig = AIAgentConfig(
    prompt = prompt("agent-name", params = LLMParams(temperature = 0.7)) {
        system("Your system prompt here")
    },
    model = OpenAIModels.Chat.GPT4o,
    maxAgentIterations = 50
)
```

### 6. Event Handling

```kotlin
import ai.koog.agents.features.eventHandler.feature.EventHandler

val agent = AIAgent(
    // ... config
    installFeatures = {
        install(EventHandler) {
            onAgentStarting { ctx -> println("Starting: ${ctx.agent.id}") }
            onAgentCompleted { ctx -> println("Result: ${ctx.result}") }
            onToolCallStarting { ctx -> println("Calling tool: ${ctx.tool.name}") }
        }
    }
)
```

### 7. MCP Integration

**Stdio Transport (Docker/Process):**
```kotlin
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport

val process = ProcessBuilder("docker", "run", "-i", "mcp/server").start()
val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultStdioTransport(process)
)
```

**SSE Transport (Web Service):**
```kotlin
import ai.koog.agents.mcp.defaultSseTransport

val toolRegistry = McpToolRegistryProvider.fromTransport(
    transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:8931")
)
```

### 8. Structured Output

```kotlin
import ai.koog.prompt.structure.StructureFixingParser
import kotlinx.serialization.Serializable

@Serializable
data class ClassifiedRequest(val type: String, val content: String)

val classifyNode by nodeLLMRequestStructured<ClassifiedRequest>(
    examples = listOf(ClassifiedRequest("greeting", "Hello")),
    fixingParser = StructureFixingParser(
        fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
        retries = 2
    )
)
```

## Gradle Dependencies

**Core Dependencies:**
```kotlin
// build.gradle.kts
dependencies {
    // Core agent framework
    implementation("ai.koog:koog-agents:0.5.2")

    // LLM Clients (choose as needed)
    implementation("ai.koog.prompt:prompt-executor-openai-client:0.5.2")
    implementation("ai.koog.prompt:prompt-executor-anthropic-client:0.5.2")
    implementation("ai.koog.prompt:prompt-executor-google-client:0.5.2")

    // MCP Integration (optional)
    implementation("ai.koog:koog-mcp:0.5.2")

    // Spring Boot Integration (optional)
    implementation("ai.koog:koog-spring-boot-starter:0.5.2")
}
```

**Repository:**
```kotlin
repositories {
    mavenCentral()
}
```

## Best Practices

1. **Tool Design**: Keep tools focused and well-documented with `@LLMDescription`
2. **Error Handling**: Use event handlers to monitor agent lifecycle
3. **Iteration Limits**: Set appropriate `maxAgentIterations` to prevent infinite loops
4. **API Keys**: Always use environment variables for sensitive credentials
5. **Strategy Selection**: Use `functionalStrategy` for simple tasks, graph-based for complex workflows
6. **Temperature**: Lower values (0.0-0.3) for deterministic tasks, higher (0.7-1.0) for creative tasks

## Common Patterns

### Request-Execute-Respond Loop
```kotlin
functionalStrategy { input ->
    var responses = requestLLMMultiple(input)
    while (responses.containsToolCalls()) {
        val results = executeMultipleTools(extractToolCalls(responses))
        responses = sendMultipleToolResults(results)
    }
    responses.single().asAssistantMessage().content
}
```

### Subgraph Routing
```kotlin
val subgraphA by subgraphWithTask<Input, Output>(tools = toolsA) { "Task A: $it" }
val subgraphB by subgraphWithTask<Input, Output>(tools = toolsB) { "Task B: $it" }

edge(classifier forwardTo subgraphA onCondition { it.type == "A" })
edge(classifier forwardTo subgraphB onCondition { it.type == "B" })
```

## Resources

- Documentation: https://docs.koog.ai/
- GitHub: https://github.com/JetBrains/koog
- Latest Version: 0.5.2
