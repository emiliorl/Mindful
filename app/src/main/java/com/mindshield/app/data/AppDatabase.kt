package com.mindshield.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HeldNotification::class, RoutineCompletion::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun heldNotificationDao(): HeldNotificationDao
    abstract fun routineCompletionDao(): RoutineCompletionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindshield.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE held_notifications ADD COLUMN status TEXT NOT NULL DEFAULT 'QUEUED'")
                db.execSQL("ALTER TABLE held_notifications ADD COLUMN deliveredAtMs INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS routine_completions (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        dateStr TEXT NOT NULL,
                        completedAtMs INTEGER NOT NULL
                    )"""
                )
            }
        }
    }
}
