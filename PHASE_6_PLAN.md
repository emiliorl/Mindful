# Phase 6 — Statistics (Your Time, Visualised)

## Goal

Give the user a Digital Wellbeing-style dashboard showing exactly how they've spent their phone time: session history by intent type, how often friction paused them vs. they pushed through, and how many notifications were batched vs. delivered instantly.

---

## Scope for this phase

- Persist completed sessions to Room (previously only stored the live session)
- Log friction overlay outcomes (shown / opened-anyway / went-back)
- Stats screen: bar chart, intent breakdown, friction summary, notification summary
- New "Stats" tab in the bottom nav

Out of scope for Phase 6:
- Routine streak tracking (requires Phase 4 routine completion hooks — deferred)
- Export to CSV/JSON (deferred to polish pass)
- Custom intent types (deferred)

---

## New data

### `CompletedSession` entity

| Field | Type | Notes |
|---|---|---|
| `id` | Int | autoGenerate |
| `intentType` | String | `IntentType.name` |
| `startMs` | Long | epoch ms |
| `endMs` | Long | epoch ms |
| `durationMs` | Long | `endMs - startMs` |

**`CompletedSessionDao`**:
- `insert(session: CompletedSession)`
- `getAll(): Flow<List<CompletedSession>>`
- `getBetween(fromMs: Long, toMs: Long): Flow<List<CompletedSession>>` — for date-range queries
- `getTotalDurationByType(fromMs: Long, toMs: Long): Flow<List<TypeDuration>>` — aggregated

### `FrictionEvent` entity

| Field | Type | Notes |
|---|---|---|
| `id` | Int | autoGenerate |
| `packageName` | String | which app triggered friction |
| `appLabel` | String | display name |
| `timestampMs` | Long | when overlay appeared |
| `outcome` | String | `"OPENED"` or `"WENT_BACK"` |

**`FrictionEventDao`**:
- `insert(event: FrictionEvent)`
- `getBetween(fromMs: Long, toMs: Long): Flow<List<FrictionEvent>>`
- `countByOutcomeBetween(outcome: String, fromMs: Long, toMs: Long): Flow<Int>`

---

## Step-by-step plan

### Step 1 — Room entities + DAOs

Create `data/CompletedSession.kt` and `data/FrictionEvent.kt` with their DAOs.

Add both to `AppDatabase`:
```kotlin
@Database(entities = [HeldNotification::class, CompletedSession::class, FrictionEvent::class], version = 2)
```

Bump schema version to 2. Add a `Migration(1, 2)` that creates both new tables.

### Step 2 — Wire session persistence into ZoneManagerService

In `ACTION_START`: before updating `_sessionState`, if there is an existing session in `_sessionState.value`, compute its duration and insert a `CompletedSession` via the DAO (coroutine on `Dispatchers.IO`).

In `ACTION_STOP`: same — if `_sessionState.value != null`, insert a `CompletedSession` before clearing.

### Step 3 — Wire friction event logging into MindShieldAccessibilityService

In the `FrictionOverlay` callbacks:
- `onOpenAnyway` → insert `FrictionEvent(pkg, label, now, "OPENED")`
- `onGoBack` → insert `FrictionEvent(pkg, label, now, "WENT_BACK")`

Pass a `onEventLogged` lambda into `FrictionOverlay`, or call the DAO directly from `MindShieldAccessibilityService` using `CoroutineScope(Dispatchers.IO)`.

### Step 4 — StatsViewModel

`StatsViewModel` exposes:
- `rangeOption: StateFlow<RangeOption>` — `TODAY | WEEK | MONTH`
- `dailyBars: StateFlow<List<DayBar>>` — one entry per day in range, each with per-intent durations
- `intentBreakdown: StateFlow<List<IntentTotal>>` — total ms + percentage for each intent type
- `frictionSummary: StateFlow<FrictionSummary>` — `shown`, `openedAnyway`, `wentBack` counts
- `notificationsSummary: StateFlow<NotifSummary>` — batched count from `HeldNotificationDao`

`DayBar` = `data class DayBar(val dayLabel: String, val byIntent: Map<IntentType, Long>)`
`IntentTotal` = `data class IntentTotal(val type: IntentType, val totalMs: Long, val fraction: Float)`
`FrictionSummary` = `data class FrictionSummary(val shown: Int, val openedAnyway: Int, val wentBack: Int)`

### Step 5 — StatsScreen UI

Top section: segmented button — **Today / Week / Month**

**Bar chart** (custom Compose Canvas, no library):
- 1, 7, or 30 bars depending on range
- Each bar stacked by intent type color
- Y-axis shows hours (auto-scaled to max bar height)
- X-axis shows day labels (Mon, Tue…)

**Intent breakdown list** (below chart):
- Each row: colored dot + intent label + total time formatted ("2h 14m") + thin progress bar
- Sorted by total time descending
- Empty state: "No sessions recorded yet."

**Friction card**:
- "Friction moments" header
- Three stats in a row: `Shown`, `Opened anyway`, `Went back`
- Percentage "You resisted X% of friction moments"

**Notifications card**:
- "Notifications batched" count for the range period

### Step 6 — New Stats tab

Add to `AppShell.kt`:
```kotlin
object Stats : Tab(Routes.STATS, "Stats", Icons.Outlined.BarChart)
```

Add `composable(Routes.STATS) { StatsScreen() }` to the `NavHost`.

Update `Tab.all` to include `Stats` (between Silence and Routines, or at end).

---

## Intent type color palette

| Intent | Color |
|---|---|
| SOCIAL_MEDIA | `#E57373` (red) |
| WORK | `#64B5F6` (blue) |
| STUDY | `#81C784` (green) |
| FITNESS | `#FFB74D` (orange) |
| ENTERTAINMENT | `#BA68C8` (purple) |
| JUST_LOOKING | `#90A4AE` (grey) |

---

## File list

| File | Change |
|---|---|
| `data/CompletedSession.kt` | New — entity + DAO |
| `data/FrictionEvent.kt` | New — entity + DAO |
| `data/AppDatabase.kt` | Add new entities, bump version to 2, add migration |
| `service/ZoneManagerService.kt` | Write CompletedSession on session start/stop |
| `accessibility/MindShieldAccessibilityService.kt` | Log FrictionEvent on overlay outcomes |
| `screens/StatsScreen.kt` | New — full stats UI |
| `viewmodel/StatsViewModel.kt` | New — data aggregation |
| `shell/AppShell.kt` | Add Stats tab + route |

---

## Verification checklist

- [ ] Start a session (Work, 2 min) → end it → open Stats → "Work" bar appears with ~2 min
- [ ] Open a friction-enabled app, tap "Open anyway" → Stats friction card shows 1 shown, 1 opened
- [ ] Open a friction-enabled app, tap "Go back" → Stats friction card shows 1 shown, 1 went back
- [ ] Switch range from Today → Week → bars regroup correctly
- [ ] No sessions → empty state message shown
- [ ] Batch a notification → Stats notifications card count increments
