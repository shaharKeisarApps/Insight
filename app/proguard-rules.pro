# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin serialization
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

# Ktor / OkHttp (used by Koog)
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# Netty / Reactor / Log4j transitive deps (from Ktor/Koog, not needed on Android)
-dontwarn io.micrometer.context.ContextAccessor
-dontwarn javax.enterprise.inject.spi.Extension
-dontwarn okhttp3.internal.Util
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# SQLDelight
-keep class app.cash.sqldelight.** { *; }
