# Phase 1 — Intent Selection: Implementation Plan

**Branch:** `phase-1/intent-selection`  
**Goal:** When the user opens the app they select their current intent before proceeding. The home screen then shows the active session and a running timer.

---

## Overview of what gets built

| File | Purpose |
|---|---|
| `data/IntentSession.kt` | Data model for a session type + `SessionStore` (SharedPreferences wrapper) |
| `service/ZoneManagerService.kt` | Foreground service that holds current session state in a `StateFlow` |
| `receiver/BootReceiver.kt` | Restart `ZoneManagerService` after device reboot |
| `screens/IntentPickerScreen.kt` | Full-screen intent picker (large tappable tiles) |
| `screens/HomeScreen.kt` | Replaces placeholder — shows active intent + live session timer |
| `viewmodel/HomeViewModel.kt` | Bridges `ZoneManagerService` state into Compose |
| `AppShell.kt` (update) | Route to `IntentPickerScreen` when no active session |

---

## Task breakdown

### Task 1 — IntentSession model + SessionStore
**File:** `app/src/main/java/com/mindshield/app/data/IntentSession.kt`

Define the sealed class / enum for built-in intent types:
```
Social Media · Work · Study · Fitness · Entertainment · Just Looking (escape hatch)
```
`SessionStore` is a thin SharedPreferences wrapper with:
- `saveSession(type: IntentType, startMillis: Long)`
- `clearSession()`
- `getActiveSession(): IntentSession?`

No Room needed — sessions are ephemeral (cleared when user changes intent or app process dies naturally).

---

### Task 2 — ZoneManagerService (foreground service)
**File:** `app/src/main/java/com/mindshield/app/service/ZoneManagerService.kt`

- Extends `Service`, runs as a foreground service with a persistent notification ("MindShield active — Work session")
- Exposes `companion object { val sessionState: StateFlow<IntentSession?> }` so Compose screens can collect it without binding
- On `START_STICKY` start: reads `SessionStore` to restore any in-progress session across restarts
- Public methods: `startSession(type)`, `endSession()`
- Register in `AndroidManifest.xml` with `android:foregroundServiceType="dataSync"`

---

### Task 3 — BootReceiver
**File:** `app/src/main/java/com/mindshield/app/receiver/BootReceiver.kt` (already exists as a stub — flesh it out)

- Receives `BOOT_COMPLETED`
- Reads `SessionStore` — if a session was active before reboot, restarts `ZoneManagerService` to restore it
- Register receiver in manifest with `RECEIVE_BOOT_COMPLETED` permission (already declared)

---

### Task 4 — IntentPickerScreen
**File:** `app/src/main/java/com/mindshield/app/screens/IntentPickerScreen.kt`

UI requirements:
- Full-screen, no bottom nav (modal feel)
- Title: "Why are you picking up your phone?"
- 6 large tiles in a 2-column grid: Social Media, Work, Study, Fitness, Entertainment, Just Looking
- Each tile has an emoji + label
- Tapping any tile calls `ZoneManagerService.startSession(type)` and navigates to Home
- "Just Looking" immediately starts a session — no block, no shame
- Design target: completable in < 3 seconds

---

### Task 5 — HomeScreen (replace placeholder)
**File:** `app/src/main/java/com/mindshield/app/screens/HomeScreen.kt`

Replace `PlaceholderScreen` with:
- Active intent card: large emoji + intent name (e.g., "Work")
- Session timer: `LaunchedEffect` coroutine counting up from session start time (format: `0:00`, `1:23`, etc.)
- "Change intent" button → navigates to `IntentPickerScreen`
- If `sessionState == null` (no active session), show a "Start a session" prompt that navigates to `IntentPickerScreen`

---

### Task 6 — HomeViewModel
**File:** `app/src/main/java/com/mindshield/app/viewmodel/HomeViewModel.kt`

- Collects `ZoneManagerService.sessionState` into `StateFlow<IntentSession?>` for the Home screen
- Exposes `elapsedSeconds: StateFlow<Long>` computed from `session.startMillis`
- Keeps the timer logic out of the composable

---

### Task 7 — Wire navigation: show IntentPickerScreen on cold open
**File:** `app/src/main/java/com/mindshield/app/shell/AppShell.kt`

- Add `"intent_picker"` as a named route in the NavHost
- On cold launch (when `sessionState == null`), navigate to `intent_picker` before showing `home`
- After a session starts, back-stack pops back to `home` (picker is not in the back stack)

---

### Task 8 — Update AndroidManifest
**File:** `app/src/main/AndroidManifest.xml`

- Add `<service android:name=".service.ZoneManagerService" android:foregroundServiceType="dataSync" />`
- Verify `BootReceiver` is registered (may already be there from Phase 0)

---

## Verification checklist

- [ ] Cold launch → `IntentPickerScreen` appears (no active session)
- [ ] Tap "Work" → navigates to `HomeScreen`, shows "Work" + timer at 0:00
- [ ] Timer ticks up every second
- [ ] "Change intent" → picker reappears, tap "Study" → home updates to "Study", timer resets
- [ ] Background the app, reopen → timer has continued (service kept running)
- [ ] Kill and reopen app → session restores from `SessionStore` via service restart
- [ ] Persistent notification visible while session is active

---

## What this phase does NOT include

- No blocking or friction (that's Phase 2)
- No accessibility service changes
- No custom user-defined intents (that's Phase 6)
- The intent picker is not a hard gate — "Just Looking" is always there
