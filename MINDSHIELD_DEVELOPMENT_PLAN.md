# MindShield — Development Plan

## What is MindShield?

MindShield is an Android app built around one idea: **making you conscious of why you picked up your phone.**

Most digital wellbeing apps try to stop you from using your phone. MindShield doesn't. It adds a layer of intentionality between you and your apps — a brief moment of awareness that asks "why am I here?" before habit takes over. The goal is not restriction, it's consciousness. You can still do everything. You just do it on purpose.

The app is built around four pillars:

1. **Intent** — declare why you're unlocking your phone before you use it
2. **Friction** — a breath moment before habit-loop apps open
3. **Silence** — batch your notifications so you check on your terms, not theirs
4. **Rhythm** — structure your mornings and wind-downs so your phone serves your life, not the other way around

---

## Core principles that guide every design decision

- **Low friction for the user, high friction for mindless use.** Setup should be simple. Onboarding should be fast. But opening Instagram at 11pm should make you pause.
- **Never punish intentional use.** Camera, Spotify, Maps, Calendar, Calculator are always accessible instantly. Friction is reserved for the apps the user explicitly flags.
- **No willpower required at the moment of temptation.** All decisions about what gets blocked and when are made in advance, when the user is calm — not in the moment when they're exhausted and reaching for the phone.
- **Accountability without shame.** The partner feature is about support, not surveillance.

---

## Phase 0 — Foundation (Project Setup)

**Goal:** A clean, compilable Android project with permissions onboarding. No features yet, just the scaffolding everything else depends on.

### What gets built
- Fresh Android Studio project (Empty Activity, Kotlin + Compose, min SDK 26)
- Permissions onboarding screen that requests all 5 required permissions in sequence:
  - `POST_NOTIFICATIONS`
  - Accessibility Service access
  - VPN permission (for future use, requested upfront to avoid interrupting later flows)
  - Battery optimization exemption (keeps services alive)
  - Draw over other apps (required for the friction overlay)
- SharedPreferences flag so onboarding only shows once
- Clean app navigation shell with bottom nav (4 tabs: Home, Apps, Notifications, Routines)

### Verification
App launches → shows onboarding → after all permissions granted → shows empty 4-tab navigation shell.

### Key files
- `MainActivity.kt`
- `PermissionGate.kt`
- `AppShell.kt` (navigation scaffold)
- `AndroidManifest.xml` (all permissions declared)

---

## Phase 1 — Intent Selection (Why Are You Here?)

**Goal:** When the user opens the app (or optionally on every unlock via the accessibility service), they select their current intent before proceeding.

### What gets built
- **IntentSession model** — a set of predefined session types with user-customizable additions:
  - Social media
  - Work
  - Study
  - Fitness / Gym
  - Entertainment
  - Others (user-defined custom intents, stored locally)
- **IntentPickerScreen** — a clean, fast UI showing intent options as large tappable tiles. Designed to be completable in under 3 seconds so it never feels like a wall.
- **SessionStore** — SharedPreferences wrapper that stores the current active intent and its start time
- **ZoneManagerService** — a foreground service (persistent notification) that holds the current session state and exposes it to the rest of the app via a `StateFlow`
- **Home screen** — shows the current active intent, how long the session has been running, and a "Change intent" button

### Key design decision
The intent picker is not a blocker. The user can tap "Other / Just looking" and proceed immediately. The goal is the moment of reflection, not enforcement. Friction comes in Phase 2.

### Verification
Open app → intent picker appears → select "Work" → home screen shows "Work session — 0:00" → session timer ticks up.

### Key files
- `IntentSession.kt` (model + SessionStore)
- `IntentPickerScreen.kt`
- `HomeScreen.kt`
- `ZoneManagerService.kt`
- `BootReceiver.kt` (restarts service on reboot)

---

## Phase 2 — Friction Layer (The Breath Moment)

**Goal:** For apps the user designates, a brief pause screen appears before the app opens — forcing one conscious decision before habit takes over. This is the core OneSec equivalent.

### What gets built
- **Apps tab** — lists all installed apps on the device (using `PackageManager`). Each app has a toggle: "Add friction" on/off. Blocked list stored in SharedPreferences.
- **MindShieldAccessibilityService** — listens for `TYPE_WINDOW_STATE_CHANGED` events. When a friction-enabled app is detected as the foreground window, it immediately draws a fullscreen overlay.
- **FrictionOverlay** — a Compose overlay drawn via `TYPE_ACCESSIBILITY_OVERLAY` window type containing:
  - The app icon and name ("You're opening Instagram")
  - A 5-second animated breath circle (inhale/exhale animation)
  - Two buttons: **"Open anyway"** and **"Go back"**
  - After the animation completes, "Open anyway" becomes available
- **Accessibility config XML** — `typeWindowStateChanged` only, `canRetrieveWindowContent: false` (minimal permissions, privacy-respecting)

### Key design decision
The overlay is dismissible at any time via "Open anyway" — there is no hard block. The research (Grüning et al., 2023) shows the mere act of interrupting the automatic behavior is what reduces usage, not whether the user can bypass it. The dismiss option actually strengthens the effect.

### Optional enhancement (Phase 2B)
Allow the user to set a custom delay per app (5s, 10s, 30s) rather than a fixed 5 seconds.

### Verification
Mark Instagram as friction-enabled → open Instagram from home screen → overlay appears with breath animation → "Open anyway" becomes available after 5s → tapping it opens Instagram.

### Key files
- `AppsScreen.kt` (app list with friction toggles)
- `FrictionBlocklist.kt` (SharedPreferences wrapper for blocked apps)
- `MindShieldAccessibilityService.kt`
- `FrictionOverlay.kt`
- `res/xml/accessibility_config.xml`

---

## Phase 3 — Notification Batching (Silence on Your Terms)

**Goal:** The user controls when notifications arrive. Important contacts and apps get through instantly. Everything else is held and delivered in batches — hourly, or when a threshold count is reached.

### What gets built
- **NotificationStore** — an in-memory + Room database queue that holds intercepted notifications with their original content and timestamp
- **MindShieldNotificationService** — extends `NotificationListenerService`. Intercepts incoming notifications and routes them:
  - **VIP list** (specific contacts or apps) → passes through immediately, no batching
  - **Batch list** → suppresses original, stores in NotificationStore
  - **Everything else** → passes through (untracked apps are unaffected by default)
- **Delivery rules** — stored per-app:
  - Deliver every N hours (1h, 2h, 4h)
  - Deliver when count reaches N (5, 10, 20 messages)
  - Deliver at specific times (e.g., 9am, 1pm, 6pm)
- **Notifications tab** — shows the current batch queue, delivery schedule, and a "Deliver now" manual override button
- **Batch delivery** — when triggered, re-posts all held notifications as a grouped summary notification

### Key design decision
The default is opt-in per app. No app is batched unless the user explicitly enables it. This avoids the anxiety of missing something important (Dekker et al., 2024 showed notification-only interventions increased FOMO). Users must consciously choose which apps to batch.

### Verification
Enable batching for a messaging app → receive a message → notification does not appear → open MindShield Notifications tab → message shows in queue → tap "Deliver now" → notification appears.

### Key files
- `MindShieldNotificationService.kt`
- `NotificationStore.kt` (Room entity + DAO)
- `BatchRule.kt` (model for per-app delivery rules)
- `NotificationsScreen.kt`
- `AppDatabase.kt`

---

## Phase 4 — Routines (Morning & Wind-Down)

**Goal:** Structured transition periods that help the user start and end the day intentionally — without the phone hijacking those moments.

### What gets built

#### Morning routine
- User sets a wake time and a "phone available" time (e.g., wake at 7am, phone available at 7:30am)
- During the gap, the friction layer is applied to ALL friction-enabled apps, not just specific ones
- A morning routine card appears on the home screen: a checklist the user builds themselves (e.g., "Drink water", "Stretch 5 min", "Read 10 pages")
- Completing the checklist items earns access to specific apps (configurable — e.g., "After checklist → social media unlocks")
- Routine completion is tracked locally

#### Wind-down routine
- User sets a wind-down start time (e.g., 10pm) and a sleep time (e.g., 11pm)
- During wind-down:
  - All friction-enabled apps get extended delay (configurable, e.g., 30 seconds instead of 5)
  - A wind-down card appears on the home screen with a simple checklist ("Gym bag packed?", "Outfit ready?")
  - Completing preparation tasks unlocks a 15-minute passive audio window (Spotify, podcasts)
- At sleep time: all friction-enabled apps are blocked with an "It's sleep time" overlay (user can still override with a 3-tap unlock, but it's deliberate)

#### Routines tab
- Visual timeline showing today's scheduled routine periods
- Edit button for morning and wind-down checklists
- History view showing streak of completed routines

### Verification
Set wind-down start to 2 minutes from now → wait → home screen shows wind-down card → open a friction-enabled app → delay is longer than normal.

### Key files
- `RoutineConfig.kt` (model + SharedPreferences storage)
- `RoutineScheduler.kt` (WorkManager periodic task, checks time and updates ZoneManagerService)
- `RoutinesScreen.kt`
- `MorningCard.kt` / `WindDownCard.kt` (home screen composables)

---

## Phase 5 — Accountability Partner

**Goal:** Optional social friction for users who want external accountability when bypassing their own rules.

### What gets built
- **Partner setup** — user enters a partner's email address (stored locally, never shared with servers)
- **Override alert** — when the user overrides the sleep-time block (the 3-tap unlock), an email is sent to the partner via a lightweight email intent (no backend needed for MVP — uses Android's `ACTION_SEND` intent, which opens the user's email app pre-composed)
- **NFC tag support** — for the "walk to another room" friction mechanic:
  - User taps a physical NFC tag placed across the room
  - MindShield reads the tag's UID, validates it against a stored pairing
  - Grants a 15-minute extension window
  - Tag pairing done in-app (tap tag to pair, then confirm)
- **Weekly summary** — a local notification every Sunday showing: sessions this week, most-used intent, apps opened despite friction, notifications batched

### Key design decision
The MVP partner feature requires no backend — it uses the device's native email app. This keeps the feature shippable without any server infrastructure while still providing meaningful social friction.

### Verification
Set a partner email → trigger 3-tap sleep override → email app opens pre-composed with summary of the override.

### Key files
- `AccountabilityConfig.kt`
- `PartnerAlert.kt` (email intent builder)
- `NfcManager.kt` (tag pairing and validation)
- `WeeklySummaryWorker.kt`

---

## Phase 6 — Polish & Personalization

**Goal:** Make the app feel considered and personal, not clinical.

### What gets built
- **Statistics screen** — weekly and monthly views of: sessions by intent type, friction moments (how many times you paused vs. proceeded), notifications batched vs. delivered, routine streak
- **Custom intents** — full CRUD for user-defined session types with emoji picker and color
- **Per-app friction customization** — set delay duration, custom message, or skip-count limit (e.g., "After 3 skips today, delay doubles")
- **Themes** — light/dark/system, with an optional grayscale mode that activates automatically during wind-down (research-backed: Zimmermann & Sobolev, 2022)
- **Widgets** — home screen widgets for quick intent selection and notification queue count
- **Export** — export weekly stats as a simple JSON or CSV for personal tracking

---

## Phase 7 — VPN Blocking (Nice to Have)

**Goal:** Network-level blocking of specific websites and domains during Recovery/wind-down periods.

This is deliberately last because it requires the most invasive permission (VPN), the most complex code (local TUN interface + DNS filtering), and conflicts with real VPN apps the user may already have. The friction overlay (Phase 2) and routine restrictions (Phase 4) accomplish 80% of the behavioral goal without any of this complexity.

### What gets built
- Local VPN service using Android's `VpnService` API (no external server)
- Domain blocklist (default list + user-customizable)
- Active only during wind-down and sleep periods
- Clear UI indicator when VPN is active
- Automatic disable if a real VPN connection is detected

---

## Technical architecture summary

| Layer | Technology |
|---|---|
| UI | Jetpack Compose |
| Navigation | Compose Navigation |
| State | StateFlow + ViewModel |
| Persistence | SharedPreferences (settings) + Room (notification queue) |
| Background | Foreground Service + WorkManager + BroadcastReceiver |
| System hooks | AccessibilityService + NotificationListenerService |
| NFC | NfcAdapter foreground dispatch |
| No backend required for Phases 0–5 | — |

---

## What this app is not

- It is not a parental control app
- It is not a website blocker (that's Phase 7, last priority)
- It is not tracking your data or sending anything to a server
- It is not trying to make you use your phone less — it's trying to make every minute you spend on your phone one you chose

---

## Research grounding

The core mechanics are validated by peer-reviewed research cited in the MindShield research brief:

- Intent selection → psychological detachment theory (Sonnentag & Fritz, 2015)
- Friction overlay → one sec app study showing 57% reduction in app openings (Grüning et al., 2023)
- Notification batching → quality-over-quantity detachment effect (Kubo et al., 2021)
- Wind-down routine → recovery experience framework, mastery/control (Bennett et al., 2018)
- Grayscale during wind-down → Zimmermann & Sobolev, 2022
- Opt-in batching design → FOMO risk of notification-only interventions (Dekker et al., 2024)
