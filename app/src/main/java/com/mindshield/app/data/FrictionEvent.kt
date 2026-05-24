package com.mindshield.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// Entity
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "friction_events")
data class FrictionEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appLabel: String,
    val timestampMs: Long,
    /** "OPENED" when the user tapped "Open anyway"; "WENT_BACK" when they tapped Go back. */
    val outcome: String
) {
    companion object {
        const val OUTCOME_OPENED    = "OPENED"
        const val OUTCOME_WENT_BACK = "WENT_BACK"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DAO
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface FrictionEventDao {

    @Insert
    suspend fun insert(event: FrictionEvent)

    @Query("SELECT * FROM friction_events WHERE timestampMs >= :fromMs AND timestampMs <= :toMs ORDER BY timestampMs DESC")
    fun getBetween(fromMs: Long, toMs: Long): Flow<List<FrictionEvent>>

    @Query("SELECT COUNT(*) FROM friction_events WHERE outcome = :outcome AND timestampMs >= :fromMs AND timestampMs <= :toMs")
    fun countByOutcomeBetween(outcome: String, fromMs: Long, toMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM friction_events WHERE timestampMs >= :fromMs AND timestampMs <= :toMs")
    fun countBetween(fromMs: Long, toMs: Long): Flow<Int>
}
