package com.keisardev.insight.core.ai.service

/**
 * Abstraction for cloud-based AI service.
 * Android: implemented by KoogAiService
 * iOS: no-op (IosCloudAiService) since Koog's reflection API is JVM-only
 */
interface CloudAiService : AiService {
    val hasDevKey: Boolean
}
