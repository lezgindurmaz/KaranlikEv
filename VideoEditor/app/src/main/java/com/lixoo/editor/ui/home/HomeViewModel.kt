package com.lixoo.editor.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lixoo.editor.data.local.AppDatabase
import com.lixoo.editor.data.repository.ProjectRepository
import kotlinx.coroutines.launch
import androidx.room.Room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProjectRepository
    val projects: Flow<List<ProjectEntity>>

    init {
        val db = Room.databaseBuilder(application, AppDatabase::class.java, "lixoo-editor.db").build()
        repository = ProjectRepository(db.projectDao())
        projects = repository.getAllProjects()
    }

    fun createNewProject(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createProject("Yeni Proje ${System.currentTimeMillis() / 100000}")
            onCreated(id)
        }
    }
}
