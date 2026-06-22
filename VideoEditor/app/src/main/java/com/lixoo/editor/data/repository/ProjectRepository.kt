package com.lixoo.editor.data.repository

import com.lixoo.editor.data.local.dao.ProjectDao
import com.lixoo.editor.data.local.entity.ProjectEntity
import com.lixoo.editor.data.local.entity.VideoClipEntity
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Long): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun createProject(name: String): Long {
        val project = ProjectEntity(name = name)
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: ProjectEntity) = projectDao.deleteProject(project)

    fun getVideoClips(projectId: Long): Flow<List<VideoClipEntity>> =
        projectDao.getVideoClipsForProject(projectId)

    suspend fun addVideoClip(clip: VideoClipEntity) = projectDao.insertVideoClip(clip)

    suspend fun updateVideoClip(clip: VideoClipEntity) = projectDao.updateVideoClip(clip)

    suspend fun deleteVideoClip(clip: VideoClipEntity) = projectDao.deleteVideoClip(clip)
}
