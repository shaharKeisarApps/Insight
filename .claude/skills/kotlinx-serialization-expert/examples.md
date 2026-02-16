# kotlinx.serialization Production Examples

> kotlinx.serialization **1.10.0** | Ktor **3.4.0** | SQLDelight **2.2.1** | Metro DI

## 1. Complete DTO with Polymorphism and Sealed Discriminator

```kotlin
package com.example.network.dto

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/** Base response envelope from the API. */
@Serializable
data class ApiEnvelope<T>(
    @SerialName("data") val data: T,
    @SerialName("cursor") val cursor: String? = null,
    @SerialName("errors") val errors: List<ApiError> = emptyList(),
)

@Serializable
data class ApiError(
    @SerialName("code") val code: String,
    @SerialName("message") val message: String,
    @SerialName("field") val field: String? = null,
)

/** Sealed hierarchy with custom discriminator for push notification payloads. */
@Serializable
@JsonClassDiscriminator("action")
sealed interface NotificationPayload {
    val notificationId: String
    val sentAt: Long
}

@Serializable
@SerialName("message.received")
data class MessageReceived(
    @SerialName("notification_id") override val notificationId: String,
    @SerialName("sent_at") override val sentAt: Long,
    @SerialName("sender_id") val senderId: String,
    @SerialName("preview") val preview: String,
    @SerialName("conversation_id") val conversationId: String,
) : NotificationPayload

@Serializable
@SerialName("order.shipped")
data class OrderShipped(
    @SerialName("notification_id") override val notificationId: String,
    @SerialName("sent_at") override val sentAt: Long,
    @SerialName("order_id") val orderId: String,
    @SerialName("tracking_url") val trackingUrl: String? = null,
    @SerialName("carrier") val carrier: String = "unknown",
) : NotificationPayload

@Serializable
@SerialName("promo.offer")
data class PromoOffer(
    @SerialName("notification_id") override val notificationId: String,
    @SerialName("sent_at") override val sentAt: Long,
    @SerialName("title") val title: String,
    @SerialName("discount_percent") val discountPercent: Int,
    @SerialName("expires_at") val expiresAt: Long,
) : NotificationPayload

// Serialization round-trip:
// val json = AppJson.encodeToString<NotificationPayload>(payload)
// {"action":"message.received","notification_id":"n-1","sent_at":1700000000000,...}
// val decoded: NotificationPayload = AppJson.decodeFromString(json)
// check(decoded is MessageReceived)
```

## 2. Custom Json Configuration Injected via Metro DI

```kotlin
package com.example.di

import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.DependencyGraph
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@DependencyGraph(AppScope::class)
interface SerializationGraph {

    val json: Json

    companion object {

        @Provides
        fun provideSerializersModule(): SerializersModule = SerializersModule {
            polymorphic(NotificationPayload::class) {
                subclass(MessageReceived::class)
                subclass(OrderShipped::class)
                subclass(PromoOffer::class)
                defaultDeserializer { UnknownPayload.serializer() }
            }
        }

        @Provides
        @SingleIn(AppScope::class)
        fun provideJson(module: SerializersModule): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
            coerceInputValues = true
            classDiscriminator = "action"
            serializersModule = module
        }
    }
}
```

## 3. SQLDelight ColumnAdapter Using kotlinx.serialization

```kotlin
package com.example.db.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Generic adapter that stores any @Serializable type as a JSON TEXT column.
 * Inject the shared [Json] instance so configuration (ignoreUnknownKeys, etc.)
 * stays consistent between network and database layers.
 */
class JsonColumnAdapter<T>(
    private val json: Json,
    private val serializer: KSerializer<T>,
) : ColumnAdapter<T, String> {

    override fun decode(databaseValue: String): T =
        json.decodeFromString(serializer, databaseValue)

    override fun encode(value: T): String =
        json.encodeToString(serializer, value)
}

// Usage with the NotificationPayload sealed hierarchy:
//
// .sq schema:
//   payload TEXT AS NotificationPayload NOT NULL
//
// Adapter wiring:
//   val adapter = JsonColumnAdapter(json, NotificationPayload.serializer())
```

## 4. Ktor 3.4.0 ContentNegotiation Sharing the Same Json Instance

```kotlin
package com.example.network

import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.DependencyGraph
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@DependencyGraph(AppScope::class)
interface NetworkGraph {

    val httpClient: HttpClient

    companion object {

        @Provides
        @SingleIn(AppScope::class)
        fun provideHttpClient(
            engine: HttpClientEngine,
            json: Json, // Same instance from SerializationGraph
        ): HttpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(json) // Reuses ignoreUnknownKeys, serializersModule, etc.
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}

// API call using the shared client:
// suspend fun fetchNotifications(): ApiEnvelope<List<NotificationPayload>> {
//     return httpClient.get("/v1/notifications").body()
// }
```

## 5. Protobuf Message for Binary Format

```kotlin
package com.example.network.proto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoBuf

/**
 * Binary envelope used over WebSocket or gRPC where JSON overhead
 * is unacceptable. Field numbers must remain stable across versions.
 */
@Serializable
data class SensorReading(
    @ProtoNumber(1) val deviceId: String,
    @ProtoNumber(2) val timestampMs: Long,
    @ProtoNumber(3) val temperatureCelsius: Double,
    @ProtoNumber(4) val humidityPercent: Float,
    @ProtoNumber(5) val labels: List<String> = emptyList(),
    @ProtoNumber(6) val batteryLevel: Int = 100,
)

// Encode / decode
// val bytes: ByteArray = ProtoBuf.encodeToByteArray(reading)
// val decoded: SensorReading = ProtoBuf.decodeFromByteArray(bytes)

// Configure with defaults encoded:
// val protoBuf = ProtoBuf { encodeDefaults = true }
```

## 6. Unit Test for Serialization Round-Trip

```kotlin
package com.example.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf

class SerializationRoundTripTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        classDiscriminator = "action"
        serializersModule = SerializersModule {
            polymorphic(NotificationPayload::class) {
                subclass(MessageReceived::class)
                subclass(OrderShipped::class)
                subclass(PromoOffer::class)
            }
        }
    }

    @Test
    fun `sealed class round-trip preserves discriminator and all fields`() {
        val original: NotificationPayload = MessageReceived(
            notificationId = "n-1",
            sentAt = 1_700_000_000_000L,
            senderId = "usr-42",
            preview = "Hey there!",
            conversationId = "conv-7",
        )

        val encoded = testJson.encodeToString<NotificationPayload>(original)
        val decoded: NotificationPayload = testJson.decodeFromString(encoded)

        assertIs<MessageReceived>(decoded)
        assertEquals(original, decoded)
        assert("\"action\":\"message.received\"" in encoded) {
            "Discriminator missing from JSON output"
        }
    }

    @Test
    fun `unknown keys are ignored without crashing`() {
        val raw = """
            {"action":"order.shipped","notification_id":"n-2","sent_at":0,
             "order_id":"ord-1","unknown_field":"should be ignored"}
        """.trimIndent()

        val decoded: NotificationPayload = testJson.decodeFromString(raw)
        assertIs<OrderShipped>(decoded)
        assertEquals("ord-1", decoded.orderId)
    }

    @Test
    fun `default values populate absent fields`() {
        val raw = """
            {"action":"order.shipped","notification_id":"n-3","sent_at":0,"order_id":"ord-2"}
        """.trimIndent()

        val decoded: NotificationPayload = testJson.decodeFromString(raw)
        assertIs<OrderShipped>(decoded)
        assertEquals("unknown", decoded.carrier)
    }

    @Test
    fun `protobuf round-trip preserves sensor reading`() {
        val original = SensorReading(
            deviceId = "device-A",
            timestampMs = 1_700_000_000_000L,
            temperatureCelsius = 22.5,
            humidityPercent = 65.0f,
            labels = listOf("indoor", "lab"),
            batteryLevel = 87,
        )

        val bytes = ProtoBuf.encodeToByteArray(SensorReading.serializer(), original)
        val decoded = ProtoBuf.decodeFromByteArray(SensorReading.serializer(), bytes)

        assertEquals(original, decoded)
    }
}
```
