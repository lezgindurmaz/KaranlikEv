package com.lixoo.editor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lixoo.editor.data.local.dao.ProjectDao
import com.lixoo.editor.data.local.entity.ProjectEntity
import com.lixoo.editor.data.local.entity.VideoClipEntity

@Database(
    entities = [ProjectEntity::class, VideoClipEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
