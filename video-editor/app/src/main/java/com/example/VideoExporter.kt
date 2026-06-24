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
import androidx.media3.effect.Presentation
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlMatrixTransformation
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
import android.opengl.Matrix

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

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val remaining = inputBuffer.remaining()
        if (outputBuffer.capacity() < remaining) {
            outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        val sampleSize = 2
        val sampleRate = activeFormat.sampleRate
        val channelCount = activeFormat.channelCount
        val bytesPerMs = (sampleRate * channelCount * sampleSize) / 1000.0f
        val startByte = (rangeStartMs * bytesPerMs).toLong()
        val endByte = (rangeEndMs * bytesPerMs).toLong()

        while (inputBuffer.hasRemaining()) {
            var sample = inputBuffer.getShort()
            val scale = if (bytesWritten in startByte..endByte) volumeInside else volumeOutside
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

    override fun queueEndOfStream() { inputEnded = true }
    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    override fun flush() { outputBuffer = AudioProcessor.EMPTY_BUFFER; inputEnded = false; bytesWritten = 0L; activeFormat = pendingFormat }
    override fun reset() { flush(); activeFormat = AudioProcessor.AudioFormat.NOT_SET; pendingFormat = AudioProcessor.AudioFormat.NOT_SET }
}

class ZoomTransformation(private val zoomIn: Boolean, private val durationUs: Long) : GlMatrixTransformation {
    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        val progress = presentationTimeUs.toFloat() / durationUs.coerceAtLeast(1L).toFloat()
        val scale = if (zoomIn) 1.0f + (progress * 0.5f) else 1.5f - (progress * 0.5f)
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        Matrix.scaleM(matrix, 0, scale, scale, 1.0f)
        return matrix
    }
}

class SlideTransformation(private val slideLeft: Boolean, private val durationUs: Long) : GlMatrixTransformation {
    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray {
        val progress = presentationTimeUs.toFloat() / durationUs.coerceAtLeast(1L).toFloat()
        val translate = if (slideLeft) progress * 2.0f else progress * -2.0f
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        Matrix.translateM(matrix, 0, translate, 0f, 0f)
        return matrix
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

        for (item in appliedEffects) {
            val startUs = (item.startMs - startMs).coerceAtLeast(0) * 1000L
            val endUs = (item.endMs - startMs).coerceAtMost(durationMs) * 1000L
            if (startUs < endUs && startUs < durationMs * 1000L) {
                val effectDurationUs = endUs - startUs
                val glEffect: GlEffect = when (item.type) {
                    EffectType.ZOOM_IN -> ZoomTransformation(true, effectDurationUs)
                    EffectType.ZOOM_OUT -> ZoomTransformation(false, effectDurationUs)
                    EffectType.SLIDE_LEFT -> SlideTransformation(true, effectDurationUs)
                    EffectType.SLIDE_RIGHT -> SlideTransformation(false, effectDurationUs)
                    EffectType.FADE_IN -> RgbFilter.createGrayscaleFilter()
                    EffectType.FADE_OUT -> RgbFilter.createGrayscaleFilter()
                }
                effectsList.add(TimestampWrapper(glEffect, startUs, endUs))
            }
        }

        for (filter in appliedFilters) {
            val startUs = (filter.startMs - startMs).coerceAtLeast(0) * 1000L
            val endUs = (filter.endMs - startMs).coerceAtMost(durationMs) * 1000L
            if (startUs < endUs && startUs < durationMs * 1000L) {
                val media3Filter: GlEffect = when (filter.type) {
                    FilterType.GRAYSCALE -> RgbFilter.createGrayscaleFilter()
                    FilterType.SEPIA -> RgbMatrix { _, _ -> floatArrayOf(0.393f, 0.349f, 0.272f, 0f, 0.769f, 0.686f, 0.534f, 0f, 0.189f, 0.168f, 0.131f, 0f, 0f, 0f, 0f, 1f) }
                    FilterType.CYBERPUNK -> RgbMatrix { _, _ -> floatArrayOf(1.5f, -0.5f, 0.5f, 0f, -0.5f, 1.0f, 1.5f, 0f, 0.5f, 0f, 2.0f, 0f, 0f, 0f, 0f, 1f) }
                    FilterType.VINTAGE -> RgbMatrix { _, _ -> floatArrayOf(0.9f, 0.2f, 0.1f, 0f, 0.1f, 0.8f, 0.2f, 0f, 0.1f, 0.1f, 0.7f, 0f, 0f, 0f, 0f, 1f) }
                    FilterType.COOL -> RgbMatrix { _, _ -> floatArrayOf(0.7f, 0f, 0.3f, 0f, 0f, 0.8f, 0.5f, 0f, 0f, 0f, 1.4f, 0f, 0f, 0f, 0f, 1f) }
                    FilterType.WARM -> RgbMatrix { _, _ -> floatArrayOf(1.4f, 0f, 0f, 0f, 0f, 1.1f, 0f, 0f, 0f, 0f, 0.8f, 0f, 0f, 0f, 0f, 1f) }
                }
                effectsList.add(TimestampWrapper(media3Filter, startUs, endUs))
            }
        }

        if (textOverlays.isNotEmpty() || subtitles.isNotEmpty() || enableTransition) {
            val bitmapOverlay = object : androidx.media3.effect.BitmapOverlay() {
                private var lastBitmap: android.graphics.Bitmap? = null
                override fun getBitmap(presentationTimeUs: Long): android.graphics.Bitmap {
                    val presentationTimeMs = presentationTimeUs / 1000
                    val width = 1280; val height = 720
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    for (item in textOverlays) {
                        val startInTrim = item.startMs - startMs; val endInTrim = item.endMs - startMs
                        if (presentationTimeMs in startInTrim..endInTrim) {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE; textSize = item.size; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                                setShadowLayer(6f, 3f, 3f, android.graphics.Color.BLACK)
                                typeface = when {
                                    item.isBold && item.isItalic -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC)
                                    item.isBold -> android.graphics.Typeface.DEFAULT_BOLD
                                    item.isItalic -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.ITALIC)
                                    else -> android.graphics.Typeface.DEFAULT
                                }
                            }
                            val x = ((item.xCo + 1f) / 2f) * width; val y = ((1f - item.yCo) / 2f) * height
                            if (item.rotation != 0f) { canvas.save(); canvas.rotate(item.rotation, x, y); canvas.drawText(item.text, x, y, paint); canvas.restore() }
                            else canvas.drawText(item.text, x, y, paint)
                        }
                    }
                    for (sub in subtitles) {
                        val startInTrim = sub.startMs - startMs; val endInTrim = sub.endMs - startMs
                        if (presentationTimeMs in startInTrim..endInTrim) {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor(sub.colorHex)
                                textSize = height * 0.05f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER
                                setShadowLayer(6f, 3f, 3f, android.graphics.Color.BLACK)
                            }
                            val x = width / 2f; val y = height * 0.88f
                            var currentY = y; for (line in sub.text.split("\n")) { canvas.drawText(line, x, currentY, paint); currentY += paint.textSize + 12f }
                        }
                    }
                    if (enableTransition && isJoinMode) {
                        val fadeMs = 1000L; var drawFade = false; var fadeAlpha = 0f
                        if (!isFirstSegment && !hasIntro && presentationTimeMs <= fadeMs) { fadeAlpha = 1f - (presentationTimeMs.toFloat() / fadeMs).coerceIn(0f, 1f); drawFade = true }
                        else if (!isLastSegment && !hasOutro && presentationTimeMs >= (durationMs - fadeMs)) { fadeAlpha = (presentationTimeMs - (durationMs - fadeMs)).toFloat() / fadeMs; drawFade = true }
                        if (drawFade && fadeAlpha > 0.01f) {
                            val p = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; alpha = (fadeAlpha * 255).toInt(); style = android.graphics.Paint.Style.FILL }
                            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
                        }
                    }
                    lastBitmap?.recycle(); lastBitmap = bitmap; return bitmap
                }
                override fun getOverlaySettings(presentationTimeUs: Long): androidx.media3.effect.OverlaySettings = androidx.media3.effect.OverlaySettings.Builder().build()
            }
            effectsList.add(androidx.media3.effect.OverlayEffect(com.google.common.collect.ImmutableList.of(bitmapOverlay)))
        }
        return effectsList
    }

    fun export(
        context: Context, videoUri: Uri, startMs: Long, endMs: Long, audioUri: Uri?, audioStartTrimMs: Long = 0L, audioEndTrimMs: Long = 0L, muteOriginalAudio: Boolean, textOverlays: List<VideoTextOverlay>, subtitles: List<SubtitleItem>, videoUri2: Uri? = null, startMs2: Long = 0L, endMs2: Long = 0L, enableTransition: Boolean = true, originalVolume: Float = 1.0f, volumeRangeStartMs: Long = 0L, volumeRangeEndMs: Long = 0L, enableVolumeDucking: Boolean = false, musicVolume: Float = 1.0f, musicRangeStartMs: Long = 0L, musicRangeEndMs: Long = 0L, enableMusicRange: Boolean = false, introImageUri: Uri? = null, introDurationMs: Long = 0L, outroImageUri: Uri? = null, outroDurationMs: Long = 0L, effects: List<EffectItem> = emptyList(), filters: List<FilterItem> = emptyList(), onProgress: (Float) -> Unit, onSuccess: (Uri) -> Unit, onError: (Exception) -> Unit
    ) {
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                val outputDir = File(context.cacheDir, "edited_videos").apply { if (!exists()) mkdirs() }
                val outputFile = File(outputDir, "edited_video_${System.currentTimeMillis()}.mp4")
                var totalDuration = 0L; val videoSegments = mutableListOf<EditedMediaItem>()
                if (introImageUri != null && introDurationMs > 0L) {
                    totalDuration += introDurationMs
                    copyUriToCache(context, introImageUri, "intro_image.png")?.let {
                        val effects = createEffectsForSegment(0, introDurationMs, true, false, enableTransition, emptyList(), emptyList(), emptyList(), emptyList(), false, false, false)
                        videoSegments.add(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).setDurationUs(introDurationMs * 1000L).setFrameRate(30).setEffects(Effects(com.google.common.collect.ImmutableList.of(), com.google.common.collect.ImmutableList.copyOf(effects))).build())
                    }
                }
                val duration1 = endMs - startMs; totalDuration += duration1
                val seg1Effects = createEffectsForSegment(startMs, duration1, videoSegments.isEmpty(), videoUri2 == null && outroImageUri == null, enableTransition, textOverlays, subtitles, effects, filters, videoUri2 != null || outroImageUri != null || introImageUri != null, introImageUri != null, videoUri2 != null || outroImageUri != null)
                val videoEditedItemBuilder1 = EditedMediaItem.Builder(MediaItem.Builder().setUri(videoUri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build())
                if (muteOriginalAudio) videoEditedItemBuilder1.setRemoveAudio(true)
                val seg1AudioProcessors = mutableListOf<AudioProcessor>()
                if (!muteOriginalAudio) {
                    if (enableVolumeDucking) { val oS = volumeRangeStartMs.coerceAtLeast(0); val oE = volumeRangeEndMs.coerceAtMost(duration1); if (oS < oE) seg1AudioProcessors.add(RangeVolumeProcessor(originalVolume, 1.0f, oS, oE)) }
                    else if (originalVolume < 0.99f || originalVolume > 1.01f) seg1AudioProcessors.add(RangeVolumeProcessor(originalVolume, originalVolume, 0, duration1))
                }
                videoSegments.add(videoEditedItemBuilder1.setEffects(Effects(com.google.common.collect.ImmutableList.copyOf(seg1AudioProcessors), com.google.common.collect.ImmutableList.copyOf(seg1Effects))).build())
                if (videoUri2 != null) {
                    val duration2 = endMs2 - startMs2; totalDuration += duration2
                    val seg2Effects = createEffectsForSegment(startMs2, duration2, false, outroImageUri == null, enableTransition, emptyList(), emptyList(), emptyList(), emptyList(), true, false, false)
                    val videoMediaItem2 = MediaItem.Builder().setUri(videoUri2).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs2).setEndPositionMs(endMs2).build()).build()
                    val videoEditedItemBuilder2 = EditedMediaItem.Builder(videoMediaItem2)
                    if (muteOriginalAudio) videoEditedItemBuilder2.setRemoveAudio(true)
                    val seg2AudioProcessors = mutableListOf<AudioProcessor>()
                    if (!muteOriginalAudio) {
                        if (enableVolumeDucking) { val oS = (volumeRangeStartMs - duration1).coerceAtLeast(0); val oE = (volumeRangeEndMs - duration1).coerceAtMost(duration2); if (oS < oE) seg2AudioProcessors.add(RangeVolumeProcessor(originalVolume, 1.0f, oS, oE)) }
                        else if (originalVolume < 0.99f || originalVolume > 1.01f) seg2AudioProcessors.add(RangeVolumeProcessor(originalVolume, originalVolume, 0, duration2))
                    }
                    videoSegments.add(videoEditedItemBuilder2.setEffects(Effects(com.google.common.collect.ImmutableList.copyOf(seg2AudioProcessors), com.google.common.collect.ImmutableList.copyOf(seg2Effects))).build())
                }
                if (outroImageUri != null && outroDurationMs > 0L) {
                    totalDuration += outroDurationMs
                    copyUriToCache(context, outroImageUri, "outro_image.png")?.let {
                        val effects = createEffectsForSegment(0, outroDurationMs, false, true, enableTransition, emptyList(), emptyList(), emptyList(), emptyList(), false, false, false)
                        videoSegments.add(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(it))).setDurationUs(outroDurationMs * 1000L).setFrameRate(30).setEffects(Effects(com.google.common.collect.ImmutableList.of(), com.google.common.collect.ImmutableList.copyOf(effects))).build())
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
                val transformer = Transformer.Builder(context).setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264).setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC).build()
                transformer.addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { onSuccess(insertVideoToGallery(context, outputFile, "EditedVideo_${System.currentTimeMillis()}") ?: Uri.fromFile(outputFile)) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { onError(exportException) }
                })
                transformer.start(Composition.Builder(sequences).experimentalSetForceAudioTrack(true).build(), outputFile.absolutePath)
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
