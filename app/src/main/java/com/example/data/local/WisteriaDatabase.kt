package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Renames the three legacy v2 columns while preserving existing local check-ins. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE `daily_checkins_new` (
                        `id` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `ratingValue` INTEGER NOT NULL,
                        `singleInputResponse` TEXT NOT NULL,
                        `detectedTexture` TEXT NOT NULL,
                        `agentAcknowledgment` TEXT NOT NULL,
                        `isOffDay` INTEGER NOT NULL,
                        `restOrHydrationLogged` INTEGER NOT NULL,
                        `lowEffortMeal` TEXT NOT NULL,
                        `comfortContent` TEXT NOT NULL,
                        `confidenceScore` REAL NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        `firestoreDocPath` TEXT,
                        `nightShiftRunId` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO `daily_checkins_new` (
                        `id`, `date`, `timestamp`, `ratingValue`, `singleInputResponse`,
                        `detectedTexture`, `agentAcknowledgment`, `isOffDay`,
                        `restOrHydrationLogged`, `lowEffortMeal`, `comfortContent`,
                        `confidenceScore`, `syncStatus`, `firestoreDocPath`, `nightShiftRunId`
                    )
                    SELECT
                        `id`, `date`, `timestamp`, `ratingValue`, `singleInputResponse`,
                        `detectedTexture`, `agentAcknowledgment`, `isPmddWindowActive`,
                        `nerveTonicTaken`, `lowEffortMeal`, `comfortContent`,
                        `confidenceScore`, `syncStatus`, `firestoreDocPath`, `cloudRunJobId`
                    FROM `daily_checkins`
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE `daily_checkins`")
                database.execSQL("ALTER TABLE `daily_checkins_new` RENAME TO `daily_checkins`")
            }
        }
    }
}
