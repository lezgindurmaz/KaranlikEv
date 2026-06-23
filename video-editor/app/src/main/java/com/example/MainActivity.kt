package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.DeepViolet
import com.example.ui.theme.HotPink
import com.example.ui.theme.SpaceObsidian
import com.example.ui.theme.SurfaceDarkBlue
import com.example.ui.theme.TextLight
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                VideoEditorApp()
            }
        }
    }
}

@Composable
fun VideoEditorApp() {
    val context = LocalContext.current

    // File State
    var videoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoName by rememberSaveable { mutableStateOf("") }
    var videoDurationMs by rememberSaveable { mutableLongStateOf(0L) }

    // Editor Parameters
    var startTrimMs by rememberSaveable { mutableLongStateOf(0L) }
    var endTrimMs by rememberSaveable { mutableLongStateOf(0L) }
    var muteOriginalAudio by rememberSaveable { mutableStateOf(false) }

    // Second Video states
    var videoUri2 by rememberSaveable { mutableStateOf<Uri?>(null) }
    var videoName2 by rememberSaveable { mutableStateOf("") }
    var videoDurationMs2 by rememberSaveable { mutableLongStateOf(0L) }
    var startTrimMs2 by rememberSaveable { mutableLongStateOf(0L) }
    var endTrimMs2 by rememberSaveable { mutableLongStateOf(0L) }
    var enableTransition by rememberSaveable { mutableStateOf(true) }
    var currentMediaIndex by rememberSaveable { mutableStateOf(0) }
    var currentPlaybackPositionMs by rememberSaveable { mutableLongStateOf(0L) }

    // Background music track state
    var audioUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var audioName by rememberSaveable { mutableStateOf("") }
    var audioVolume by rememberSaveable { mutableFloatStateOf(1f) }
    var audioDurationMs by rememberSaveable { mutableLongStateOf(0L) }
    var audioStartTrimMs by rememberSaveable { mutableLongStateOf(0L) }
    var audioEndTrimMs by rememberSaveable { mutableLongStateOf(0L) }

    // Text Overlays state
    val textOverlaysList = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf<VideoTextOverlay>().apply { addAll(it) } }
        )
    ) { mutableStateListOf<VideoTextOverlay>() }
    var newOverlayText by rememberSaveable { mutableStateOf("") }
    var newOverlaySize by rememberSaveable { mutableFloatStateOf(40f) }
    var newOverlayX by rememberSaveable { mutableFloatStateOf(0f) }
    var newOverlayY by rememberSaveable { mutableFloatStateOf(0f) }
    var newOverlayStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var newOverlayEndMs by rememberSaveable { mutableLongStateOf(4000L) }
    var newOverlayRotation by rememberSaveable { mutableFloatStateOf(0f) }
    var newOverlayIsBold by rememberSaveable { mutableStateOf(false) }
    var newOverlayIsItalic by rememberSaveable { mutableStateOf(false) }

    // Subtitle states
    val srtSubtitlesList = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf<SubtitleItem>().apply { addAll(it) } }
        )
    ) { mutableStateListOf<SubtitleItem>() }
    var srtContent by rememberSaveable { mutableStateOf("") }
    var srtFileName by rememberSaveable { mutableStateOf("") }

    // Effects & Filters state
    val appliedEffects = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf<EffectItem>().apply { addAll(it) } }
        )
    ) { mutableStateListOf<EffectItem>() }

    val appliedFilters = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { mutableStateListOf<FilterItem>().apply { addAll(it) } }
        )
    ) { mutableStateListOf<FilterItem>() }

    var selectedEffectType by rememberSaveable { mutableStateOf(EffectType.ZOOM_IN) }
    var effectStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var effectEndMs by rememberSaveable { mutableLongStateOf(2000L) }

    var selectedFilterType by rememberSaveable { mutableStateOf(FilterType.GRAYSCALE) }
    var filterStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var filterEndMs by rememberSaveable { mutableLongStateOf(2000L) }

    // Original Audio Volume Levels & Range states
    var originalVolume by rememberSaveable { mutableFloatStateOf(1.0f) }
    var enableVolumeDucking by rememberSaveable { mutableStateOf(false) }
    var volumeRangeStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var volumeRangeEndMs by rememberSaveable { mutableLongStateOf(0L) }

    // Music Duration / Range states
    var enableMusicRange by rememberSaveable { mutableStateOf(false) }
    var musicRangeStartMs by rememberSaveable { mutableLongStateOf(0L) }
    var musicRangeEndMs by rememberSaveable { mutableLongStateOf(0L) }

    // Intro Image states
    var introImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var introImageName by rememberSaveable { mutableStateOf("") }
    var introDurationMs by rememberSaveable { mutableLongStateOf(3000L) }

    // Outro Image states
    var outroImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var outroImageName by rememberSaveable { mutableStateOf("") }
    var outroDurationMs by rememberSaveable { mutableLongStateOf(3000L) }

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }

    // Process State
    var exportProgress by remember { mutableStateOf<Float?>(null) }
    var exportSuccessUri by remember { mutableStateOf<Uri?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

    // Players
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
        }
    }

    val audioPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
        }
    }

    // Release players when disposed
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            audioPlayer.release()
        }
    }

    // Reset video params on load
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            videoDurationMs = Utils.getVideoDurationMs(context, videoUri!!)
            startTrimMs = 0L
            endTrimMs = videoDurationMs
            isPlaying = false
            volumeRangeStartMs = 0L
            volumeRangeEndMs = videoDurationMs
            musicRangeStartMs = 0L
            musicRangeEndMs = videoDurationMs
        }
    }

    LaunchedEffect(videoUri2) {
        if (videoUri2 != null) {
            videoDurationMs2 = Utils.getVideoDurationMs(context, videoUri2!!)
            startTrimMs2 = 0L
            endTrimMs2 = videoDurationMs2
        }
    }

    val totalTrimmedDurationMs = (endTrimMs - startTrimMs) + (if (videoUri2 != null) (endTrimMs2 - startTrimMs2) else 0L)

    LaunchedEffect(totalTrimmedDurationMs) {
        if (volumeRangeEndMs == 0L || volumeRangeEndMs > totalTrimmedDurationMs) {
            volumeRangeEndMs = totalTrimmedDurationMs
        }
        if (musicRangeEndMs == 0L || musicRangeEndMs > totalTrimmedDurationMs) {
            musicRangeEndMs = totalTrimmedDurationMs
        }
    }

    // Whenever videoUri or videoUri2 or trim settings change, build clipped MediaItems for ExoPlayer
    LaunchedEffect(videoUri, startTrimMs, endTrimMs, videoUri2, startTrimMs2, endTrimMs2) {
        if (videoUri != null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            val clip1 = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startTrimMs)
                .setEndPositionMs(endTrimMs)
                .build()
            val item1 = MediaItem.Builder()
                .setUri(videoUri)
                .setClippingConfiguration(clip1)
                .build()

            val list = mutableListOf(item1)

            if (videoUri2 != null) {
                val clip2 = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startTrimMs2)
                    .setEndPositionMs(endTrimMs2)
                    .build()
                val item2 = MediaItem.Builder()
                    .setUri(videoUri2)
                    .setClippingConfiguration(clip2)
                    .build()
                list.add(item2)
            }

            exoPlayer.setMediaItems(list)
            exoPlayer.prepare()
        }
    }

    // Unconditional position and item index tracking for Compose overlay preview and background audio sync
    LaunchedEffect(Unit) {
        var lastPos = 0L
        while (true) {
            if (videoUri != null) {
                val currentPos = exoPlayer.currentPosition
                val currentIdx = exoPlayer.currentMediaItemIndex
                currentPlaybackPositionMs = currentPos
                currentMediaIndex = currentIdx

                if (currentPos < lastPos && isPlaying) {
                    if (audioUri != null) {
                        audioPlayer.seekTo(0)
                    }
                }
                lastPos = currentPos

                // Music range preview: mute/unmute audio based on position
                if (audioUri != null && enableMusicRange && isPlaying) {
                    val globalPositionMs = if (currentIdx == 0) currentPos else (endTrimMs - startTrimMs) + currentPos
                    val inRange = globalPositionMs in musicRangeStartMs..musicRangeEndMs
                    audioPlayer.volume = if (inRange) audioVolume else 0f
                } else if (audioUri != null && !enableMusicRange) {
                    audioPlayer.volume = audioVolume
                }
            }
            delay(50)
        }
    }

    // Whenever audioUri changes, prepare secondary audio player
    LaunchedEffect(audioUri, audioStartTrimMs, audioEndTrimMs) {
        if (audioUri != null) {
            audioPlayer.stop()
            audioPlayer.clearMediaItems()
            val clip = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(audioStartTrimMs)
                .setEndPositionMs(if (audioEndTrimMs > 0) audioEndTrimMs else Long.MAX_VALUE)
                .build()
            val item = MediaItem.Builder()
                .setUri(audioUri)
                .setClippingConfiguration(clip)
                .build()
            audioPlayer.setMediaItem(item)
            audioPlayer.prepare()
        } else {
            audioPlayer.stop()
            audioPlayer.clearMediaItems()
        }
    }

    // Sync play/pause states
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
            if (audioUri != null) {
                audioPlayer.play()
            }
        } else {
            exoPlayer.pause()
            audioPlayer.pause()
        }
    }

    // Apply volume and mute toggles to the native previews
    LaunchedEffect(muteOriginalAudio, originalVolume) {
        exoPlayer.volume = if (muteOriginalAudio) 0f else originalVolume
    }

    LaunchedEffect(audioVolume, audioUri) {
        audioPlayer.volume = audioVolume
    }

    // File Pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            videoUri = uri
            videoName = Utils.getFileName(context, uri)
        }
    }

    val videoPickerLauncher2 = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            videoUri2 = uri
            videoName2 = Utils.getFileName(context, uri)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            audioUri = uri
            audioName = Utils.getFileName(context, uri)
            audioDurationMs = Utils.getVideoDurationMs(context, uri) // Works for audio too
            audioStartTrimMs = 0L
            audioEndTrimMs = audioDurationMs
            Toast.makeText(context, "Müzik başarıyla yüklendi!", Toast.LENGTH_SHORT).show()
        }
    }

    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    srtContent = content
                    srtFileName = Utils.getFileName(context, uri)
                    val items = Utils.parseSrt(content)
                    srtSubtitlesList.clear()
                    srtSubtitlesList.addAll(items)
                    Toast.makeText(context, "${items.size} altyazı başarıyla yüklendi!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Altyazı dosyası okunamadı: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val introImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            introImageUri = uri
            introImageName = Utils.getFileName(context, uri)
            Toast.makeText(context, "Giriş görseli başarıyla seçildi!", Toast.LENGTH_SHORT).show()
        }
    }

    val outroImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            outroImageUri = uri
            outroImageName = Utils.getFileName(context, uri)
            Toast.makeText(context, "Kapanış görseli başarıyla seçildi!", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceObsidian),
        containerColor = SpaceObsidian
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CINEFX",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Gelişmiş Mobil Video Düzenleyici",
                    fontSize = 12.sp,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Top Area: Media Selection / Video Preview
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.4f), Color.Transparent)),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
            ) {
                if (videoUri == null) {
                    // Empty state (Media Selection area at the top)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { videoPickerLauncher.launch("video/*") }
                            .padding(vertical = 64.dp, horizontal = 24.dp)
                            .testTag("select_video_button"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(NeonCyan.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, NeonCyan.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = "Video Seçin",
                                tint = NeonCyan,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bir Video Seçin",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextLight
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Düzenlemeye başlamak için galerinize göz atın.",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Loaded Video Preview Area
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .background(Color.Black)
                        ) {
                            val originalTimeMs = currentPlaybackPositionMs + startTrimMs

                            // Determine active effect for preview
                            var currentScale = 1.0f
                            var currentTranslationX = 0f

                            // Check added effects
                            appliedEffects.forEach { effect ->
                                if (originalTimeMs in effect.startMs..effect.endMs) {
                                    val progress = (originalTimeMs - effect.startMs).toFloat() / (effect.endMs - effect.startMs).coerceAtLeast(1L).toFloat()
                                    when (effect.type) {
                                        EffectType.ZOOM_IN -> currentScale = 1.0f + (progress * 0.4f)
                                        EffectType.ZOOM_OUT -> currentScale = 1.4f - (progress * 0.4f)
                                        EffectType.SLIDE_LEFT -> currentTranslationX = progress * 100f
                                        EffectType.SLIDE_RIGHT -> currentTranslationX = -progress * 100f
                                        else -> {}
                                    }
                                }
                            }

                            // Check pending effect (if any)
                            if (originalTimeMs in effectStartMs..effectEndMs) {
                                val progress = (originalTimeMs - effectStartMs).toFloat() / (effectEndMs - effectStartMs).coerceAtLeast(1L).toFloat()
                                when (selectedEffectType) {
                                    EffectType.ZOOM_IN -> currentScale = 1.0f + (progress * 0.4f)
                                    EffectType.ZOOM_OUT -> currentScale = 1.4f - (progress * 0.4f)
                                    EffectType.SLIDE_LEFT -> currentTranslationX = progress * 100f
                                    EffectType.SLIDE_RIGHT -> currentTranslationX = -progress * 100f
                                    else -> {}
                                }
                            }

                            // Determine active filter for preview
                            var currentColorMatrix: ColorMatrix? = null

                            fun getFilterMatrix(type: FilterType): ColorMatrix {
                                return when (type) {
                                    FilterType.GRAYSCALE -> ColorMatrix().apply { setToSaturation(0f) }
                                    FilterType.SEPIA -> ColorMatrix(floatArrayOf(
                                        0.393f, 0.769f, 0.189f, 0f, 0f,
                                        0.349f, 0.686f, 0.168f, 0f, 0f,
                                        0.272f, 0.534f, 0.131f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    FilterType.CYBERPUNK -> ColorMatrix(floatArrayOf(
                                        1.5f, -0.5f, 0.5f, 0f, 0f,
                                        -0.5f, 1.0f, 1.5f, 0f, 0f,
                                        0.5f, 0f, 2.0f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    FilterType.VINTAGE -> ColorMatrix(floatArrayOf(
                                        0.9f, 0.1f, 0.1f, 0f, 0f,
                                        0.2f, 0.8f, 0.1f, 0f, 0f,
                                        0.1f, 0.2f, 0.7f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    FilterType.COOL -> ColorMatrix(floatArrayOf(
                                        0.7f, 0f, 0f, 0f, 0f,
                                        0f, 0.8f, 0f, 0f, 0f,
                                        0.3f, 0.5f, 1.4f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                    FilterType.WARM -> ColorMatrix(floatArrayOf(
                                        1.4f, 0f, 0f, 0f, 0f,
                                        0f, 1.1f, 0f, 0f, 0f,
                                        0f, 0f, 0.8f, 0f, 0f,
                                        0f, 0f, 0f, 1f, 0f
                                    ))
                                }
                            }

                            appliedFilters.forEach { filter ->
                                if (originalTimeMs in filter.startMs..filter.endMs) {
                                    currentColorMatrix = getFilterMatrix(filter.type)
                                }
                            }
                            if (originalTimeMs in filterStartMs..filterEndMs) {
                                currentColorMatrix = getFilterMatrix(selectedFilterType)
                            }

                            // Player View Wrapper
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = exoPlayer
                                        useController = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = currentScale
                                        scaleY = currentScale
                                        translationX = currentTranslationX
                                    }
                            )

                            // Apply Color Filter via overlay if active
                            if (currentColorMatrix != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawWithCache {
                                            onDrawWithContent {
                                                drawContent()
                                                drawRect(
                                                    color = Color.Black,
                                                    colorFilter = ColorFilter.colorMatrix(currentColorMatrix!!),
                                                    blendMode = androidx.compose.ui.graphics.BlendMode.Color
                                                )
                                            }
                                        }
                                )
                            }

                            // Live Preview Overlays (Texts & Subtitles)
                            Box(modifier = Modifier.fillMaxSize()) {
                                // 1. Render manual text overlays
                                if (currentMediaIndex == 0) {
                                    val originalTimeMs = currentPlaybackPositionMs + startTrimMs
                                    textOverlaysList.forEach { item ->
                                        if (originalTimeMs >= item.startMs && originalTimeMs <= item.endMs) {
                                            androidx.compose.ui.BiasAlignment(item.xCo, -item.yCo).let { alignment ->
                                                Text(
                                                    text = item.text,
                                                    color = Color.White,
                                                    fontSize = (item.size / 3.0f).coerceIn(12f, 32f).sp, // Scaled for preview pane
                                                    style = androidx.compose.ui.text.TextStyle(
                                                    fontWeight = if (item.isBold) FontWeight.Bold else FontWeight.Normal,
                                                    fontStyle = if (item.isItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                                        shadow = androidx.compose.ui.graphics.Shadow(
                                                            color = Color.Black,
                                                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                                            blurRadius = 4f
                                                        )
                                                    ),
                                                    modifier = Modifier
                                                        .align(alignment)
                                                        .padding(12.dp)
                                                        .rotate(item.rotation)
                                                )
                                            }
                                        }
                                    }

                                    // Live Preview of the text being currently edited
                                    if (newOverlayText.isNotBlank()) {
                                        val originalTimeMs = currentPlaybackPositionMs + startTrimMs
                                        if (originalTimeMs >= newOverlayStartMs && originalTimeMs <= newOverlayEndMs) {
                                            androidx.compose.ui.BiasAlignment(newOverlayX, -newOverlayY).let { alignment ->
                                                Text(
                                                    text = newOverlayText,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    fontSize = (newOverlaySize / 3.0f).coerceIn(12f, 32f).sp,
                                                    style = androidx.compose.ui.text.TextStyle(
                                                        fontWeight = if (newOverlayIsBold) FontWeight.Bold else FontWeight.Normal,
                                                        fontStyle = if (newOverlayIsItalic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                                        shadow = androidx.compose.ui.graphics.Shadow(
                                                            color = Color.Black,
                                                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                                            blurRadius = 4f
                                                        )
                                                    ),
                                                    modifier = Modifier
                                                        .align(alignment)
                                                        .padding(12.dp)
                                                        .rotate(newOverlayRotation)
                                                        .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .padding(4.dp)
                                                )
                                            }
                                        }
                                    }

                                    // 2. Render subtitle SRT overlays
                                    srtSubtitlesList.forEach { sub ->
                                        if (originalTimeMs >= sub.startMs && originalTimeMs <= sub.endMs) {
                                            Text(
                                                text = sub.text,
                                                color = Color.Yellow,
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    fontWeight = FontWeight.Bold,
                                                    shadow = androidx.compose.ui.graphics.Shadow(
                                                        color = Color.Black,
                                                        offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                                        blurRadius = 4f
                                                    )
                                                ),
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 32.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // 3. Transition Fade Overlays
                                if (videoUri2 != null && enableTransition) {
                                    val duration1 = endTrimMs - startTrimMs
                                    var fadeAlphaPrv = 0f
                                    if (currentMediaIndex == 0) {
                                        // Fade Out in the last 1 second of segment 1
                                        if (currentPlaybackPositionMs >= (duration1 - 1000L)) {
                                            val progress = (currentPlaybackPositionMs - (duration1 - 1000L)).toFloat() / 1000f
                                            fadeAlphaPrv = progress.coerceIn(0f, 1f)
                                        }
                                    } else if (currentMediaIndex == 1) {
                                        // Fade In in the first 1 second of segment 2
                                        if (currentPlaybackPositionMs <= 1000L) {
                                            val progress = currentPlaybackPositionMs.toFloat() / 1000f
                                            fadeAlphaPrv = 1f - progress.coerceIn(0f, 1f)
                                        }
                                    }

                                    if (fadeAlphaPrv > 0.01f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = fadeAlphaPrv))
                                        )
                                    }
                                }
                            }

                            // Quick Change Video Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(SpaceObsidian.copy(alpha = 0.8f), CircleShape)
                                    .clickable { videoPickerLauncher.launch("video/*") }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Videoyu Değiştir",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Range marker labels
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Seçilen Aralık: ${Utils.formatTime(startTrimMs)} - ${Utils.formatTime(endTrimMs)}",
                                    fontSize = 12.sp,
                                    color = TextLight,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = "Toplam Kırpılmış: ${Utils.formatTime(endTrimMs - startTrimMs)}",
                                    fontSize = 12.sp,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Playback Control Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(NeonCyan, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Durdur" else "Oynat",
                                    tint = SpaceObsidian,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = videoUri != null,
                enter = fadeIn(animationSpec = tween(400)),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Video duration details info name
                    Text(
                        text = "Dosya: $videoName",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Trimming Sliders Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = "Trim",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Videoyu Kırpma",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Start Slider
                            Text(
                                text = "Başlangıç Noktası (Kırp): ${Utils.formatTime(startTrimMs)}",
                                fontSize = 13.sp,
                                color = TextLight,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = startTrimMs.toFloat(),
                                onValueChange = { newVal ->
                                    startTrimMs = newVal.toLong().coerceAtMost(endTrimMs - 500L)
                                    exoPlayer.seekTo(startTrimMs)
                                },
                                valueRange = 0f..videoDurationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trim_start_slider")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // End Slider
                            Text(
                                text = "Bitiş Noktası (Kırp): ${Utils.formatTime(endTrimMs)}",
                                fontSize = 13.sp,
                                color = TextLight,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = endTrimMs.toFloat(),
                                onValueChange = { newVal ->
                                    endTrimMs = newVal.toLong().coerceAtLeast(startTrimMs + 500L)
                                    exoPlayer.seekTo(endTrimMs)
                                },
                                valueRange = 0f..videoDurationMs.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = HotPink,
                                    activeTrackColor = HotPink,
                                    inactiveTrackColor = Color.DarkGray
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("trim_end_slider")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1b. Join Second Video Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Birleştir",
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "İkinci Videoyu Birleştir",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextLight
                                    )
                                }

                                if (videoUri2 != null) {
                                    IconButton(
                                        onClick = {
                                            videoUri2 = null
                                            videoName2 = ""
                                            videoDurationMs2 = 0L
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "İkinci Videoyu Kaldır",
                                            tint = HotPink,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (videoUri2 == null) {
                                Button(
                                    onClick = { videoPickerLauncher2.launch("video/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepViolet),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Movie, "Seç", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Birleştirilecek İkinci Videoyu Seç", color = Color.White)
                                }
                            } else {
                                Text(
                                    text = "Dosya: $videoName2",
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // Transition Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Yumuşak Geçiş Efekti (Fade)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextLight
                                        )
                                        Text(
                                            text = "1 saniyelik siyah ekran geçişi uygular.",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Switch(
                                        checked = enableTransition,
                                        onCheckedChange = { enableTransition = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = NeonCyan,
                                            checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Second Video Start Slider
                                Text(
                                    text = "İkinci Başlangıç Noktası (Kırp): ${Utils.formatTime(startTrimMs2)}",
                                    fontSize = 13.sp,
                                    color = TextLight,
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = startTrimMs2.toFloat(),
                                    onValueChange = { newVal ->
                                        startTrimMs2 = newVal.toLong().coerceAtMost(endTrimMs2 - 500L)
                                    },
                                    valueRange = 0f..videoDurationMs2.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan,
                                        inactiveTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Second Video End Slider
                                Text(
                                    text = "İkinci Bitiş Noktası (Kırp): ${Utils.formatTime(endTrimMs2)}",
                                    fontSize = 13.sp,
                                    color = TextLight,
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = endTrimMs2.toFloat(),
                                    onValueChange = { newVal ->
                                        endTrimMs2 = newVal.toLong().coerceAtLeast(startTrimMs2 + 500L)
                                    },
                                    valueRange = 0f..videoDurationMs2.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = HotPink,
                                        activeTrackColor = HotPink,
                                        inactiveTrackColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Audio & Muting / Adding music controls CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Ses",
                                    tint = DeepViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Müzik Ekleme / Ses Ayarları",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Mute Switch Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (muteOriginalAudio) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Mute State",
                                        tint = if (muteOriginalAudio) HotPink else NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Orijinal Sesi Kapat",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextLight
                                        )
                                        Text(
                                            text = "Videonun kendi sesini tamamen susturur.",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Switch(
                                    checked = muteOriginalAudio,
                                    onCheckedChange = { muteOriginalAudio = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonCyan,
                                        checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("mute_audio_checkbox")
                                )
                            }

                            // Range & Volume Level control under muteOriginalAudio
                            if (!muteOriginalAudio) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = Color.DarkGray.copy(alpha = 0.3f)
                                )

                                Text(
                                    text = "Orijinal Ses Seviyesi: %${(originalVolume * 100).toInt()}",
                                    fontSize = 12.sp,
                                    color = TextLight,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = originalVolume,
                                    onValueChange = { originalVolume = it },
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sesi Belirli Bölümde Kıs/Ayarla",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextLight
                                        )
                                        Text(
                                            text = "Seçilen sürede ayarlı sesi uygular, dışarıda tam ses.",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Switch(
                                        checked = enableVolumeDucking,
                                        onCheckedChange = { enableVolumeDucking = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = NeonCyan,
                                            checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                if (enableVolumeDucking && totalTrimmedDurationMs > 0L) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Kısma Aralığı: ${(volumeRangeStartMs / 1000)}s - ${(volumeRangeEndMs / 1000)}s",
                                        fontSize = 12.sp,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Başlangıç Saniyesi: ${(volumeRangeStartMs / 1000)}s",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    Slider(
                                        value = volumeRangeStartMs.toFloat(),
                                        onValueChange = {
                                            volumeRangeStartMs = it.toLong()
                                                .coerceAtMost(volumeRangeEndMs)
                                        },
                                        valueRange = 0f..totalTrimmedDurationMs.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonCyan,
                                            activeTrackColor = NeonCyan,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )

                                    Text(
                                        text = "Bitiş Saniyesi: ${(volumeRangeEndMs / 1000)}s",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                    Slider(
                                        value = volumeRangeEndMs.toFloat(),
                                        onValueChange = {
                                            volumeRangeEndMs = it.toLong()
                                                .coerceAtLeast(volumeRangeStartMs)
                                        },
                                        valueRange = 0f..totalTrimmedDurationMs.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonCyan,
                                            activeTrackColor = NeonCyan,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = Color.DarkGray.copy(alpha = 0.3f)
                            )

                            // Add background music section
                            if (audioUri == null) {
                                Button(
                                    onClick = { audioPickerLauncher.launch("audio/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepViolet),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_music_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = "Müzik Ekle",
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Arka Plan Müziği Ekle",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                // Music loaded state details
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = "Müzik",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = audioName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextLight,
                                                maxLines = 1
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                audioUri = null
                                                audioName = ""
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Müziği Sil",
                                                tint = HotPink,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Background music volume control
                                    Text(
                                        text = "Müzik Sesi Seviyesi: %${(audioVolume * 100).toInt()}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = audioVolume,
                                        onValueChange = { audioVolume = it },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = DeepViolet,
                                            activeTrackColor = DeepViolet,
                                            inactiveTrackColor = Color.DarkGray
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCut,
                                            contentDescription = "Trim",
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Müziği Kırp",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextLight
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Başlangıç: ${Utils.formatTime(audioStartTrimMs)}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = audioStartTrimMs.toFloat(),
                                        onValueChange = { audioStartTrimMs = it.toLong().coerceAtMost(audioEndTrimMs - 500L) },
                                        valueRange = 0f..audioDurationMs.toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = NeonCyan)
                                    )

                                    Text(
                                        text = "Bitiş: ${Utils.formatTime(audioEndTrimMs)}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = audioEndTrimMs.toFloat(),
                                        onValueChange = { audioEndTrimMs = it.toLong().coerceAtLeast(audioStartTrimMs + 500L) },
                                        valueRange = 0f..audioDurationMs.toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = HotPink)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Müziği Sadece Belirli Bölümde Çal",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextLight
                                            )
                                            Text(
                                                text = "Müzik sadece seçilen zaman aralığında çalar.",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Switch(
                                            checked = enableMusicRange,
                                            onCheckedChange = { enableMusicRange = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = NeonCyan,
                                                checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                                            )
                                        )
                                    }

                                    if (enableMusicRange && totalTrimmedDurationMs > 0L) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Müzik Aralığı: ${(musicRangeStartMs / 1000)}s - ${(musicRangeEndMs / 1000)}s",
                                            fontSize = 12.sp,
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Başlangıç Saniyesi: ${(musicRangeStartMs / 1000)}s",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                        Slider(
                                            value = musicRangeStartMs.toFloat(),
                                            onValueChange = {
                                                musicRangeStartMs = it.toLong()
                                                    .coerceAtMost(musicRangeEndMs)
                                            },
                                            valueRange = 0f..totalTrimmedDurationMs.toFloat(),
                                            colors = SliderDefaults.colors(
                                                thumbColor = NeonCyan,
                                                activeTrackColor = NeonCyan,
                                                inactiveTrackColor = Color.DarkGray
                                            )
                                        )

                                        Text(
                                            text = "Bitiş Saniyesi: ${(musicRangeEndMs / 1000)}s",
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                        Slider(
                                            value = musicRangeEndMs.toFloat(),
                                            onValueChange = {
                                                musicRangeEndMs = it.toLong()
                                                    .coerceAtLeast(musicRangeStartMs)
                                            },
                                            valueRange = 0f..totalTrimmedDurationMs.toFloat(),
                                            colors = SliderDefaults.colors(
                                                thumbColor = NeonCyan,
                                                activeTrackColor = NeonCyan,
                                                inactiveTrackColor = Color.DarkGray
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2.1 Giriş Görseli (Intro) Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Giriş",
                                    tint = DeepViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Giriş Görseli (Intro) Ekle",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (introImageUri == null) {
                                Button(
                                    onClick = { introImagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepViolet),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Görsel Seç"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Giriş Görseli Seç (Galeriden)",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Image,
                                                contentDescription = "Görsel",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = introImageName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextLight,
                                                maxLines = 1
                                             )
                                         }

                                         IconButton(
                                             onClick = {
                                                 introImageUri = null
                                                 introImageName = ""
                                             }
                                         ) {
                                             Icon(
                                                 imageVector = Icons.Default.Delete,
                                                 contentDescription = "Görseli Sil",
                                                 tint = HotPink,
                                                 modifier = Modifier.size(18.dp)
                                             )
                                         }
                                     }

                                     Spacer(modifier = Modifier.height(12.dp))

                                     Text(
                                         text = "Giriş Süresi: ${(introDurationMs / 1000)} Saniye",
                                         fontSize = 12.sp,
                                         color = TextLight,
                                         fontWeight = FontWeight.Medium
                                     )
                                     Slider(
                                         value = introDurationMs.toFloat(),
                                         onValueChange = { introDurationMs = it.toLong() },
                                         valueRange = 1000f..10000f,
                                         steps = 8,
                                         colors = SliderDefaults.colors(
                                             thumbColor = DeepViolet,
                                             activeTrackColor = DeepViolet,
                                             inactiveTrackColor = Color.DarkGray
                                         )
                                     )
                                 }
                             }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2.2 Kapanış Görseli (Outro) Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "Kapanış",
                                    tint = DeepViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Kapanış Görseli (Outro) Ekle",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (outroImageUri == null) {
                                Button(
                                    onClick = { outroImagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepViolet),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = "Görsel Seç"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Kapanış Görseli Seç (Galeriden)",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = "Görsel",
                                                tint = NeonCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = outroImageName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextLight,
                                                maxLines = 1
                                             )
                                         }

                                         IconButton(
                                             onClick = {
                                                 outroImageUri = null
                                                 outroImageName = ""
                                             }
                                         ) {
                                             Icon(
                                                 imageVector = Icons.Default.Delete,
                                                 contentDescription = "Görseli Sil",
                                                 tint = HotPink,
                                                 modifier = Modifier.size(18.dp)
                                             )
                                         }
                                     }

                                     Spacer(modifier = Modifier.height(12.dp))

                                     Text(
                                         text = "Kapanış Süresi: ${(outroDurationMs / 1000)} Saniye",
                                         fontSize = 12.sp,
                                         color = TextLight,
                                         fontWeight = FontWeight.Medium
                                     )
                                     Slider(
                                         value = outroDurationMs.toFloat(),
                                         onValueChange = { outroDurationMs = it.toLong() },
                                         valueRange = 1000f..10000f,
                                         steps = 8,
                                         colors = SliderDefaults.colors(
                                             thumbColor = DeepViolet,
                                             activeTrackColor = DeepViolet,
                                             inactiveTrackColor = Color.DarkGray
                                         )
                                     )
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.SpaceBetween
                                     ) {
                                         Text("1 saniye", fontSize = 10.sp, color = TextMuted)
                                         Text("10 saniye", fontSize = 10.sp, color = TextMuted)
                                     }
                                 }
                             }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. Metin Ekleme Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TextFields,
                                    contentDescription = "Metin",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Metin Ekleme (Video Üzerine)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Outlined text input
                            OutlinedTextField(
                                value = newOverlayText,
                                onValueChange = { newOverlayText = it },
                                label = { Text("Mevcut Metin") },
                                textStyle = androidx.compose.ui.text.TextStyle(color = TextLight),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = NeonCyan,
                                    unfocusedLabelColor = Color.Gray
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Numerical/Slider Tool: Text size
                            Text(
                                text = "Metin Boyutu: ${newOverlaySize.toInt()} sp",
                                fontSize = 13.sp,
                                color = TextLight,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = newOverlaySize,
                                onValueChange = { newOverlaySize = it },
                                valueRange = 20f..120f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = Color.DarkGray
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Switch(
                                        checked = newOverlayIsBold,
                                        onCheckedChange = { newOverlayIsBold = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Kalın", color = TextLight, fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Switch(
                                        checked = newOverlayIsItalic,
                                        onCheckedChange = { newOverlayIsItalic = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Eğik", color = TextLight, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Döndürme: ${newOverlayRotation.toInt()}°",
                                fontSize = 13.sp,
                                color = TextLight,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = newOverlayRotation,
                                onValueChange = { newOverlayRotation = it },
                                valueRange = 0f..360f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Coordinate Inputs: Slider/Numerical relative position
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "X Koordinatı: ${String.format("%.2f", newOverlayX)}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = newOverlayX,
                                        onValueChange = { newOverlayX = it },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = HotPink,
                                            activeTrackColor = HotPink
                                        )
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Y Koordinatı: ${String.format("%.2f", newOverlayY)}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = newOverlayY,
                                        onValueChange = { newOverlayY = it },
                                        valueRange = -1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = HotPink,
                                            activeTrackColor = HotPink
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Time span of overlay (start time and end time) within video bounds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Giriş: ${Utils.formatTime(newOverlayStartMs)}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = newOverlayStartMs.toFloat(),
                                        onValueChange = { newOverlayStartMs = it.toLong().coerceAtMost(newOverlayEndMs - 100L) },
                                        valueRange = 0f..videoDurationMs.toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = NeonCyan)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Çıkış: ${Utils.formatTime(newOverlayEndMs)}",
                                        fontSize = 12.sp,
                                        color = TextLight
                                    )
                                    Slider(
                                        value = newOverlayEndMs.toFloat(),
                                        onValueChange = { newOverlayEndMs = it.toLong().coerceAtLeast(newOverlayStartMs + 100L) },
                                        valueRange = 0f..videoDurationMs.toFloat(),
                                        colors = SliderDefaults.colors(thumbColor = NeonCyan)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Add Overlay Button
                            Button(
                                onClick = {
                                    if (newOverlayText.isNotBlank()) {
                                        textOverlaysList.add(
                                            VideoTextOverlay(
                                                text = newOverlayText,
                                                size = newOverlaySize,
                                                xCo = newOverlayX,
                                                yCo = newOverlayY,
                                                startMs = newOverlayStartMs,
                                                endMs = newOverlayEndMs,
                                                rotation = newOverlayRotation,
                                                isBold = newOverlayIsBold,
                                                isItalic = newOverlayIsItalic
                                            )
                                        )
                                        newOverlayText = ""
                                        newOverlayRotation = 0f
                                        newOverlayIsBold = false
                                        newOverlayIsItalic = false
                                        Toast.makeText(context, "Metin listeye eklendi!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Lütfen bir metin girin!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, "Ekle", tint = SpaceObsidian)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Metni Ekle", fontWeight = FontWeight.Bold, color = SpaceObsidian)
                            }

                            // Added Text overlays list visualization
                            if (textOverlaysList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Eklenen Metinler:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                textOverlaysList.forEachIndexed { index, overlay ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "\"${overlay.text}\" (${overlay.size.toInt()} sp)",
                                                fontSize = 12.sp,
                                                color = TextLight,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Konum: X:${String.format("%.1f", overlay.xCo)} Y:${String.format("%.1f", overlay.yCo)} | Dönüş: ${overlay.rotation.toInt()}° | Süre: ${Utils.formatTime(overlay.startMs)} - ${Utils.formatTime(overlay.endMs)}",
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        IconButton(
                                            onClick = { textOverlaysList.removeAt(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Sil",
                                                tint = HotPink,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Altyazı .SRT Ekleme Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Altyazı",
                                    tint = DeepViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Altyazı Dosyası (.SRT) Ekle",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action button to load file
                            Button(
                                onClick = { srtPickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Subtitles, "Altyazı Dosyası Seç", tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (srtFileName.isNotEmpty()) srtFileName else "Altyazı (.srt) Dosyası Seç",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (srtSubtitlesList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${srtSubtitlesList.size} altyazı öğesi aktif durumda.",
                                        fontSize = 12.sp,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    IconButton(
                                        onClick = {
                                            srtSubtitlesList.clear()
                                            srtFileName = ""
                                            srtContent = ""
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Yedek altyazıları sil",
                                            tint = HotPink,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Alternatif: Altyazı içeriğini yapıştırabilirsiniz",
                                fontSize = 11.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = srtContent,
                                onValueChange = { contentVal ->
                                    srtContent = contentVal
                                    if (contentVal.isNotBlank()) {
                                        val items = Utils.parseSrt(contentVal)
                                        srtSubtitlesList.clear()
                                        srtSubtitlesList.addAll(items)
                                    } else {
                                        srtSubtitlesList.clear()
                                    }
                                },
                                label = { Text("SRT Altyazı Kodları / Metni") },
                                placeholder = { Text("1\n00:00:01,000 --> 00:00:04,000\nMerhaba video!") },
                                textStyle = androidx.compose.ui.text.TextStyle(color = TextLight, fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepViolet,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = DeepViolet
                                ),
                                maxLines = 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Efekt Ekleme Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Efektler",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Videoya Efekt Ekle",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Effect Selection
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                EffectType.values().forEach { type ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selectedEffectType == type) NeonCyan else Color.DarkGray)
                                            .clickable { selectedEffectType = type }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(type.name.replace("_", " "), fontSize = 10.sp, color = if (selectedEffectType == type) SpaceObsidian else Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Aralık: ${Utils.formatTime(effectStartMs)} - ${Utils.formatTime(effectEndMs)}", color = TextLight, fontSize = 12.sp)
                            Slider(
                                value = effectStartMs.toFloat(),
                                onValueChange = { effectStartMs = it.toLong().coerceAtMost(effectEndMs - 500L) },
                                valueRange = 0f..videoDurationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = NeonCyan)
                            )
                            Slider(
                                value = effectEndMs.toFloat(),
                                onValueChange = { effectEndMs = it.toLong().coerceAtLeast(effectStartMs + 500L) },
                                valueRange = 0f..videoDurationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = HotPink)
                            )

                            Button(
                                onClick = {
                                    appliedEffects.add(EffectItem(selectedEffectType, effectStartMs, effectEndMs))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text("Efekti Ekle", color = SpaceObsidian)
                            }

                            appliedEffects.forEachIndexed { index, item ->
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.type.name} (${Utils.formatTime(item.startMs)}-${Utils.formatTime(item.endMs)})", color = TextMuted, fontSize = 11.sp)
                                    Icon(Icons.Default.Delete, "", tint = HotPink, modifier = Modifier.size(16.dp).clickable { appliedEffects.removeAt(index) })
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 6. Filtre Ekleme Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDarkBlue)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Filter,
                                    contentDescription = "Filtreler",
                                    tint = DeepViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Renk Filtreleri",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterType.values().take(3).forEach { type ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selectedFilterType == type) DeepViolet else Color.DarkGray)
                                            .clickable { selectedFilterType = type }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(type.name, fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterType.values().drop(3).forEach { type ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selectedFilterType == type) DeepViolet else Color.DarkGray)
                                            .clickable { selectedFilterType = type }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(type.name, fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Aralık: ${Utils.formatTime(filterStartMs)} - ${Utils.formatTime(filterEndMs)}", color = TextLight, fontSize = 12.sp)
                            Slider(
                                value = filterStartMs.toFloat(),
                                onValueChange = { filterStartMs = it.toLong().coerceAtMost(filterEndMs - 500L) },
                                valueRange = 0f..videoDurationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = NeonCyan)
                            )
                            Slider(
                                value = filterEndMs.toFloat(),
                                onValueChange = { filterEndMs = it.toLong().coerceAtLeast(filterStartMs + 500L) },
                                valueRange = 0f..videoDurationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = HotPink)
                            )

                            Button(
                                onClick = {
                                    appliedFilters.add(FilterItem(selectedFilterType, filterStartMs, filterEndMs))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
                            ) {
                                Text("Filtreyi Ekle", color = Color.White)
                            }

                            appliedFilters.forEachIndexed { index, item ->
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${item.type.name} (${Utils.formatTime(item.startMs)}-${Utils.formatTime(item.endMs)})", color = TextMuted, fontSize = 11.sp)
                                    Icon(Icons.Default.Delete, "", tint = HotPink, modifier = Modifier.size(16.dp).clickable { appliedFilters.removeAt(index) })
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. Export Main CTA Button
                    Button(
                        onClick = {
                            isPlaying = false // Pause previews prior to export
                            exportProgress = 0f
                            VideoExporter.export(
                                context = context,
                                videoUri = videoUri!!,
                                startMs = startTrimMs,
                                endMs = endTrimMs,
                                audioUri = audioUri,
                                audioStartTrimMs = audioStartTrimMs,
                                audioEndTrimMs = audioEndTrimMs,
                                muteOriginalAudio = muteOriginalAudio,
                                textOverlays = textOverlaysList.toList(),
                                subtitles = srtSubtitlesList.toList(),
                                videoUri2 = videoUri2,
                                startMs2 = startTrimMs2,
                                endMs2 = endTrimMs2,
                                enableTransition = enableTransition,
                                originalVolume = originalVolume,
                                volumeRangeStartMs = volumeRangeStartMs,
                                volumeRangeEndMs = volumeRangeEndMs,
                                enableVolumeDucking = enableVolumeDucking,
                                musicVolume = audioVolume,
                                musicRangeStartMs = musicRangeStartMs,
                                musicRangeEndMs = musicRangeEndMs,
                                enableMusicRange = enableMusicRange,
                                introImageUri = introImageUri,
                                introDurationMs = if (introImageUri != null) introDurationMs else 0L,
                                outroImageUri = outroImageUri,
                                outroDurationMs = if (outroImageUri != null) outroDurationMs else 0L,
                                effects = appliedEffects.toList(),
                                filters = appliedFilters.toList(),
                                onProgress = { progress ->
                                    exportProgress = progress
                                },
                                onSuccess = { outUri ->
                                    exportProgress = null
                                    exportSuccessUri = outUri
                                    Toast.makeText(context, "Video Başarıyla Dışa Aktarıldı!", Toast.LENGTH_LONG).show()
                                },
                                onError = { err ->
                                    exportProgress = null
                                    exportError = err.localizedMessage ?: "Dışa aktarım hatası meydana geldi."
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp)
                            .shadow(
                                elevation = 8.dp,
                                spotColor = NeonCyan,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .testTag("export_button")
                    ) {
                        Text(
                            text = "Yeni Videoyu Kaydet / Aktar",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SpaceObsidian,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }

    // Export Progress Full-Screen Dialog
    if (exportProgress != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, Brush.radialGradient(listOf(NeonCyan, DeepViolet)), RoundedCornerShape(24.dp)),
                color = SurfaceDarkBlue
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = exportProgress ?: 0f,
                        modifier = Modifier.size(64.dp),
                        color = NeonCyan,
                        strokeWidth = 6.dp,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Videonuz Hazırlanıyor...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "İşlem Tamamlanıyor: %${((exportProgress ?: 0f) * 100).toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NeonCyan,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Kırpma ve ses ayarlarınız uygulanıyor. Lütfen uygulamayı kapatmayın.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // Export Success Dialog
    if (exportSuccessUri != null) {
        Dialog(
            onDismissRequest = { exportSuccessUri = null }
        ) {
            Surface(
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, NeonCyan, RoundedCornerShape(24.dp)),
                color = SurfaceDarkBlue
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(NeonCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Success",
                            tint = NeonCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Başarıyla Dışa Aktarıldı!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextLight,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Düzenlenen yeni video başarıyla galerinize ('Movies/VideoEditor' dizini) ve cihaz hafızasına kaydedildi.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(exportSuccessUri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Videonuzu oynatacak bir uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Videoyu Oynat",
                            color = SpaceObsidian,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, exportSuccessUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Videoyu Paylaş"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Paylaşım başlatılamadı.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DeepViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Videoyu Paylaş",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { exportSuccessUri = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Kapat",
                            color = TextLight,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Export Error Dialog
    if (exportError != null) {
        Dialog(
            onDismissRequest = { exportError = null }
        ) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, HotPink, RoundedCornerShape(24.dp)),
                color = SurfaceDarkBlue
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(HotPink.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Hata",
                            tint = HotPink,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Aktarım Başarısız!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextLight,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = exportError ?: "Beklenmeyen hata.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { exportError = null },
                        colors = ButtonDefaults.buttonColors(containerColor = HotPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tamam",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
