package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek

/**
 * Single source of truth for Accountability Partner configuration and the
 * single pending-share slot (latest wins, no history/queue).
 *
 * SharedPreferences keys:
 *   enabled                  → boolean
 *   partner_email            → string ("" = not set up)
 *   trigger_weekly_digest    → boolean (default true)
 *   trigger_session_end      → boolean (default false)
 *   trigger_violation_alert  → boolean (default false)
 *   digest_day_of_week       → DayOfWeek.name (default SUNDAY)
 *   digest_hour              → int, 24h local (default 18)
 *   pending_share_label      → string?
 *   pending_share_text       → string?
 *   pending_share_created_ms → long?
 *   last_digest_week_key     → string? (e.g. "2026-W38")
 */
object AccountabilityPartnerStore {

    private const val PREFS_NAME = "accountability_partner_store"

    private const val KEY_ENABLED                  = "enabled"
    private const val KEY_PARTNER_EMAIL            = "partner_email"
    private const val KEY_TRIGGER_WEEKLY_DIGEST    = "trigger_weekly_digest"
    private const val KEY_TRIGGER_SESSION_END      = "trigger_session_end"
    private const val KEY_TRIGGER_VIOLATION_ALERT  = "trigger_violation_alert"
    private const val KEY_DIGEST_DAY_OF_WEEK       = "digest_day_of_week"
    private const val KEY_DIGEST_HOUR              = "digest_hour"
    private const val KEY_PENDING_SHARE_LABEL      = "pending_share_label"
    private const val KEY_PENDING_SHARE_TEXT       = "pending_share_text"
    private const val KEY_PENDING_SHARE_CREATED_MS = "pending_share_created_ms"
    private const val KEY_LAST_DIGEST_WEEK_KEY     = "last_digest_week_key"

    const val DEFAULT_DIGEST_HOUR = 18
    val DEFAULT_DIGEST_DAY: DayOfWeek = DayOfWeek.SUNDAY

    // ── State ─────────────────────────────────────────────────────────────────

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled

    private val _partnerEmail = MutableStateFlow("")
    val partnerEmail: StateFlow<String> = _partnerEmail

    private val _triggerWeeklyDigest = MutableStateFlow(true)
    val triggerWeeklyDigest: StateFlow<Boolean> = _triggerWeeklyDigest

    private val _triggerSessionEnd = MutableStateFlow(false)
    val triggerSessionEnd: StateFlow<Boolean> = _triggerSessionEnd

    private val _triggerViolationAlert = MutableStateFlow(false)
    val triggerViolationAlert: StateFlow<Boolean> = _triggerViolationAlert

    private val _digestDayOfWeek = MutableStateFlow(DEFAULT_DIGEST_DAY)
    val digestDayOfWeek: StateFlow<DayOfWeek> = _digestDayOfWeek

    private val _digestHour = MutableStateFlow(DEFAULT_DIGEST_HOUR)
    val digestHour: StateFlow<Int> = _digestHour

    private val _pendingShareLabel = MutableStateFlow<String?>(null)
    val pendingShareLabel: StateFlow<String?> = _pendingShareLabel

    private val _pendingShareText = MutableStateFlow<String?>(null)
    val pendingShareText: StateFlow<String?> = _pendingShareText

    private val _pendingShareCreatedMs = MutableStateFlow<Long?>(null)
    val pendingShareCreatedMs: StateFlow<Long?> = _pendingShareCreatedMs

    private val _lastDigestWeekKey = MutableStateFlow<String?>(null)
    val lastDigestWeekKey: StateFlow<String?> = _lastDigestWeekKey

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        val p = prefs(context)
        _enabled.value                 = p.getBoolean(KEY_ENABLED, false)
        _partnerEmail.value            = p.getString(KEY_PARTNER_EMAIL, "") ?: ""
        _triggerWeeklyDigest.value     = p.getBoolean(KEY_TRIGGER_WEEKLY_DIGEST, true)
        _triggerSessionEnd.value       = p.getBoolean(KEY_TRIGGER_SESSION_END, false)
        _triggerViolationAlert.value   = p.getBoolean(KEY_TRIGGER_VIOLATION_ALERT, false)
        _digestDayOfWeek.value         = p.getString(KEY_DIGEST_DAY_OF_WEEK, null)
            ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
            ?: DEFAULT_DIGEST_DAY
        _digestHour.value              = p.getInt(KEY_DIGEST_HOUR, DEFAULT_DIGEST_HOUR)
        _pendingShareLabel.value       = p.getString(KEY_PENDING_SHARE_LABEL, null)
        _pendingShareText.value        = p.getString(KEY_PENDING_SHARE_TEXT, null)
        _pendingShareCreatedMs.value   = if (p.contains(KEY_PENDING_SHARE_CREATED_MS))
            p.getLong(KEY_PENDING_SHARE_CREATED_MS, 0L) else null
        _lastDigestWeekKey.value       = p.getString(KEY_LAST_DIGEST_WEEK_KEY, null)
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    fun setPartner(
        context: Context,
        email: String,
        enabled: Boolean,
        weekly: Boolean,
        sessionEnd: Boolean,
        violation: Boolean
    ) {
        _partnerEmail.value          = email
        _enabled.value               = enabled
        _triggerWeeklyDigest.value   = weekly
        _triggerSessionEnd.value     = sessionEnd
        _triggerViolationAlert.value = violation
        prefs(context).edit()
            .putString(KEY_PARTNER_EMAIL, email)
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_TRIGGER_WEEKLY_DIGEST, weekly)
            .putBoolean(KEY_TRIGGER_SESSION_END, sessionEnd)
            .putBoolean(KEY_TRIGGER_VIOLATION_ALERT, violation)
            .apply()
    }

    fun setPendingShare(context: Context, label: String, text: String) {
        val now = System.currentTimeMillis()
        _pendingShareLabel.value     = label
        _pendingShareText.value      = text
        _pendingShareCreatedMs.value = now
        prefs(context).edit()
            .putString(KEY_PENDING_SHARE_LABEL, label)
            .putString(KEY_PENDING_SHARE_TEXT, text)
            .putLong(KEY_PENDING_SHARE_CREATED_MS, now)
            .apply()
    }

    fun clearPendingShare(context: Context) {
        _pendingShareLabel.value     = null
        _pendingShareText.value      = null
        _pendingShareCreatedMs.value = null
        prefs(context).edit()
            .remove(KEY_PENDING_SHARE_LABEL)
            .remove(KEY_PENDING_SHARE_TEXT)
            .remove(KEY_PENDING_SHARE_CREATED_MS)
            .apply()
    }

    fun markDigestSent(context: Context, weekKey: String) {
        _lastDigestWeekKey.value = weekKey
        prefs(context).edit()
            .putString(KEY_LAST_DIGEST_WEEK_KEY, weekKey)
            .apply()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
