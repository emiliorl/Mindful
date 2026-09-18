package com.mindshield.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HeldNotificationDao {

    @Insert
    suspend fun insert(n: HeldNotification)

    // ── Inbox (past deliveries only) ──────────────────────────────────────────

    @Query("""
        SELECT * FROM held_notifications
        WHERE status = 'DELIVERED'
        ORDER BY deliveredAtMs DESC, postedAtMs DESC
    """)
    fun getDelivered(): Flow<List<HeldNotification>>

    // ── Queue (currently held, hidden from user) ───────────────────────────────

    @Query("SELECT * FROM held_notifications WHERE status = 'QUEUED' ORDER BY postedAtMs ASC")
    suspend fun getQueuedSync(): List<HeldNotification>

    @Query("SELECT * FROM held_notifications WHERE status = 'QUEUED' AND packageName = :pkg ORDER BY postedAtMs ASC")
    suspend fun getQueuedForPackageSync(pkg: String): List<HeldNotification>

    @Query("SELECT COUNT(*) FROM held_notifications WHERE status = 'QUEUED' AND packageName = :pkg")
    suspend fun countQueuedForPackage(pkg: String): Int

    @Query("SELECT MAX(deliveredAtMs) FROM held_notifications WHERE status = 'DELIVERED' AND packageName = :pkg")
    suspend fun getLastDeliveredForPackage(pkg: String): Long?

    // ── Delivery actions ───────────────────────────────────────────────────────

    @Query("UPDATE held_notifications SET status = 'DELIVERED', deliveredAtMs = :ts WHERE status = 'QUEUED'")
    suspend fun markAllDelivered(ts: Long)

    @Query("UPDATE held_notifications SET status = 'DELIVERED', deliveredAtMs = :ts WHERE status = 'QUEUED' AND packageName = :pkg")
    suspend fun markPackageDelivered(pkg: String, ts: Long)

    @Query("UPDATE held_notifications SET status = 'DELIVERED', deliveredAtMs = :ts WHERE status = 'QUEUED' AND packageName NOT IN (:excludedPackages)")
    suspend fun markDeliveredExcept(excludedPackages: List<String>, ts: Long)

    // ── Stats queries ──────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM held_notifications WHERE deliveredAtMs >= :fromMs AND deliveredAtMs <= :toMs")
    fun countDeliveredBetween(fromMs: Long, toMs: Long): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM held_notifications WHERE status = 'QUEUED'")
    fun countQueued(): Flow<Int>

    // ── Cleanup ────────────────────────────────────────────────────────────────

    @Query("DELETE FROM held_notifications WHERE status = 'DELIVERED' AND deliveredAtMs < :olderThan")
    suspend fun deleteOldDelivered(olderThan: Long)
}
