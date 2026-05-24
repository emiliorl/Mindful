package com.mindshield.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// Entity
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "completed_sessions")
data class CompletedSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val intentType: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface CompletedSessionDao {

    @Insert
    suspend fun insert(session: CompletedSession)

    @Query("SELECT * FROM completed_sessions WHERE startMs >= :fromMs AND endMs <= :toMs ORDER BY startMs ASC")
    fun getBetween(fromMs: Long, toMs: Long): Flow<List<CompletedSession>>

    @Query("SELECT * FROM completed_sessions ORDER BY startMs DESC LIMIT 500")
    fun getRecent(): Flow<List<CompletedSession>>
}
