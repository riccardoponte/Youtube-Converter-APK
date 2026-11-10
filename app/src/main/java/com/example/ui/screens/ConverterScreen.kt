package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FolderInfo
import com.example.model.AudioQuality
import com.example.model.ConversionTask
import com.example.model.MediaFormat
import com.example.model.ParsedVideoInfo
import com.example.model.TaskStatus
import com.example.model.VideoQuality
import com.example.ui.components.ConversionTaskCard
import com.example.ui.components.VideoPreviewCard
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.FormatMp3Color
import com.example.ui.theme.FormatMp4Color
import com.example.utils.FileUtils
import com.example.viewmodel.TubeUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterScreen(
    uiState: TubeUiState,
    folderInfo: FolderInfo,
    onSelectFolder: (Uri) -> Unit,
    onResetFolder: () -> Unit,
    onInputChanged: (String) -> Unit,
    onPasteClicked: (String) -> Unit,
    onClearInput: () -> Unit,
    onAddSample: () -> Unit,
    onRemovePreview: (String) -> Unit,
    onFormatSelected: (MediaFormat) -> Unit,
    onAudioQualitySelected: (AudioQuality) -> Unit,
    onVideoQualitySelected: (VideoQuality) -> Unit,
    onStartConversion: () -> Unit,
    onCancelTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onPlayTask: (ConversionTask) -> Unit,
    onOpenVideo: (String, String, String, String) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectFolder(uri)
        }
    }

    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            onDismissNotice()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Minimal Hero Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CrimsonPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircleFilled,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "TubeConvert",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Converti & scarica YouTube in M4A / MP4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Input Card: Paste link or multiple links
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_links_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Link video YouTube",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.parsedVideos.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(CrimsonPrimary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${uiState.parsedVideos.size} rilevati",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = CrimsonPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = onInputChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("youtube_link_input"),
                            placeholder = {
                                Text(
                                    "Incolla uno o più link YouTube…\nes. https://youtu.be/xxxx",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = CrimsonPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val pasted = clipData.getItemAt(0).text?.toString() ?: ""
                                        if (pasted.isNotBlank()) {
                                            onPasteClicked(pasted)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("paste_clipboard_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Incolla", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = onAddSample,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("sample_link_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLink,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Esempio", style = MaterialTheme.typography.labelMedium)
                            }

                            if (uiState.inputText.isNotBlank() || uiState.parsedVideos.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearInput,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("clear_input_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Pulisci",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (uiState.isAnalyzing) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = CrimsonPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recupero informazioni video…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Parsed video previews (if any)
            if (uiState.parsedVideos.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Video pronti per la conversione (${uiState.parsedVideos.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        uiState.parsedVideos.forEach { video ->
                            VideoPreviewCard(
                                videoInfo = video,
                                onRemove = { onRemovePreview(video.videoId) }
                            )
                        }
                    }
                }
            }

            // Format & Quality Selection Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("format_selection_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Formato di Conversione",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Format Toggle Pills: audio vs video
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isMp3 = uiState.selectedFormat == MediaFormat.M4A
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isMp3) FormatMp3Color else Color.Transparent)
                                    .clickable { onFormatSelected(MediaFormat.M4A) }
                                    .testTag("select_format_mp3"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = if (isMp3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Audio M4A",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isMp3) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isMp3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val isMp4 = uiState.selectedFormat == MediaFormat.MP4
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isMp4) FormatMp4Color else Color.Transparent)
                                    .clickable { onFormatSelected(MediaFormat.MP4) }
                                    .testTag("select_format_mp4"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = if (isMp4) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Video MP4",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isMp4) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isMp4) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (uiState.selectedFormat == MediaFormat.M4A) "Qualità Audio" else "Risoluzione Video",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.selectedFormat == MediaFormat.M4A) {
                                AudioQuality.values().forEach { quality ->
                                    val isSelected = uiState.selectedAudioQuality == quality
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onAudioQualitySelected(quality) },
                                        label = {
                                            Text(
                                                text = quality.label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FormatMp3Color,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("audio_quality_${quality.name}")
                                    )
                                }
                            } else {
                                VideoQuality.values().forEach { quality ->
                                    val isSelected = uiState.selectedVideoQuality == quality
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onVideoQualitySelected(quality) },
                                        label = {
                                            Text(
                                                text = quality.label,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FormatMp4Color,
                                            selectedLabelColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("video_quality_${quality.name}")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save Folder Selector Card (User-selected download directory)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_folder_selector_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cartella di salvataggio",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = folderInfo.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        // Change Folder Button
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("change_folder_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scegli", style = MaterialTheme.typography.labelSmall)
                        }

                        if (folderInfo.uriString != null) {
                            IconButton(
                                onClick = onResetFolder,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("reset_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Ripristina predefinita",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Convert and Download Action Button
            item {
                val btnText = if (uiState.parsedVideos.size > 1) {
                    "Converti e Scarica (${uiState.parsedVideos.size} video)"
                } else {
                    "Converti e Scarica in ${uiState.selectedFormat.name}"
                }

                Button(
                    onClick = onStartConversion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_conversion_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = btnText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Ongoing Conversion and Download Queue
            if (uiState.activeTasks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download e Conversioni in corso (${uiState.activeTasks.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (uiState.activeTasks.any { it.status == TaskStatus.COMPLETED }) {
                            TextButton(
                                onClick = onClearCompletedTasks,
                                modifier = Modifier.testTag("clear_completed_tasks_button")
                            ) {
                                Text("Pulisci completati", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                items(uiState.activeTasks, key = { it.id }) { task ->
                    ConversionTaskCard(
                        task = task,
                        onCancel = { onCancelTask(task.id) },
                        onPlay = { onPlayTask(task) },
                        onShare = {
                            task.outputFilePath?.let { path ->
                                FileUtils.shareMediaFile(
                                    context = context,
                                    pathOrUri = path,
                                    mimeType = task.format.mimeType,
                                    title = task.title
                                )
                            }
                        },
                        onOpen = {
                            task.outputFilePath?.let { path ->
                                if (task.format == MediaFormat.MP4) {
                                    onOpenVideo(task.videoId, task.title, task.author, path)
                                } else {
                                    FileUtils.openMediaFile(
                                        context = context,
                                        pathOrUri = path,
                                        mimeType = task.format.mimeType
                                    )
                                }
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}
