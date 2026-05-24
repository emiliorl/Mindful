package com.mindshield.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mindshield.app.data.IntentSession
import com.mindshield.app.service.ZoneManagerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    val session: StateFlow<IntentSession?> = ZoneManagerService.sessionState

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
