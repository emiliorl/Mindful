package com.mindshield.app.data

import java.time.LocalTime

enum class BatchCategory {
    INSTANT, BATCHED;

    val displayLabel: String get() = when (this) {
        INSTANT -> "Instant"
        BATCHED -> "Batched"
    }
    val description: String get() = when (this) {
        INSTANT -> "Always passes through immediately."
        BATCHED -> "Held and delivered on your schedule."
    }
}

/** How a BATCHED app's held notifications get released. Only meaningful when category == BATCHED. */
enum class DeliveryMode {
    DAYPART, COUNT, INTERVAL;

    val displayLabel: String get() = when (this) {
        DAYPART  -> "Scheduled"
        COUNT    -> "By count"
        INTERVAL -> "By interval"
    }
}

/**
 * Override for a single notification channel within an app.
 * Only set when the user explicitly configures a channel differently from the app default.
 */
data class ChannelRule(
    val channelId: String,
    val channelName: String,
    val category: BatchCategory
)

/**
 * App-level batch rule.
 * [channelOverrides] contains per-channel exceptions; channels not in the map follow [category].
 */
data class BatchRule(
    val packageName: String,
    val appLabel: String,
    val category: BatchCategory = BatchCategory.BATCHED,
    val channelOverrides: Map<String, ChannelRule> = emptyMap(),
    val deliveryMode: DeliveryMode = DeliveryMode.DAYPART,
    val intervalHours: Int = 2,      // presets: 1 / 2 / 4
    val countThreshold: Int = 10     // presets: 5 / 10 / 20
)

data class GlobalBatchSettings(
    val deliveryTimes: List<LocalTime> = listOf(
        LocalTime.of(9, 0),
        LocalTime.of(13, 0),
        LocalTime.of(18, 0)
    ),
    val batchDuringSession: Boolean = true
)

/** Makes a channel ID human-readable when no display name is available. */
fun humanizeChannelId(id: String): String =
    id.replace('_', ' ').replace('-', ' ')
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
