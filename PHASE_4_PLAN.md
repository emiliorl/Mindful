# Phase 4 — Routines Implementation Plan

## Goal
Structured morning and wind-down periods that gate phone access behind intentional checklists, extend friction delays, and block apps at sleep time.

---

## Architecture Overview

### New files
| File | Purpose |
|------|---------|
| `data/RoutineConfig.kt` | Data models: `MorningConfig`, `WindDownConfig`, `ChecklistItem`, `RoutinePhase` |
| `data/RoutineStore.kt` | SharedPreferences singleton + StateFlow (follows AppFrictionStore pattern) |
| `data/RoutineChecklistStore.kt` | Tracks today's completed checklist items; resets at midnight |
| `data/RoutineCompletion.kt` | Room entity for streak tracking (date + type completed) |
| `routines/RoutineScheduler.kt` | WorkManager `CoroutineWorker` — runs every 15 min, updates `routinePhase` |
| `viewmodel/RoutinesViewModel.kt` | AndroidViewModel — exposes routines state + checklist actions |
| `screens/RoutinesScreen.kt` | Full Routines tab UI (timeline + edit sheets + streak) |
| `screens/MorningCard.kt` | Home screen card shown during morning phase |
| `screens/WindDownCard.kt` | Home screen card shown during wind-down/sleep phase |

### Modified files
| File | Change |
|------|--------|
| `service/ZoneManagerService.kt` | Add `routinePhase: MutableStateFlow<RoutinePhase?>` companion object |
| `accessibility/MindShieldAccessibilityService.kt` | Routine-aware friction: MORNING forces friction on all enabled apps; WIND_DOWN extends delay; SLEEP blocks with "sleep" overlay |
| `accessibility/FrictionOverlay.kt` | Add `isSleepBlock: Boolean` param + 3-tap unlock for sleep overlay |
| `screens/HomeScreen.kt` | Conditionally show `MorningCard` or `WindDownCard` |
| `screens/PlaceholderScreens.kt` | Remove `RoutinesScreen` stub |
| `data/AppDatabase.kt` | Add `RoutineCompletion` entity + migration |
| `MindShieldApp.kt` | Enqueue `RoutineScheduler` periodic work (15 min) |

---

## Data Models (`data/RoutineConfig.kt`)

```kotlin
enum class RoutinePhase { MORNING, WIND_DOWN, SLEEP }

data class ChecklistItem(
    val id: String,
    val text: String,
    val unlocksPackage: String? = null  // package name unlocked on completion
)

data class MorningConfig(
    val enabled: Boolean = false,
    val wakeTime: LocalTime = LocalTime.of(7, 0),
    val phoneAvailableTime: LocalTime = LocalTime.of(7, 30),
    val checklist: List<ChecklistItem> = emptyList()
)

data class WindDownConfig(
    val enabled: Boolean = false,
    val startTime: LocalTime = LocalTime.of(22, 0),
    val sleepTime: LocalTime = LocalTime.of(23, 0),
    val extendedDelaySeconds: Int = 30,  // replaces global pauseDuration during wind-down
    val checklist: List<ChecklistItem> = emptyList()
)
```

### SharedPreferences keys (RoutineStore)
```
morning_enabled          → Boolean
morning_wake_time        → "HH:MM"
morning_phone_time       → "HH:MM"
morning_checklist        → JSON array of ChecklistItem
wind_down_enabled        → Boolean
wind_down_start_time     → "HH:MM"
wind_down_sleep_time     → "HH:MM"
wind_down_delay_secs     → Int
wind_down_checklist      → JSON array of ChecklistItem
```

### RoutineChecklistStore keys
```
checklist_date           → "YYYY-MM-DD"  (to detect day rollover)
completed_ids            → comma-separated ChecklistItem ids completed today
```

---

## Routine Phase Logic (`RoutineScheduler.kt`)

```
currentTime in [wakeTime, phoneAvailableTime)  → MORNING
currentTime in [startTime, sleepTime)          → WIND_DOWN
currentTime >= sleepTime                       → SLEEP
otherwise                                      → null (normal)
```

WorkManager runs every 15 minutes, calls `ZoneManagerService.updateRoutinePhase(phase)`.  
On service start, phase is also computed from current time immediately.

---

## Accessibility Service Changes

```
routinePhase == SLEEP:
  → Show sleep overlay for all apps with mode != OFF
  → Sleep overlay: "It's sleep time" message, no timer, 3-tap unlock

routinePhase == WIND_DOWN:
  → For all apps with mode != OFF: show friction with WindDownConfig.extendedDelaySeconds
  → (overrides normal shouldFriction logic)

routinePhase == MORNING:
  → For all apps with mode != OFF: show friction with normal pauseDuration
  → SMART mode apps that wouldn't normally trigger DO trigger during morning

routinePhase == null:
  → Normal behavior (AppFrictionStore.shouldFriction)
```

---

## Sleep Overlay (3-tap unlock)

`FrictionOverlay` gains `isSleepBlock: Boolean` parameter:
- When true: shows "It's sleep time 🌙" header, no breath animation, no timer
- "Go back" button always visible
- Tap counter: each tap on an "Unlock anyway" button increments a counter
- After 3 taps: app opens (strong intentional signal, not accidental)

---

## Routines Screen UI

```
RoutinesScreen
├── Today's Timeline (horizontal)
│   ├── Morning period bar (if enabled)
│   └── Wind-down + Sleep period bar (if enabled)
├── Morning Routine Card
│   ├── Toggle enabled/disabled
│   ├── "Wake time" → "Phone available time"
│   ├── Checklist items list + add/remove
│   └── App unlock assignments
└── Wind-Down Routine Card
    ├── Toggle enabled/disabled
    ├── "Start time" → "Sleep time"
    ├── Extended delay picker (10s / 30s / 60s)
    ├── Checklist items list + add/remove
    └── Streak: "X day streak 🔥"
```

---

## Home Screen Cards

**MorningCard** (shown when phase == MORNING):
- "Good morning 🌅 — [wakeTime] → [phoneAvailableTime]"
- Checklist with checkboxes (tapping completes item + possibly unlocks an app)
- Progress: "3/5 items done"

**WindDownCard** (shown when phase == WIND_DOWN):
- "Wind-down 🌙 — [startTime] → sleep at [sleepTime]"
- Checklist items
- "Friction is extended to Xs tonight"

**SleepCard** (shown when phase == SLEEP, replaces home content):
- "Sleep time 🌙"
- "Your phone is in sleep mode — apps are blocked"

---

## Room: RoutineCompletion (`data/RoutineCompletion.kt`)

```kotlin
@Entity(tableName = "routine_completions")
data class RoutineCompletion(
    @PrimaryKey val id: String,  // "<type>_<date>" e.g. "MORNING_2026-05-24"
    val type: String,             // "MORNING" | "WIND_DOWN"
    val dateStr: String,          // "YYYY-MM-DD"
    val completedAtMs: Long
)
```

Used to compute streak in RoutinesScreen.

---

## Build steps
1. `data/RoutineConfig.kt`
2. `data/RoutineStore.kt`
3. `data/RoutineChecklistStore.kt`
4. `data/RoutineCompletion.kt` + `RoutineCompletionDao.kt`
5. `AppDatabase.kt` — add entity + migration (version 2 → 3)
6. `service/ZoneManagerService.kt` — add `routinePhase` StateFlow + `updateRoutinePhase()`
7. `routines/RoutineScheduler.kt`
8. `accessibility/FrictionOverlay.kt` — sleep block variant
9. `accessibility/MindShieldAccessibilityService.kt` — phase-aware logic
10. `viewmodel/RoutinesViewModel.kt`
11. `screens/MorningCard.kt`
12. `screens/WindDownCard.kt`
13. `screens/RoutinesScreen.kt`
14. `screens/HomeScreen.kt` — integrate cards
15. `screens/PlaceholderScreens.kt` — remove stub
16. `MindShieldApp.kt` — enqueue scheduler
