package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.player.PlayerState
import com.example.ui.theme.CrimsonPrimary
import com.example.utils.FileUtils

@Composable
fun MiniAudioPlayer(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!playerState.isVisible) return

    val progress = if (playerState.durationMs > 0) {
        (playerState.currentPositionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mini_audio_player"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Seek bar / progress line
            Slider(
                value = progress,
                onValueChange = { newFrac ->
                    val newMs = (newFrac * playerState.durationMs).toInt()
                    onSeek(newMs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = CrimsonPrimary,
                    activeTrackColor = CrimsonPrimary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail or fallback music icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (playerState.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = playerState.thumbnailUrl,
                            contentDescription = "Copertina brano",
                            modifier = Modifier.size(46.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = CrimsonPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Track details and duration
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playerState.trackTitle.ifBlank { "In riproduzione" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = playerState.trackAuthor.ifBlank { "YouTube Audio" },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${FileUtils.formatDuration(playerState.currentPositionMs)} / ${FileUtils.formatDuration(playerState.durationMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = CrimsonPrimary
                        )
                    }
                }

                // Play / Pause button
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.testTag("player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pausa" else "Riproduci",
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Chiudi player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
