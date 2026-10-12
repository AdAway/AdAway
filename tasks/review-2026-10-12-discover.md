# Discover Review: 2026-10-12

Scope: Discover UI and associated tests only. No Gradle, commits, release metadata,
or canonical tracker changes from this review worker.

- [x] Read cache load, single/bulk unsubscribe, catalog removal, runtime sync, and existing tests.
- [x] Require successful cache parsing before the fresh-cache early return.
- [x] Rebuild runtime entries and invalidate VPN lookups after all Discover removals.
- [x] Expose local-only Apply Configuration for root installation without downloading sources
  or automatically starting a disconnected VPN.
- [x] Add deterministic cache recovery and unsubscribe runtime regressions.
- [x] Review scoped diff and hand off test list to parent.

## Coordination

No shared SourceModel edits needed: use existing `syncHostEntries()`, which also
invalidates cached VPN rule decisions. Call once after bulk deletion. UI callbacks
remain attachment-guarded; background synchronization does not depend on the view.
Native ancestor messaging is unavailable through the desktop chat tool, so this
file and the final handoff carry the contract to the parent/data reviewer.

## Findings

- Cache freshness previously ignored parse success. Fresh malformed lists,
  syntaxes, tags, or languages could suppress the network recovery path for 24h.
- Catalog, single-list, and bulk unsubscribe deleted only DAO sources. Materialized
  runtime entries, live VPN decisions, and root installation could remain stale.

## Verification

Scoped `git diff --check` passes. No Gradle, Android tests, commits, or release
operations run here; compilation/device execution belongs to parent. The global
diff check reports an unrelated trailing blank line in `tasks/todo.md`, left alone.

### New Android Tests (14)

`org.adaway.ui.discover.DiscoverCacheRecoveryInstrumentedTest` (6):

- Fresh corrupt lists, syntaxes, tags, and languages each refetch and replace all
  payloads, render a row, and reuse the repaired cache on the next load.
- Valid fresh cache renders without a network request.
- Corrupt fresh cache offline shows failure/retry, then recovers without waiting
  for the TTL when the deterministic HTTP fixture becomes available.

`org.adaway.ui.discover.DiscoverUnsubscribeRuntimeInstrumentedTest` (8):

- Control demonstrating DAO-only deletion retains a primed VPN block decision.
- Single, selected, bulk-all, and catalog removal clear primed VPN decisions and
  materialized runtime entries; unrelated/unselected sources remain blocked.
- Bulk-all rebuilds once, not once per source.
- Root Apply exports the post-removal rule set and preserves unrelated entries
  without downloading sources.
- Single and catalog removal finish synchronization after the view is destroyed.
- VPN removal does not call `apply()` or start a disconnected VPN implicitly.

These tests run the actual confirmed removal handlers with real app executors,
Room database, SourceModel rebuild, and VpnModel cache. Reflection only invokes
private confirmed handlers and installs isolated test models. The root installer
is a recording model; this does not claim privileged hosts-file installation or
live VPN packet/device coverage.

### Existing Regression Selection

- `org.adaway.ui.discover.DiscoverPresetSubscriptionTest` (JVM contracts)
- `org.adaway.ui.discover.DiscoverCatalogDetachSafetyTest` (JVM contracts)
- `org.adaway.ui.discover.DiscoverFilterListsFiltersInstrumentedTest`
- `org.adaway.ui.discover.DiscoverUnsupportedReviewInstrumentedTest`
- `org.adaway.model.source.SourceModelRuntimeTruthMutationTest`
- `org.adaway.model.vpn.VpnModelCacheInvalidationTest`

No SourceModel/API edits, source-editor contract changes, or unrelated files were
made by this worker. ApplyConfigurationSnackbar is shared and concurrently owned
by the app reviewer; Discover uses its existing public constructor and notification
method with `syncSources=false`.
