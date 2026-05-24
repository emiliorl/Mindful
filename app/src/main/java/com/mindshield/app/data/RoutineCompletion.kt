package com.mindshield.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "routine_completions")
data class RoutineCompletion(
    @PrimaryKey val id: String,        // "<TYPE>_<YYYY-MM-DD>" e.g. "MORNING_2026-05-24"
    val type: String,                   // "MORNING" | "WIND_DOWN"
    val dateStr: String,                // "YYYY-MM-DD"
    val completedAtMs: Long
)

@Dao
interface RoutineCompletionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: RoutineCompletion)

    @Query("SELECT * FROM routine_completions WHERE type = :type ORDER BY dateStr DESC")
    fun getAllForType(type: String): Flow<List<RoutineCompletion>>

    @Query("SELECT dateStr FROM routine_completions WHERE type = :type ORDER BY dateStr DESC")
    suspend fun getDatesForType(type: String): List<String>

    @Query("SELECT COUNT(*) FROM routine_completions WHERE type = :type AND dateStr = :dateStr")
    suspend fun countForDate(type: String, dateStr: String): Int
}
