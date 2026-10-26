package com.mindshield.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.HeldNotification
import com.mindshield.app.notification.BatchDeliveryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A single delivered batch: timestamp → notifications within it. */
data class DeliveredBatch(val deliveredAtMs: Long, val notifications: List<HeldNotification>)

/** Delivered-digest history for the Insights tab. Rule/schedule editing lives in [ShieldViewModel]. */
class NotificationsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).heldNotificationDao()

    /**
     * Past deliveries grouped into batches, newest first.
     * The live queue is intentionally never exposed — users should not
     * be able to see what's being held right now.
     */
    val batches: StateFlow<List<DeliveredBatch>> = dao.getDelivered()
        .map { items ->
            items
                .groupBy { it.deliveredAtMs ?: 0L }
                .entries
                .sortedByDescending { it.key }
                .map { (ts, list) -> DeliveredBatch(ts, list) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Number of notifications currently held, waiting for the next scheduled delivery. */
    val queuedCount: StateFlow<Int> = dao.countQueued()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun deliverNow() {
        viewModelScope.launch(Dispatchers.IO) { BatchDeliveryHelper.deliverAll(getApplication()) }
    }
}
