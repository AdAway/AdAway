package org.adaway.ui.discover;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DiscoverPresetSubscriptionTest {
    @Test
    public void quickPresetQueuesImmediateUpdateAndReenablesExistingSources()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java");

        assertTrue("Preset subscription must enqueue the same update/apply worker as other flows.",
                source.contains("SourceUpdateService.enqueueUpdateNow(appContext)"));
        assertTrue("Existing disabled preset sources must be re-enabled.",
                source.contains("sourceDao.setSourceEnabled(source.getId(), true)"));
        assertTrue("Existing source rows must be re-enabled with their source.",
                source.contains("sourceDao.setSourceItemsEnabled(source.getId(), true)"));
        assertTrue("Never-fetched preset sources must trigger an update.",
                source.contains("source.getLocalModificationDate() == null"));
        assertTrue("Previously failed preset sources must trigger an update.",
                source.contains("source.getLastDownloadError() != null"));
    }

    @Test
    public void quickPresetPersistsDurableActiveProfile()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java");
        String layout = readRepoFile(
                "app/src/main/res/layout/fragment_discover.xml");
        String store = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/FilterSetStore.java");

        assertTrue("Quick preset application must save every preset URL into profile storage.",
                source.contains("profileUrls.add(entry.url)"));
        assertTrue("Quick preset application must persist the active preset profile.",
                source.contains("FilterSetStore.savePresetProfile(appContext, preset, profileUrls)"));
        assertTrue("FilterSetStore must persist an active profile key.",
                store.contains("KEY_ACTIVE_PROFILE"));
        assertTrue("FilterSetStore must expose named preset profiles.",
                store.contains("PROFILE_SAFE") &&
                        store.contains("PROFILE_BALANCED") &&
                        store.contains("PROFILE_AGGRESSIVE") &&
                        store.contains("PROFILE_CUSTOM"));
        assertTrue("FilterSetStore must persist profile URLs and schedules by stable ids.",
                store.contains("KEY_SET_IDS") &&
                        store.contains("KEY_SET_DISPLAY_PREFIX") &&
                        store.contains("KEY_SET_ID_PREFIX") &&
                        store.contains("KEY_SCHEDULE_ID_PREFIX") &&
                        store.contains("KEY_ACTIVE_PROFILE_ID"));
        assertTrue("FilterSetStore must lazily migrate legacy display-name keyed profiles.",
                store.contains("migrateLegacySets(prefs)") &&
                        store.contains("resolveSetId(") &&
                        store.contains("legacySetUrls"));
        assertTrue("Discover must expose a visible active-profile status line.",
                layout.contains("discoverProfileStatus"));
        assertTrue("Discover must derive profile status from current enabled sources.",
                source.contains("FilterProfileState.resolve(profileUrls, enabledUrls)"));
        assertTrue("Discover must reconcile exact saved-profile matches before rendering status.",
                source.contains("FilterSetStore.reconcileActiveProfile(appContext, enabledUrls)"));
        assertTrue("Discover must refresh profile status after preset application.",
                source.contains("updateProfileStatus();"));
        assertTrue("Discover profile status must distinguish extended customizations.",
                source.contains("R.string.filter_profile_status_extended"));
    }

    @Test
    public void discoverQuickActionsStayBetweenProfileAndBrowser()
            throws Exception {
        String layout = readRepoFile(
                "app/src/main/res/layout/fragment_discover.xml");

        int profile = layout.indexOf("discoverProfileStatus");
        int safe = layout.indexOf("chipDiscoverSafe");
        int balanced = layout.indexOf("chipDiscoverBalanced");
        int aggressive = layout.indexOf("chipDiscoverAggressive");
        int browser = layout.indexOf("discoverBrowserContainer");

        assertTrue("Discover must show profile status before quick actions.",
                profile >= 0 && profile < safe);
        assertFalse("Discover quick actions must not keep a removed AI chip.",
                layout.contains("chipDiscoverAskAi"));
        assertTrue("Preset chips must stay in the persistent Discover header.",
                safe < balanced && balanced < aggressive);
        assertFalse("Discover quick actions must wrap at large font instead of looking clipped.",
                layout.contains("<HorizontalScrollView"));
        assertTrue("Discover preset chips must live in a wrapping ChipGroup.",
                layout.contains("android:id=\"@+id/discoverPresetChipGroup\"") &&
                        layout.contains("app:singleLine=\"false\""));
        assertTrue("Quick actions must appear before the loading/list browser body.",
                aggressive < browser);
    }

    @Test
    public void quickPresetSnackbarUsesResourcesInsteadOfRawEnglish()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFragment.java");

        assertTrue(source.contains("R.plurals.filter_preset_added_updating"));
        assertTrue(source.contains("R.plurals.filter_preset_existing_updating"));
        assertTrue(source.contains("R.string.filter_preset_already_subscribed"));
        assertFalse("Quick preset snackbar must not hard-code added-list copy.",
                source.contains("\"Added \""));
        assertFalse("Quick preset snackbar must not hard-code already-subscribed copy.",
                source.contains("\"Already subscribed"));
    }

    @Test
    public void catalogAddSelectedSnackbarUsesPluralResource()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java");

        assertTrue(source.contains("R.plurals.filter_added_success_count"));
        assertTrue(source.contains("R.string.filter_preset_already_subscribed"));
        assertFalse("Catalog add-selected snackbar must not append counts by hand.",
                source.contains("getString(R.string.filter_added_success) + \" (\""));
    }

    @Test
    public void catalogPresetSelectionPersistsFullProfileAndManualSelectionBecomesCustom()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverCatalogFragment.java");

        assertTrue("Catalog preset chips must remember which profile is being selected.",
                source.contains("selectedPresetProfile = FilterSetStore.normalizePresetProfile(preset)"));
        assertTrue("Catalog preset chips must save the full preset URL set, including already added rows.",
                source.contains("selectedProfileUrls.add(entry.url)"));
        assertTrue("Adding a catalog preset selection must persist the preset profile.",
                source.contains("FilterSetStore.savePresetProfile(appContext, profile, profileUrls)"));
        assertTrue("Manual catalog selections must persist as the custom profile.",
                source.contains("FilterSetStore.saveCustomProfile(appContext, profileUrls)"));
        assertTrue("Manual row or select-all changes must clear preset identity.",
                source.contains("markCustomSelection()") &&
                        source.contains("selectedPresetProfile = null"));
    }

    @Test
    public void sourceManualToggleAndSavedSetReconcileActiveProfile()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");

        assertTrue("Saving a named source selection must make the named set active.",
                source.contains("FilterSetStore.saveSet(requireContext(), name, enabledUrls)") &&
                        source.contains("FilterSetStore.setActiveProfile(requireContext(), name)"));
        assertFalse("Saving a named set must not silently overwrite the custom profile first.",
                source.contains("FilterSetStore.saveCustomProfile(requireContext(), enabledUrls);\n"
                        + "        if (!FilterSetStore.PROFILE_CUSTOM.equals(name))"));
        assertTrue("Saving a named set must reject reserved or duplicate display names.",
                source.contains("FilterSetStore.validateSetName(") &&
                        source.contains("R.string.filter_set_name_reserved") &&
                        source.contains("R.string.filter_set_name_duplicate"));
        assertTrue("Save dialog validation must stay inline and keep the dialog open.",
                source.contains(".create()") &&
                        source.contains("dialog.setOnShowListener(") &&
                        source.contains("layout.setError(") &&
                        source.contains("FilterSetStore.validateSetName(") &&
                        source.contains("dialog.dismiss()"));
        assertTrue("Empty names must show an inline validation error.",
                source.contains("R.string.filter_set_name_empty"));
        assertTrue("Applying any saved set must record it as the active profile.",
                source.contains("FilterSetStore.setActiveProfile(appCtx, name)"));
        assertTrue("Manual source toggles must mark the active profile custom.",
                source.contains("FilterSetStore.markCustomProfile(requireContext())"));
        assertTrue("Scheduling current selection must generate a real non-reserved saved-set name.",
                source.contains("createScheduledSelectionName()") &&
                        source.contains("filter_set_schedule_current_selection_saved_name") &&
                        source.contains("FilterSetStore.validateSetName(candidate, names)"));
        assertFalse("The visible Current selection placeholder must not be persisted as a saved set.",
                source.contains("String name = getString(R.string.filter_set_schedule_current_selection);\n"
                        + "                        saveFilterSet(name);"));
        assertTrue("Applying a saved set must preview impact before mutating sources.",
                source.contains("previewApplyFilterSet(arr[which])") &&
                        source.contains("showApplyFilterSetConfirmation(") &&
                        source.contains("FilterProfileDiff.resolve("));
        assertTrue("Missing saved sets must be refused before an empty URL set can disable lists.",
                source.contains("FilterSetStore.hasSet(appCtx, name)") &&
                        source.contains("FilterSetStore.hasSetUrls(appCtx, name)") &&
                        source.contains("R.string.filter_set_missing"));
        assertTrue("Empty saved sets must use a dedicated disable-all confirmation.",
                source.contains("showEmptyFilterSetConfirmation(") &&
                        source.contains("filter_set_apply_empty_title"));
        assertTrue("Missing-only saved sets must use a dedicated partial-profile confirmation.",
                source.contains("diff.isMissingOnlyProfile()") &&
                        source.contains("filter_set_apply_missing_only_title"));
        assertTrue("Exact no-op applies must branch on active saved-set identity.",
                source.contains("FilterSetStore.getActiveProfile(appCtx)") &&
                        source.contains("showNoChangeFilterSetDialog("));
        assertTrue("Already-active exact matches must close without mutating state.",
                source.contains("filter_set_apply_no_change_title") &&
                        source.contains("filter_set_apply_no_change_active_message"));
        assertTrue("Identity-only applies must be explicit active-set changes.",
                source.contains("filter_set_apply_set_active_message") &&
                        source.contains("filter_set_apply_set_active_button"));
        assertTrue("The confirmed apply must use the previewed URL set, not re-read profile data.",
                source.contains("applyFilterSet(name, targetUrls)") &&
                        source.contains("private void applyFilterSet(@NonNull String name,") &&
                        source.contains("@NonNull Set<String> enabledUrls)"));
        assertTrue("Confirmed saved-set apply must read a fresh source snapshot from the DAO.",
                source.contains("dao.applySourceSelections(enabledUrls)"));

        String dao = readRepoFile(
                "app/src/main/java/org/adaway/db/dao/HostsSourceDao.java");
        assertTrue("Confirmed saved-set apply must update all source rows in one transaction.",
                dao.contains("@Transaction") &&
                        dao.contains("default void applySourceSelections("));

        String strings = readRepoFile("app/src/main/res/values/strings.xml");
        assertTrue("No-op copy must not claim the profile is already active.",
                strings.contains("No list changes. My Lists already matches this profile."));
        assertTrue("Missing-only copy must disclose that local lists will not match the profile.",
                strings.contains("No saved lists from this profile are on this device."));
    }

    @Test
    public void sourceSavedSetManagerSupportsRenameAndDelete()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");
        String menu = readRepoFile("app/src/main/res/menu/hosts_sources_menu.xml");
        String store = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/FilterSetStore.java");
        String strings = readRepoFile("app/src/main/res/values/strings.xml");

        assertTrue("Sources menu must expose saved-set management.",
                menu.contains("action_hosts_manage_filter_sets") &&
                        source.contains("promptManageFilterSets()"));
        assertTrue("Manage dialog must filter out reserved preset profile names.",
                source.contains("FilterSetStore.isReservedSetName(name)"));
        assertTrue("Rename flow must validate against duplicate and reserved names.",
                source.contains("FilterSetStore.validateSetName(rawName, existingNames)") &&
                        source.contains("FilterSetStore.renameSet(requireContext(), name, newName)"));
        assertTrue("Delete flow must use an explicit confirmation.",
                source.contains("filter_set_delete_message") &&
                        source.contains("FilterSetStore.deleteSet(requireContext(), name)"));
        assertTrue("FilterSetStore must expose stable-id rename and delete APIs.",
                store.contains("boolean renameSet(") &&
                        store.contains("boolean deleteSet("));
        assertTrue("FilterSetStore delete must reset active deleted profiles to custom.",
                store.contains("KEY_ACTIVE_PROFILE_ID") &&
                        store.contains("PROFILE_CUSTOM"));
        assertTrue("Manage strings must be resource-backed.",
                strings.contains("menu_manage_filter_sets") &&
                        strings.contains("filter_set_renamed") &&
                        strings.contains("filter_set_deleted"));
    }

    @Test
    public void scheduleUiUsesLocalizedStringsAndPlurals()
            throws Exception {
        String hosts = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");
        String sourceEdit = readRepoFile(
                "app/src/main/java/org/adaway/ui/source/SourceEditActivity.java");
        String schedules = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/SchedulesActivity.java");
        String strings = readRepoFile("app/src/main/res/values/strings.xml");

        assertTrue("Filter-set schedule snackbars must use a string resource.",
                hosts.contains("R.string.filter_set_schedule_applied"));
        assertFalse("Filter-set schedule snackbars must not hard-code English copy.",
                hosts.contains("\"Scheduled: \""));
        assertTrue("Schedule pickers must use localized weekday labels.",
                hosts.contains("getWeekdayLabels()") &&
                        sourceEdit.contains("getWeekdayLabels()") &&
                        schedules.contains("getWeekdayLabels()"));
        assertFalse("Hosts schedule picker must not hard-code Monday.",
                hosts.contains("\"Monday\""));
        assertFalse("Source schedule picker must not hard-code Monday.",
                sourceEdit.contains("\"Monday\""));
        assertFalse("Schedules screen must not hard-code Monday.",
                schedules.contains("\"Monday\""));
        assertTrue("Scheduled set counts must use plurals.",
                schedules.contains("R.plurals.schedule_filter_sets_count"));
        assertTrue("Scheduled source counts must use plurals.",
                schedules.contains("R.plurals.schedule_sources_count"));
        assertFalse("Scheduled set count copy must not be concatenated.",
                schedules.contains("\"Scheduled sets: \""));
        assertFalse("Scheduled source count copy must not be concatenated.",
                schedules.contains("\"Scheduled sources: \""));
        assertTrue("String resources must define schedule confirmation copy.",
                strings.contains("filter_set_schedule_applied"));
        assertTrue("String resources must define scheduled set and source plurals.",
                strings.contains("schedule_filter_sets_count") &&
                        strings.contains("schedule_sources_count"));
    }

    @Test
    public void addSourceFlowKeepsSchedulingOutOfCreationSurface()
            throws Exception {
        String hosts = readRepoFile(
                "app/src/main/java/org/adaway/ui/hosts/HostsSourcesFragment.java");
        String sheet = readRepoFile("app/src/main/res/layout/hosts_add_options_sheet.xml");
        String sourceEdit = readRepoFile(
                "app/src/main/java/org/adaway/ui/source/SourceEditActivity.java");
        String sourceEditLayout = readRepoFile(
                "app/src/main/res/layout/source_edit_activity.xml");
        String sourceEditMenu = readRepoFile("app/src/main/res/menu/source_edit_menu.xml");
        String sourcesMenu = readRepoFile("app/src/main/res/menu/hosts_sources_menu.xml");

        int addSheetStart = hosts.indexOf("private void showAddSourceOptions()");
        int addSheetEnd = hosts.indexOf("@Override\n    public void setEnabled", addSheetStart);
        String addSheetCode = hosts.substring(addSheetStart, addSheetEnd);

        assertTrue("Add source sheet must keep source-creation choices.",
                sheet.contains("@+id/browseCatalogOption") &&
                        sheet.contains("@+id/addCustomOption"));
        assertFalse("Add source sheet must not mix schedule management into creation.",
                sheet.contains("manageSchedulesOption") ||
                        sheet.contains("@string/menu_manage_schedules") ||
                        addSheetCode.contains("SchedulesActivity.class"));
        assertTrue("Scheduling must remain reachable from the Sources toolbar.",
                sourcesMenu.contains("action_hosts_manage_schedules") &&
                        hosts.contains("startActivity(new Intent(requireContext(), " +
                                "SchedulesActivity.class))"));
        assertTrue("Source edit scheduling must be available only after a source exists.",
                sourceEditMenu.contains("auto_update_action") &&
                        sourceEdit.contains("menu.findItem(R.id.auto_update_action)" +
                                ".setVisible(this.editing)") &&
                        sourceEdit.contains("if (!this.editing)"));
        assertTrue("Source edit layout must keep advanced format controls for existing sources.",
                sourceEditLayout.contains("@+id/format_button_group") &&
                        sourceEditLayout.contains("@+id/redirected_hosts_checkbox"));
        assertTrue("Add-source mode must collapse advanced format and redirected-host controls.",
                sourceEdit.contains("hideAddSourceAdvancedControls();") &&
                        sourceEdit.contains("this.binding.formatTextView.setVisibility(View.GONE)") &&
                        sourceEdit.contains("this.binding.formatButtonGroup.setVisibility(View.GONE)") &&
                        sourceEdit.contains("this.binding.redirectedHostsCheckbox.setVisibility(View.GONE)") &&
                        sourceEdit.contains("this.binding.redirectedHostsWarningTextView" +
                                ".setVisibility(View.GONE)"));
        assertFalse("Add-source simplification must not remove the URL/File source type choice.",
                sourceEdit.contains("this.binding.typeTextView.setVisibility(View.GONE)") ||
                        sourceEdit.contains("this.binding.typeButtonGroup.setVisibility(View.GONE)"));
    }

    @Test
    public void filterListsBulkCommandsDoNotUseDestructiveSwitchSemantics()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");
        String layout = readRepoFile(
                "app/src/main/res/layout/fragment_discover_filterlists.xml");
        String rowLayout = readRepoFile(
                "app/src/main/res/layout/filterlists_import_item.xml");
        String strings = readRepoFile("app/src/main/res/values/strings_filter_catalog.xml");

        assertTrue("Selected-scope bulk actions must be grouped together.",
                layout.contains("filterlistsSelectedActionsRow"));
        assertTrue("All-directory bulk actions must be grouped separately.",
                layout.contains("filterlistsAllActionsRow"));
        assertTrue("Selected bulk add must be an explicit command button.",
                layout.contains("filterlistsSubscribeVisibleButton"));
        assertTrue("Selected bulk remove must be an explicit command button.",
                layout.contains("filterlistsRemoveVisibleButton"));
        assertTrue("All-directory bulk add must be an explicit command button.",
                layout.contains("filterlistsSubscribeAllButton"));
        assertTrue("All-directory bulk remove must be an explicit command button.",
                layout.contains("filterlistsRemoveAllButton"));
        assertTrue("Bulk actions must have a row container so empty states can hide them.",
                layout.contains("filterlistsBulkActionsRow"));
        assertTrue("Selected and all-directory actions must be visible in the copy.",
                strings.contains("Subscribe selected")
                        && strings.contains("Unsubscribe selected")
                        && strings.contains("Subscribe all")
                        && strings.contains("Unsubscribe all"));
        assertTrue("Selected bulk actions must be backed by row checkboxes.",
                rowLayout.contains("filterlistsItemSelectionCheckBox")
                        && source.contains("selectedListIds")
                        && source.contains("setFilterListSelected(s, checked)"));
        assertFalse("Bulk add/remove must not be represented as one destructive switch.",
                layout.contains("filterlistsSubscribeAllSwitch"));
        assertTrue("Selected add button must call selected subscribe confirmation.",
                source.contains("filterlistsSubscribeVisibleButton.setOnClickListener")
                        && source.contains("confirmSubscribeAll(false)"));
        assertTrue("Selected remove button must call selected remove confirmation.",
                source.contains("filterlistsRemoveVisibleButton.setOnClickListener")
                        && source.contains("confirmUnsubscribeAll(false)"));
        assertTrue("All add button must call all-directory subscribe confirmation.",
                source.contains("filterlistsSubscribeAllButton.setOnClickListener")
                        && source.contains("confirmSubscribeAll(true)"));
        assertTrue("All remove button must call all-directory remove confirmation.",
                source.contains("filterlistsRemoveAllButton.setOnClickListener")
                        && source.contains("confirmUnsubscribeAll(true)"));
        assertTrue("Selected bulk command enablement must use checked row state.",
                source.contains("getSelectedSummariesForBulkScope()")
                        && source.contains("getSelectedIdsForBulkScope()")
                        && source.contains("filterlistsSubscribeVisibleButton.setEnabled(" + "\n"
                        + "                !busy && !selected.isEmpty())")
                        && source.contains("filterlistsRemoveVisibleButton.setEnabled(" + "\n"
                        + "                !busy && !selected.isEmpty())"));
        assertTrue("Selected bulk commands must stay responsive for visible cached rows.",
                source.contains("boolean directoryBlocking = directoryLoading && all.isEmpty();")
                        && source.contains("boolean busy = directoryBlocking || bulkOperationRunning;"));
        assertFalse("Background directory refresh must not keep visible selected rows disabled.",
                source.contains("boolean busy = directoryLoading || bulkOperationRunning;"));
        assertTrue("Subscribed-only filter must be available without adding another tall row.",
                layout.contains("filterlistsShowSubscribedSwitch")
                        && strings.contains("Show subscribed")
                        && source.contains("mSubscribedOnly")
                        && source.contains("if (mSubscribedOnly && !isSummarySubscribed(s))"));
        assertTrue("Compatible-only switch must keep a 48dp touch target.",
                xmlTagById(layout, "filterlistsCompatibleOnlySwitch")
                        .contains("android:minHeight=\"48dp\""));
        assertTrue("Subscribed-only switch must keep a 48dp touch target.",
                xmlTagById(layout, "filterlistsShowSubscribedSwitch")
                        .contains("android:minHeight=\"48dp\""));
        for (String buttonId : new String[]{"filterlistsSubscribeVisibleButton",
                "filterlistsRemoveVisibleButton", "filterlistsSubscribeAllButton",
                "filterlistsRemoveAllButton"}) {
            String button = xmlTagById(layout, buttonId);
            assertTrue(buttonId + " must keep a 48dp touch target.",
                    button.contains("android:minHeight=\"48dp\""));
            assertTrue(buttonId + " must retain compact labels and equal column sizing.",
                    button.contains("android:textSize=\"12sp\"")
                            && button.contains("android:layout_weight=\"1\""));
        }
        assertTrue("Bulk actions must hide when the directory has no rows to act on.",
                source.contains("filterlistsBulkActionsRow.setVisibility") &&
                        source.contains("all.isEmpty() && filtered.isEmpty()"));
        assertFalse("Subscribe-all switch must not use any-subscription state as checked.",
                source.contains("setSubscribeAllSwitchChecked(hasFilterListsSubscriptions())"));
    }

    @Test
    public void filterListsSubscribedRowsOpenExistingSourceInsteadOfAddFlow()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");

        assertTrue("Subscribed rows must route to existing source edit.",
                source.contains("openExistingSource(s, cachedUrl)"));
        assertTrue("Existing source edit must pass SourceEditActivity.SOURCE_ID.",
                source.contains("intent.putExtra(SourceEditActivity.SOURCE_ID"));
        assertFalse("Rows must not always launch add-source flow.",
                source.contains("holder.itemView.setOnClickListener(v -> onPick(s))"));
    }

    @Test
    public void filterListsSingleSubscribePersistsProvenanceAndIgnoresEmptyNegativeCache()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");

        assertTrue("Single subscribe must normalize worker negative-cache empty URLs to no URL.",
                source.contains("normalizeCachedUrl(url)"));
        assertTrue("Single subscribe must persist FilterLists metadata on direct row insert.",
                source.contains("FilterListsSourceMetadata.apply(src, summary.id, summary.name"));
        assertTrue("Add-source flow must carry FilterLists list id.",
                source.contains("SourceEditActivity.EXTRA_FILTER_LIST_ID"));
        assertTrue("Add-source flow must carry FilterLists directory name.",
                source.contains("SourceEditActivity.EXTRA_FILTER_LIST_NAME"));
        assertTrue("Add-source flow must carry FilterLists syntax ids.",
                source.contains("SourceEditActivity.EXTRA_FILTER_LIST_SYNTAX_IDS"));
        assertTrue("Add-source flow must carry FilterLists tag ids.",
                source.contains("SourceEditActivity.EXTRA_FILTER_LIST_TAG_IDS"));
        assertTrue("Add-source flow must carry FilterLists language ids.",
                source.contains("SourceEditActivity.EXTRA_FILTER_LIST_LANGUAGE_IDS"));
        assertTrue("Add-source flow must carry selected download URL.",
                source.contains("SourceEditActivity.EXTRA_FILTER_LIST_SELECTED_URL"));
    }

    @Test
    public void sourceEditPreservesFilterListsProvenanceWhenUrlIsUnchanged()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/source/SourceEditActivity.java");

        assertTrue("Source edit must accept FilterLists provenance extras from Discover.",
                source.contains("EXTRA_FILTER_LIST_ID"));
        assertTrue("Source edit must accept FilterLists tag provenance extras from Discover.",
                source.contains("EXTRA_FILTER_LIST_TAG_IDS"));
        assertTrue("Source edit must accept FilterLists language provenance extras from Discover.",
                source.contains("EXTRA_FILTER_LIST_LANGUAGE_IDS"));
        assertTrue("New source validation must apply FilterLists provenance extras.",
                source.contains("FilterListsSourceMetadata.apply(source, this.initialFilterListId"));
        assertTrue("Editing an existing source must preserve metadata when URL is unchanged.",
                source.contains("FilterListsSourceMetadata.copy(this.edited, source)"));
        assertTrue("Metadata must be cleared when the URL changes instead of lying about provenance.",
                source.contains("&& url.equals(this.edited.getUrl())"));
    }

    @Test
    public void filterListsBulkActionsSeparateSelectedAndAllDirectoryScopes()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");

        assertTrue("Bulk subscribe worker must receive an explicit scope.",
                source.contains("FilterListsSubscribeAllWorker.buildScopeInput("));
        assertTrue("Selected subscribe must pass exact checked row ids.",
                source.contains("int[] selectedIds = allDirectory ? null : getSelectedIdsForBulkScope()")
                        && source.contains("selectedIds);"));
        assertTrue("All-directory subscribe must clear text, tag, language, and id filters.",
                source.contains("allDirectory ? null : getCurrentSearchQuery()")
                        && source.contains("allDirectory ? 0 : selectedTagId")
                        && source.contains("allDirectory ? 0 : selectedLanguageId")
                        && source.contains("!allDirectory && mCompatibleOnly"));
        assertTrue("Subscribe confirmation must count the selected scope for that command.",
                source.contains("List<FilterListsDirectoryApi.ListSummary> scope ="
                        + "\n                allDirectory ? all : getSelectedSummariesForBulkScope()")
                        && source.contains("int safeCount = countCompatible(scope)"));
        assertTrue("Selected bulk actions must stay responsive for checked rows.",
                source.contains("filterlistsSubscribeVisibleButton.setEnabled(" + "\n"
                        + "                !busy && !selected.isEmpty())")
                        && source.contains("filterlistsRemoveVisibleButton.setEnabled(" + "\n"
                        + "                !busy && !selected.isEmpty())"));
        assertTrue("All-directory bulk action state must resolve against all loaded rows.",
                source.contains("FilterListsSubscriptionState.resolve(" + "\n"
                        + "                all, this::getCachedUrlForId, existingUrls)"));
        assertTrue("Selected unsubscribe must remove sources from the checked-row scope.",
                source.contains("allDirectory ? new ArrayList<>() : getSelectedSummariesForBulkScope()"));
        assertTrue("All unsubscribe must remove every stored FilterLists-derived source.",
                source.contains("for (HostsSource source : hostsSourceDao.getAll())")
                        && source.contains("isFilterListsSource(source)"));
        assertTrue("Bulk subscribe-all must not queue a background job for zero DNS-safe rows.",
                source.contains("R.string.filterlists_no_dns_safe_lists_in_scope"));
        assertTrue("Unsupported selected rows must route to review instead of a dead-end snackbar.",
                source.contains("if (!allDirectory && showUnsupportedSelectionReview(scope))")
                        && source.contains("private boolean showUnsupportedSelectionReview")
                        && source.contains("showUnsupportedChoicesDialog(unsupported)"));
        assertTrue("Selected unsubscribe must stay responsive and explain no-op selections.",
                source.contains("countSubscribed(selectedScope) == 0")
                        && source.contains("R.string.filterlists_no_subscribed_selected_lists"));
    }

    @Test
    public void filterListsSubscribedRowsUseDurableSourceMetadataWhenUrlCacheMisses()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");

        assertTrue("Discover must keep a durable FilterLists ID to source URL index.",
                source.contains("existingFilterListUrlsById"));
        assertTrue("The durable index must be built from source FilterLists metadata.",
                source.contains("source.getFilterListId()"));
        assertTrue("The durable index must prefer the selected FilterLists download URL.",
                source.contains("source.getFilterListSelectedUrl()"));
        assertTrue("Row state must fall back to source metadata before transient URL prefs.",
                source.contains("String sourceUrl = getSourceUrlForFilterListId(id);")
                        && source.indexOf("String sourceUrl = getSourceUrlForFilterListId(id);")
                        < source.indexOf("prefs.getString(KEY_URL_PREFIX + id, null)"));
        assertTrue("Visible bulk remove must use the same durable source metadata fallback.",
                source.contains("String url = getSourceUrlForFilterListId(summary.id);"));
    }

    @Test
    public void filterListsCancellationKeepsDurableStoppingAndCancelledStates()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");

        assertTrue("Cancel must persist a stopping state across WorkManager observer updates.",
                source.contains("KEY_SUBSCRIBE_ALL_STOPPING"));
        assertTrue("Running observer must display stopping copy while cancellation is pending.",
                source.contains("filterlists_subscribe_all_stopping"));
        assertTrue("WorkManager CANCELLED state must produce a cancelled summary even with no output.",
                source.contains("info.getState() == WorkInfo.State.CANCELLED"));
        assertTrue("Zero-output cancelled work must keep a visible cancelled status.",
                source.contains("return cancelled ? getString(R.string.filterlists_subscribe_all_cancelled) : null"));
    }

    @Test
    public void filterListsBulkReviewDetailsUsePersistedOutcomeLedger()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");
        String layout = readRepoFile(
                "app/src/main/res/layout/fragment_discover_filterlists.xml");

        assertTrue("Bulk review details must expose an explicit action.",
                layout.contains("filterlistsReviewLastRunButton"));
        assertTrue("Bulk no-URL retry must expose an explicit action.",
                layout.contains("filterlistsRetryLastRunButton"));
        assertTrue("Bulk unsupported review must expose an explicit action.",
                layout.contains("filterlistsReviewUnsupportedButton"));
        assertTrue("Review action must be wired to the last-run details dialog.",
                source.contains("filterlistsReviewLastRunButton.setOnClickListener"));
        assertTrue("Retry action must be wired to last-run no-URL retry.",
                source.contains("filterlistsRetryLastRunButton.setOnClickListener"));
        assertTrue("Unsupported review action must be wired to the last-run ledger.",
                source.contains("filterlistsReviewUnsupportedButton.setOnClickListener"));
        assertTrue("Review action must read the durable worker outcome ledger.",
                source.contains("FilterListsSubscribeAllWorker.KEY_LAST_RUN_OUTCOMES"));
        assertTrue("Review action must only show when persisted review items exist.",
                source.contains("FilterListsSubscribeAllWorker.KEY_LAST_RUN_REVIEW_COUNT"));
        assertTrue("Dialog formatting must cap rendered rows for large runs.",
                source.contains("MAX_LAST_RUN_DIALOG_ROWS"));
        assertTrue("Retry must clear cached no-URL markers before requeueing.",
                source.contains("editor.remove(KEY_URL_PREFIX + id)"));
        assertTrue("Retry must clear the in-memory URL cache before requeueing.",
                source.contains("resolvedUrlCache.remove(id)"));
        assertTrue("Retry must requeue the worker with an explicit list-id scope.",
                source.contains("FilterListsSubscribeAllWorker.buildScopeInput(null, 0, 0, false,\n"
                        + "                retryIds)"));
        assertTrue("Unsupported review must parse unsupported outcomes from the same ledger.",
                source.contains("parseUnsupportedIds(outcomes)"));
    }

    @Test
    public void filterListsBulkReviewFormatterRendersReadableOutcomes() {
        String ledger = "SUBSCRIBED\t10\tSafe hosts\thttps://safe.test/hosts.txt\n"
                + "ALREADY\t11\tExisting\thttps://existing.test/hosts.txt\n"
                + "SKIPPED_NO_URL\t12\tNo URL\t\n"
                + "SKIPPED_UNSUPPORTED\t13\tBrowser rules\t";

        assertEquals("Cancelled\n\n"
                        + "Added - Safe hosts\n"
                        + "https://safe.test/hosts.txt\n"
                        + "Already - Existing\n"
                        + "https://existing.test/hosts.txt\n"
                        + "No URL - No URL\n"
                        + "Unsupported - Browser rules",
                DiscoverFilterListsFragment.formatLastRunReviewMessage(ledger, 4, true));
    }

    @Test
    public void filterListsBulkRetryParserKeepsOnlyUniqueNoUrlIds() {
        String ledger = "SUBSCRIBED\t10\tSafe hosts\thttps://safe.test/hosts.txt\n"
                + "SKIPPED_NO_URL\t12\tNo URL\t\n"
                + "SKIPPED_UNSUPPORTED\t13\tBrowser rules\t\n"
                + "SKIPPED_NO_URL\t12\tNo URL duplicate\t\n"
                + "SKIPPED_NO_URL\t0\tUnknown\t\n"
                + "SKIPPED_NO_URL\tbad\tBad\t\n"
                + "SKIPPED_NO_URL\t14\tNo URL 2\t";

        assertArrayEquals(new int[]{12, 14},
                DiscoverFilterListsFragment.parseRetryableNoUrlIds(ledger));
    }

    @Test
    public void filterListsUnsupportedRowsOpenManualReviewInsteadOfDeadEndSnackbar()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");
        String strings = readRepoFile(
                "app/src/main/res/values/strings_filter_catalog.xml");

        assertTrue("Unsupported rows must open a manual-review dialog.",
                source.contains("showUnsupportedReviewDialog(summary)"));
        assertTrue("Manual review must resolve FilterLists details before offering a URL.",
                source.contains("api.getListDetails(summary.id)"));
        assertTrue("Manual review must keep the compatibility warning visible.",
                source.contains("filterlists_review_unsupported_message"));
        assertTrue("Manual review must show exact capability/skipped-semantics detail.",
                source.contains("FilterListCompatibility.capabilitySummary(summary.syntaxIds)"));
        assertTrue("Manual add must remain an explicit dialog action.",
                source.contains("builder.setPositiveButton(R.string.filterlists_add_manually"));
        assertTrue("Manual add must still route through the normal source editor.",
                source.contains("openSourceEditForFilterList(summary, label, url)"));
        assertFalse("Unsupported row tap must not be only a dead-end snackbar.",
                source.contains("showSnackbar(getString(R.string.filterlists_manual_review_required));\n"
                        + "            return;"));
        assertTrue("Unsupported review copy must keep the safety boundary on bulk subscribe.",
                strings.contains("bulk subscribe skips it by default"));
    }

    @Test
    public void filterListsSingleToggleCanSubscribeLimitedSupportRows()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");

        int methodStart = source.indexOf("private void updateSubscription");
        int methodEnd = source.indexOf("private void confirmSubscribeAll", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue("Single-list toggle must still resolve the FilterLists direct URL.",
                method.contains("api.getListDetails(summary.id)"));
        assertTrue("Single-list toggle must insert a normal enabled source.",
                method.contains("hostsSourceDao.insert(src)") &&
                        method.contains("SourceUpdateService.enqueueUpdateNow(appContext)"));
        assertFalse("Single-list toggle must not be blocked by the bulk-safe compatibility gate.",
                method.contains("!isAdAwayCompatible(summary.syntaxIds)"));
        assertFalse("Single-list toggle must not bounce the user to manual review.",
                method.contains("filterlists_manual_review_required"));
        assertTrue("Row switches must remain available for explicit single-list subscription.",
                source.contains("holder.switchView.setEnabled(true);"));
        assertFalse("Row switches must not be disabled just because bulk subscribe skips a syntax.",
                source.contains("holder.switchView.setEnabled(isSubscribed || compatible);"));
    }

    @Test
    public void filterListsRowsExposeCapabilityDisclosure()
            throws Exception {
        String source = readRepoFile(
                "app/src/main/java/org/adaway/ui/discover/DiscoverFilterListsFragment.java");
        String layout = readRepoFile(
                "app/src/main/res/layout/filterlists_import_item.xml");

        assertTrue("Rows must show capability detail, not only raw syntax names.",
                source.contains("formatDescriptionWithCapabilities(s.description, s.syntaxIds)"));
        assertTrue("Rows must include capability detail in accessibility copy.",
                source.contains("rowState + \". \" + capabilitySummary"));
        assertTrue("Limited-support rows must show status copy.",
                source.contains("FilterListCompatibility.rowSummary(s.syntaxIds)"));
        String descriptionView = xmlTagById(layout, "filterlistsItemDesc");
        assertFalse("Capability disclosure must wrap instead of clipping the description.",
                descriptionView.contains("android:maxLines="));
        assertFalse("Capability disclosure must not ellipsize support detail.",
                descriptionView.contains("android:ellipsize="));
    }

    @Test
    public void filterListsBulkUnsupportedParserKeepsOnlyUniqueUnsupportedIds() {
        String ledger = "SUBSCRIBED\t10\tSafe hosts\thttps://safe.test/hosts.txt\n"
                + "SKIPPED_NO_URL\t12\tNo URL\t\n"
                + "SKIPPED_UNSUPPORTED\t13\tBrowser rules\t\n"
                + "SKIPPED_UNSUPPORTED\t13\tBrowser rules duplicate\t\n"
                + "SKIPPED_UNSUPPORTED\t0\tUnknown\t\n"
                + "SKIPPED_UNSUPPORTED\tbad\tBad\t\n"
                + "SKIPPED_UNSUPPORTED\t15\tBrowser rules 2\t";

        assertArrayEquals(new int[]{13, 15},
                DiscoverFilterListsFragment.parseUnsupportedIds(ledger));
    }

    private static String readRepoFile(String relativePath) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path repo = Files.isDirectory(cwd.resolve("app")) ? cwd : cwd.getParent();
        return normalizeLineEndings(new String(Files.readAllBytes(repo.resolve(relativePath)),
                StandardCharsets.UTF_8));
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String xmlTagById(String layout, String id) {
        String needle = "android:id=\"@+id/" + id + "\"";
        int idIndex = layout.indexOf(needle);
        assertTrue("Expected layout to contain " + needle, idIndex >= 0);
        int tagStart = layout.lastIndexOf("<", idIndex);
        int tagEnd = layout.indexOf("/>", idIndex);
        assertTrue("Expected " + id + " to be declared in a self-closing XML tag",
                tagStart >= 0 && tagEnd > idIndex);
        return layout.substring(tagStart, tagEnd);
    }
}
