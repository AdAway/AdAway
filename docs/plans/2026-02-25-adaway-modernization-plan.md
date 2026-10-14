# AdAway Modernization Implementation Plan

> **For Claude:** REQUIRED: Follow this plan task-by-task. Each phase has a demonstrable exit criterion.
> **Architecture ref:** See Current State Analysis section for file:line references.

**Goal:** Transform AdAway into a modern, simple-by-default ad blocker with full FilterLists.com integration and bottom-nav UX, keeping root and VPN modes untouched.

**Architecture:** Bottom BottomNavigationView with 3 tabs (Home, Discover, More) replacing the navigation drawer. FilterCatalogActivity and FilterListsImportActivity merge into a single DiscoverFragment with 2 sub-tabs. FilterLists.com API extended with /tags and /languages endpoints for chip-based filtering.

**Tech Stack:** Android Java 21, Room 2.6.1, OkHttp, WorkManager, Material Design 3, ViewBinding

**Prerequisites:** Android SDK 34, NDK 27.2.12479018, JDK 21. Existing app builds cleanly with `./gradlew assembleDebug`.

---

## Executive Summary

This plan covers 4 phases of modernization:

| Phase | Title | Exit Criterion |
|-------|-------|---------------|
| 1 | Navigation and UX Simplification | App opens with bottom nav, 3 tabs visible, Welcome is one screen |
| 2 | FilterLists Full API Integration | Tag chips and language filter work in Discover tab |
| 3 | One-tap Onboarding | Fresh install: defaults auto-subscribed, update runs, user lands on Home |
| 4 | Code Simplification | Old Activities deleted, dead code removed, FilterSetStore overlap resolved |

**Key invariant throughout:** `app/src/main/java/org/adaway/vpn/` and `app/src/main/java/org/adaway/model/adblocking/` are never touched. Root and VPN models are read-only.

---

## Current State Analysis

### Navigation Debt
The app uses a navigation drawer (`HomeActivity.java` lines 1-500+) that opens a slide-out menu listing all features. This pattern is considered deprecated in Material Design 3. Users must open the drawer to find Hosts Sources, DNS Log, Settings, Help, etc.

**Specific files driving navigation today:**
- `app/src/main/java/org/adaway/ui/home/HomeActivity.java` — Drawer-based main activity, ~500 lines
- `app/src/main/res/layout/home_activity.xml` — DrawerLayout root
- `app/src/main/res/menu/home_drawer.xml` — Drawer menu items

### Duplicate Browse Screens
Two separate Activities for browsing filter lists create user confusion:

| Activity | File | Role | Problem |
|----------|------|------|---------|
| FilterCatalogActivity | `ui/hosts/FilterCatalogActivity.java:61` | Browse 60+ curated lists by preset | Separate screen, no connection to FilterLists.com |
| FilterListsImportActivity | `ui/hosts/FilterListsImportActivity.java:59` | Browse FilterLists.com directory | Separate screen, no curated presets |

Both are launched from `HostsSourcesActivity` via separate buttons. A user must choose one or the other — there is no unified "Discover" experience.

### FilterLists API Gaps
`app/src/main/java/org/adaway/model/source/FilterListsDirectoryApi.java` currently fetches:
- `GET /lists` → array of `{id, name, description, syntaxIds[]}`
- `GET /syntaxes` → array of `{id, name}`
- `GET /lists/{id}` → `{id, name, description, syntaxIds[], viewUrls[]}`

The actual `/lists` response already returns `tagIds[]` and `languageIds[]` per item (confirmed via live API call). The API also exposes:
- `GET /tags` → `[{id, name, description}]` — 41 tags including "ads", "privacy", "crypto", "malware"
- `GET /languages` → `[{id, iso6391, name}]` — 81 languages

The `ListSummary` model at `FilterListsDirectoryApi.java:34` does not capture `tagIds` or `languageIds`. The UI at `FilterListsImportActivity.java:76` has no chip row for filtering.

### Welcome Wizard
`WelcomeActivity.java:24` runs a 3-screen ViewPager2 wizard (intro, method selection, sync). The method selection screen `WelcomeMethodFragment.java:39` already handles root detection and VPN authorization. This wizard adds friction for the common case (VPN on non-rooted device).

### FilterSetStore Overlap
`FilterSetStore.java` manages scheduling (daily/weekly cron) and named preset URL sets. `FilterListCatalog.java` manages curated presets (Safe, Balanced, Aggressive). These concepts overlap but live in different layers with no coordination — FilterSetStore does not know about FilterListCatalog presets.

---

## Relevant Codebase Files

### Core Files to Modify

| File | Lines | Change Type |
|------|-------|-------------|
| `app/src/main/java/org/adaway/ui/home/HomeActivity.java` | All | Refactor to host BottomNavigationView + 3 Fragments |
| `app/src/main/java/org/adaway/model/source/FilterListsDirectoryApi.java` | 34-46, 109+ | Add Tag, Language models + fetchTags(), fetchLanguages() |
| `app/src/main/java/org/adaway/ui/hosts/FilterListsImportActivity.java` | All | Convert to Fragment (DiscoverFilterListsFragment) |
| `app/src/main/java/org/adaway/ui/hosts/FilterCatalogActivity.java` | All | Convert to Fragment (DiscoverCatalogFragment) |
| `app/src/main/AndroidManifest.xml` | 67-161 | Remove deleted activities, keep retained ones |

### New Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/java/org/adaway/ui/home/HomeFragment.java` | Tab 1: status card + update button + live progress |
| `app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java` | Tab 2: ViewPager2 with Curated / FilterLists sub-tabs |
| `app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java` | Curated lists sub-tab (content from FilterCatalogActivity) |
| `app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java` | FilterLists.com sub-tab (content from FilterListsImportActivity) |
| `app/src/main/java/org/adaway/ui/more/MoreFragment.java` | Tab 3: Settings, DNS Log, Backup, Adware links |
| `app/src/main/java/org/adaway/ui/onboarding/OnboardingActivity.java` | Simplified single-screen onboarding |
| `app/src/main/res/layout/home_activity_v2.xml` | New BottomNavigationView root layout |
| `app/src/main/res/layout/fragment_home.xml` | Home fragment layout |
| `app/src/main/res/layout/fragment_discover.xml` | Discover fragment layout with TabLayout + ViewPager2 |
| `app/src/main/res/layout/fragment_more.xml` | More/Advanced fragment layout |
| `app/src/main/res/navigation/bottom_nav_menu.xml` | 3-item bottom navigation menu |

### Files to Delete (Phase 4)
- `app/src/main/java/org/adaway/ui/hosts/FilterCatalogActivity.java`
- `app/src/main/java/org/adaway/ui/hosts/FilterListsImportActivity.java`
- `app/src/main/java/org/adaway/ui/help/HelpActivity.java`
- `app/src/main/java/org/adaway/ui/help/HelpFragmentHtml.java`
- `app/src/main/java/org/adaway/ui/support/SupportActivity.java`
- `app/src/main/res/layout/filter_catalog_dialog.xml` (replaced)
- `app/src/main/res/layout/filterlists_import_activity.xml` (replaced)

### Leave Untouched
- All of `app/src/main/java/org/adaway/vpn/`
- All of `app/src/main/java/org/adaway/model/adblocking/`
- `app/src/main/java/org/adaway/model/source/SourceModel.java`
- `app/src/main/java/org/adaway/model/source/SourceLoader.java`
- `app/src/main/java/org/adaway/db/` (all schema files — no Room migrations)
- `app/src/main/java/org/adaway/ui/source/SourceEditActivity.java`
- `app/src/main/java/org/adaway/ui/hosts/FilterListsSubscribeAllWorker.java`
- `app/src/main/java/org/adaway/ui/hosts/FilterSetStore.java`

---

## ASCII UI Mockups

### Tab 1 — Home

```
┌──────────────────────────────────────┐
│  AdAway                    [gear]    │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  [shield icon]                 │  │
│  │  Ad Blocking is ON             │  │
│  │  [toggle switch ────────●  ]   │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌──────────┐  ┌──────────────────┐  │
│  │ 48,231   │  │ 4 sources        │  │
│  │ blocked  │  │ up to date       │  │
│  └──────────┘  └──────────────────┘  │
│                                      │
│  ────────────── Update ───────────── │
│  [  ████████████░░░░░░░░  62%    ]  │
│  Downloading: StevenBlack Unified   │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  [UPDATE FILTER LISTS]  button │  │
│  └────────────────────────────────┘  │
│                                      │
│  [No lists yet? Tap Discover →]      │
│                                      │
├──────────────────────────────────────┤
│  [home]      [search]     [more]     │
│   Home       Discover     Advanced   │
└──────────────────────────────────────┘
```

### Tab 2 — Discover (two sub-tabs)

```
┌──────────────────────────────────────┐
│  Discover Filter Lists               │
│                                      │
│  [  Curated  ] [  FilterLists.com  ] │  <- TabLayout
│  ────────────────────────────────    │
│                                      │
│  (Curated sub-tab shown)             │
│  [Safe] [Balanced] [Aggressive] [+]  │  <- Preset chips
│                                      │
│  [search bar: "Search lists..."   ]  │
│                                      │
│  Ads (5 lists)                       │
│  ├─ [x] AdAway Official      [✓ OK] │
│  ├─ [x] StevenBlack Unified  [✓ OK] │
│  ├─ [x] Peter Lowe           [✓ OK] │
│  ├─ [ ] 1Hosts Pro            [Add] │
│  └─ [ ] OISD Full             [Add] │
│                                      │
│  Privacy (7 lists)                   │
│  └─ ...                              │
│                                      │
│  ┌───────────────────────────────┐   │
│  │  [ADD SELECTED (3)]   button  │   │
│  └───────────────────────────────┘   │
│                                      │
├──────────────────────────────────────┤
│  [home]      [search]     [more]     │
└──────────────────────────────────────┘
```

### Tab 2 — Discover (FilterLists.com sub-tab)

```
┌──────────────────────────────────────┐
│  Discover Filter Lists               │
│                                      │
│  [  Curated  ] [  FilterLists.com  ] │
│  ────────────────────────────────    │
│                                      │
│  [search bar: "Search..."         ]  │
│                                      │
│  Tags:                               │
│  [ads] [privacy] [malware] [crypto]  │  <- horizontal scroll
│  [annoyances] [security] [...more]   │
│                                      │
│  Language: [All ▼]                   │  <- spinner or chip
│                                      │
│  [Subscribe All ●────────────────]   │
│  213/847 (25%) • EasyList Privacy   │
│                                      │
│  AdGuard Base                  [●]   │
│  Syntax: AdGuard                     │
│  Filters ads on all websites   [✓]   │
│                                      │
│  EasyList                      [○]   │
│  Syntax: AdBlock Plus                │
│  Primary ad block list               │
│                                      │
├──────────────────────────────────────┤
│  [home]      [search]     [more]     │
└──────────────────────────────────────┘
```

### Tab 3 — Advanced (More)

```
┌──────────────────────────────────────┐
│  Advanced                            │
│                                      │
│  TOOLS                               │
│  ─────                               │
│  [DNS Request Log]              [>]  │
│  View all DNS queries                │
│                                      │
│  [Custom Host Rules]            [>]  │
│  Block, Allow, Redirect hosts        │
│                                      │
│  [Filter Sources]               [>]  │
│  Manage all subscriptions            │
│                                      │
│  [Adware Scanner]               [>]  │
│  Scan installed apps                 │
│                                      │
│  SETTINGS                            │
│  ────────                            │
│  [Preferences]                  [>]  │
│  VPN mode, update schedule, theme    │
│                                      │
│  [Backup / Restore]             [>]  │
│  Export and import configuration     │
│                                      │
│  ABOUT                               │
│  ─────                               │
│  [About AdAway]                 [>]  │
│  Version 13.0.8                      │
│                                      │
│  [GitHub / Help]                [>]  │
│  Documentation and issue tracker     │
│                                      │
├──────────────────────────────────────┤
│  [home]      [search]     [more]     │
└──────────────────────────────────────┘
```

### Onboarding (single screen)

```
┌──────────────────────────────────────┐
│                                      │
│              [Shield logo]           │
│                                      │
│         Welcome to AdAway            │
│    Block ads for all your apps       │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  [VPN Mode]  (recommended)     │  │
│  │  Works without root access     │  │
│  │  Best for most Android phones  │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  [Root Mode]  (advanced)       │  │
│  │  Requires root/Magisk          │  │
│  │  Edits /etc/hosts directly     │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  [START PROTECTING]   button   │  │
│  └────────────────────────────────┘  │
│                                      │
│  VPN mode is pre-selected when       │
│  no root is detected.                │
│                                      │
└──────────────────────────────────────┘
```

---

## Phase 1: Navigation and UX Simplification

> **Exit Criteria:** App launches with a BottomNavigationView showing Home, Discover, Advanced tabs. Navigating between tabs works. Welcome is a single-screen Activity that auto-selects VPN when no root is available.

### Task 1.1: Add BottomNavigationView dependency and menu resource

**Files:**
- Create: `app/src/main/res/menu/bottom_nav_menu.xml`
- Check: `app/build.gradle` (Material 1.12.0 already includes BottomNavigationView — no new dep needed)

**Step 1:** Create the bottom nav menu resource.

```xml
<!-- app/src/main/res/menu/bottom_nav_menu.xml -->
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/nav_home"
        android:icon="@drawable/ic_home_black_24dp"
        android:title="@string/nav_home" />
    <item
        android:id="@+id/nav_discover"
        android:icon="@drawable/ic_search_black_24dp"
        android:title="@string/nav_discover" />
    <item
        android:id="@+id/nav_more"
        android:icon="@drawable/ic_more_horiz_black_24dp"
        android:title="@string/nav_advanced" />
</menu>
```

**Step 2:** Add string resources to `app/src/main/res/values/strings.xml`:
```xml
<string name="nav_home">Home</string>
<string name="nav_discover">Discover</string>
<string name="nav_advanced">Advanced</string>
```

**Step 3:** Verify existing drawable resources. Run:
```bash
ls app/src/main/res/drawable/ | grep "ic_home\|ic_search\|ic_more"
```
If missing, use Material icon XML drawables (standard 24dp vector assets from the Material icon set).

**Step 4:** Commit.
```bash
git add app/src/main/res/menu/bottom_nav_menu.xml app/src/main/res/values/strings.xml
git commit -m "feat: add bottom navigation menu resource and string labels"
```

---

### Task 1.2: Create HomeFragment (Status Card)

**Files:**
- Create: `app/src/main/java/org/adaway/ui/home/HomeFragment.java`
- Create: `app/src/main/res/layout/fragment_home.xml`

**Step 1:** Write the layout. Extract the status card, stats row, and update button from the existing `HomeActivity` layout into `fragment_home.xml`. Key views to include:
- Status card (shield icon, ON/OFF text, toggle MaterialSwitch)
- Stats row (blocked count, sources count)
- Update progress bar (visibility GONE by default)
- "UPDATE FILTER LISTS" button
- "No lists yet? Tap Discover" hint text (visibility GONE when sources > 0)

```xml
<!-- app/src/main/res/layout/fragment_home.xml -->
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Status card -->
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/statusCard"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp">
            <!-- shield icon, state text, MaterialSwitch -->
        </com.google.android.material.card.MaterialCardView>

        <!-- Stats row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">
            <!-- blockedCountCard, sourcesCountCard -->
        </LinearLayout>

        <!-- Progress bar (GONE when idle) -->
        <LinearLayout
            android:id="@+id/updateProgressContainer"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:visibility="gone"
            android:orientation="vertical">
            <ProgressBar
                android:id="@+id/updateProgressBar"
                style="@style/Widget.AppCompat.ProgressBar.Horizontal"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
            <TextView android:id="@+id/updateProgressText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />
        </LinearLayout>

        <!-- Update button -->
        <com.google.android.material.button.MaterialButton
            android:id="@+id/updateButton"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/home_update_button" />

        <!-- No-sources hint -->
        <TextView
            android:id="@+id/noSourcesHint"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:visibility="gone"
            android:text="@string/home_no_sources_hint" />

    </LinearLayout>
</ScrollView>
```

**Step 2:** Create `HomeFragment.java`. Move the LiveData observation logic from `HomeActivity` into this fragment. Use `ViewModelProvider` with `requireActivity()` scope to share the existing `HomeViewModel`.

```java
package org.adaway.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import org.adaway.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        observeViewModel();
        bindActions();
    }

    private void observeViewModel() {
        viewModel.isAdBlocked().observe(getViewLifecycleOwner(), blocked -> {
            // Update shield icon and status text
        });
        viewModel.getBlockedHostCount().observe(getViewLifecycleOwner(), count -> {
            // Update blocked count text
        });
        viewModel.getMultiPhaseProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null && progress.isActive()) {
                binding.updateProgressContainer.setVisibility(View.VISIBLE);
                binding.updateProgressBar.setProgress(progress.getOverallPercent());
                binding.updateProgressText.setText(progress.getCurrentPhaseName());
            } else {
                binding.updateProgressContainer.setVisibility(View.GONE);
            }
        });
        viewModel.getUpToDateSourceCount().observe(getViewLifecycleOwner(), count -> {
            boolean hasSources = count != null && count > 0;
            binding.noSourcesHint.setVisibility(hasSources ? View.GONE : View.VISIBLE);
        });
    }

    private void bindActions() {
        binding.updateButton.setOnClickListener(v -> viewModel.update());
        binding.statusCard.setOnClickListener(v -> viewModel.toggleAdBlocking());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
```

**Step 3:** Compile check.
```bash
./gradlew compileDebugJavaWithJavac 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL (or only warnings, no errors).

**Step 4:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/home/HomeFragment.java \
        app/src/main/res/layout/fragment_home.xml
git commit -m "feat: create HomeFragment with status card and live progress binding"
```

---

### Task 1.3: Create MoreFragment (Advanced tab)

**Files:**
- Create: `app/src/main/java/org/adaway/ui/more/MoreFragment.java`
- Create: `app/src/main/res/layout/fragment_more.xml`

**Step 1:** Write the layout as a scrollable list of grouped preference-style rows using `MaterialCardView` + `TextView` pairs. Groups: TOOLS (DNS Log, Custom Host Rules, Filter Sources, Adware Scanner), SETTINGS (Preferences, Backup/Restore), ABOUT (About, GitHub/Help).

**Step 2:** Create `MoreFragment.java`. Each row is a simple `setOnClickListener` launching the relevant Activity via `Intent`:

```java
package org.adaway.ui.more;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import org.adaway.databinding.FragmentMoreBinding;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.adware.AdwareFragment;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.about.AboutActivity;

public class MoreFragment extends Fragment {
    private FragmentMoreBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMoreBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.rowDnsLog.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), LogActivity.class)));
        binding.rowCustomRules.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), ListsActivity.class)));
        binding.rowFilterSources.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), HostsSourcesActivity.class)));
        binding.rowPreferences.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), PrefsActivity.class)));
        binding.rowAbout.setOnClickListener(v ->
            startActivity(new Intent(requireContext(), AboutActivity.class)));
        binding.rowHelp.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/AdAway/AdAway/wiki"));
            startActivity(intent);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
```

**Step 3:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/more/ \
        app/src/main/res/layout/fragment_more.xml
git commit -m "feat: create MoreFragment with links to all advanced features"
```

---

### Task 1.4: Refactor HomeActivity to use BottomNavigationView

**Files:**
- Modify: `app/src/main/java/org/adaway/ui/home/HomeActivity.java`
- Create: `app/src/main/res/layout/home_activity_v2.xml`

**Step 1:** Create the new layout. Replace the DrawerLayout with a CoordinatorLayout containing AppBarLayout + BottomNavigationView + FrameLayout for fragment container.

```xml
<!-- app/src/main/res/layout/home_activity_v2.xml -->
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">
        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize" />
    </com.google.android.material.appbar.AppBarLayout>

    <FrameLayout
        android:id="@+id/fragmentContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior"
        android:layout_marginBottom="56dp" />

    <com.google.android.material.bottomnavigation.BottomNavigationView
        android:id="@+id/bottomNavigation"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_gravity="bottom"
        app:menu="@menu/bottom_nav_menu" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Step 2:** Refactor `HomeActivity.java`. Keep the ViewModel setup and application-level logic. Replace drawer menu handling with fragment switching:

```java
// Key changes to HomeActivity.java

// In onCreate():
setContentView(R.layout.home_activity_v2);
BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
bottomNav.setOnItemSelectedListener(item -> {
    Fragment fragment;
    if (item.getItemId() == R.id.nav_home) {
        fragment = new HomeFragment();
    } else if (item.getItemId() == R.id.nav_discover) {
        fragment = new DiscoverFragment();
    } else {
        fragment = new MoreFragment();
    }
    getSupportFragmentManager().beginTransaction()
        .replace(R.id.fragmentContainer, fragment)
        .commit();
    return true;
});
// Show home by default
bottomNav.setSelectedItemId(R.id.nav_home);
```

**Important:** Keep the `checkSetup()` method that redirects to WelcomeActivity when no blocking method is configured. Keep WorkManager observation for update notifications. Keep VPN permission request flow.

Remove: drawer toggle, `NavigationView` imports, drawer menu handling, `onNavigationItemSelected` for drawer items.

**Step 3:** Build and test navigation works between tabs.
```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

**Step 4:** Commit.
```bash
git add app/src/main/res/layout/home_activity_v2.xml \
        app/src/main/java/org/adaway/ui/home/HomeActivity.java
git commit -m "feat: replace navigation drawer with BottomNavigationView, 3-tab layout"
```

---

### Task 1.5: Simplify WelcomeActivity to single screen

**Files:**
- Create: `app/src/main/java/org/adaway/ui/onboarding/OnboardingActivity.java`
- Create: `app/src/main/res/layout/onboarding_activity.xml`
- Modify: `app/src/main/AndroidManifest.xml` (add OnboardingActivity, keep WelcomeActivity for now)

**Strategy:** Create a NEW `OnboardingActivity` that is simpler. It reuses `WelcomeMethodFragment`'s logic inline (root check + VPN auth). When setup is complete, it starts `HomeActivity` with a flag to trigger onboarding (Phase 3). Keep `WelcomeActivity` working until Phase 4 cleanup.

**Step 1:** Write `onboarding_activity.xml`. Single screen layout matching the mockup: logo, title subtitle, two MaterialCardView rows (VPN, Root), one "START PROTECTING" button.

**Step 2:** Write `OnboardingActivity.java`.

```java
package org.adaway.ui.onboarding;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.topjohnwu.superuser.Shell;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.home.HomeActivity;

public class OnboardingActivity extends AppCompatActivity {
    private AdBlockMethod chosenMethod = AdBlockMethod.UNDEFINED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.onboarding_activity);

        // Auto-detect: if no root, pre-select VPN
        boolean hasRoot = Boolean.TRUE.equals(Shell.isAppGrantedRoot());
        if (!hasRoot) {
            selectVpn(); // auto-select
        }

        findViewById(R.id.vpnCard).setOnClickListener(v -> promptVpn());
        findViewById(R.id.rootCard).setOnClickListener(v -> promptRoot());
        findViewById(R.id.startButton).setOnClickListener(v -> {
            if (chosenMethod != AdBlockMethod.UNDEFINED) {
                PreferenceHelper.setAbBlockMethod(this, chosenMethod);
                Intent intent = new Intent(this, HomeActivity.class);
                intent.putExtra(HomeActivity.EXTRA_ONBOARDING_COMPLETE, true);
                startActivity(intent);
                finish();
            }
        });
    }

    private void selectVpn() {
        chosenMethod = AdBlockMethod.VPN;
        // highlight VPN card, grey out root card
        // enable start button
    }

    private void promptVpn() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent == null) {
            selectVpn();
        } else {
            // launch VPN permission dialog
        }
    }

    private void promptRoot() {
        Shell.getShell();
        if (Boolean.TRUE.equals(Shell.isAppGrantedRoot())) {
            chosenMethod = AdBlockMethod.ROOT;
            // highlight root card
        } else {
            // show "no root" dialog
        }
    }
}
```

**Step 3:** Update `HomeActivity.checkSetup()` (or equivalent method) to launch `OnboardingActivity` instead of `WelcomeActivity`. This is the method that runs on first launch when `PreferenceHelper.getAbBlockMethod()` returns `UNDEFINED`.

**Step 4:** Add to manifest:
```xml
<activity
    android:name=".ui.onboarding.OnboardingActivity"
    android:exported="false"
    android:label="@string/app_name"
    android:theme="@style/Theme.AdAway.NoActionBar" />
```

**Step 5:** Build and verify first-run redirect works.
```bash
./gradlew assembleDebug
```

**Step 6:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/onboarding/ \
        app/src/main/res/layout/onboarding_activity.xml \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add single-screen OnboardingActivity replacing multi-step wizard"
```

---

## Phase 2: FilterLists.com Full API Integration

> **Exit Criteria:** Discover tab, FilterLists.com sub-tab shows scrollable tag chips and language spinner. Selecting a tag or language filters the list. Filter selection persists in SharedPreferences across app restarts.

### Task 2.1: Extend FilterListsDirectoryApi with Tag and Language models

**Files:**
- Modify: `app/src/main/java/org/adaway/model/source/FilterListsDirectoryApi.java`

**Context:** The `/lists` endpoint already returns `tagIds[]` and `languageIds[]` per item (verified via live API). The `ListSummary` inner class at line 34 does not capture these fields.

**Step 1:** Add `Tag` and `Language` inner classes to `FilterListsDirectoryApi`:

```java
// Add after the existing Syntax class (around line 56):

public static final class Tag {
    public final int id;
    public final String name;
    public final String description;

    public Tag(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}

public static final class Language {
    public final int id;
    public final String name;
    public final String iso6391; // 2-letter code e.g. "en", "de", "ja"

    public Language(int id, String name, String iso6391) {
        this.id = id;
        this.name = name;
        this.iso6391 = iso6391;
    }
}
```

**Step 2:** Extend `ListSummary` to include `tagIds` and `languageIds`:

```java
// Modify ListSummary class (starting at line 34) to add two new fields:
public static final class ListSummary {
    public final int id;
    public final String name;
    public final String description;
    public final int[] syntaxIds;
    public final int[] tagIds;      // NEW
    public final int[] languageIds; // NEW

    public ListSummary(int id, String name, String description,
                       int[] syntaxIds, int[] tagIds, int[] languageIds) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.syntaxIds = syntaxIds;
        this.tagIds = tagIds;
        this.languageIds = languageIds;
    }
}
```

**Step 3:** Add fetch methods for tags and languages:

```java
// Add after getSyntaxesJson() method (around line 131):

@NonNull
public List<Tag> fetchTags() throws IOException {
    String body = getJson(BASE_URL + "/tags");
    return parseTagsJson(body);
}

@NonNull
public String getTagsJson() throws IOException {
    return getJson(BASE_URL + "/tags");
}

@NonNull
public List<Language> fetchLanguages() throws IOException {
    String body = getJson(BASE_URL + "/languages");
    return parseLanguagesJson(body);
}

@NonNull
public String getLanguagesJson() throws IOException {
    return getJson(BASE_URL + "/languages");
}

@NonNull
public static List<Tag> parseTagsJson(@NonNull String body) throws IOException {
    try {
        JSONArray arr = new JSONArray(body);
        List<Tag> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new Tag(
                o.getInt("id"),
                o.optString("name", ""),
                o.optString("description", "")
            ));
        }
        return out;
    } catch (JSONException e) {
        throw new IOException("Failed to parse FilterLists /tags JSON", e);
    }
}

@NonNull
public static List<Language> parseLanguagesJson(@NonNull String body) throws IOException {
    try {
        JSONArray arr = new JSONArray(body);
        List<Language> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new Language(
                o.getInt("id"),
                o.optString("name", ""),
                o.optString("iso6391", "")
            ));
        }
        return out;
    } catch (JSONException e) {
        throw new IOException("Failed to parse FilterLists /languages JSON", e);
    }
}
```

**Step 4:** Update `parseListsJson` to read `tagIds` and `languageIds` from the JSON:

```java
// Modify parseListsJson (starting at line 135):
@NonNull
public static List<ListSummary> parseListsJson(@NonNull String body) throws IOException {
    try {
        JSONArray arr = new JSONArray(body);
        List<ListSummary> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            int id = o.getInt("id");
            String name = o.optString("name", "");
            String desc = o.optString("description", null);
            int[] syntaxIds = toIntArray(o.optJSONArray("syntaxIds"));
            int[] tagIds = toIntArray(o.optJSONArray("tagIds"));         // NEW
            int[] languageIds = toIntArray(o.optJSONArray("languageIds")); // NEW
            out.add(new ListSummary(id, name, desc, syntaxIds, tagIds, languageIds));
        }
        return out;
    } catch (JSONException e) {
        throw new IOException("Failed to parse FilterLists /lists JSON", e);
    }
}
```

**Step 5:** Run unit tests to verify parsing:
```bash
./gradlew test --tests "*.FilterListsDirectoryApiTest" 2>&1 | tail -20
```
(If no test exists for this class, write a minimal one in `app/src/test/java/org/adaway/model/source/FilterListsDirectoryApiTest.java` that parses a known JSON string and asserts tag/language fields.)

**Step 6:** Build check:
```bash
./gradlew compileDebugJavaWithJavac 2>&1 | tail -20
```

**Step 7:** Commit.
```bash
git add app/src/main/java/org/adaway/model/source/FilterListsDirectoryApi.java
git commit -m "feat: add Tag, Language models and fetchTags/fetchLanguages to FilterListsDirectoryApi"
```

---

### Task 2.2: Create DiscoverFragment with two sub-tabs

**Files:**
- Create: `app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java`
- Create: `app/src/main/res/layout/fragment_discover.xml`
- Create: `app/src/main/java/org/adaway/ui/discover/DiscoverPagerAdapter.java`

**Step 1:** Write `fragment_discover.xml`. Root is a LinearLayout containing a `TabLayout` and `ViewPager2`:

```xml
<!-- app/src/main/res/layout/fragment_discover.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/discoverTabLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:tabMode="fixed"
        app:tabGravity="fill" />

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/discoverViewPager"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

</LinearLayout>
```

**Step 2:** Create `DiscoverPagerAdapter.java`:

```java
package org.adaway.ui.discover;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class DiscoverPagerAdapter extends FragmentStateAdapter {
    public DiscoverPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new DiscoverCatalogFragment();
        return new DiscoverFilterListsFragment();
    }

    @Override
    public int getItemCount() { return 2; }
}
```

**Step 3:** Create `DiscoverFragment.java`:

```java
package org.adaway.ui.discover;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import org.adaway.databinding.FragmentDiscoverBinding;

public class DiscoverFragment extends Fragment {
    private FragmentDiscoverBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDiscoverBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        DiscoverPagerAdapter adapter = new DiscoverPagerAdapter(requireActivity());
        binding.discoverViewPager.setAdapter(adapter);
        new TabLayoutMediator(binding.discoverTabLayout, binding.discoverViewPager,
            (tab, position) -> {
                if (position == 0) tab.setText("Curated");
                else tab.setText("FilterLists.com");
            }).attach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
```

**Step 4:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/discover/ \
        app/src/main/res/layout/fragment_discover.xml
git commit -m "feat: create DiscoverFragment with Curated/FilterLists.com tab selector"
```

---

### Task 2.3: Create DiscoverCatalogFragment (migrate FilterCatalogActivity)

**Files:**
- Create: `app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java`
- Create: `app/src/main/res/layout/fragment_discover_catalog.xml`

**Step 1:** Copy the content of `FilterCatalogActivity.java` (lines 61-548). Remove `extends AppCompatActivity`. Change to `extends Fragment`. Replace `setContentView()` with `onCreateView()`. Replace `runOnUiThread()` with `requireActivity().runOnUiThread()`. Replace `startActivity()` calls accordingly (they remain the same — still launch `SourceEditActivity`). Remove toolbar setup (toolbar is now in the parent Activity).

Key pattern replacements:
- `EXECUTOR.execute(...)` stays the same (uses static `AppExecutors.getInstance().diskIO()`)
- `hostsSourceDao` initialization moves to `onViewCreated()`
- `getApplicationContext()` becomes `requireContext().getApplicationContext()`
- `this` as context argument → `requireContext()`

**Step 2:** Write `fragment_discover_catalog.xml`. This is the same content as `filter_catalog_dialog.xml` minus the toolbar — just the search bar, preset chips, RecyclerView, and add button.

**Step 3:** Compile check.
```bash
./gradlew compileDebugJavaWithJavac 2>&1 | tail -30
```

**Step 4:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java \
        app/src/main/res/layout/fragment_discover_catalog.xml
git commit -m "feat: migrate FilterCatalogActivity logic into DiscoverCatalogFragment"
```

---

### Task 2.4: Create DiscoverFilterListsFragment with tag chip filtering

**Files:**
- Create: `app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java`
- Create: `app/src/main/res/layout/fragment_discover_filterlists.xml`

**Step 1:** Write `fragment_discover_filterlists.xml`. This extends the existing `filterlists_import_activity.xml` layout by adding:
- A `HorizontalScrollView` containing a `ChipGroup` for tags (below the search bar)
- A `Spinner` or chip for language selection (below tags)

```xml
<!-- fragment_discover_filterlists.xml key additions: -->
<HorizontalScrollView
    android:id="@+id/tagScrollView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <com.google.android.material.chip.ChipGroup
        android:id="@+id/tagChipGroup"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:singleSelection="true"
        app:selectionRequired="false" />
</HorizontalScrollView>

<Spinner
    android:id="@+id/languageSpinner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

**Step 2:** Create `DiscoverFilterListsFragment.java`. Copy content from `FilterListsImportActivity.java`. Convert from Activity to Fragment (same pattern as Task 2.3). Add tag and language loading and filtering:

```java
// Key additions to the Fragment:

private static final String PREFS = "filterlists_cache";
private static final String KEY_TAGS_JSON = "tagsJson";
private static final String KEY_LANGUAGES_JSON = "languagesJson";
private static final String KEY_SELECTED_TAG_ID = "selectedTagId";
private static final String KEY_SELECTED_LANGUAGE_ID = "selectedLanguageId";

private List<FilterListsDirectoryApi.Tag> allTags = new ArrayList<>();
private List<FilterListsDirectoryApi.Language> allLanguages = new ArrayList<>();
private int selectedTagId = -1;    // -1 = no filter
private int selectedLanguageId = -1;

// In loadTags() (called from background thread alongside loadLists()):
private void loadTagsAndLanguages() {
    AppExecutors.getInstance().diskIO().execute(() -> {
        try {
            AdAwayApplication app = (AdAwayApplication) requireContext().getApplicationContext();
            FilterListsDirectoryApi api = new FilterListsDirectoryApi(
                app.getSourceModel().getHttpClientForUi());
            SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, MODE_PRIVATE);

            // Load tags
            String cachedTagsJson = prefs.getString(KEY_TAGS_JSON, null);
            List<FilterListsDirectoryApi.Tag> tags;
            if (cachedTagsJson != null) {
                tags = FilterListsDirectoryApi.parseTagsJson(cachedTagsJson);
            } else {
                String tagsJson = api.getTagsJson();
                prefs.edit().putString(KEY_TAGS_JSON, tagsJson).apply();
                tags = FilterListsDirectoryApi.parseTagsJson(tagsJson);
            }

            // Load languages
            String cachedLanguagesJson = prefs.getString(KEY_LANGUAGES_JSON, null);
            List<FilterListsDirectoryApi.Language> languages;
            if (cachedLanguagesJson != null) {
                languages = FilterListsDirectoryApi.parseLanguagesJson(cachedLanguagesJson);
            } else {
                String languagesJson = api.getLanguagesJson();
                prefs.edit().putString(KEY_LANGUAGES_JSON, languagesJson).apply();
                languages = FilterListsDirectoryApi.parseLanguagesJson(languagesJson);
            }

            // Restore persisted selection
            int persistedTag = prefs.getInt(KEY_SELECTED_TAG_ID, -1);
            int persistedLang = prefs.getInt(KEY_SELECTED_LANGUAGE_ID, -1);

            AppExecutors.getInstance().mainThread().execute(() -> {
                allTags.clear();
                allTags.addAll(tags);
                allLanguages.clear();
                allLanguages.addAll(languages);
                selectedTagId = persistedTag;
                selectedLanguageId = persistedLang;
                populateTagChips();
                populateLanguageSpinner();
                filter(); // re-filter with restored selections
            });
        } catch (IOException e) {
            // Silent fail — tag/language chips just don't appear
        }
    });
}

private void populateTagChips() {
    ChipGroup chipGroup = binding.tagChipGroup;
    chipGroup.removeAllViews();
    for (FilterListsDirectoryApi.Tag tag : allTags) {
        Chip chip = new Chip(requireContext());
        chip.setText(tag.name);
        chip.setCheckable(true);
        chip.setChecked(tag.id == selectedTagId);
        chip.setOnCheckedChangeListener((v, checked) -> {
            selectedTagId = checked ? tag.id : -1;
            requireContext().getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putInt(KEY_SELECTED_TAG_ID, selectedTagId).apply();
            filter();
        });
        chipGroup.addView(chip);
    }
}

private void populateLanguageSpinner() {
    // Build adapter with "All languages" as position 0
    List<String> names = new ArrayList<>();
    names.add("All languages");
    for (FilterListsDirectoryApi.Language lang : allLanguages) {
        names.add(lang.name + " (" + lang.iso6391 + ")");
    }
    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
        android.R.layout.simple_spinner_item, names);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    binding.languageSpinner.setAdapter(adapter);

    // Restore selection
    if (selectedLanguageId != -1) {
        for (int i = 0; i < allLanguages.size(); i++) {
            if (allLanguages.get(i).id == selectedLanguageId) {
                binding.languageSpinner.setSelection(i + 1); // +1 for "All"
                break;
            }
        }
    }

    binding.languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            selectedLanguageId = pos == 0 ? -1 : allLanguages.get(pos - 1).id;
            requireContext().getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putInt(KEY_SELECTED_LANGUAGE_ID, selectedLanguageId).apply();
            filter();
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    });
}
```

**Step 3:** Extend the `filter()` method to apply tag and language filters:

```java
private void filter() {
    String q = binding.filterlistsSearchEditText.getText() != null
        ? binding.filterlistsSearchEditText.getText().toString().toLowerCase(Locale.ROOT).trim()
        : "";
    filtered.clear();
    for (FilterListsDirectoryApi.ListSummary s : all) {
        // Text search
        if (!q.isEmpty()) {
            String name = s.name != null ? s.name.toLowerCase(Locale.ROOT) : "";
            String desc = s.description != null ? s.description.toLowerCase(Locale.ROOT) : "";
            if (!name.contains(q) && !desc.contains(q)) continue;
        }
        // Tag filter
        if (selectedTagId != -1 && !containsId(s.tagIds, selectedTagId)) continue;
        // Language filter
        if (selectedLanguageId != -1 && !containsId(s.languageIds, selectedLanguageId)) continue;
        filtered.add(s);
    }
    adapter.notifyDataSetChanged();
}

private static boolean containsId(int[] ids, int target) {
    if (ids == null) return false;
    for (int id : ids) if (id == target) return true;
    return false;
}
```

**Step 4:** Build and install on emulator/device. Verify tag chips appear and filter the list.
```bash
./gradlew installDebug
```

**Step 5:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java \
        app/src/main/res/layout/fragment_discover_filterlists.xml
git commit -m "feat: add tag chip row and language spinner to FilterLists.com browse tab"
```

---

### Task 2.5: Wire DiscoverFragment into HomeActivity bottom nav

**Files:**
- Modify: `app/src/main/java/org/adaway/ui/home/HomeActivity.java`

**Step 1:** The `DiscoverFragment` class is now ready. Update the nav item switch in `HomeActivity` to instantiate `DiscoverFragment` for `R.id.nav_discover`:

```java
} else if (item.getItemId() == R.id.nav_discover) {
    fragment = new DiscoverFragment();
}
```

**Step 2:** Verify no import errors and build passes.
```bash
./gradlew assembleDebug 2>&1 | tail -10
```

**Step 3:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/home/HomeActivity.java
git commit -m "feat: connect DiscoverFragment to bottom nav Discover tab"
```

---

## Phase 3: One-tap Onboarding

> **Current onboarding behavior:** See [Set Up Protection](../../README.md#set-up-protection-first-time).

### Task 3.1: Default list auto-subscription on first run

The original implementation sketch is superseded. The current initialization path
is owned by [InitialSourcesViewModel](../../app/src/main/java/org/adaway/ui/onboarding/InitialSourcesViewModel.java)
and [DefaultListsSubscriber](../../app/src/main/java/org/adaway/ui/onboarding/DefaultListsSubscriber.java),
with regression coverage in [DefaultListsSubscriberTest](../../app/src/test/java/org/adaway/ui/onboarding/DefaultListsSubscriberTest.java).
Default membership is defined by [FilterListCatalog.getDefaults](../../app/src/main/java/org/adaway/model/source/FilterListCatalog.java).

---

### Task 3.2: Home screen no-sources CTA to Discover tab

**Files:**
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/org/adaway/ui/home/HomeFragment.java`

**Step 1:** The `noSourcesHint` TextView in `fragment_home.xml` should be a clickable `MaterialButton` styled as text (no background, with an arrow icon):

```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/discoverCta"
    style="@style/Widget.Material3.Button.TextButton"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/home_no_sources_cta"
    android:visibility="gone"
    app:icon="@drawable/ic_arrow_forward_24dp"
    app:iconGravity="end" />
```

Add string: `<string name="home_no_sources_cta">No lists yet — tap Discover to add some</string>`

**Step 2:** In `HomeFragment.java`, when the CTA is tapped, switch the bottom navigation to the Discover tab:

```java
binding.discoverCta.setOnClickListener(v -> {
    // Navigate parent Activity to Discover tab
    if (getActivity() instanceof HomeActivity) {
        ((HomeActivity) getActivity()).navigateTo(R.id.nav_discover);
    }
});
```

**Step 3:** Add `navigateTo(int navItemId)` method to `HomeActivity.java`:
```java
public void navigateTo(int navItemId) {
    binding.bottomNavigation.setSelectedItemId(navItemId);
}
```

**Step 4:** Commit.
```bash
git add app/src/main/res/layout/fragment_home.xml \
        app/src/main/java/org/adaway/ui/home/HomeFragment.java \
        app/src/main/java/org/adaway/ui/home/HomeActivity.java
git commit -m "feat: add Discover CTA on Home when no filter lists are configured"
```

---

### Task 3.3: Live progress bar on Home during updates

**Step 1:** Verify `HomeViewModel.getMultiPhaseProgress()` returns a `LiveData<SourceModel.MultiPhaseProgress>` (line 130 of `HomeViewModel.java`). This is already wired in the existing code.

**Step 2:** The `HomeFragment.observeViewModel()` already has a skeleton observation of `multiPhaseProgress` from Task 1.2. Ensure `SourceModel.MultiPhaseProgress` exposes `getOverallPercent()` and `getCurrentPhaseName()`. If not, inspect `SourceModel.java` and adapt the binding to use whatever fields exist.

**Step 3:** On completion of update (progress becomes null/inactive), show a brief Snackbar: "Filter lists updated. X domains blocked."

**Step 4:** Commit.
```bash
git commit -m "feat: live progress bar on Home screen reflects filter update state"
```

---

## Phase 4: Code Simplification

> **Exit Criteria:** FilterCatalogActivity, FilterListsImportActivity, HelpActivity, SupportActivity are deleted. Manifest cleaned. No dead imports. Build passes with no warnings about missing classes.

### Task 4.1: Delete obsolete Activity files

**Files to delete:**
- `app/src/main/java/org/adaway/ui/hosts/FilterCatalogActivity.java`
- `app/src/main/java/org/adaway/ui/hosts/FilterListsImportActivity.java`
- `app/src/main/java/org/adaway/ui/help/HelpActivity.java`
- `app/src/main/java/org/adaway/ui/help/HelpFragmentHtml.java`
- `app/src/main/java/org/adaway/ui/support/SupportActivity.java`

**Step 1:** Verify no remaining references to these classes:
```bash
grep -r "FilterCatalogActivity\|FilterListsImportActivity\|HelpActivity\|SupportActivity" \
    app/src/main/java/org/adaway/
```
Expected: zero results (all references should now go through DiscoverFragment and MoreFragment).

**Step 2:** Delete files:
```bash
rm app/src/main/java/org/adaway/ui/hosts/FilterCatalogActivity.java
rm app/src/main/java/org/adaway/ui/hosts/FilterListsImportActivity.java
rm app/src/main/java/org/adaway/ui/help/HelpActivity.java
rm app/src/main/java/org/adaway/ui/help/HelpFragmentHtml.java
rm app/src/main/java/org/adaway/ui/support/SupportActivity.java
```

**Step 3:** Remove their entries from `AndroidManifest.xml`:
- Remove `<activity android:name=".ui.hosts.FilterCatalogActivity" ...>`
- Remove `<activity android:name=".ui.hosts.FilterListsImportActivity" ...>`
- Remove `<activity android:name=".ui.help.HelpActivity" ...>`
- Remove `<activity android:name=".ui.support.SupportActivity" ...>`

**Step 4:** Build to confirm no missing references:
```bash
./gradlew compileDebugJavaWithJavac 2>&1 | grep -i error
```
Expected: no errors.

**Step 5:** Commit.
```bash
git add -A
git commit -m "refactor: delete obsolete FilterCatalogActivity, FilterListsImportActivity, HelpActivity, SupportActivity"
```

---

### Task 4.2: Keep WelcomeActivity or redirect to OnboardingActivity

**Decision:** [CHECKPOINT] During Phase 4 review, decide whether to:
- (A) Delete `WelcomeActivity` and all welcome fragments, updating `HomeActivity` to launch `OnboardingActivity` only
- (B) Keep `WelcomeActivity` as a legacy path for edge cases

**Recommendation:** Option A — delete. `OnboardingActivity` covers all cases (VPN + root detection). The 3-screen wizard added no functionality over the single-screen version.

**Step 1 (if Option A chosen):**
```bash
rm app/src/main/java/org/adaway/ui/welcome/WelcomeActivity.java
rm app/src/main/java/org/adaway/ui/welcome/WelcomePagerAdapter.java
rm app/src/main/java/org/adaway/ui/welcome/WelcomeFragment.java
rm app/src/main/java/org/adaway/ui/welcome/WelcomeMethodFragment.java
rm app/src/main/java/org/adaway/ui/welcome/WelcomeNavigable.java
rm app/src/main/java/org/adaway/ui/welcome/WelcomeSupportFragment.java
rm app/src/main/java/org/adaway/ui/welcome/WelcomeSyncFragment.java
```

**Step 2:** Remove from manifest. Update `HomeActivity.checkSetup()` to launch `OnboardingActivity`.

**Step 3:** Build check.
```bash
./gradlew assembleDebug 2>&1 | tail -10
```

**Step 4:** Commit.
```bash
git add -A
git commit -m "refactor: remove multi-step WelcomeActivity, OnboardingActivity is the sole first-run screen"
```

---

### Task 4.3: Consolidate FilterSetStore and FilterListCatalog overlap

**Context:** `FilterSetStore` stores named URL sets with schedules. `FilterListCatalog` has preset methods (`getDefaults()`, `getBalancedPreset()`, `getAggressivePreset()`). There is no coordination: a user who applies "Balanced" via `FilterCatalogActivity` adds sources to the DB but does not create a named set in `FilterSetStore`. This means the schedule feature in `FilterSetStore` cannot track which preset is active.

**Decision:** [CHECKPOINT] Choose coordination strategy:
- (A) Lightweight: When user applies a preset in `DiscoverCatalogFragment`, also save a named set in `FilterSetStore` (e.g., "Balanced") containing the same URLs. No schema change.
- (B) Heavier: Add a `presetName` column to `HostsSource` table (Room migration required — avoid per constraints).

**Recommendation:** Option A — no schema change. In `DiscoverCatalogFragment.addSelectedSources()`, after inserting to DB, call:
```java
FilterSetStore.saveSet(context, "custom", selectedUrls);
```
This creates a named snapshot. The schedule system can then apply/refresh this set.

**Step 1 (Option A):** Modify `DiscoverCatalogFragment.addSelectedSources()` to call `FilterSetStore.saveSet()` with the URL set of selected entries.

**Step 2:** Update `SchedulesActivity` (if it exists and references FilterCatalogActivity) to work with the new fragment-based flow.

**Step 3:** Commit.
```bash
git add app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java
git commit -m "feat: save applied preset as named set in FilterSetStore for schedule integration"
```

---

### Task 4.4: Final build verification

**Step 1:** Full release build:
```bash
./gradlew assembleRelease 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL.

**Step 2:** Unit tests:
```bash
./gradlew test 2>&1 | tail -20
```
Expected: all existing tests pass.

**Step 3:** Verify no broken references:
```bash
./gradlew lint 2>&1 | grep "Error\|Warning" | head -20
```

**Step 4:** Commit and tag.
```bash
git commit -m "chore: phase 4 complete — clean build, all tests pass"
git tag v14.0.0-modernized
```

---

## Risk Table

| Risk | Probability (1-5) | Impact (1-5) | Score | Mitigation |
|------|-------------------|--------------|-------|------------|
| Fragment back-stack confusion when using BottomNavigationView | 3 | 3 | 9 | Use `replace()` not `add()`, clear back stack on tab switch. Use `FragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)` before replace |
| ViewBinding naming conflict (e.g., `fragment_home.xml` generates `FragmentHomeBinding` already used somewhere) | 2 | 2 | 4 | Check existing layout names before creating; rename if conflict |
| WorkManager `FilterListsSubscribeAllWorker` references `FilterListsImportActivity` for notification tap intent (it references `HomeActivity` at line 333 — safe) | 1 | 3 | 3 | Confirmed: worker already targets HomeActivity — no change needed |
| `parseListsJson` adding tagIds/languageIds breaks existing cached JSON in SharedPreferences | 3 | 2 | 6 | Use `optJSONArray()` (already used) — missing fields gracefully return empty arrays. Old cache still works. |
| `ListSummary` constructor arity change breaks callers (FilterListsSubscribeAllWorker.java, FilterListsImportActivity.java) | 4 | 3 | 12 | Update all callers in same commit as the model change. Search for `new ListSummary` usages first. |
| `DiscoverFilterListsFragment` context leaks (Activity context stored past onDestroyView) | 3 | 3 | 9 | Use `requireContext().getApplicationContext()` for DB/network, clear binding in `onDestroyView()` |
| Tags/languages network call adds latency to Discover tab first open | 3 | 2 | 6 | Cache in SharedPreferences with 24h TTL (same pattern as existing lists cache). Show chips once loaded, not before. |
| `DefaultListsSubscriber` called on UI thread in edge case | 2 | 4 | 8 | Always call via `AppExecutors.getInstance().diskIO().execute()` — enforce this with `@WorkerThread` annotation |
| OnboardingActivity VPN permission flow differs from WelcomeMethodFragment in edge cases | 2 | 3 | 6 | Copy exact VPN prepare + always-on VPN dialog logic from WelcomeMethodFragment.java:78-155 |
| HomeActivity loses state when bottom nav switches fragments | 3 | 2 | 6 | Use Fragment tag-based caching: check if fragment already exists via `findFragmentByTag()` before creating new instance |

---

## Definition of Done Per Phase

### Phase 1 Done When:
- [ ] App opens directly to Home tab (bottom nav visible)
- [ ] Tapping Discover tab shows the discover layout with two sub-tabs
- [ ] Tapping Advanced tab shows all tool links
- [ ] First run (clean install) shows `OnboardingActivity`, not the multi-step wizard
- [ ] VPN is auto-selected when root is unavailable
- [ ] Back button from tabs works correctly (exits app from Home, not crashes)
- [ ] `./gradlew assembleDebug` succeeds

### Phase 2 Done When:
- [ ] FilterLists.com sub-tab shows a horizontal scrollable row of tag chips
- [ ] Tapping a tag chip filters the list to only matching lists
- [ ] Language spinner filters by language
- [ ] Chip selection persists after navigating away and returning
- [ ] `fetchTags()` and `fetchLanguages()` have unit tests
- [ ] `parseListsJson` unit test verifies `tagIds` and `languageIds` are populated
- [ ] `./gradlew test` passes

### Phase 3 Done When:
- [ ] Fresh install: after onboarding, 4 default sources appear in the DB
- [ ] Update triggers immediately after onboarding
- [ ] Home screen shows live progress bar during update
- [ ] After update completes, blocked count reflects actual count
- [ ] No-sources CTA on Home navigates to Discover tab

### Phase 4 Done When:
- [ ] `FilterCatalogActivity.java` does not exist
- [ ] `FilterListsImportActivity.java` does not exist
- [ ] `HelpActivity.java` does not exist
- [ ] `SupportActivity.java` does not exist
- [ ] `WelcomeActivity.java` does not exist (if Option A chosen in Task 4.2)
- [ ] `AndroidManifest.xml` has no references to deleted activities
- [ ] `grep -r "FilterCatalogActivity\|FilterListsImportActivity" app/` returns zero results
- [ ] `./gradlew assembleRelease` succeeds
- [ ] `./gradlew test` passes

---

## Execution Handoff

Plan is saved to `docs/plans/2026-02-25-adaway-modernization-plan.md`.

**Two execution options:**
1. **Subagent-Driven (this session):** Fresh subagent per phase, review between phases.
2. **Manual Execution:** Follow phase by phase, run build commands at each step.

Recommend starting with **Phase 1** (navigation refactor) as it has the widest surface area and sets the structure every subsequent phase depends on.

---

## Implementation Results

*(Appended by build agents as each phase completes)*
