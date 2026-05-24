package com.mindshield.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "held_notifications")
data class HeldNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    /** "QUEUED" while being silently held, "DELIVERED" after the batch fires. */
    val status: String = STATUS_QUEUED,
    val deliveredAtMs: Long? = null,
    val iconLargeBase64: String? = null
) {
    companion object {
        const val STATUS_QUEUED    = "QUEUED"
        const val STATUS_DELIVERED = "DELIVERED"
    }
}
