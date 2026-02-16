---
name: kotlinx-serialization-expert
description: Expert guidance on kotlinx.serialization for KMP. Use for JSON configuration, custom serializers, polymorphism, Protobuf, and integration with Ktor and SQLDelight.
---

# kotlinx.serialization Expert Skill

## Overview

kotlinx.serialization is a compiler plugin-based, multiplatform serialization framework for Kotlin. Unlike reflection-based libraries (Gson, Moshi, Jackson), it generates serializers at compile time via the Kotlin compiler plugin, making it the only serialization solution that works across all KMP targets (Android, iOS, Desktop, Web, Server).

## When to Use

- **API Responses**: Parsing JSON from REST APIs via Ktor.
- **Database Storage**: Serializing complex objects into SQLDelight text columns.
- **IPC / Deep Links**: Encoding structured data into URI parameters or Bundles.
- **Caching**: Persisting typed objects to disk or Store5 SourceOfTruth.
- **Protobuf / CBOR**: When binary formats are needed for performance or interop.

## Quick Reference

See [reference.md](reference.md) for annotation catalog, Json configuration flags, and Protobuf format.
See [examples.md](examples.md) for production-ready code patterns.

## Core Rules

1. **Always annotate with `@Serializable`**. The compiler plugin generates a serializer only for annotated classes. Forgetting this is the number-one source of runtime crashes.

2. **Configure `Json` explicitly**. Never use `Json.Default` in production. Always create a configured instance:
   ```kotlin
   val AppJson = Json {
       ignoreUnknownKeys = true
       explicitNulls = false
       encodeDefaults = true
   }
   ```

3. **Never use Gson or Moshi in KMP**. They are JVM-only and rely on reflection. kotlinx.serialization is the only multiplatform option.

4. **Use `@SerialName` for API stability**. Decouple Kotlin property names from wire format keys so renaming a property does not break deserialization.

5. **Provide default values for backward compatibility**. When the server adds or removes fields, default values prevent deserialization failures.

6. **Share the `Json` instance**. Create one configured `Json` object and inject it everywhere. This ensures consistent behavior and allows SerializersModule to be registered once.

## Polymorphic Serialization

### Sealed Classes (Closed Polymorphism)

Sealed hierarchies are the preferred approach. All subclasses are known at compile time, and the compiler plugin registers them automatically.

- Mark the sealed base and all subclasses with `@Serializable`.
- Use `@SerialName` on each subclass to control the discriminator value.
- Customize the discriminator property name with `@JsonClassDiscriminator` on the base class or globally via `classDiscriminator` in the Json builder.

### Open Polymorphism (Interfaces / Abstract Classes)

When subclasses live in different modules or are defined by consumers:

- Register subclasses explicitly via `SerializersModule { polymorphic(...) { subclass(...) } }`.
- Provide a `defaultDeserializer` for unknown types to avoid crashes.

## Integration Points

### Ktor ContentNegotiation

Install `ContentNegotiation` with `json(AppJson)` to automatically serialize request bodies and deserialize responses using your configured `Json` instance.

### SQLDelight Column Adapters

Use kotlinx.serialization to store complex objects as JSON text in SQLDelight columns. Create a generic `ColumnAdapter` that calls `encodeToString` / `decodeFromString`.

### Store5 Converters

When Fetcher output differs from SourceOfTruth format, use Store5 `Converter` backed by kotlinx.serialization to transform between network DTOs and local models.

## Best Practices

| Practice | Rationale |
|----------|-----------|
| Use `@SerialName` on every network DTO property | Decouples Kotlin names from API contract |
| Provide default values for optional fields | Backward/forward compatible deserialization |
| Use `@Transient` for computed or local-only fields | Excludes from serialization without breaking the class |
| Create a single `Json` instance per app | Consistent config, shared SerializersModule |
| Prefer sealed classes over open polymorphism | Compile-time safety, no manual registration |
| Use `@Contextual` for third-party types | Keeps serializers modular and testable |
| Use `@EncodeDefault` sparingly | Only when the receiver requires explicit default values |

## Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| Missing serialization Gradle plugin | `Serializer has not been found for type 'X'` at compile time | Add `kotlin("plugin.serialization")` to `plugins {}` block |
| `runCatching` around deserialization | Silently catches `CancellationException`, breaking structured concurrency | Use explicit `try/catch(SerializationException)` instead |
| R8/ProGuard stripping generated serializers | `SerializerNotFoundException` at runtime on release builds | Add keep rules for `kotlinx.serialization` and `@Serializable` classes |
| Using `Json.Default` everywhere | Inconsistent behavior, no `ignoreUnknownKeys` | Create and share one configured `Json` instance |
| Forgetting `@SerialName` on sealed subclasses | Discriminator uses fully-qualified class name, fragile to refactoring | Always provide explicit `@SerialName` values |
| Nullable vs. default confusion | `null` is not the same as "absent" when `explicitNulls = false` | Understand the difference: nullable allows null, default allows absence |
