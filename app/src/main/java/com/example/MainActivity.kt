package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.TaskStatus
import com.example.ui.components.MiniAudioPlayer
import com.example.ui.components.VideoPlayerDialog
import com.example.ui.screens.ConverterScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.TubeConvertTheme
import com.example.utils.FileUtils
import com.example.viewmodel.AppTab
import com.example.viewmodel.TubeConvertViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TubeConvertViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingShareIntent(intent)

        setContent {
            TubeConvertTheme(darkTheme = true) {
                TubeConvertApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.pasteLinks(sharedText)
            }
        }
    }
}

@Composable
fun TubeConvertApp(viewModel: TubeConvertViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    val activeCount = uiState.activeTasks.count { it.status != TaskStatus.COMPLETED && it.status != TaskStatus.FAILED }

    // Built-in Video Player Dialog when watching a video
    uiState.activeVideoDialog?.let { videoInfo ->
        VideoPlayerDialog(
            videoId = videoInfo.videoId,
            videoTitle = videoInfo.title,
            videoAuthor = videoInfo.author,
            pathOrUri = videoInfo.pathOrUri,
            onDismiss = { viewModel.closeVideoPlayer() },
            onShare = {
                FileUtils.shareMediaFile(
                    context = context,
                    pathOrUri = videoInfo.pathOrUri,
                    mimeType = "video/mp4",
                    title = videoInfo.title
                )
            },
            onOpenExternal = {
                FileUtils.openMediaFile(
                    context = context,
                    pathOrUri = videoInfo.pathOrUri,
                    mimeType = "video/mp4"
                )
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Persistent mini player shown whenever a track is loaded
                AnimatedVisibility(
                    visible = playerState.isVisible,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    MiniAudioPlayer(
                        playerState = playerState,
                        onTogglePlayPause = { viewModel.audioPlayerManager.togglePlayPause() },
                        onSeek = { positionMs -> viewModel.audioPlayerManager.seekTo(positionMs) },
                        onClose = { viewModel.audioPlayerManager.closePlayer() }
                    )
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    // Tab 1: Convertitore
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.CONVERT,
                        onClick = { viewModel.setTab(AppTab.CONVERT) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeCount > 0) {
                                        Badge(containerColor = CrimsonPrimary) {
                                            Text("$activeCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Download, contentDescription = "Converti")
                            }
                        },
                        label = { Text("Converti") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_convert")
                    )

                    // Tab 2: I Miei Download
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.LIBRARY,
                        onClick = { viewModel.setTab(AppTab.LIBRARY) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (downloads.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                            Text("${downloads.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Folder, contentDescription = "Download")
                            }
                        },
                        label = { Text("Download") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_library")
                    )

                    // Tab 3: Impostazioni
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.SETTINGS,
                        onClick = { viewModel.setTab(AppTab.SETTINGS) },
                        icon = {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Impostazioni")
                        },
                        label = { Text("Impostazioni") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            when (uiState.currentTab) {
                AppTab.CONVERT -> {
                    ConverterScreen(
                        uiState = uiState,
                        folderInfo = selectedFolder,
                        onSelectFolder = { uri -> viewModel.setCustomFolder(uri) },
                        onResetFolder = { viewModel.resetDefaultFolder() },
                        onInputChanged = { text -> viewModel.onInputTextChanged(text) },
                        onPasteClicked = { pasted -> viewModel.pasteLinks(pasted) },
                        onClearInput = { viewModel.clearInput() },
                        onAddSample = { viewModel.addSampleLink() },
                        onRemovePreview = { videoId -> viewModel.removeParsedVideo(videoId) },
                        onFormatSelected = { format -> viewModel.setFormat(format) },
                        onAudioQualitySelected = { quality -> viewModel.setAudioQuality(quality) },
                        onVideoQualitySelected = { quality -> viewModel.setVideoQuality(quality) },
                        onStartConversion = { viewModel.startConversionAndDownload() },
                        onCancelTask = { taskId -> viewModel.cancelTask(taskId) },
                        onClearCompletedTasks = { viewModel.clearCompletedTasks() },
                        onPlayTask = { task -> viewModel.playTaskOutput(task) },
                        onOpenVideo = { videoId, title, author, path -> viewModel.openVideoPlayer(videoId, title, author, path) },
                        onDismissNotice = { viewModel.dismissNotice() }
                    )
                }

                AppTab.LIBRARY -> {
                    LibraryScreen(
                        downloads = downloads,
                        selectedFilter = uiState.libraryFilter,
                        onFilterChanged = { filter -> viewModel.setLibraryFilter(filter) },
                        onPlayItem = { item -> viewModel.playAudio(item) },
                        onOpenVideo = { videoId, title, author, path -> viewModel.openVideoPlayer(videoId, title, author, path) },
                        onDeleteItem = { item -> viewModel.deleteDownloadedItem(item) },
                        onClearAll = { viewModel.clearAllLibrary() },
                        onNavigateToConvert = { viewModel.setTab(AppTab.CONVERT) }
                    )
                }

                AppTab.SETTINGS -> {
                    SettingsScreen(
                        downloads = downloads,
                        folderInfo = selectedFolder,
                        onSelectFolder = { uri -> viewModel.setCustomFolder(uri) },
                        onResetFolder = { viewModel.resetDefaultFolder() }
                    )
                }
            }
        }
    }
}
