# Phase 2 — Friction Layer: Implementation Plan

**Branch:** `phase-2/friction-layer`  
**Goal:** For apps the user designates, a brief pause screen appears before the app opens — forcing one conscious decision before habit takes over.

---

## Overview of what gets built

| File | Purpose |
|---|---|
| `data/FrictionBlocklist.kt` | Persists the set of friction-enabled package names; exposes a `StateFlow` |
| `viewmodel/AppsViewModel.kt` | Loads all installed launchable apps, merges blocklist state, supports search |
| `screens/AppsScreen.kt` | Replaces placeholder — searchable app list with per-app friction toggle |
| `accessibility/FrictionOverlay.kt` | Pure-View fullscreen overlay drawn via `TYPE_ACCESSIBILITY_OVERLAY` |
| `accessibility/MindShieldAccessibilityService.kt` | Listens for `TYPE_WINDOW_STATE_CHANGED`, checks blocklist, shows overlay |
| `AndroidManifest.xml` (update) | Add `QUERY_ALL_PACKAGES` + `<queries>` block for Android 11+ app enumeration |

---

## Task breakdown

### Task 1 — FrictionBlocklist
**File:** `data/FrictionBlocklist.kt`

- `object` singleton backed by `SharedPreferences`
- Stores a `Set<String>` of blocked package names
- Exposes `blockedPackages: StateFlow<Set<String>>` so the service and UI react to changes
- `init(context)` called from `MindShieldApp.onCreate()` to load persisted state on startup
- Methods: `isBlocked(pkg)`, `setBlocked(ctx, pkg, enabled)`, `load()`, `save()`

---

### Task 2 — AppsViewModel
**File:** `viewmodel/AppsViewModel.kt`

- `AndroidViewModel` — needs `Application` for `PackageManager`
- `loadUserApps()` on `Dispatchers.IO`: queries `ACTION_MAIN + CATEGORY_LAUNCHER` with `MATCH_ALL` flag
- Excludes own package name (`com.mindshield.app`)
- Results sorted alphabetically
- `apps: StateFlow<List<AppEntry>>` = `combine(_allApps, FrictionBlocklist.blockedPackages, _searchQuery)`
- `setFriction(pkg, enabled)` delegates to `FrictionBlocklist`
- `onSearchQueryChanged(query)` for live search

**Android 11+ note:** `queryIntentActivities` requires `QUERY_ALL_PACKAGES` permission or `<queries>` declaration in manifest to return non-visible packages.

---

### Task 3 — AppsScreen
**File:** `screens/AppsScreen.kt`

- Header with title + subtitle
- `OutlinedTextField` search bar (live filters the list)
- `LazyColumn` of `AppRow` items — each row shows app name, "Friction on" badge when active, and a `Switch`
- Loading spinner shown while `apps` is empty (before background load completes)
- Removes `AppsScreen` stub from `PlaceholderScreens.kt`

---

### Task 4 — FrictionOverlay
**File:** `accessibility/FrictionOverlay.kt`

Uses `WindowManager` with `TYPE_ACCESSIBILITY_OVERLAY` — no Activity or Fragment needed.

Implemented with **pure Android Views** (not ComposeView) to avoid the `ViewTreeLifecycleOwner`/`ViewTreeSavedStateRegistryOwner` requirements that don't apply outside an Activity context.

Layout (programmatic `LinearLayout`):
- App name label: "You're opening [AppName]"
- "Take a breath first." subtitle
- `BreathCircleView` (custom `View`): `ValueAnimator` expanding a circle over 5 seconds
- **"Open anyway"** button — disabled until animation completes
- **"Go back"** button — always enabled, calls `performGlobalAction(GLOBAL_ACTION_BACK)`

`FakeLifecycleOwner` is **not needed** in the View-based approach.

Overlay colors: warm off-white background, calm green primary — intentionally calm palette.

---

### Task 5 — MindShieldAccessibilityService
**File:** `accessibility/MindShieldAccessibilityService.kt`

- Overrides `onServiceConnected()` to configure `serviceInfo` dynamically
- `onAccessibilityEvent()`: only acts on `TYPE_WINDOW_STATE_CHANGED`
- Never intercepts own package (`com.mindshield.app`)
- Tracks `overlayShownForPackage`: after "Open anyway", same package won't retrigger until the user navigates away and returns — prevents overlay looping
- `appLabel(pkg)` reads the human-readable name for the overlay title
- Cleans up overlay in `onInterrupt()` and `onDestroy()`

---

### Task 6 — AndroidManifest updates

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

`QUERY_ALL_PACKAGES` is required for a digital wellbeing app that must enumerate all installed apps. Google Play accepts this for apps in the digital wellbeing category.

---

## Verification checklist

- [ ] Apps tab shows all installed apps (including system launchable apps like Settings, Calculator)
- [ ] Search bar filters in real time
- [ ] Toggle switches persist across app restarts
- [ ] Enable accessibility service in device Settings → Accessibility
- [ ] Mark an app as friction-enabled → open it → overlay appears immediately
- [ ] Breath circle expands over 5 seconds → "Open anyway" unlocks
- [ ] "Go back" navigates back at any time
- [ ] After "Open anyway", reopening the same app does NOT retrigger the overlay until you navigate away first
- [ ] Overlay does NOT appear when opening MindShield itself

---

## What this phase does NOT include

- Per-app custom delay duration (Phase 2B / Phase 6)
- Custom message per app (Phase 6)
- Skip-count limits ("after 3 skips, delay doubles") (Phase 6)
- Any VPN or network-level blocking (Phase 7)
