# ── Debugging ─────────────────────────────────────────────────────
# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin Serialization ─────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.keisardev.insight.**$$serializer { *; }
-keepclassmembers class com.keisardev.insight.** {
    *** Companion;
}
-keepclasseswithmembers class com.keisardev.insight.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Ktor Client (keep only what's needed at runtime) ─────────────
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.** { *; }
-keep class io.ktor.serialization.** { *; }

# ── Transitive deps not needed on Android ────────────────────────
-dontwarn io.micrometer.context.ContextAccessor
-dontwarn javax.enterprise.inject.spi.Extension
-dontwarn okhttp3.internal.Util
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# ── Coroutines ───────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── SQLDelight (keep generated query interfaces + adapters) ──────
-keep class app.cash.sqldelight.driver.** { *; }
-keep class com.keisardev.insight.core.database.** { *; }

# ── Llamatik / llama.cpp JNI ─────────────────────────────────────
-keep class com.llamatik.** { *; }

# ── R8 full mode optimizations ───────────────────────────────────
-allowaccessmodification
-repackageclasses
