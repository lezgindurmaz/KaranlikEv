package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.transformer.Effects
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RangeVolumeProcessor(
    private val volumeInside: Float,
    private val volumeOutside: Float,
    private val rangeStartMs: Long,
    private val rangeEndMs: Long
) : AudioProcessor {
    private var activeFormat = AudioProcessor.AudioFormat.NOT_SET
    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var bytesWritten = 0L

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return pendingFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }

        val sampleSize = 2 // 16-bit PCM = 2 bytes
        val sampleRate = activeFormat.sampleRate
        val channelCount = activeFormat.channelCount
        val bytesPerMillisecond = (sampleRate * channelCount * sampleSize) / 1000.0f

        val startByte = (rangeStartMs * bytesPerMillisecond).toLong()
        val endByte = (rangeEndMs * bytesPerMillisecond).toLong()

        while (inputBuffer.hasRemaining()) {
            var sample = inputBuffer.getShort()
            val currentBytePos = bytesWritten

            val scale = if (currentBytePos in startByte..endByte) {
                volumeInside
            } else {
                volumeOutside
            }

            sample = (sample * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            outputBuffer.putShort(sample)
            bytesWritten += sampleSize
        }
        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        bytesWritten = 0L
        activeFormat = pendingFormat
    }

    override fun reset() {
        flush()
        activeFormat = AudioProcessor.AudioFormat.NOT_SET
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    }
}

object VideoExporter {
    private const val TAG = "VideoExporter"

    private fun createEffectsForSegment(
        startMs: Long,
        durationMs: Long,
        isFirstSegment: Boolean,
        isLastSegment: Boolean,
        enableTransition: Boolean,
        textOverlays: List<VideoTextOverlay>,
        subtitles: List<SubtitleItem>,
        isJoinMode: Boolean,
        hasIntro: Boolean = false,
        hasOutro: Boolean = false
    ): List<Effect> {
        val effectsList = mutableListOf<Effect>()
        if (textOverlays.isNotEmpty() || subtitles.isNotEmpty() || enableTransition) {
            val bitmapOverlay = object : androidx.media3.effect.BitmapOverlay() {
                private var lastBitmap: android.graphics.Bitmap? = null

                override fun getBitmap(presentationTimeUs: Long): android.graphics.Bitmap {
                    val presentationTimeMs = presentationTimeUs / 1000

                    val width = 1280
                    val height = 720
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)

                    // 1. Render manual text overlays
                    for (item in textOverlays) {
                        val startInTrim = item.startMs - startMs
                        val endInTrim = item.endMs - startMs
                        // If it corresponds to this segment's timeline
                        if (presentationTimeMs >= startInTrim && presentationTimeMs <= endInTrim) {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = item.size
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                setShadowLayer(6f, 3f, 3f, android.graphics.Color.BLACK)
                                if (item.isBold && item.isItalic) {
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
                                } else if (item.isBold) {
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                } else if (item.isItalic) {
                                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                                }
                            }
                            val x = ((item.xCo + 1f) / 2f) * width
                            val y = ((1f - item.yCo) / 2f) * height

                            if (item.rotation != 0f) {
                                canvas.save()
                                canvas.rotate(item.rotation, x, y)
                                canvas.drawText(item.text, x, y, paint)
                                canvas.restore()
                            } else {
                                canvas.drawText(item.text, x, y, paint)
                            }
                        }
                    }

                    // 2. Render subtitle SRT overlays
                    for (sub in subtitles) {
                        val startInTrim = sub.startMs - startMs
                        val endInTrim = sub.endMs - startMs
                        if (presentationTimeMs >= startInTrim && presentationTimeMs <= endInTrim) {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.YELLOW
                                textSize = height * 0.05f
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                setShadowLayer(6f, 3f, 3f, android.graphics.Color.BLACK)
                            }
                            val x = width / 2f
                            val y = height * 0.88f
                            val lines = sub.text.split("\n")
                            var currentY = y
                            for (line in lines) {
                                canvas.drawText(line, x, currentY, paint)
                                currentY += paint.textSize + 12f
                            }
                        }
                    }

                    // 3. Render transition fade effect
                    if (enableTransition && isJoinMode) {
                        val fadeDurationMs = 1000L
                        var drawFade = false
                        var fadeAlpha = 0f

                        // Fade IN (from black) at start of segment
                        // We ONLY do this for joined videos (segment 2+), NOT for Intro/Outro transitions as per latest request
                        if (!isFirstSegment && !hasIntro && presentationTimeMs <= fadeDurationMs) {
                            val progress = presentationTimeMs.toFloat() / fadeDurationMs.toFloat()
                            fadeAlpha = 1f - progress.coerceIn(0f, 1f)
                            drawFade = true
                        }
                        // Fade OUT (to black) at end of segment
                        // We ONLY do this for joined videos (segment 1), NOT for Intro/Outro transitions as per latest request
                        else if (!isLastSegment && !hasOutro && presentationTimeMs >= (durationMs - fadeDurationMs)) {
                            val fadeStart = durationMs - fadeDurationMs
                            val progress = (presentationTimeMs - fadeStart).toFloat() / fadeDurationMs.toFloat()
                            fadeAlpha = progress.coerceIn(0f, 1f)
                            drawFade = true
                        }

                        if (drawFade && fadeAlpha > 0.01f) {
                            val transitionPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                alpha = (fadeAlpha * 255).toInt()
                                style = android.graphics.Paint.Style.FILL
                            }
                            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), transitionPaint)
                        }
                    }

                    lastBitmap?.recycle()
                    lastBitmap = bitmap
                    return bitmap
                }

                override fun getOverlaySettings(presentationTimeUs: Long): androidx.media3.effect.OverlaySettings {
                    return androidx.media3.effect.OverlaySettings.Builder().build()
                }
            }

            val overlayEffect = androidx.media3.effect.OverlayEffect(
                com.google.common.collect.ImmutableList.of<androidx.media3.effect.TextureOverlay>(bitmapOverlay)
            )
            effectsList.add(overlayEffect)
        }
        return effectsList
    }

    fun export(
        context: Context,
        videoUri: Uri,
        startMs: Long,
        endMs: Long,
        audioUri: Uri?,
        audioStartTrimMs: Long = 0L,
        audioEndTrimMs: Long = 0L,
        muteOriginalAudio: Boolean,
        textOverlays: List<VideoTextOverlay>,
        subtitles: List<SubtitleItem>,
        videoUri2: Uri? = null,
        startMs2: Long = 0L,
        endMs2: Long = 0L,
        enableTransition: Boolean = true,
        originalVolume: Float = 1.0f,
        volumeRangeStartMs: Long = 0L,
        volumeRangeEndMs: Long = 0L,
        enableVolumeDucking: Boolean = false,
        musicVolume: Float = 1.0f,
        musicRangeStartMs: Long = 0L,
        musicRangeEndMs: Long = 0L,
        enableMusicRange: Boolean = false,
        introImageUri: Uri? = null,
        introDurationMs: Long = 0L,
        outroImageUri: Uri? = null,
        outroDurationMs: Long = 0L,
        onProgress: (Float) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                // Determine output path in the app cache area
                val outputDir = File(context.cacheDir, "edited_videos")
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                val outputFile = File(outputDir, "edited_video_${System.currentTimeMillis()}.mp4")
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                var totalDuration = 0L
                val videoSegments = mutableListOf<EditedMediaItem>()

                // Append Intro Image if present
                if (introImageUri != null && introDurationMs > 0L) {
                    totalDuration += introDurationMs
                    val introFile = copyUriToCache(context, introImageUri, "intro_image.png")
                    if (introFile != null) {
                        val introUri = Uri.fromFile(introFile)
                        val introMediaItem = MediaItem.Builder().setUri(introUri).build()
                        val introEffects = createEffectsForSegment(
                            startMs = 0,
                            durationMs = introDurationMs,
                            isFirstSegment = true,
                            isLastSegment = false,
                            enableTransition = enableTransition,
                            textOverlays = emptyList(),
                            subtitles = emptyList(),
                            isJoinMode = false,
                            hasOutro = false
                        )
                        val introEditedItem = EditedMediaItem.Builder(introMediaItem)
                            .setDurationUs(introDurationMs * 1000L)
                            .setFrameRate(30)
                            .setEffects(Effects(com.google.common.collect.ImmutableList.of(), com.google.common.collect.ImmutableList.copyOf(introEffects)))
                            .build()
                        videoSegments.add(introEditedItem)
                    }
                }

                val duration1 = endMs - startMs
                totalDuration += duration1

                // Segment 1 Build
                val seg1Effects = createEffectsForSegment(
                    startMs = startMs,
                    durationMs = duration1,
                    isFirstSegment = videoSegments.isEmpty(),
                    isLastSegment = videoUri2 == null && outroImageUri == null,
                    enableTransition = enableTransition,
                    textOverlays = textOverlays,
                    subtitles = subtitles,
                    isJoinMode = videoUri2 != null || outroImageUri != null || introImageUri != null,
                    hasIntro = introImageUri != null,
                    hasOutro = videoUri2 != null || outroImageUri != null
                )

                val videoClippingConfig1 = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()

                val videoMediaItem1 = MediaItem.Builder()
                    .setUri(videoUri)
                    .setClippingConfiguration(videoClippingConfig1)
                    .build()

                val videoEditedItemBuilder1 = EditedMediaItem.Builder(videoMediaItem1)

                if (muteOriginalAudio) {
                    videoEditedItemBuilder1.setRemoveAudio(true)
                }

                val seg1AudioProcessors = mutableListOf<AudioProcessor>()
                if (!muteOriginalAudio) {
                    if (enableVolumeDucking) {
                        val overlapStart = volumeRangeStartMs.coerceAtLeast(0)
                        val overlapEnd = volumeRangeEndMs.coerceAtMost(duration1)
                        if (overlapStart < overlapEnd) {
                            seg1AudioProcessors.add(
                                RangeVolumeProcessor(
                                    volumeInside = originalVolume,
                                    volumeOutside = 1.0f,
                                    rangeStartMs = overlapStart,
                                    rangeEndMs = overlapEnd
                                )
                            )
                        }
                    } else if (originalVolume < 0.99f || originalVolume > 1.01f) {
                        seg1AudioProcessors.add(
                            RangeVolumeProcessor(
                                volumeInside = originalVolume,
                                volumeOutside = originalVolume,
                                rangeStartMs = 0,
                                rangeEndMs = duration1
                            )
                        )
                    }
                }

                val seg1AudioList = com.google.common.collect.ImmutableList.copyOf(seg1AudioProcessors)
                val seg1VideoList = com.google.common.collect.ImmutableList.copyOf(seg1Effects)
                videoEditedItemBuilder1.setEffects(Effects(seg1AudioList, seg1VideoList))

                val editedItem1 = videoEditedItemBuilder1.build()
                videoSegments.add(editedItem1)

                // Segment 2 Build if present
                if (videoUri2 != null) {
                    val duration2 = endMs2 - startMs2
                    totalDuration += duration2

                    val seg2Effects = createEffectsForSegment(
                        startMs = startMs2,
                        durationMs = duration2,
                        isFirstSegment = false,
                        isLastSegment = outroImageUri == null,
                        enableTransition = enableTransition,
                        textOverlays = emptyList(),
                        subtitles = emptyList(),
                        isJoinMode = true,
                        hasIntro = false,
                        hasOutro = false
                    )

                    val videoClippingConfig2 = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs2)
                        .setEndPositionMs(endMs2)
                        .build()

                    val videoMediaItem2 = MediaItem.Builder()
                        .setUri(videoUri2)
                        .setClippingConfiguration(videoClippingConfig2)
                        .build()

                    val videoEditedItemBuilder2 = EditedMediaItem.Builder(videoMediaItem2)

                    if (muteOriginalAudio) {
                        videoEditedItemBuilder2.setRemoveAudio(true)
                    }

                    val seg2AudioProcessors = mutableListOf<AudioProcessor>()
                    if (!muteOriginalAudio) {
                        if (enableVolumeDucking) {
                            val overlapStart = (volumeRangeStartMs - duration1).coerceAtLeast(0)
                            val overlapEnd = (volumeRangeEndMs - duration1).coerceAtMost(duration2)
                            if (overlapStart < overlapEnd) {
                                seg2AudioProcessors.add(
                                    RangeVolumeProcessor(
                                        volumeInside = originalVolume,
                                        volumeOutside = 1.0f,
                                        rangeStartMs = overlapStart,
                                        rangeEndMs = overlapEnd
                                    )
                                )
                            }
                        } else if (originalVolume < 0.99f || originalVolume > 1.01f) {
                            seg2AudioProcessors.add(
                                RangeVolumeProcessor(
                                    volumeInside = originalVolume,
                                    volumeOutside = originalVolume,
                                    rangeStartMs = 0,
                                    rangeEndMs = duration2
                                )
                            )
                        }
                    }

                    val seg2AudioList = com.google.common.collect.ImmutableList.copyOf(seg2AudioProcessors)
                    val seg2VideoList = com.google.common.collect.ImmutableList.copyOf(seg2Effects)
                    videoEditedItemBuilder2.setEffects(Effects(seg2AudioList, seg2VideoList))

                    val editedItem2 = videoEditedItemBuilder2.build()
                    videoSegments.add(editedItem2)
                }

                // Append Outro Image if present
                if (outroImageUri != null && outroDurationMs > 0L) {
                    totalDuration += outroDurationMs
                    // Decode and re-encode image to local PNG file for Transformer compatibility
                    val outroFile = copyUriToCache(context, outroImageUri, "outro_image.png")
                    if (outroFile == null) {
                        throw Exception("Kapanış görseli işlenemedi. Lütfen farklı bir görsel deneyin.")
                    }
                    val outroUri = Uri.fromFile(outroFile)
                    val outroMediaItem = MediaItem.Builder()
                        .setUri(outroUri)
                        .build()

                    val outroEffects = createEffectsForSegment(
                        startMs = 0,
                        durationMs = outroDurationMs,
                        isFirstSegment = false,
                        isLastSegment = true,
                        enableTransition = enableTransition,
                        textOverlays = emptyList(),
                        subtitles = emptyList(),
                        isJoinMode = false,
                        hasIntro = false
                    )

                    val outroDurationUs = outroDurationMs * 1000L
                    val outroEditedItem = EditedMediaItem.Builder(outroMediaItem)
                        .setDurationUs(outroDurationUs)
                        .setFrameRate(30)
                        .setEffects(Effects(com.google.common.collect.ImmutableList.of(), com.google.common.collect.ImmutableList.copyOf(outroEffects)))
                        .build()
                    videoSegments.add(outroEditedItem)
                }

                val mainVideoSequence = EditedMediaItemSequence(videoSegments)

                // Build composition
                val sequences = mutableListOf<EditedMediaItemSequence>()
                sequences.add(mainVideoSequence)

                // 3. If adding external music
                if (audioUri != null) {
                    val audioClippingConfig = MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(audioStartTrimMs)
                        .setEndPositionMs(if (audioEndTrimMs > 0) audioEndTrimMs else (audioStartTrimMs + totalDuration))
                        .build()

                    val audioMediaItem = MediaItem.Builder()
                        .setUri(audioUri)
                        .setClippingConfiguration(audioClippingConfig)
                        .build()

                    val audioEditedItemBuilder = EditedMediaItem.Builder(audioMediaItem)
                        .setRemoveVideo(true)

                    val musicProcessors = mutableListOf<AudioProcessor>()
                    if (enableMusicRange) {
                        musicProcessors.add(
                            RangeVolumeProcessor(
                                volumeInside = musicVolume,
                                volumeOutside = 0.0f,
                                rangeStartMs = musicRangeStartMs,
                                rangeEndMs = musicRangeEndMs
                            )
                        )
                    } else if (musicVolume < 0.99f || musicVolume > 1.01f) {
                        musicProcessors.add(
                            RangeVolumeProcessor(
                                volumeInside = musicVolume,
                                volumeOutside = musicVolume,
                                rangeStartMs = 0,
                                rangeEndMs = totalDuration
                            )
                        )
                    }

                    if (musicProcessors.isNotEmpty()) {
                        audioEditedItemBuilder.setEffects(
                            Effects(
                                com.google.common.collect.ImmutableList.copyOf(musicProcessors),
                                com.google.common.collect.ImmutableList.of()
                            )
                        )
                    }

                    val audioEditedItem = audioEditedItemBuilder.build()
                    val audioSequence = EditedMediaItemSequence(audioEditedItem)
                    sequences.add(audioSequence)
                }

                val composition = Composition.Builder(sequences)
                    .experimentalSetForceAudioTrack(true)
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .build()

                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Log.d(TAG, "Export completed successfully")
                        val galleryUri = insertVideoToGallery(context, outputFile, "EditedVideo_${System.currentTimeMillis()}")
                        if (galleryUri != null) {
                            onSuccess(galleryUri)
                        } else {
                            onSuccess(Uri.fromFile(outputFile))
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.e(TAG, "Export error: ${exportException.message}", exportException)
                        onError(exportException)
                    }
                }

                transformer.addListener(listener)
                transformer.start(composition, outputFile.absolutePath)

                // Poll progress on main thread (Transformer requires it)
                coroutineScope.launch(Dispatchers.Main) {
                    val progressHolder = ProgressHolder()
                    while (true) {
                        try {
                            val progressState = transformer.getProgress(progressHolder)
                            if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                                val progressVal = progressHolder.progress / 100f
                                Log.d(TAG, "Export progress: $progressVal")
                                onProgress(progressVal)
                            } else if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                                // wait
                            } else {
                                break
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error polling progress", e)
                            break
                        }
                        delay(250)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exporter launch exception", e)
                onError(e)
            }
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val cacheDir = File(context.cacheDir, "outro_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val destFile = File(cacheDir, fileName)

            // Decode image to Bitmap first (handles HEIC, WebP, etc.)
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            }

            // Downsample if too large to avoid OOM
            val maxDim = 1920
            var sampleSize = 1
            while ((options.outWidth / sampleSize) > maxDim || (options.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // Re-encode as PNG (universally supported format)
            destFile.outputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()

            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to cache: ${e.message}", e)
            null
        }
    }

    private fun insertVideoToGallery(context: Context, sourceFile: File, title: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4")
            put(MediaStore.Video.Media.TITLE, title)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collectionUri, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                Log.e(TAG, "Failed to insert into gallery", e)
                return null
            }
        }
        return uri
    }
}
