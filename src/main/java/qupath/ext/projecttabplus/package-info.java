/**
 * Project+: a second Project tab for QuPath 0.7 with metadata grouping, display profiles and
 * checkbox-mode bulk actions.
 *
 * <p>Start at {@link qupath.ext.projecttabplus.ProjectTabPlusExtension}, which installs
 * {@link qupath.ext.projecttabplus.ui.ProjectTabPlusPane}. The pane asks
 * {@link qupath.ext.projecttabplus.core.RefreshCoordinator} when to rebuild; a rebuild snapshots
 * the project into {@link qupath.ext.projecttabplus.model.EntryInfo} rows on the ptp-tree thread,
 * groups them with {@link qupath.ext.projecttabplus.model.TreeBuilder}, and hands the result back
 * to the FX thread, where {@link qupath.ext.projecttabplus.ui.EntryTreeCell} draws each row from
 * the pane's thumbnail and summary caches.
 *
 * <p>Bulk actions go {@code BulkCommands} (confirm, close viewers) to
 * {@link qupath.ext.projecttabplus.core.BulkJobs} (the work, on ptp-bulk) to {@code BulkDialogs}
 * (the result). Profiles are {@link qupath.ext.projecttabplus.model.DisplayProfile} objects
 * persisted by {@link qupath.ext.projecttabplus.model.ProfileStore}. Every user-facing string is
 * in {@code ui/strings.properties}.
 */
package qupath.ext.projecttabplus;
