package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DownloadedItemEntity
import com.example.model.MediaFormat
import com.example.ui.components.DownloadedItemCard
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.FormatMp3Color
import com.example.ui.theme.FormatMp4Color
import com.example.utils.FileUtils

@Composable
fun LibraryScreen(
    downloads: List<DownloadedItemEntity>,
    selectedFilter: MediaFormat?,
    onFilterChanged: (MediaFormat?) -> Unit,
    onPlayItem: (DownloadedItemEntity) -> Unit,
    onOpenVideo: (String, String, String, String) -> Unit,
    onDeleteItem: (DownloadedItemEntity) -> Unit,
    onClearAll: () -> Unit,
    onNavigateToConvert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val filteredDownloads = remember(downloads, selectedFilter) {
        when (selectedFilter) {
            MediaFormat.M4A -> downloads.filter { MediaFormat.isAudioFormat(it.format) }
            MediaFormat.MP4 -> downloads.filter { it.format.equals("MP4", ignoreCase = true) }
            null -> downloads
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Eliminare tutti i file?") },
            text = { Text("Questa operazione cancellerà tutti i brani e i video scaricati dalla memoria dell'app.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Elimina tutto")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Screen title and clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "I Miei Download",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${downloads.size} file completati",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (downloads.isNotEmpty()) {
                IconButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.testTag("clear_all_library_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Pulisci libreria",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter chips: Tutti, Solo audio, Solo video
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterChanged(null) },
                label = { Text("Tutti (${downloads.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CrimsonPrimary,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("filter_all")
            )

            val audioCount = downloads.count { MediaFormat.isAudioFormat(it.format) }
            FilterChip(
                selected = selectedFilter == MediaFormat.M4A,
                onClick = { onFilterChanged(MediaFormat.M4A) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Audio ($audioCount)")
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FormatMp3Color,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("filter_mp3")
            )

            val mp4Count = downloads.count { it.format.equals("MP4", ignoreCase = true) }
            FilterChip(
                selected = selectedFilter == MediaFormat.MP4,
                onClick = { onFilterChanged(MediaFormat.MP4) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("MP4 ($mp4Count)")
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = FormatMp4Color,
                    selectedLabelColor = Color.White
                ),
                modifier = Modifier.testTag("filter_mp4")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Downloaded Items List
        if (filteredDownloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (downloads.isEmpty()) "Nessun download completato" else "Nessun file con questo filtro",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (downloads.isEmpty()) "Incolla un link YouTube nella schermata di conversione per scaricare file M4A o MP4." else "Prova a selezionare il filtro 'Tutti' per visualizzare tutti i file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (downloads.isEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToConvert,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("go_to_convert_button")
                        ) {
                            Text("Vai al Convertitore", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredDownloads, key = { it.id }) { item ->
                    val isAudio = MediaFormat.isAudioFormat(item.format)
                    val mimeType = if (isAudio) "audio/mp4" else "video/mp4"

                    DownloadedItemCard(
                        item = item,
                        onPlay = {
                            if (isAudio) {
                                onPlayItem(item)
                            } else {
                                onOpenVideo(item.videoId, item.title, item.author, item.filePath)
                            }
                        },
                        onShare = {
                            FileUtils.shareMediaFile(
                                context = context,
                                pathOrUri = item.filePath,
                                mimeType = mimeType,
                                title = item.title
                            )
                        },
                        onOpenExternal = {
                            FileUtils.openMediaFile(
                                context = context,
                                pathOrUri = item.filePath,
                                mimeType = mimeType
                            )
                        },
                        onDelete = {
                            onDeleteItem(item)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }
}
