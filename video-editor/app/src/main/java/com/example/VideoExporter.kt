package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Effect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.TimestampWrapper
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.GlEffect
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
        appliedEffects: List<EffectItem>,
        appliedFilters: List<FilterItem>,
        isJoinMode: Boolean,
        hasIntro: Boolean = false,
        hasOutro: Boolean = false
    ): List<Effect> {
        val effectsList = mutableListOf<Effect>()

        // 1. Video Effects (Zoom, Slide, Fade)
        for (item in appliedEffects) {
            val startUs = (item.startMs - startMs).coerceAtLeast(0) * 1000L
            val endUs = (item.endMs - startMs).coerceAtMost(durationMs) * 1000L

            if (startUs < endUs && startUs < durationMs * 1000L) {
                val glEffect: GlEffect = when (item.type) {
                    EffectType.ZOOM_IN -> ScaleAndRotateTransformation.Builder().setScale(1.4f, 1.4f).build()
                    EffectType.ZOOM_OUT -> ScaleAndRotateTransformation.Builder().setScale(0.6f, 0.6f).build()
                    EffectType.SLIDE_LEFT -> Presentation.createForWidthAndHeight(1280, 720, Presentation.LAYOUT_SCALE_TO_FIT)
                    EffectType.SLIDE_RIGHT -> Presentation.createForWidthAndHeight(1280, 720, Presentation.LAYOUT_SCALE_TO_FIT)
                    EffectType.FADE_IN -> RgbAdjustment.Builder().build() // Placeholder
                    EffectType.FADE_OUT -> RgbAdjustment.Builder().build() // Placeholder
                }
                effectsList.add(TimestampWrapper(glEffect, startUs, endUs))
            }
        }

        // 2. Color Filters
        for (filter in appliedFilters) {
            val startUs = (filter.startMs - startMs).coerceAtLeast(0) * 1000L
            val endUs = (filter.endMs - startMs).coerceAtMost(durationMs) * 1000L

            if (startUs < endUs && startUs < durationMs * 1000L) {
                val media3Filter: GlEffect = when (filter.type) {
                    FilterType.GRAYSCALE -> RgbFilter.createGrayscaleFilter()
                    FilterType.SEPIA -> {
                        val sepiaMatrix = floatArrayOf(
                            0.393f, 0.349f, 0.272f, 0f,
                            0.769f, 0.686f, 0.534f, 0f,
                            0.189f, 0.168f, 0.131f, 0f,
                            0f, 0f, 0f, 1f
                        )
                        RgbMatrix { _, _ -> sepiaMatrix }
                    }
                    FilterType.CYBERPUNK -> {
                        val matrix = floatArrayOf(
                            1.2f, 0f, 0.5f, 0f,
                            0f, 0.8f, 1.2f, 0f,
                            0.8f, 0f, 1.5f, 0f,
                            0f, 0f, 0f, 1f
                        )
                        RgbMatrix { _, _ -> matrix }
                    }
                    FilterType.VINTAGE -> {
                        val matrix = floatArrayOf(
                            0.9f, 0.2f, 0.1f, 0f,
                            0.1f, 0.8f, 0.2f, 0f,
                            0.1f, 0.1f, 0.7f, 0f,
                            0f, 0f, 0f, 1f
                        )
                        RgbMatrix { _, _ -> matrix }
                    }
                    FilterType.COOL -> {
                        val matrix = floatArrayOf(
                            0.7f, 0f, 0f, 0f,
                            0f, 0.9f, 0f, 0f,
                            0f, 0f, 1.3f, 0f,
                            0f, 0f, 0f, 1f
                        )
                        RgbMatrix { _, _ -> matrix }
                    }
                    FilterType.WARM -> {
                        val matrix = floatArrayOf(
                            1.3f, 0f, 0f, 0f,
                            0f, 1.0f, 0f, 0f,
                            0f, 0f, 0.7f, 0f,
                            0f, 0f, 0f, 1f
                        )
                        RgbMatrix { _, _ -> matrix }
                    }
                }
                effectsList.add(TimestampWrapper(media3Filter, startUs, endUs))
            }
        }

        if (textOverlays.isNotEmpty() || subtitles.isNotEmpty() || enableTransition) {
            val bitmapOverlay = object : androidx.media3.effect.BitmapOverlay() {
                private var lastBitmap: android.graphics.Bitmap? = null

                override fun getBitmap(presentationTimeUs: Long): android.graphics.Bitmap {
                    val presentationTimeMs = presentationTimeUs / 1000

                    val width = 1280
                    val height = 720
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)

                    for (item in textOverlays) {
                        val startInTrim = item.startMs - startMs
                        val endInTrim = item.endMs - startMs
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

                    if (enableTransition && isJoinMode) {
                        val fadeDurationMs = 1000L
                        var drawFade = false
                        var fadeAlpha = 0f

                        if (!isFirstSegment && !hasIntro && presentationTimeMs <= fadeDurationMs) {
                            val progress = presentationTimeMs.toFloat() / fadeDurationMs.toFloat()
                            fadeAlpha = 1f - progress.coerceIn(0f, 1f)
                            drawFade = true
                        }
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
        effects: List<EffectItem> = emptyList(),
        filters: List<FilterItem> = emptyList(),
        onProgress: (Float) -> Unit,
        onSuccess: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                val outputDir = File(context.cacheDir, "edited_videos")
                if (!outputDir.exists()) outputDir.mkdirs()
                val outputFile = File(outputDir, "edited_video_${System.currentTimeMillis()}.mp4")

                var totalDuration = 0L
                val videoSegments = mutableListOf<EditedMediaItem>()

                if (introImageUri != null && introDurationMs > 0L) {
                    totalDuration += introDurationMs
                    val introFile = copyUriToCache(context, introImageUri, "intro_image.png")
                    if (introFile != null) {
                        val introUri = Uri.fromFile(introFile)
                        val introMediaItem = MediaItem.Builder().setUri(introUri).build()
                        val introEffects = createEffectsForSegment(0, introDurationMs, true, false, enableTransition, emptyList(), emptyList(), emptyList(), emptyList(), false, false, false)
                        videoSegments.add(EditedMediaItem.Builder(introMediaItem).setDurationUs(introDurationMs * 1000L).setFrameRate(30).setEffects(Effects(com.google.common.collect.ImmutableList.of(), com.google.common.collect.ImmutableList.copyOf(introEffects))).build())
                    }
                }

                val duration1 = endMs - startMs
                totalDuration += duration1
                val seg1Effects = createEffectsForSegment(startMs, duration1, videoSegments.isEmpty(), videoUri2 == null && outroImageUri == null, enableTransition, textOverlays, subtitles, effects, filters, videoUri2 != null || outroImageUri != null || introImageUri != null, introImageUri != null, videoUri2 != null || outroImageUri != null)
                val videoMediaItem1 = MediaItem.Builder().setUri(videoUri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build()
                val videoEditedItemBuilder1 = EditedMediaItem.Builder(videoMediaItem1)
                if (muteOriginalAudio) videoEditedItemBuilder1.setRemoveAudio(true)
                val seg1AudioProcessors = mutableListOf<AudioProcessor>()
                if (!muteOriginalAudio) {
                    if (enableVolumeDucking) {
                        val overlapStart = volumeRangeStartMs.coerceAtLeast(0); val overlapEnd = volumeRangeEndMs.coerceAtMost(duration1)
                        if (overlapStart < overlapEnd) seg1AudioProcessors.add(RangeVolumeProcessor(originalVolume, 1.0f, overlapStart, overlapEnd))
                    } else if (originalVolume < 0.99f || originalVolume > 1.01f) seg1AudioProcessors.add(RangeVolumeProcessor(originalVolume, originalVolume, 0, duration1))
                }
                videoSegments.add(videoEditedItemBuilder1.setEffects(Effects(com.google.common.collect.ImmutableList.copyOf(seg1AudioProcessors), com.google.common.collect.ImmutableList.copyOf(seg1Effects))).build())

                if (videoUri2 != null) {
                    val duration2 = endMs2 - startMs2
                    totalDuration += duration2
                    val seg2Effects = createEffectsForSegment(startMs2, duration2, false, outroImageUri == null, enableTransition, emptyList(), emptyList(), emptyList(), emptyList(), true, false, false)
                    val videoMediaItem2 = MediaItem.Builder().setUri(videoUri2).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs2).setEndPositionMs(endMs2).build()).build()
                    val videoEditedItemBuilder2 = EditedMediaItem.Builder(videoMediaItem2)
                    if (muteOriginalAudio) videoEditedItemBuilder2.setRemoveAudio(true)
                    val seg2AudioProcessors = mutableListOf<AudioProcessor>()
                    if (!muteOriginalAudio) {
                        if (enableVolumeDucking) {
                            val overlapStart = (volumeRangeStartMs - duration1).coerceAtLeast(0); val overlapEnd = (volumeRangeEndMs - duration1).coerceAtMost(duration2)
                            if (overlapStart < overlapEnd) seg2AudioProcessors.add(RangeVolumeProcessor(originalVolume, 1.0f, overlapStart, overlapEnd))
                        } else if (originalVolume < 0.99f || originalVolume > 1.01f) seg2AudioProcessors.add(RangeVolumeProcessor(originalVolume, originalVolume, 0, duration2))
                    }
                    videoSegments.add(videoEditedItemBuilder2.setEffects(Effects(com.google.common.collect.ImmutableList.copyOf(seg2AudioProcessors), com.google.common.collect.ImmutableList.copyOf(seg2Effects))).build())
                }

                if (outroImageUri != null && outroDurationMs > 0L) {
                    totalDuration += outroDurationMs
                    val outroFile = copyUriToCache(context, outroImageUri, "outro_image.png")
                    if (outroFile != null) {
                        val outroUri = Uri.fromFile(outroFile)
                        val outroEffects = createEffectsForSegment(0, outroDurationMs, false, true, enableTransition, emptyList(), emptyList(), emptyList(), emptyList(), false, false, false)
                        videoSegments.add(EditedMediaItem.Builder(MediaItem.Builder().setUri(outroUri).build()).setDurationUs(outroDurationMs * 1000L).setFrameRate(30).setEffects(Effects(com.google.common.collect.ImmutableList.of(), com.google.common.collect.ImmutableList.copyOf(outroEffects))).build())
                    }
                }

                val sequences = mutableListOf(EditedMediaItemSequence(videoSegments))
                if (audioUri != null) {
                    val audioEditedItemBuilder = EditedMediaItem.Builder(MediaItem.Builder().setUri(audioUri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(audioStartTrimMs).setEndPositionMs(if (audioEndTrimMs > 0) audioEndTrimMs else (audioStartTrimMs + totalDuration)).build()).build()).setRemoveVideo(true)
                    val musicProcessors = mutableListOf<AudioProcessor>()
                    if (enableMusicRange) musicProcessors.add(RangeVolumeProcessor(musicVolume, 0.0f, musicRangeStartMs, musicRangeEndMs))
                    else if (musicVolume < 0.99f || musicVolume > 1.01f) musicProcessors.add(RangeVolumeProcessor(musicVolume, musicVolume, 0, totalDuration))
                    if (musicProcessors.isNotEmpty()) audioEditedItemBuilder.setEffects(Effects(com.google.common.collect.ImmutableList.copyOf(musicProcessors), com.google.common.collect.ImmutableList.of()))
                    sequences.add(EditedMediaItemSequence(audioEditedItemBuilder.build()))
                }

                val composition = Composition.Builder(sequences).experimentalSetForceAudioTrack(true).build()
                val transformer = Transformer.Builder(context).setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264).setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC).build()
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { onSuccess(insertVideoToGallery(context, outputFile, "EditedVideo_${System.currentTimeMillis()}") ?: Uri.fromFile(outputFile)) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { onError(exportException) }
                })
                transformer.start(composition, outputFile.absolutePath)
                coroutineScope.launch(Dispatchers.Main) {
                    val progressHolder = ProgressHolder()
                    while (true) {
                        try {
                            if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(progressHolder.progress / 100f)
                            else if (transformer.getProgress(progressHolder) != Transformer.PROGRESS_STATE_NOT_STARTED) break
                        } catch (e: Exception) { break }
                        delay(250)
                    }
                }
            } catch (e: Exception) { onError(e) }
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val destFile = File(File(context.cacheDir, "outro_cache").apply { if (!exists()) mkdirs() }, fileName)
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
            val sampleSize = generateSequence(1) { it * 2 }.first { options.outWidth / it <= 1920 && options.outHeight / it <= 1920 }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }) } ?: return null
            destFile.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle(); if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) { null }
    }

    private fun insertVideoToGallery(context: Context, sourceFile: File, title: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$title.mp4"); put(MediaStore.Video.Media.TITLE, title); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4"); put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoEditor"); put(MediaStore.Video.Media.IS_PENDING, 1) }
        }
        val uri = context.contentResolver.insert(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output -> FileInputStream(sourceFile).use { input -> input.copyTo(output) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { contentValues.clear(); contentValues.put(MediaStore.Video.Media.IS_PENDING, 0); context.contentResolver.update(uri, contentValues, null, null) }
            } catch (e: Exception) { context.contentResolver.delete(uri, null, null); return null }
        }
        return uri
    }
}
