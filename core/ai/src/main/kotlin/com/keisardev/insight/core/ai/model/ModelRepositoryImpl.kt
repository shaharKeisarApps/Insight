package com.keisardev.insight.core.ai.model

import android.app.Application
import com.keisardev.insight.core.common.di.AppScope
import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ModelRepositoryImpl(
    private val application: Application,
    private val httpClient: HttpClient,
) : ModelRepository {

    private val modelsDir = File(application.filesDir, "models")
    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private val _searchResults = MutableStateFlow<List<ModelInfo>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    private var downloadJob: Job? = null

    override val modelState: StateFlow<ModelState> = _modelState.asStateFlow()
    override val searchResults: StateFlow<List<ModelInfo>> = _searchResults.asStateFlow()
    override val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    override val availableModels: List<ModelInfo> = listOf(
        ModelInfo(
            id = "smollm2-360m",
            name = "SmolLM2 360M",
            description = "Tiny, fast — basic chat quality",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
            sizeBytes = 387_000_000L,
            fileName = "smollm2-360m-instruct-q8_0.gguf",
        ),
        ModelInfo(
            id = "qwen2.5-0.5b",
            name = "Qwen2.5 0.5B",
            description = "Compact, decent quality",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeBytes = 400_000_000L,
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
        ),
        ModelInfo(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B (Recommended)",
            description = "Good quality, moderate size",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            sizeBytes = 1_100_000_000L,
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        ),
        ModelInfo(
            id = "phi-4-mini",
            name = "Phi-4 Mini 3.8B",
            description = "High quality, larger download",
            downloadUrl = "https://huggingface.co/bartowski/phi-4-mini-instruct-GGUF/resolve/main/phi-4-mini-instruct-Q4_K_M.gguf",
            sizeBytes = 2_400_000_000L,
            fileName = "phi-4-mini-instruct-Q4_K_M.gguf",
        ),
        ModelInfo(
            id = "gemma3-1b",
            name = "Gemma 3 1B",
            description = "Google's compact model",
            downloadUrl = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf",
            sizeBytes = 780_000_000L,
            fileName = "google_gemma-3-1b-it-Q4_K_M.gguf",
        ),
        ModelInfo(
            id = "tinyllama-1.1b",
            name = "TinyLlama 1.1B",
            description = "Fast inference, lightweight",
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            sizeBytes = 670_000_000L,
            fileName = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
        ),
    )

    init {
        refreshModelStatus()
    }

    override fun refreshModelStatus() {
        val currentState = _modelState.value
        if (currentState is ModelState.Downloading) return

        modelsDir.mkdirs()
        val modelFile = modelsDir.listFiles()?.firstOrNull { it.extension == "gguf" }
        _modelState.value = if (modelFile != null) {
            ModelState.Ready(
                modelName = modelFile.nameWithoutExtension,
                filePath = modelFile.absolutePath,
                sizeBytes = modelFile.length(),
            )
        } else {
            ModelState.NotInstalled
        }
    }

    override suspend fun startDownload(model: ModelInfo) {
        if (_modelState.value is ModelState.Downloading) return

        // Delete existing model before downloading new one
        deleteExistingModel()

        _modelState.value = ModelState.Downloading(
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = model.sizeBytes,
        )

        withContext(Dispatchers.IO) {
            val job = coroutineContext[Job]
            downloadJob = job

            modelsDir.mkdirs()
            val tempFile = File(modelsDir, "${model.fileName}.tmp")
            val targetFile = File(modelsDir, model.fileName)

            try {
                httpClient.prepareGet(model.downloadUrl).execute { response ->
                    val totalBytes = response.contentLength() ?: model.sizeBytes
                    val channel = response.bodyAsChannel()

                    tempFile.outputStream().use { output ->
                        var downloadedBytes = 0L
                        val buffer = ByteArray(8192)

                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead <= 0) break

                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _modelState.value = ModelState.Downloading(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                        }
                    }

                    tempFile.renameTo(targetFile)
                    _modelState.value = ModelState.Ready(
                        modelName = model.name,
                        filePath = targetFile.absolutePath,
                        sizeBytes = targetFile.length(),
                    )
                }
            } catch (e: Exception) {
                tempFile.delete()
                if (e is kotlinx.coroutines.CancellationException) {
                    _modelState.value = ModelState.NotInstalled
                } else {
                    _modelState.value = ModelState.Error(e.message ?: "Download failed")
                }
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null

        // Clean up partial file
        modelsDir.listFiles()?.filter { it.extension == "tmp" }?.forEach { it.delete() }
        _modelState.value = ModelState.NotInstalled
    }

    override suspend fun searchModels(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        try {
            val results = withContext(Dispatchers.IO) {
                val searchUrl = "https://huggingface.co/api/models?search=${query}+gguf&sort=downloads&direction=-1&limit=20"
                val response = httpClient.prepareGet(searchUrl).execute { it.bodyAsText() }
                parseSearchResults(response)
            }
            _searchResults.value = results
        } catch (_: Exception) {
            _searchResults.value = emptyList()
        } finally {
            _isSearching.value = false
        }
    }

    override suspend fun deleteCurrentModel() {
        withContext(Dispatchers.IO) {
            deleteExistingModel()
        }
        _modelState.value = ModelState.NotInstalled
    }

    private fun deleteExistingModel() {
        modelsDir.listFiles()
            ?.filter { it.extension == "gguf" || it.extension == "tmp" }
            ?.forEach { it.delete() }
    }

    private fun parseSearchResults(json: String): List<ModelInfo> {
        val results = mutableListOf<ModelInfo>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val modelId = obj.getString("modelId")
                val siblings = obj.optJSONArray("siblings") ?: continue

                for (j in 0 until siblings.length()) {
                    val sibling = siblings.getJSONObject(j)
                    val filename = sibling.getString("rfilename")
                    if (!filename.endsWith(".gguf")) continue
                    // Skip very large quantizations
                    if (filename.contains("f32") || filename.contains("f16")) continue

                    results.add(
                        ModelInfo(
                            id = "$modelId/$filename",
                            name = modelId.substringAfterLast("/"),
                            description = filename,
                            downloadUrl = "https://huggingface.co/$modelId/resolve/main/$filename",
                            sizeBytes = 0L, // Size unknown from search
                            fileName = filename,
                        )
                    )
                    // Only take first matching GGUF per model
                    break
                }
            }
        } catch (_: Exception) {
            // JSON parse error — return empty
        }
        return results
    }
}
