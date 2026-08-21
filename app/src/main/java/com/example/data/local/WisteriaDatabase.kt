package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CheckInDao
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity

@Database(
    entities = [
        DailyCheckInEntity::class,
        CareActionEntity::class,
        AgentMemoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class WisteriaDatabase : RoomDatabase() {
    abstract fun checkInDao(): CheckInDao

    companion object {
        @Volatile
        private var INSTANCE: WisteriaDatabase? = null

        fun getInstance(context: Context): WisteriaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WisteriaDatabase::class.java,
                    "wisteria_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
