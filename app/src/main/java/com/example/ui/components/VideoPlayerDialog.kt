package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CrimsonPrimary
import com.example.utils.FileUtils
import kotlinx.coroutines.delay

enum class VideoPlayerTab {
    REAL_STREAM,
    OFFLINE_FILE
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayerDialog(
    videoId: String,
    videoTitle: String,
    videoAuthor: String,
    pathOrUri: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(VideoPlayerTab.REAL_STREAM) }

    val playableUri = remember(pathOrUri) { FileUtils.getPlayableUri(context, pathOrUri) }

    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }

    // Progress update timer for local file playback
    LaunchedEffect(isPlaying, selectedTab) {
        if (selectedTab == VideoPlayerTab.OFFLINE_FILE) {
            while (isPlaying) {
                videoViewRef?.let { vv ->
                    if (vv.isPlaying) {
                        currentPosMs = vv.currentPosition
                        durationMs = vv.duration.coerceAtLeast(1000)
                    }
                }
                delay(300)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("in_app_video_player_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with title and close button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = videoTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = videoAuthor.ifBlank { "YouTube Video" },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_video_player_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Chiudi",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Mode Switcher: Real Video Stream vs Offline File
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Real stream tab
                        val isReal = selectedTab == VideoPlayerTab.REAL_STREAM
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isReal) CrimsonPrimary else Color.Transparent)
                                .clickable {
                                    videoViewRef?.pause()
                                    selectedTab = VideoPlayerTab.REAL_STREAM
                                }
                                .testTag("tab_real_stream"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = if (isReal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Video Originale HD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isReal) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isReal) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Offline file tab
                        val isOffline = selectedTab == VideoPlayerTab.OFFLINE_FILE
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isOffline) CrimsonPrimary else Color.Transparent)
                                .clickable { selectedTab = VideoPlayerTab.OFFLINE_FILE }
                                .testTag("tab_offline_file"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = if (isOffline) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "File Scaricato",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isOffline) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isOffline) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Video Container (16:9 aspect ratio)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        when (selectedTab) {
                            VideoPlayerTab.REAL_STREAM -> {
                                // Real YouTube Video Player via embedded WebView
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.mediaPlaybackRequiresUserGesture = false
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                            webChromeClient = WebChromeClient()
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    isLoading = false
                                                }
                                            }

                                            val embedHtml = """
                                                <!DOCTYPE html>
                                                <html>
                                                <head>
                                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                    <style>
                                                        * { box-sizing: border-box; }
                                                        html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
                                                        iframe { width:100%; height:100%; border:none; display:block; }
                                                    </style>
                                                </head>
                                                <body>
                                                    <iframe 
                                                        src="https://www.youtube.com/embed/$videoId?autoplay=1&enablejsapi=1&playsinline=1&rel=0&modestbranding=1" 
                                                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                                        allowfullscreen>
                                                    </iframe>
                                                </body>
                                                </html>
                                            """.trimIndent()

                                            loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                                            webViewRef = this
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            VideoPlayerTab.OFFLINE_FILE -> {
                                if (playableUri != null) {
                                    AndroidView(
                                        factory = { ctx ->
                                            VideoView(ctx).apply {
                                                layoutParams = FrameLayout.LayoutParams(
                                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                                    FrameLayout.LayoutParams.MATCH_PARENT
                                                )
                                                setVideoURI(playableUri)
                                                setOnPreparedListener { mp ->
                                                    mp.isLooping = true
                                                    isLoading = false
                                                    durationMs = duration.coerceAtLeast(1000)
                                                    start()
                                                    isPlaying = true
                                                }
                                                setOnCompletionListener { isPlaying = false }
                                                videoViewRef = this
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Play/Pause overlay for offline video
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            videoViewRef?.let { vv ->
                                                if (vv.isPlaying) {
                                                    vv.pause()
                                                    isPlaying = false
                                                } else {
                                                    vv.start()
                                                    isPlaying = true
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!isPlaying && !isLoading) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.65f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Riproduci",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Seekbar & Controls (shown for offline file)
                    if (selectedTab == VideoPlayerTab.OFFLINE_FILE) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            val progressFraction = if (durationMs > 0) {
                                (currentPosMs.toFloat() / durationMs).coerceIn(0f, 1f)
                            } else 0f

                            Slider(
                                value = progressFraction,
                                onValueChange = { frac ->
                                    val targetMs = (frac * durationMs).toInt()
                                    currentPosMs = targetMs
                                    videoViewRef?.seekTo(targetMs)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = CrimsonPrimary,
                                    activeTrackColor = CrimsonPrimary
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${FileUtils.formatDuration(currentPosMs)} / ${FileUtils.formatDuration(durationMs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IconButton(
                                    onClick = {
                                        videoViewRef?.let { vv ->
                                            if (vv.isPlaying) {
                                                vv.pause()
                                                isPlaying = false
                                            } else {
                                                vv.start()
                                                isPlaying = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                                        tint = CrimsonPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Open YouTube app or browser
                        OutlinedButton(
                            onClick = {
                                val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                                try {
                                    context.startActivity(ytIntent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("YouTube", style = MaterialTheme.typography.labelMedium)
                        }

                        // Open external player
                        OutlinedButton(
                            onClick = onOpenExternal,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apri esterno", style = MaterialTheme.typography.labelMedium)
                        }

                        // Share file
                        OutlinedButton(
                            onClick = onShare,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Condividi", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
                webViewRef?.destroy()
            } catch (_: Exception) {}
        }
    }
}
