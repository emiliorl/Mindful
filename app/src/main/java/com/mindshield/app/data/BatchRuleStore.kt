package com.mindshield.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime

/**
 * Single source of truth for per-app batch rules, per-channel overrides,
 * the global delivery schedule, and the discovered-channel cache.
 *
 * SharedPreferences key families:
 *   batch_label:<pkg>    → app display label
 *   batch_cat:<pkg>      → "INSTANT" | "BATCHED"
 *   batch_channels:<pkg> → "id§name§CAT|id§name§CAT|..."
 *
 *   known_ch:<pkg>       → "id§name|id§name|..."  (channels seen, no override set)
 *
 *   global_times         → "09:00,13:00,18:00"
 *   global_batch_session → boolean
 */
object BatchRuleStore {

    private const val PREFS_NAME          = "batch_rule_store"
    private const val PREFIX_LABEL        = "batch_label:"
    private const val PREFIX_CAT          = "batch_cat:"
    private const val PREFIX_CHANNELS     = "batch_channels:"
    private const val PREFIX_KNOWN_CH     = "known_ch:"
    private const val KEY_GLOBAL_TIMES    = "global_times"
    private const val KEY_GLOBAL_SESSION  = "global_batch_session"

    private const val FIELD_SEP  = "§"   // separates fields within a record
    private const val RECORD_SEP = "|"   // separates records within a value

    // ── State ─────────────────────────────────────────────────────────────────

    private val _configs = MutableStateFlow<Map<String, BatchRule>>(emptyMap())
    val configs: StateFlow<Map<String, BatchRule>> = _configs

    private val _globalSettings = MutableStateFlow(GlobalBatchSettings())
    val globalSettings: StateFlow<GlobalBatchSettings> = _globalSettings

    /** packageName → (channelId → channelName) — channels discovered from incoming notifications. */
    private val _knownChannels = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val knownChannels: StateFlow<Map<String, Map<String, String>>> = _knownChannels

    // ── Init ──────────────────────────────────────────────────────────────────

    fun init(context: Context) {
        _configs.value        = loadAllRules(context)
        _globalSettings.value = loadGlobalSettings(context)
        _knownChannels.value  = loadAllKnownChannels(context)
    }

    // ── Per-app rules ─────────────────────────────────────────────────────────

    fun setRule(context: Context, rule: BatchRule) {
        _configs.value = _configs.value.toMutableMap().also { it[rule.packageName] = rule }
        persistRule(context, rule)
    }

    fun removeRule(context: Context, pkg: String) {
        _configs.value = _configs.value.toMutableMap().also { it.remove(pkg) }
        prefs(context).edit().apply {
            remove("$PREFIX_LABEL$pkg")
            remove("$PREFIX_CAT$pkg")
            remove("$PREFIX_CHANNELS$pkg")
        }.apply()
    }

    // ── Channel overrides ─────────────────────────────────────────────────────

    fun setChannelOverride(context: Context, pkg: String, channelRule: ChannelRule) {
        val existing = _configs.value[pkg] ?: return
        val updated  = existing.copy(
            channelOverrides = existing.channelOverrides.toMutableMap().also {
                it[channelRule.channelId] = channelRule
            }
        )
        setRule(context, updated)
    }

    fun clearChannelOverride(context: Context, pkg: String, channelId: String) {
        val existing = _configs.value[pkg] ?: return
        val updated  = existing.copy(
            channelOverrides = existing.channelOverrides.toMutableMap().also { it.remove(channelId) }
        )
        setRule(context, updated)
    }

    // ── Channel discovery (called from service as notifications arrive) ────────

    fun recordChannel(context: Context, pkg: String, channelId: String, channelName: String) {
        val current    = _knownChannels.value.toMutableMap()
        val pkgMap     = current[pkg]?.toMutableMap() ?: mutableMapOf()
        if (pkgMap[channelId] == channelName) return   // already known, skip write
        pkgMap[channelId] = channelName
        current[pkg]   = pkgMap
        _knownChannels.value = current
        prefs(context).edit()
            .putString("$PREFIX_KNOWN_CH$pkg", encodeChannelNames(pkgMap))
            .apply()
    }

    // ── Global settings ───────────────────────────────────────────────────────

    fun setGlobalSettings(context: Context, settings: GlobalBatchSettings) {
        _globalSettings.value = settings
        prefs(context).edit().apply {
            putString(KEY_GLOBAL_TIMES,    encodeTimes(settings.deliveryTimes))
            putBoolean(KEY_GLOBAL_SESSION, settings.batchDuringSession)
        }.apply()
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private fun persistRule(context: Context, rule: BatchRule) {
        prefs(context).edit().apply {
            putString("$PREFIX_LABEL${rule.packageName}",    rule.appLabel)
            putString("$PREFIX_CAT${rule.packageName}",      rule.category.name)
            putString("$PREFIX_CHANNELS${rule.packageName}", encodeChannelRules(rule.channelOverrides))
        }.apply()
    }

    private fun loadAllRules(context: Context): Map<String, BatchRule> {
        val all = prefs(context).all
        return all.keys
            .filter { it.startsWith(PREFIX_CAT) }
            .mapNotNull { key ->
                val pkg    = key.removePrefix(PREFIX_CAT)
                val label  = all["$PREFIX_LABEL$pkg"] as? String ?: return@mapNotNull null
                val catStr = all["$PREFIX_CAT$pkg"] as? String ?: BatchCategory.BATCHED.name
                val cat    = runCatching { BatchCategory.valueOf(catStr) }.getOrDefault(BatchCategory.BATCHED)
                val overrides = decodeChannelRules(all["$PREFIX_CHANNELS$pkg"] as? String ?: "")
                pkg to BatchRule(pkg, label, cat, overrides)
            }
            .toMap()
    }

    private fun loadGlobalSettings(context: Context): GlobalBatchSettings {
        val p         = prefs(context)
        val timesStr  = p.getString(KEY_GLOBAL_TIMES, null)
        val times     = if (timesStr != null) decodeTimes(timesStr) else GlobalBatchSettings().deliveryTimes
        val session   = p.getBoolean(KEY_GLOBAL_SESSION, true)
        return GlobalBatchSettings(deliveryTimes = times, batchDuringSession = session)
    }

    private fun loadAllKnownChannels(context: Context): Map<String, Map<String, String>> {
        val all = prefs(context).all
        return all.keys
            .filter { it.startsWith(PREFIX_KNOWN_CH) }
            .associate { key ->
                val pkg    = key.removePrefix(PREFIX_KNOWN_CH)
                val rawVal = all[key] as? String ?: ""
                pkg to decodeChannelNames(rawVal)
            }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Encoding / decoding ───────────────────────────────────────────────────

    private fun encodeTimes(times: List<LocalTime>): String =
        times.joinToString(",") { "%02d:%02d".format(it.hour, it.minute) }

    private fun decodeTimes(s: String): List<LocalTime> =
        s.split(",").mapNotNull { part ->
            runCatching { val (h, m) = part.trim().split(":"); LocalTime.of(h.toInt(), m.toInt()) }.getOrNull()
        }

    /** "id§name§CAT|id§name§CAT|..." */
    private fun encodeChannelRules(overrides: Map<String, ChannelRule>): String =
        overrides.values.joinToString(RECORD_SEP) { r ->
            "${r.channelId}$FIELD_SEP${r.channelName}$FIELD_SEP${r.category.name}"
        }

    private fun decodeChannelRules(s: String): Map<String, ChannelRule> {
        if (s.isBlank()) return emptyMap()
        return s.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size < 3) return@mapNotNull null
            val cat = runCatching { BatchCategory.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
            val rule = ChannelRule(parts[0], parts[1], cat)
            rule.channelId to rule
        }.toMap()
    }

    /** "id§name|id§name|..." */
    private fun encodeChannelNames(map: Map<String, String>): String =
        map.entries.joinToString(RECORD_SEP) { (id, name) -> "$id$FIELD_SEP$name" }

    private fun decodeChannelNames(s: String): Map<String, String> {
        if (s.isBlank()) return emptyMap()
        return s.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size < 2) null else parts[0] to parts[1]
        }.toMap()
    }
}
