package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ConversionTask
import com.example.model.MediaFormat
import com.example.model.TaskStatus
import com.example.ui.theme.FormatMp3Color
import com.example.ui.theme.FormatMp4Color
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.utils.FileUtils

@Composable
fun ConversionTaskCard(
    task: ConversionTask,
    onCancel: () -> Unit,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "task_progress"
    )

    val formatColor = if (task.format == MediaFormat.M4A) FormatMp3Color else FormatMp4Color
    val formatIcon = if (task.format == MediaFormat.M4A) Icons.Default.Headphones else Icons.Default.Movie

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("conversion_task_card_${task.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(width = 80.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = "Miniatura",
                        modifier = Modifier.size(width = 80.dp, height = 48.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title, Author and Format badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Format tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(formatColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = formatIcon,
                                    contentDescription = null,
                                    tint = formatColor,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (task.format == MediaFormat.M4A) "M4A • ${task.audioQuality.label}" else "MP4 • ${task.videoQuality.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = formatColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Status badge text
                        val statusText = when (task.status) {
                            TaskStatus.QUEUED -> "In coda"
                            TaskStatus.FETCHING_METADATA -> "Analisi..."
                            TaskStatus.CONVERTING -> "Conversione in corso..."
                            TaskStatus.DOWNLOADING -> "Download file..."
                            TaskStatus.COMPLETED -> "Completato"
                            TaskStatus.FAILED -> "Errore"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = when (task.status) {
                                TaskStatus.COMPLETED -> StatusSuccess
                                TaskStatus.FAILED -> StatusError
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                // Status Icon or Cancel button
                when (task.status) {
                    TaskStatus.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completato",
                            tint = StatusSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    TaskStatus.FAILED -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Fallito",
                            tint = StatusError,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    else -> {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("cancel_task_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Annulla",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar and details
            if (task.status != TaskStatus.COMPLETED && task.status != TaskStatus.FAILED) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = formatColor,
                    trackColor = MaterialTheme.colorScheme.surface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (task.downloadedBytes > 0) {
                            "${FileUtils.formatBytes(task.downloadedBytes)} • ${task.speedText}"
                        } else {
                            task.speedText
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Motivo del fallimento, così l'errore è leggibile e non solo un'icona
            if (task.status == TaskStatus.FAILED) {
                Text(
                    text = task.errorMessage ?: "Download non riuscito. Riprova.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = StatusError,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("task_error_${task.id}")
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Completed action buttons
            if (task.status == TaskStatus.COMPLETED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (task.format == MediaFormat.M4A) {
                        FilledTonalButton(
                            onClick = onPlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("play_completed_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ascolta", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onOpen,
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("open_video_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guarda", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("share_completed_${task.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Condividi", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
