package com.lixoo.editor.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_clips",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class VideoClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val videoPath: String,
    val startTimeMs: Long = 0,
    val endTimeMs: Long,
    val orderIndex: Int,
    val speed: Float = 1.0f,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f
)
