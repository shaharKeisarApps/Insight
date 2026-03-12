package com.keisardev.insight.core.ai.model

import com.keisardev.insight.core.ai.di.ModelsDir
import com.keisardev.insight.core.data.datastore.UserSettingsRepository
import com.keisardev.insight.core.model.InstalledModel
import com.keisardev.insight.core.model.ModelInfo
import com.keisardev.insight.core.model.ModelState
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import com.keisardev.insight.core.common.di.AppScope
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class ModelRepositoryImpl(
    @ModelsDir private val modelsDir: Path,
    private val fileSystem: FileSystem,
    private val httpClient: HttpClient,
    private val userSettingsRepository: UserSettingsRepository,
) : ModelRepository {

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotInstalled)
    private val _searchResults = MutableStateFlow<List<ModelInfo>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    @kotlin.concurrent.Volatile private var downloadJob: Job? = null
    @kotlin.concurrent.Volatile private var _activeModelFileName: String = ""

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
        // Load persisted active model preference before first refresh
        try {
            _activeModelFileName = runBlocking {
                userSettingsRepository.observeSettings().first().activeModelFileName
            }
        } catch (_: Exception) { /* keep default empty */ }
        refreshModelStatus()
    }

    override fun refreshModelStatus() {
        val currentState = _modelState.value
        if (currentState is ModelState.Downloading) return
        doRefreshModelStatus()
    }

    /**
     * Unconditionally refreshes model status, bypassing the Downloading guard.
     * Used after download completion to avoid emitting a transient NotInstalled
     * state that could cause the foreground service to stopSelf() prematurely.
     */
    private fun doRefreshModelStatus() {
        fileSystem.createDirectories(modelsDir)
        val modelFiles = fileSystem.listOrNull(modelsDir)
            ?.filter { it.name.substringAfterLast('.') == "gguf" }
            ?: emptyList()

        if (modelFiles.isEmpty()) {
            _modelState.value = ModelState.NotInstalled
            return
        }

        val activeFileName = _activeModelFileName

        val installedModels = modelFiles.map { path ->
            InstalledModel(
                fileName = path.name,
                displayName = path.name.substringBeforeLast('.'),
                filePath = path.toString(),
                sizeBytes = fileSystem.metadata(path).size ?: 0L,
                isActive = path.name == activeFileName,
            )
        }

        // If no active model set or the active one doesn't exist, default to first
        val effectiveActive = if (installedModels.any { it.isActive }) {
            installedModels
        } else {
            val first = installedModels.first()
            installedModels.map { it.copy(isActive = it.fileName == first.fileName) }
        }

        val activeModel = effectiveActive.first { it.isActive }

        _modelState.value = ModelState.Ready(
            modelName = activeModel.displayName,
            filePath = activeModel.filePath,
            sizeBytes = activeModel.sizeBytes,
            installedModels = effectiveActive,
            activeModelFileName = activeModel.fileName,
        )
    }

    override suspend fun startDownload(model: ModelInfo) {
        if (_modelState.value is ModelState.Downloading) return

        _modelState.value = ModelState.Downloading(
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = model.sizeBytes,
        )

        withContext(Dispatchers.Default) {
            val job = coroutineContext[Job]
            downloadJob = job

            fileSystem.createDirectories(modelsDir)
            val tempPath = modelsDir / "${model.fileName}.tmp"
            val targetPath = modelsDir / model.fileName

            try {
                httpClient.prepareGet(model.downloadUrl).execute { response ->
                    val totalBytes = response.contentLength() ?: model.sizeBytes
                    val channel = response.bodyAsChannel()

                    fileSystem.sink(tempPath).buffer().use { sink ->
                        var downloadedBytes = 0L
                        val buffer = ByteArray(8192)

                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead <= 0) break

                            sink.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            _modelState.value = ModelState.Downloading(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            )
                        }
                    }

                    // Delete existing target to ensure atomicMove succeeds (re-downloads)
                    if (fileSystem.exists(targetPath)) fileSystem.delete(targetPath)
                    fileSystem.atomicMove(tempPath, targetPath)
                    _activeModelFileName = model.fileName
                    try { userSettingsRepository.updateActiveModel(model.fileName) } catch (_: Exception) {}
                    // Directly refresh, bypassing the Downloading guard — no transient state
                    doRefreshModelStatus()
                }
            } catch (e: Exception) {
                if (fileSystem.exists(tempPath)) fileSystem.delete(tempPath)
                if (e is kotlinx.coroutines.CancellationException) {
                    doRefreshModelStatus()
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
        fileSystem.listOrNull(modelsDir)
            ?.filter { it.name.substringAfterLast('.') == "tmp" }
            ?.forEach { fileSystem.delete(it) }
        refreshModelStatus()
    }

    override suspend fun searchModels(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        try {
            val results = withContext(Dispatchers.Default) {
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

    override suspend fun deleteModel(fileName: String) {
        withContext(Dispatchers.Default) {
            val path = modelsDir / fileName
            if (fileSystem.exists(path)) fileSystem.delete(path)
        }
        // If we deleted the active model, clear the preference
        if (_activeModelFileName == fileName) {
            _activeModelFileName = ""
            userSettingsRepository.updateActiveModel("")
        }
        refreshModelStatus()
    }

    override suspend fun setActiveModel(fileName: String) {
        _activeModelFileName = fileName
        userSettingsRepository.updateActiveModel(fileName)
        refreshModelStatus()
    }

    private fun parseSearchResults(json: String): List<ModelInfo> {
        val results = mutableListOf<ModelInfo>()
        try {
            val jsonArray = Json.parseToJsonElement(json).jsonArray
            for (element in jsonArray) {
                val obj = element.jsonObject
                val modelId = obj["modelId"]?.jsonPrimitive?.content ?: continue
                val siblings = obj["siblings"]?.jsonArray ?: continue

                for (sibling in siblings) {
                    val sibObj = sibling.jsonObject
                    val filename = sibObj["rfilename"]?.jsonPrimitive?.content ?: continue
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
