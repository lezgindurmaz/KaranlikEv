package com.lixoo.editor.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.lixoo.editor.data.local.AppDatabase
import com.lixoo.editor.data.local.entity.VideoClipEntity
import com.lixoo.editor.data.repository.ProjectRepository
import com.lixoo.editor.media.PreviewEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class EditorViewModel(private val application: Application) : AndroidViewModel(application) {
    private val repository: ProjectRepository
    private val previewEngine: PreviewEngine = PreviewEngine(application)

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private var _projectId: Long = -1
    private val _clips = MutableStateFlow<List<VideoClipEntity>>(emptyList())
    val clips: StateFlow<List<VideoClipEntity>> = _clips

    init {
        val db = Room.databaseBuilder(application, AppDatabase::class.java, "lixoo-editor.db").build()
        repository = ProjectRepository(db.projectDao())
        previewEngine.initialize()
        _player.value = previewEngine.getPlayer()
    }

    fun loadProject(projectId: Long) {
        _projectId = projectId
        viewModelScope.launch {
            repository.getVideoClips(projectId).collect {
                _clips.value = it
            }
        }
    }

    fun addVideoClip(path: String, durationMs: Long) {
        viewModelScope.launch {
            val clip = VideoClipEntity(
                projectId = _projectId,
                videoPath = path,
                endTimeMs = durationMs,
                orderIndex = 0 // Simple logic for now
            )
            repository.addVideoClip(clip)
            previewEngine.setVideoSource(path, 0, durationMs)
        }
    }

    fun onTrimSelected() {
        viewModelScope.launch {
            selectedClip?.let { clip ->
                // Simple trim: cut first 2 seconds from the start for demonstration
                val newClip = clip.copy(startTimeMs = clip.startTimeMs + 2000)
                repository.updateVideoClip(newClip)
                selectedClip = newClip
                previewEngine.setVideoSource(newClip.videoPath, newClip.startTimeMs, newClip.endTimeMs)
            }
        }
    }

    private var selectedClip: VideoClipEntity? = null

    fun onClipSelected(clip: VideoClipEntity) {
        selectedClip = clip
        previewEngine.setVideoSource(clip.videoPath, clip.startTimeMs, clip.endTimeMs)
        previewEngine.setSpeed(clip.speed)
        previewEngine.setVolume(if (clip.isMuted) 0f else 1f)
    }

    fun onToggleMute() {
        viewModelScope.launch {
            selectedClip?.let { clip ->
                val newClip = clip.copy(isMuted = !clip.isMuted)
                repository.updateVideoClip(newClip)
                selectedClip = newClip
                previewEngine.setVolume(if (newClip.isMuted) 0f else 1f)
            }
        }
    }

    fun onSpeedSelected() {
        viewModelScope.launch {
            selectedClip?.let { clip ->
                val newSpeed = if (clip.speed == 1.0f) 2.0f else 1.0f
                val newClip = clip.copy(speed = newSpeed)
                repository.updateVideoClip(newClip)
                selectedClip = newClip
                previewEngine.setSpeed(newSpeed)
            }
        }
    }

    fun onFilterSelected() {
        // For now, we just log it or apply a grayscale effect if implemented in PreviewEngine
    }

    fun onTextSelected() {
        // Logic for adding text overlay
    }

    fun exportVideo() {
        val outputPath = "${application.getExternalFilesDir(null)}/export_${System.currentTimeMillis()}.mp4"
        com.lixoo.editor.media.ExportEngine.exportVideo(
            clips = _clips.value,
            outputPath = outputPath,
            onProgress = { progress ->
                // Update progress UI
            },
            onComplete = { success ->
                // Notify user
            }
        )
    }

    fun releasePlayer() {
        previewEngine.release()
    }
}
