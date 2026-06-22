package com.lixoo.editor.data.local.dao

import androidx.room.*
import com.lixoo.editor.data.local.entity.ProjectEntity
import com.lixoo.editor.data.local.entity.VideoClipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastModifiedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("SELECT * FROM video_clips WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getVideoClipsForProject(projectId: Long): Flow<List<VideoClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideoClip(clip: VideoClipEntity): Long

    @Update
    suspend fun updateVideoClip(clip: VideoClipEntity)

    @Delete
    suspend fun deleteVideoClip(clip: VideoClipEntity)
}
