package com.mindshield.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HeldNotification::class, RoutineCompletion::class, CompletedSession::class, FrictionEvent::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun heldNotificationDao(): HeldNotificationDao
    abstract fun routineCompletionDao(): RoutineCompletionDao
    abstract fun completedSessionDao(): CompletedSessionDao
    abstract fun frictionEventDao(): FrictionEventDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindshield.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS completed_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        intentType TEXT NOT NULL,
                        startMs INTEGER NOT NULL,
                        endMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS friction_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appLabel TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        outcome TEXT NOT NULL
                    )"""
                )
            }
        }
    }
}
