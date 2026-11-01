package qupath.ext.projecttabplus.ui;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.projecttabplus.core.BulkJobs;
import qupath.ext.projecttabplus.core.BulkJobs.Outcome;
import qupath.ext.projecttabplus.ui.BulkDialogs.Kind;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Orchestrates the checkbox-mode actions: confirm on the FX thread, close viewers, run the job
 * on {@code ptp-bulk} with the busy strip, then report.
 */
public final class BulkCommands {

    private static final Logger logger = LoggerFactory.getLogger(BulkCommands.class);

    private final ProjectTabPlusPane pane;
    private final QuPathGUI qupath;
    private Path lastExportFolder;

    BulkCommands(ProjectTabPlusPane pane) {
        this.pane = pane;
        this.qupath = pane.qupath();
    }

    /** D4 then remove the checked entries. */
    public void removeChecked() {
        removeEntries(pane.checkedEntries());
    }

    /** D4 then remove; used by the checkbox bar and the context menu's Remove image(s). */
    public void removeEntries(List<ProjectImageEntry<BufferedImage>> entries) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null || entries.isEmpty()) {
            return;
        }
        Map<String, List<QuPathViewer>> open = openViewers(project, entries);
        Optional<Boolean> choice = BulkDialogs.confirmRemove(qupath.getStage(), entries, open.keySet());
        if (choice.isEmpty()) {
            return;
        }
        List<ImageData<BufferedImage>> closed = closeViewers(open);
        if (closed == null) {
            pane.setTransientStatus(Strings.get("status.bulkRemovalCanceled"), true);
            return;
        }
        boolean dataToTrash = choice.get();
        pane.resetCancel();
        pane.bulkExecutor().submit(() -> {
            closeAll(closed);
            Outcome outcome = BulkJobs.removeEntries(project, entries, dataToTrash,
                    pane.busyProgress("busy.removing"), pane::isCancelRequested);
            Platform.runLater(() -> {
                // The checked set drives the status label, so it changes on the FX thread only.
                for (ProjectImageEntry<BufferedImage> e : entries) {
                    pane.checkedIds().remove(e.getID());
                }
                pane.hideBusy();
                qupath.refreshProject();
                pane.refreshAfterChange();
                report(Kind.REMOVE, outcome, null);
            });
        });
    }

    /** D5 then clear the saved data of the checked entries. */
    public void clearCheckedData() {
        Project<BufferedImage> project = qupath.getProject();
        List<ProjectImageEntry<BufferedImage>> entries = pane.checkedEntries();
        if (project == null || entries.isEmpty()) {
            return;
        }
        Set<String> skipped = new HashSet<>();
        for (ProjectImageEntry<BufferedImage> e : entries) {
            if (!e.hasImageData()) {
                skipped.add(e.getID());
            }
        }
        Map<String, List<QuPathViewer>> open = openViewers(project, entries);
        if (!BulkDialogs.confirmClear(qupath.getStage(), entries, open.keySet(), skipped)) {
            return;
        }
        List<ImageData<BufferedImage>> closed = closeViewers(open);
        if (closed == null) {
            pane.setTransientStatus(Strings.get("status.bulkRemovalCanceled"), true);
            return;
        }
        pane.resetCancel();
        pane.bulkExecutor().submit(() -> {
            closeAll(closed);
            Outcome outcome = BulkJobs.clearData(entries, pane.busyProgress("busy.clearing"), pane::isCancelRequested);
            for (ProjectImageEntry<BufferedImage> e : entries) {
                pane.summaryReader().invalidate(e.getID());
            }
            Platform.runLater(() -> {
                pane.hideBusy();
                pane.refreshAfterChange();
                report(Kind.CLEAR, outcome, null);
            });
        });
    }

    /** D6 then export the checked entries to GeoJSON. */
    public void exportChecked() {
        Project<BufferedImage> project = qupath.getProject();
        List<ProjectImageEntry<BufferedImage>> entries = pane.checkedEntries();
        if (project == null || entries.isEmpty()) {
            return;
        }
        Map<String, ImageData<BufferedImage>> openData = new HashMap<>();
        for (QuPathViewer viewer : qupath.getAllViewers()) {
            ImageData<BufferedImage> imageData = viewer.getImageData();
            ProjectImageEntry<BufferedImage> entry = imageData == null ? null : project.getEntry(imageData);
            if (entry != null && entries.contains(entry)) {
                openData.put(entry.getID(), imageData);
            }
        }
        int unsaved = 0;
        for (ImageData<BufferedImage> d : openData.values()) {
            if (d.isChanged()) {
                unsaved++;
            }
        }
        int noData = 0;
        for (ProjectImageEntry<BufferedImage> e : entries) {
            if (!e.hasImageData() && !openData.containsKey(e.getID())) {
                noData++;
            }
        }
        Path initial = lastExportFolder != null ? lastExportFolder : projectDirectory(project);
        Optional<BulkDialogs.ExportChoice> choice = BulkDialogs.exportOptions(qupath.getStage(), entries.size(),
                noData, unsaved, PathPrefs.maskImageNamesProperty().get(), initial);
        if (choice.isEmpty()) {
            return;
        }
        BulkDialogs.ExportChoice c = choice.get();
        lastExportFolder = c.folder();
        // Snapshot the live objects of open images on the FX thread before the job starts.
        Map<String, Collection<PathObject>> live = new HashMap<>();
        for (Map.Entry<String, ImageData<BufferedImage>> e : openData.entrySet()) {
            live.put(e.getKey(), new ArrayList<>(e.getValue().getHierarchy().getAllObjects(false)));
        }
        pane.resetCancel();
        pane.bulkExecutor().submit(() -> {
            Map<String, String> names = BulkJobs.plannedFileNames(entries, c.compress());
            int existing = BulkJobs.countExisting(c.folder(), names.values());
            BulkJobs.OverwritePolicy policy = BulkJobs.OverwritePolicy.OVERWRITE;
            if (existing > 0) {
                Optional<BulkJobs.OverwritePolicy> p;
                try {
                    p = FXUtils.callOnApplicationThread(
                            () -> BulkDialogs.overwritePrompt(qupath.getStage(), existing, names.size()));
                } catch (Exception e) {
                    logger.error("Overwrite prompt failed: {}", e.getMessage(), e);
                    p = Optional.empty();
                }
                if (p.isEmpty()) {
                    return;
                }
                policy = p.get();
            }
            BulkJobs.ExportOptions options = new BulkJobs.ExportOptions(c.folder(), c.compress(), c.prettyPrint(),
                    c.excludeMeasurements(), policy);
            Outcome outcome = BulkJobs.exportGeoJson(entries, live, options,
                    pane.busyProgress("busy.exporting"), pane::isCancelRequested);
            Platform.runLater(() -> {
                pane.hideBusy();
                report(Kind.EXPORT, outcome, c.folder());
            });
        });
    }

    /** The project folder: {@code getPath()} is the .qpproj file for a DefaultProject. */
    private static Path projectDirectory(Project<BufferedImage> project) {
        Path path = project.getPath();
        if (path == null) {
            return null;
        }
        return java.nio.file.Files.isDirectory(path) ? path : path.getParent();
    }

    private Map<String, List<QuPathViewer>> openViewers(Project<BufferedImage> project,
                                                        List<ProjectImageEntry<BufferedImage>> entries) {
        Map<String, List<QuPathViewer>> open = new HashMap<>();
        for (QuPathViewer viewer : qupath.getAllViewers()) {
            ImageData<BufferedImage> imageData = viewer.getImageData();
            ProjectImageEntry<BufferedImage> entry = imageData == null ? null : project.getEntry(imageData);
            if (entry != null && entries.contains(entry)) {
                open.computeIfAbsent(entry.getID(), k -> new ArrayList<>()).add(viewer);
            }
        }
        return open;
    }

    /** @return the image data of every closed viewer, or null when a save prompt was canceled */
    private List<ImageData<BufferedImage>> closeViewers(Map<String, List<QuPathViewer>> open) {
        List<ImageData<BufferedImage>> closed = new ArrayList<>();
        for (List<QuPathViewer> viewers : open.values()) {
            for (QuPathViewer viewer : viewers) {
                ImageData<BufferedImage> imageData = viewer.getImageData();
                if (imageData == null) {
                    continue;
                }
                if (!qupath.closeViewer(viewer)) {
                    return null;
                }
                closed.add(imageData);
            }
        }
        return closed;
    }

    private static void closeAll(List<ImageData<BufferedImage>> closed) {
        for (ImageData<BufferedImage> imageData : closed) {
            try {
                imageData.close();
            } catch (Exception e) {
                logger.warn("Error closing image data: {}", e.getMessage());
            }
        }
    }

    private void report(Kind kind, Outcome outcome, Path folder) {
        String verb = switch (kind) {
            case REMOVE -> Strings.get("status.verb.removed");
            case CLEAR -> Strings.get("status.verb.cleared");
            case EXPORT -> Strings.get("status.verb.exported");
        };
        if (!outcome.failures().isEmpty()) {
            pane.setTransientStatus(Strings.format("status.bulkFailed", verb, outcome.done(), outcome.total(),
                    outcome.failures().size()), true);
            BulkDialogs.showResult(qupath.getStage(), kind, outcome);
            return;
        }
        if (outcome.canceled()) {
            pane.setTransientStatus(Strings.format("status.bulkStopped", outcome.attempted(), outcome.total()), true);
            return;
        }
        String status = switch (kind) {
            case REMOVE -> Strings.plural("status.bulkRemoved.one", "status.bulkRemoved.many", outcome.done());
            case CLEAR -> Strings.format("status.bulkCleared", outcome.done(), outcome.skipped());
            case EXPORT -> Strings.format("status.bulkExported", outcome.done(), folder);
        };
        String notice = switch (kind) {
            case REMOVE -> Strings.plural("notify.removed.one", "notify.removed.many", outcome.done());
            case CLEAR -> Strings.format("notify.cleared", outcome.done());
            case EXPORT -> Strings.format("notify.exported", outcome.done(), folder);
        };
        pane.setTransientStatus(status, false);
        Dialogs.showInfoNotification(Strings.get("notify.title"), notice);
    }
}
