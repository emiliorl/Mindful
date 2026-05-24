# Phase 3 — Notification Batching (Silence on Your Terms)

## Goal

Give the user control over when notifications arrive. VIP apps and contacts pass through instantly. Everything else is held in a queue and delivered in batches — on a schedule the user sets, or on demand.

---

## Scope for this phase

- `NotificationListenerService` that intercepts notifications and routes them
- Room database as the notification queue
- Per-app batch rules (interval, count threshold, or time-of-day)
- Notifications tab UI (queue view + rule management)
- Batch delivery (re-posts held notifications as a grouped summary)
- "Deliver now" manual override

Out of scope for Phase 3:
- VIP contact list (individual contacts, not apps) — deferred to Polish phase
- Push-through based on notification content/keywords

---

## Step-by-step plan

### Step 1 — Room setup

Create `AppDatabase` (singleton) with one entity and DAO:

**`HeldNotification`** entity:
- `id: Int` (autoGenerate)
- `packageName: String`
- `appLabel: String`
- `title: String`
- `text: String`
- `postedAtMs: Long` (original arrival time)
- `iconLargeBase64: String?` (nullable, best-effort)

**`HeldNotificationDao`**:
- `insert(n: HeldNotification)`
- `getAll(): Flow<List<HeldNotification>>`
- `getForPackage(pkg: String): Flow<List<HeldNotification>>`
- `countForPackage(pkg: String): Int` (suspend, for threshold check)
- `deleteAll()`
- `deleteForPackage(pkg: String)`

**`AppDatabase`**:
- Singleton using `Room.databaseBuilder`
- Initialized in `MindShieldApp.onCreate()`

### Step 2 — BatchRule model + store

**`BatchRule`** data class:
- `packageName: String`
- `appLabel: String`
- `enabled: Boolean`
- `deliveryMode: DeliveryMode` — enum: `INTERVAL` | `COUNT` | `SCHEDULE`
- `intervalHours: Int` (1, 2, 4 — used when mode == INTERVAL)
- `countThreshold: Int` (5, 10, 20 — used when mode == COUNT)
- `scheduleTimes: List<LocalTime>` (used when mode == SCHEDULE, e.g. [9:00, 13:00, 18:00])

**`BatchRuleStore`** (SharedPreferences-backed, similar to `AppFrictionStore`):
- `configs: StateFlow<Map<String, BatchRule>>`
- `init(context)`, `setRule(context, rule)`, `removeRule(context, pkg)`
- Serializes to JSON string per package key `"batch:com.pkg"`

### Step 3 — NotificationListenerService

**`MindShieldNotificationService`** extends `NotificationListenerService`:

Routing logic in `onNotificationPosted(sbn)`:
1. Skip our own package
2. Look up `BatchRule` for `sbn.packageName`
3. If no rule or `rule.enabled == false` → pass through (do nothing)
4. If rule is enabled → cancel the original notification, insert into Room queue
5. After insert, check delivery trigger:
   - **COUNT mode**: `dao.countForPackage(pkg) >= rule.countThreshold` → trigger batch delivery for that package
   - **INTERVAL / SCHEDULE**: delivery handled by WorkManager worker

`onNotificationRemoved` → no action needed (user dismissed it manually)

Declare in manifest with `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` and intent filter.

### Step 4 — Batch delivery

**`BatchDeliveryHelper`** (plain object, not a service):
- `deliverAll(context)` — fetches all held notifications, re-posts each as a notification in a group, then clears the queue
- `deliverForPackage(context, pkg)` — same but scoped to one package
- Groups notifications using `NotificationCompat.Builder` with `setGroup(pkg)` + a summary notification
- Uses `NotificationManagerCompat` — reuses existing `CHANNEL_BATCH` channel from `MindShieldApp`

**`BatchDeliveryWorker`** (WorkManager `CoroutineWorker`):
- Periodic worker, runs every hour minimum
- Checks each rule with `INTERVAL` or `SCHEDULE` mode:
  - INTERVAL: checks `lastDeliveredAtMs` in SharedPrefs; if elapsed >= intervalHours → deliver
  - SCHEDULE: checks if current time crossed any scheduled time since last delivery
- Calls `BatchDeliveryHelper.deliverAll()` or per-package as needed
- Enqueued with `ExistingPeriodicWorkPolicy.KEEP` in `MindShieldApp.onCreate()`

### Step 5 — Notifications screen UI

**`NotificationsScreen`** composable (replaces the current placeholder):

Two sections:

**"Queue" section** (top):
- `LazyColumn` of held notifications
- Each row: app icon + app name + notification title + preview text + relative time ("3 min ago")
- Empty state: "No notifications held. Apps you batch will appear here."
- "Deliver all now" button at the top right of the section header

**"Batched apps" section** (bottom):
- List of apps that have a `BatchRule` with `enabled = true`
- Each row: app icon + name + rule summary ("Every 2 hours" / "Every 10 messages" / "9am, 1pm, 6pm")
- Tap row → opens `BatchRuleSheet` (bottom sheet) to edit the rule
- FAB or "Add app" button → opens app picker to add a new batched app

**`BatchRuleSheet`** (ModalBottomSheet):
- Toggle to enable/disable batching for that app
- Mode selector: Interval / Count / Schedule (segmented button)
- Conditional controls per mode:
  - Interval: FilterChips for 1h / 2h / 4h
  - Count: FilterChips for 5 / 10 / 20
  - Schedule: time pickers for up to 3 delivery times
- "Remove" button (deletes the rule)

**`NotificationsViewModel`**:
- `held: StateFlow<List<HeldNotification>>` from Room DAO
- `rules: StateFlow<Map<String, BatchRule>>` from BatchRuleStore
- `installedApps: StateFlow<List<AppEntry>>` (reuse same `loadUserApps` logic)
- `deliverAll()`, `deliverForPackage(pkg)`, `setRule(rule)`, `removeRule(pkg)`

### Step 6 — Manifest + permission

Add to `AndroidManifest.xml`:
```xml
<service
    android:name=".notification.MindShieldNotificationService"
    android:label="MindShield notifications"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

**Permission gate**: The Notifications tab should show a full-screen gate (same pattern as Apps tab) when `NotificationListenerCompat` permission is not granted, with a button to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.

---

## File list

| File | Purpose |
|---|---|
| `data/HeldNotification.kt` | Room entity |
| `data/HeldNotificationDao.kt` | DAO |
| `data/AppDatabase.kt` | Room database singleton |
| `data/BatchRule.kt` | Per-app batch rule model + DeliveryMode enum |
| `data/BatchRuleStore.kt` | SharedPreferences-backed StateFlow store |
| `notification/MindShieldNotificationService.kt` | NotificationListenerService |
| `notification/BatchDeliveryHelper.kt` | Re-posts held notifications |
| `notification/BatchDeliveryWorker.kt` | WorkManager periodic delivery |
| `screens/NotificationsScreen.kt` | Full UI (queue + batched apps list) |
| `screens/BatchRuleSheet.kt` | Bottom sheet for rule editing |
| `viewmodel/NotificationsViewModel.kt` | State + actions for notifications screen |

---

## Dependencies to add to `build.gradle`

```kotlin
// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// WorkManager
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Gson (for BatchRule serialization in SharedPreferences)
implementation("com.google.code.gson:gson:2.10.1")
```

Room is already declared as a future-phase dependency in `build.gradle` — verify before adding.

---

## Verification checklist

- [ ] Enable batching for a messaging app
- [ ] Receive a notification → does NOT appear on status bar
- [ ] Open Notifications tab → notification shows in queue
- [ ] Tap "Deliver now" → notification appears on status bar
- [ ] Set interval rule (1h) → wait / fast-forward → batch auto-delivers
- [ ] Set count rule (5) → receive 5 messages → auto-delivers
- [ ] Remove batching for app → notifications pass through again
- [ ] Notifications tab shows permission gate when listener not granted
