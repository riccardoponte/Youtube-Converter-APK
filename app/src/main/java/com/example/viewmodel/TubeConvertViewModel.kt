package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DownloadedItemEntity
import com.example.data.FolderInfo
import com.example.data.FolderPreferences
import com.example.model.AudioQuality
import com.example.model.ConversionTask
import com.example.model.MediaFormat
import com.example.model.ParsedVideoInfo
import com.example.model.TaskStatus
import com.example.model.VideoQuality
import com.example.player.AudioPlayerManager
import com.example.player.PlayerState
import com.example.service.MediaFileGenerator
import com.example.service.YouTubeMetadataService
import com.example.service.YouTubeParser
import com.example.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppTab {
    CONVERT,
    LIBRARY,
    SETTINGS
}

data class VideoPlaybackInfo(
    val videoId: String,
    val title: String,
    val author: String,
    val pathOrUri: String
)

data class TubeUiState(
    val inputText: String = "",
    val isAnalyzing: Boolean = false,
    val parsedVideos: List<ParsedVideoInfo> = emptyList(),
    val selectedFormat: MediaFormat = MediaFormat.M4A,
    val selectedAudioQuality: AudioQuality = AudioQuality.BEST,
    val selectedVideoQuality: VideoQuality = VideoQuality.HD_720,
    val activeTasks: List<ConversionTask> = emptyList(),
    val currentTab: AppTab = AppTab.CONVERT,
    val libraryFilter: MediaFormat? = null,
    val userNotice: String? = null,
    val activeVideoDialog: VideoPlaybackInfo? = null
)

class TubeConvertViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val downloadDao = db.downloadDao()
    private val metadataService = YouTubeMetadataService()
    private val folderPreferences = FolderPreferences(application)
    val audioPlayerManager = AudioPlayerManager(viewModelScope)

    private val _uiState = MutableStateFlow(TubeUiState())
    val uiState: StateFlow<TubeUiState> = _uiState.asStateFlow()

    private val _selectedFolder = MutableStateFlow(folderPreferences.getSelectedFolder())
    val selectedFolder: StateFlow<FolderInfo> = _selectedFolder.asStateFlow()

    val downloads: StateFlow<List<DownloadedItemEntity>> = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState: StateFlow<PlayerState> = audioPlayerManager.playerState

    private val taskJobs = mutableMapOf<String, Job>()

    fun setCustomFolder(uri: Uri) {
        val updated = folderPreferences.saveSelectedFolder(uri)
        _selectedFolder.value = updated
        _uiState.value = _uiState.value.copy(
            userNotice = "Cartella di salvataggio impostata su: ${updated.displayName}"
        )
    }

    fun resetDefaultFolder() {
        val updated = folderPreferences.resetToDefault()
        _selectedFolder.value = updated
        _uiState.value = _uiState.value.copy(
            userNotice = "Cartella reimpostata su quella predefinita"
        )
    }

    fun setTab(tab: AppTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun setLibraryFilter(format: MediaFormat?) {
        _uiState.value = _uiState.value.copy(libraryFilter = format)
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
        if (text.isNotBlank()) {
            scheduleAutoAnalyze()
        }
    }

    fun pasteLinks(pasted: String) {
        val current = _uiState.value.inputText
        val combined = if (current.isBlank()) pasted else "$current\n$pasted"
        _uiState.value = _uiState.value.copy(inputText = combined)
        analyzeLinks(combined)
    }

    fun clearInput() {
        _uiState.value = _uiState.value.copy(
            inputText = "",
            parsedVideos = emptyList()
        )
    }

    fun addSampleLink() {
        val sampleLinks = "https://www.youtube.com/watch?v=dQw4w9WgXcQ\nhttps://www.youtube.com/watch?v=kXYiU_JCYtU"
        _uiState.value = _uiState.value.copy(inputText = sampleLinks)
        analyzeLinks(sampleLinks)
    }

    private var autoAnalyzeJob: Job? = null
    private fun scheduleAutoAnalyze() {
        autoAnalyzeJob?.cancel()
        autoAnalyzeJob = viewModelScope.launch {
            delay(500)
            analyzeLinks(_uiState.value.inputText)
        }
    }

    fun analyzeLinks(text: String = _uiState.value.inputText) {
        val ids = YouTubeParser.extractVideoIds(text)
        if (ids.isEmpty()) {
            _uiState.value = _uiState.value.copy(parsedVideos = emptyList(), isAnalyzing = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            val results = mutableListOf<ParsedVideoInfo>()
            for (id in ids) {
                val info = metadataService.fetchVideoInfo(id)
                results.add(info)
            }
            _uiState.value = _uiState.value.copy(
                parsedVideos = results,
                isAnalyzing = false
            )
        }
    }

    fun removeParsedVideo(videoId: String) {
        val updated = _uiState.value.parsedVideos.filterNot { it.videoId == videoId }
        _uiState.value = _uiState.value.copy(parsedVideos = updated)
    }

    fun setFormat(format: MediaFormat) {
        _uiState.value = _uiState.value.copy(selectedFormat = format)
    }

    fun setAudioQuality(quality: AudioQuality) {
        _uiState.value = _uiState.value.copy(selectedAudioQuality = quality)
    }

    fun setVideoQuality(quality: VideoQuality) {
        _uiState.value = _uiState.value.copy(selectedVideoQuality = quality)
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(userNotice = null)
    }

    fun openVideoPlayer(videoId: String, title: String, author: String, pathOrUri: String) {
        _uiState.value = _uiState.value.copy(
            activeVideoDialog = VideoPlaybackInfo(
                videoId = videoId,
                title = title,
                author = author,
                pathOrUri = pathOrUri
            )
        )
    }

    fun closeVideoPlayer() {
        _uiState.value = _uiState.value.copy(activeVideoDialog = null)
    }

    fun startConversionAndDownload() {
        val videosToConvert = if (_uiState.value.parsedVideos.isNotEmpty()) {
            _uiState.value.parsedVideos
        } else {
            val ids = YouTubeParser.extractVideoIds(_uiState.value.inputText)
            ids.map { id ->
                ParsedVideoInfo(
                    videoId = id,
                    originalUrl = YouTubeParser.buildStandardUrl(id),
                    title = "Video YouTube $id",
                    author = "YouTube",
                    thumbnailUrl = YouTubeParser.getThumbnailUrl(id)
                )
            }
        }

        if (videosToConvert.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                userNotice = "Inserisci o incolla almeno un link YouTube valido prima di convertire."
            )
            return
        }

        val format = _uiState.value.selectedFormat
        val audioQ = _uiState.value.selectedAudioQuality
        val videoQ = _uiState.value.selectedVideoQuality

        val newTasks = videosToConvert.map { video ->
            ConversionTask(
                id = UUID.randomUUID().toString(),
                videoId = video.videoId,
                originalUrl = video.originalUrl,
                title = video.title,
                author = video.author,
                thumbnailUrl = video.thumbnailUrl,
                format = format,
                audioQuality = audioQ,
                videoQuality = videoQ,
                status = TaskStatus.QUEUED
            )
        }

        val updatedTasks = _uiState.value.activeTasks + newTasks
        _uiState.value = _uiState.value.copy(
            activeTasks = updatedTasks,
            inputText = "",
            parsedVideos = emptyList(),
            userNotice = "Avviata conversione per ${newTasks.size} elemento/i"
        )

        newTasks.forEach { task ->
            processTask(task)
        }
    }

    private fun processTask(task: ConversionTask) {
        val job = viewModelScope.launch(Dispatchers.IO) {
            updateTask(task.id) { it.copy(status = TaskStatus.FETCHING_METADATA, progress = 0.05f) }
            delay(400)

            val enriched = try {
                metadataService.fetchVideoInfo(task.videoId)
            } catch (_: Exception) {
                null
            }

            val finalTitle = enriched?.title ?: task.title
            val finalAuthor = enriched?.author ?: task.author
            val finalThumb = enriched?.thumbnailUrl ?: task.thumbnailUrl

            updateTask(task.id) {
                it.copy(
                    title = finalTitle,
                    author = finalAuthor,
                    thumbnailUrl = finalThumb,
                    status = TaskStatus.CONVERTING,
                    progress = 0.15f
                )
            }
            delay(400)

            try {
                updateTask(task.id) { it.copy(status = TaskStatus.DOWNLOADING) }

                val customFolderUri = _selectedFolder.value.uriString

                val result = MediaFileGenerator.generateMedia(
                    context = getApplication(),
                    videoId = task.videoId,
                    title = finalTitle,
                    artist = finalAuthor,
                    format = task.format,
                    audioQuality = task.audioQuality,
                    videoQuality = task.videoQuality,
                    customFolderUriString = customFolderUri
                ) { progress, written, total, speed ->
                    updateTask(task.id) {
                        it.copy(
                            progress = 0.15f + (progress * 0.85f),
                            downloadedBytes = written,
                            totalBytes = total,
                            speedText = speed
                        )
                    }
                }

                val qualityLabel = result.qualityLabel

                val downloadEntity = DownloadedItemEntity(
                    id = task.id,
                    videoId = task.videoId,
                    title = finalTitle,
                    author = finalAuthor,
                    thumbnailUrl = finalThumb,
                    format = task.format.name,
                    qualityLabel = qualityLabel,
                    filePath = result.pathOrUri,
                    fileSize = result.fileSize,
                    downloadedTimestamp = System.currentTimeMillis()
                )
                downloadDao.insertDownload(downloadEntity)

                updateTask(task.id) {
                    it.copy(
                        status = TaskStatus.COMPLETED,
                        progress = 1.0f,
                        outputFilePath = result.pathOrUri,
                        speedText = "Completato"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateTask(task.id) {
                    it.copy(
                        status = TaskStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Errore durante la conversione"
                    )
                }
            }
        }
        taskJobs[task.id] = job
    }

    private fun updateTask(taskId: String, transform: (ConversionTask) -> ConversionTask) {
        val current = _uiState.value.activeTasks
        val index = current.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            val updated = current.toMutableList()
            updated[index] = transform(updated[index])
            _uiState.value = _uiState.value.copy(activeTasks = updated)
        }
    }

    fun cancelTask(taskId: String) {
        taskJobs[taskId]?.cancel()
        taskJobs.remove(taskId)
        val updated = _uiState.value.activeTasks.filterNot { it.id == taskId }
        _uiState.value = _uiState.value.copy(activeTasks = updated)
    }

    fun clearCompletedTasks() {
        val remaining = _uiState.value.activeTasks.filterNot { it.status == TaskStatus.COMPLETED }
        _uiState.value = _uiState.value.copy(activeTasks = remaining)
    }

    fun deleteDownloadedItem(item: DownloadedItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            FileUtils.deleteLocalFile(getApplication(), item.filePath)
            downloadDao.deleteDownload(item)
        }
    }

    fun clearAllLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloads.value
            items.forEach { FileUtils.deleteLocalFile(getApplication(), it.filePath) }
            downloadDao.clearAll()
        }
    }

    fun playAudio(item: DownloadedItemEntity) {
        audioPlayerManager.playTrack(
            filePath = item.filePath,
            title = item.title,
            author = item.author,
            thumbnailUrl = item.thumbnailUrl
        )
    }

    fun playTaskOutput(task: ConversionTask) {
        val path = task.outputFilePath ?: return
        if (task.format == MediaFormat.M4A) {
            audioPlayerManager.playTrack(
                filePath = path,
                title = task.title,
                author = task.author,
                thumbnailUrl = task.thumbnailUrl
            )
        } else {
            openVideoPlayer(task.videoId, task.title, task.author, path)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.stopTrack()
        taskJobs.values.forEach { it.cancel() }
    }
}
