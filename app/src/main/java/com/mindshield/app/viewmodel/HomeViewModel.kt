package com.mindshield.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.AppDatabase
import com.mindshield.app.data.AppFrictionStore
import com.mindshield.app.data.BatchRuleStore
import com.mindshield.app.data.FrictionMode
import com.mindshield.app.data.IntentSession
import com.mindshield.app.service.ZoneManagerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val session: StateFlow<IntentSession?> = ZoneManagerService.sessionState

    /** Number of apps whose "pause screen" friction applies to the current session type. */
    val protectedCount: StateFlow<Int> = combine(session, AppFrictionStore.configs) { active, configs ->
        if (active == null) 0
        else configs.values.count { cfg ->
            when (cfg.mode) {
                FrictionMode.ALWAYS -> true
                FrictionMode.SMART  -> active.type in cfg.smartIntents
                FrictionMode.OFF    -> false
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether notifications are batched during focus sessions. */
    val batchDuringSession: StateFlow<Boolean> = BatchRuleStore.globalSettings
        .map { it.batchDuringSession }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Count only; the live queue contents are intentionally never exposed. */
    val queuedCount: StateFlow<Int> = AppDatabase.get(app).heldNotificationDao().countQueued()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    init {
        viewModelScope.launch {
            session.collectLatest { active ->
                if (active == null) {
                    _elapsedSeconds.value = 0L
                    return@collectLatest
                }
                // Tick every second for as long as this session is active
                while (true) {
                    _elapsedSeconds.value =
                        (System.currentTimeMillis() - active.startTimeMs) / 1000L
                    delay(1_000)
                }
            }
        }
    }
}

fun Long.formatElapsed(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else             "%d:%02d".format(m, s)
}

/** Spoken form of an elapsed duration, e.g. "1 hour 28 minutes". */
fun Long.spokenElapsed(): String {
    val h = this / 3600
    val m = (this % 3600) / 60
    fun plural(n: Long, unit: String) = "$n $unit" + if (n == 1L) "" else "s"
    return when {
        h > 0 && m > 0 -> "${plural(h, "hour")} ${plural(m, "minute")}"
        h > 0          -> plural(h, "hour")
        m > 0          -> plural(m, "minute")
        else           -> plural(this, "second")
    }
}
