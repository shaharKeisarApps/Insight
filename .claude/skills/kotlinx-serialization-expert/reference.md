# kotlinx.serialization API Reference

> Based on kotlinx.serialization **1.10.0**

## Gradle Setup

```kotlin
// root build.gradle.kts
plugins {
    kotlin("plugin.serialization") version "2.1.0" apply false
}

// module build.gradle.kts
plugins {
    kotlin("plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
            // Optional: Protobuf support
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
            // Optional: CBOR support
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.10.0")
        }
    }
}
```

## Json Configuration

```kotlin
val AppJson = Json {
    // --- Parsing tolerance ---
    ignoreUnknownKeys = true          // Skip keys not in the data class (default: false)
    isLenient = true                  // Allow unquoted strings, trailing commas (default: false)
    allowComments = true              // Skip C/Java-style comments in input (default: false)
    allowTrailingComma = true         // Allow trailing commas in objects/arrays (default: false)

    // --- Null handling ---
    explicitNulls = false             // Absent keys treated as null for nullable props (default: true)
    coerceInputValues = true          // Coerce nulls to defaults for non-nullable props (default: false)

    // --- Encoding ---
    encodeDefaults = true             // Encode properties even when they equal default (default: false)
    prettyPrint = false               // Minified output (default: false)
    prettyPrintIndent = "    "        // Indent string when prettyPrint = true (default: "    ")

    // --- Polymorphism ---
    classDiscriminator = "type"       // Property name for type discriminator (default: "type")
    classDiscriminatorMode =          // When to add discriminator:
        ClassDiscriminatorMode.POLYMORPHIC  // POLYMORPHIC (default), ALL_JSON_OBJECTS, NONE

    // --- Naming ---
    namingStrategy =                  // Global property naming strategy:
        JsonNamingStrategy.SnakeCase  // SnakeCase, KebabCase, or null (default: null)

    // --- SerializersModule ---
    serializersModule = module        // Register custom/polymorphic serializers
}
```

### All Json Configuration Flags

| Flag | Type | Default | Purpose |
|------|------|---------|---------|
| `ignoreUnknownKeys` | Boolean | false | Skip unknown JSON keys during deserialization |
| `isLenient` | Boolean | false | Relaxed parsing (unquoted strings, etc.) |
| `allowComments` | Boolean | false | Allow `//` and `/* */` comments in JSON |
| `allowTrailingComma` | Boolean | false | Allow trailing comma after last element |
| `explicitNulls` | Boolean | true | When false, absent keys map to null for nullable properties |
| `coerceInputValues` | Boolean | false | Coerce invalid values (null for non-null) to defaults |
| `encodeDefaults` | Boolean | false | Encode properties with default values |
| `prettyPrint` | Boolean | false | Format output with indentation |
| `prettyPrintIndent` | String | `"    "` | Indent string for pretty printing |
| `classDiscriminator` | String | `"type"` | Property name used for polymorphic type info |
| `classDiscriminatorMode` | Enum | POLYMORPHIC | When to emit class discriminator |
| `namingStrategy` | Strategy? | null | Global naming strategy for properties |
| `serializersModule` | Module | empty | Custom and polymorphic serializer registrations |
| `allowSpecialFloatingPointValues` | Boolean | false | Allow NaN, Infinity in JSON |
| `useArrayPolymorphism` | Boolean | false | Use `[type, value]` arrays instead of objects |
| `decodeEnumsCaseInsensitive` | Boolean | false | Case-insensitive enum deserialization |

## Annotations

### @Serializable

Marks a class for compiler-plugin serializer generation. Required on every class that participates in serialization.

```kotlin
@Serializable
data class User(
    val id: Long,
    val name: String,
    val email: String? = null
)
```

Can also reference a custom serializer:

```kotlin
@Serializable(with = InstantSerializer::class)
data class Event(val timestamp: Instant)
```

### @SerialName

Overrides the serialized name of a class or property. Critical for API stability.

```kotlin
@Serializable
data class User(
    @SerialName("user_id") val id: Long,
    @SerialName("display_name") val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null
)
```

On sealed subclasses, controls the discriminator value:

```kotlin
@Serializable
@SerialName("user_created")
data class UserCreated(val userId: String) : DomainEvent()
```

### @Transient

Excludes a property from serialization entirely. The property must have a default value.

```kotlin
@Serializable
data class Session(
    val token: String,
    @Transient val isExpired: Boolean = false  // Not serialized
)
```

### @Required

Forces a property to be present in the input even if it has a default value. Deserialization fails if the key is missing.

```kotlin
@Serializable
data class Config(
    @Required val apiKey: String = "",       // Must be in JSON even though it has a default
    val timeout: Long = 30_000L              // Optional in JSON, uses default if absent
)
```

### @EncodeDefault

Controls whether a property with a default value is included in serialized output, independent of the global `encodeDefaults` setting.

```kotlin
@Serializable
data class Settings(
    @EncodeDefault val version: Int = 1,          // Always encoded (ALWAYS mode, the default)
    @EncodeDefault(EncodeDefault.Mode.NEVER) val internal: String = "hidden"  // Never encoded
)
```

### @Contextual

Delegates serializer lookup to the `SerializersModule` at runtime. Used for third-party types that cannot be annotated with `@Serializable`.

```kotlin
@Serializable
data class Event(
    val name: String,
    @Contextual val timestamp: Instant    // Serializer provided via SerializersModule
)
```

### @JsonNames

Specifies alternative names accepted during deserialization (not used during serialization).

```kotlin
@Serializable
data class User(
    @JsonNames("user_name", "userName")
    @SerialName("name")
    val name: String
)
```

### @JsonClassDiscriminator

Overrides the class discriminator property name for a specific sealed hierarchy.

```kotlin
@Serializable
@JsonClassDiscriminator("kind")
sealed class Shape {
    @Serializable @SerialName("circle") data class Circle(val radius: Double) : Shape()
    @Serializable @SerialName("rect") data class Rect(val w: Double, val h: Double) : Shape()
}
// Output: {"kind":"circle","radius":5.0}
```

### @JsonIgnoreUnknownKeys (1.8.0+)

Per-class override for `ignoreUnknownKeys`. Ignores unknown keys only for the annotated class, even if the global `Json` instance has `ignoreUnknownKeys = false`.

```kotlin
@Serializable
@JsonIgnoreUnknownKeys
data class LegacyResponse(
    val id: Int,
    val name: String
    // Extra keys in JSON are silently ignored for this class only
)
```

### @KeepGeneratedSerializer (1.7.2+)

Retains the compiler-generated serializer even when a custom serializer is specified. Access it via `Companion.generatedSerializer()`.

```kotlin
@Serializable(with = CustomUserSerializer::class)
@KeepGeneratedSerializer
data class User(val id: Long, val name: String)

// Access generated serializer as fallback:
// User.generatedSerializer()
```

## Custom KSerializer

Implement `KSerializer<T>` for types you cannot annotate (third-party) or need special encoding.

```kotlin
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("kotlinx.datetime.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())  // ISO-8601
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}
```

Register via module:

```kotlin
val dateTimeModule = SerializersModule {
    contextual(Instant::class, InstantSerializer)
}
```

## SerializersModule

Central registry for custom and polymorphic serializers.

```kotlin
val appModule = SerializersModule {
    // Contextual serializers (for @Contextual)
    contextual(Instant::class, InstantSerializer)
    contextual(Uuid::class, UuidSerializer)

    // Polymorphic serializers (for open hierarchies)
    polymorphic(DomainEvent::class) {
        subclass(UserCreated::class)
        subclass(UserDeleted::class)
        defaultDeserializer { UnknownEvent.serializer() }
    }

    // Include other modules
    include(networkModule)
    include(databaseModule)
}
```

## Protobuf Format

```kotlin
import kotlinx.serialization.protobuf.*

@Serializable
data class Envelope(
    @ProtoNumber(1) val messageId: String,
    @ProtoNumber(2) val payload: ByteArray,
    @ProtoNumber(3) val timestamp: Long,
    @ProtoNumber(4) val tags: List<String> = emptyList()
)

// Encode / decode
val bytes: ByteArray = ProtoBuf.encodeToByteArray(envelope)
val decoded: Envelope = ProtoBuf.decodeFromByteArray(bytes)

// Configure
val protobuf = ProtoBuf {
    encodeDefaults = true
}
```

### @ProtoNumber

Assigns a stable field number for Protobuf encoding. Field numbers must be unique within a class and should never be reused after removal.

### @ProtoOneOf (1.7.0+)

Maps Protobuf `oneof` fields to sealed class hierarchies:

```kotlin
@Serializable
data class Message(
    @ProtoNumber(1) val id: Int,
    @ProtoOneOf val content: Content
)

@Serializable
sealed class Content {
    @Serializable data class Text(@ProtoNumber(2) val text: String) : Content()
    @Serializable data class Image(@ProtoNumber(3) val url: String) : Content()
}
```

## Encoding / Decoding

```kotlin
// String
val jsonString: String = AppJson.encodeToString(user)
val user: User = AppJson.decodeFromString(jsonString)

// JsonElement (for dynamic JSON)
val element: JsonElement = AppJson.encodeToJsonElement(user)
val user: User = AppJson.decodeFromJsonElement(element)

// Stream (with kotlinx-serialization-json-io)
AppJson.encodeToSink(user, sink)
val user: User = AppJson.decodeFromSource(source)
```

## R8 / ProGuard Keep Rules

```proguard
# Keep @Serializable classes and their generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep serializers for classes annotated with @Serializable
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep all @Serializable data classes (alternative: list explicitly)
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlinx.serialization core
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**
```
