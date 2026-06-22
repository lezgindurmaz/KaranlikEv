package com.lixoo.editor.ui.editor

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(UnstableApi::class)
@ExperimentalMaterial3Api
@Composable
fun EditorScreen(
    projectId: Long,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val player by viewModel.player.collectAsState()
    val clips by viewModel.clips.collectAsState(initial = emptyList())

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.releasePlayer() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Düzenleyici") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    Button(onClick = { viewModel.exportVideo() }) {
                        Text("Kaydet")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                player?.let { exoPlayer ->
                    AndroidView(
                        factory = {
                            PlayerView(it).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } ?: CircularProgressIndicator()
            }

            // Timeline Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    "Zaman Çizelgesi",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelMedium
                )

                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(clips) { clip ->
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .height(80.dp)
                                .padding(4.dp),
                            onClick = { viewModel.onClipSelected(clip) }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Klip ${clip.id}", style = MaterialTheme.typography.bodySmall)
                                    Text("${(clip.endTimeMs - clip.startTimeMs)/1000}s", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    item {
                        IconButton(onClick = { viewModel.addVideoClip("mock_path_${System.currentTimeMillis()}.mp4", 10000) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Ekle")
                        }
                    }
                }
            }

            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { /* Filter Logic */ }) { Text("Filtre") }
                TextButton(onClick = { viewModel.onTrimSelected() }) { Text("Kırp") }
                TextButton(onClick = { /* Text Logic */ }) { Text("Metin") }
                TextButton(onClick = { viewModel.onToggleMute() }) { Text("Ses") }
                TextButton(onClick = { viewModel.onSpeedSelected() }) { Text("Hız") }
            }
        }
    }
}
