package qupath.ext.projecttabplus.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.ext.projecttabplus.ui.RemoveImageDialogs;
import qupath.ext.projecttabplus.ui.RemoveImageDialogs.RemoveChoice;
import qupath.ext.projecttabplus.ui.Strings;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Remove the current image: eligibility check off the FX thread, D1, close every viewer
 * showing it (QuPath's own save prompt, cancel aborts), {@code imageData.close()}, the optional
 * file-to-trash step (D2, D3), then remove the entry, sync and refresh.
 */
public final class RemoveImageCommand {

    private static final Logger logger = LoggerFactory.getLogger(RemoveImageCommand.class);

    private final QuPathGUI qupath;
    private final ProjectTabPlusPane pane;

    /**
     * @param qupath the QuPath instance
     * @param pane the tab, for status and refresh
     */
    public RemoveImageCommand(QuPathGUI qupath, ProjectTabPlusPane pane) {
        this.qupath = qupath;
        this.pane = pane;
    }

    /**
     * Start the flow for an entry that is open in a viewer.
     *
     * @param entry the entry to remove
     */
    public void run(ProjectImageEntry<BufferedImage> entry) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null || entry == null) {
            return;
        }
        pane.bulkExecutor().submit(() -> {
            DeleteEligibility eligibility = eligibilityOf(project, entry);
            long size = -1;
            long modified = -1;
            if (eligibility.path() != null) {
                try {
                    size = Files.size(eligibility.path());
                    modified = Files.getLastModifiedTime(eligibility.path()).toMillis();
                } catch (IOException e) {
                    logger.debug("Could not stat {}: {}", eligibility.path(), e.getMessage());
                }
            }
            final long fileSize = size;
            final long fileModified = modified;
            Platform.runLater(() -> confirmAndRun(project, entry, eligibility, fileSize, fileModified));
        });
    }

    static DeleteEligibility eligibilityOf(Project<BufferedImage> project, ProjectImageEntry<BufferedImage> entry) {
        Collection<URI> uris;
        try {
            uris = entry.getURIs();
        } catch (IOException e) {
            return DeleteEligibility.check(List.of(), 0);
        }
        List<URI> others = new ArrayList<>();
        for (ProjectImageEntry<BufferedImage> other : project.getImageList()) {
            if (other == entry) {
                continue;
            }
            try {
                others.addAll(other.getURIs());
            } catch (IOException e) {
                logger.debug("Could not read URIs of {}: {}", other.getImageName(), e.getMessage());
            }
        }
        return DeleteEligibility.check(uris, others);
    }

    private void confirmAndRun(Project<BufferedImage> project, ProjectImageEntry<BufferedImage> entry,
                               DeleteEligibility eligibility, long fileSize, long fileModified) {
        Optional<RemoveChoice> choice = RemoveImageDialogs.showRemoveCurrent(
                qupath.getStage(), entry.getImageName(), eligibility, fileSize);
        if (choice.isEmpty()) {
            return;
        }
        List<ImageData<BufferedImage>> closed = new ArrayList<>();
        for (QuPathViewer viewer : qupath.getAllViewers()) {
            ImageData<BufferedImage> imageData = viewer.getImageData();
            if (imageData == null || project.getEntry(imageData) != entry) {
                continue;
            }
            if (!qupath.closeViewer(viewer)) {
                pane.setTransientStatus(Strings.get("status.removalCanceled"), true);
                return;
            }
            closed.add(imageData);
        }
        String name = entry.getImageName();
        String id = entry.getID();
        RemoveChoice c = choice.get();
        // Closing a Bio-Formats image can take many seconds, so the strip shows what is going on.
        pane.showBusy(Strings.format("busy.removingCurrent", name), -1, 0, false);
        pane.bulkExecutor().submit(() -> {
            long start = System.currentTimeMillis();
            logger.info("Project+ remove current image started: entry {}, data folder to trash: {}, file to trash: {}",
                    id, c.dataToTrash(), c.fileToTrash() && eligibility.isEligible());
            for (ImageData<BufferedImage> imageData : closed) {
                try {
                    imageData.close();
                } catch (Exception e) {
                    logger.warn("Error closing image data of entry {}: {}", id, e.getMessage());
                }
            }
            String fileStatus = null;
            String fileOutcome = "not offered";
            if (c.fileToTrash() && eligibility.isEligible()) {
                Path path = eligibility.path();
                boolean trash = callFx(() -> RemoveImageDialogs.showMoveFileToTrash(
                        qupath.getStage(), path, fileSize, fileModified));
                if (trash) {
                    FileTrash.Result result = FileTrash.moveToTrash(path);
                    if (result.outcome() == FileTrash.Outcome.MOVED) {
                        fileStatus = Strings.format("status.removedFileTrashed", name);
                        fileOutcome = "moved to trash";
                    } else {
                        boolean removeAnyway = callFx(() -> RemoveImageDialogs.showTrashFailed(
                                qupath.getStage(), path, result.detail()));
                        if (!removeAnyway) {
                            logger.info("Project+ remove current image canceled: entry {}, file kept ({})",
                                    id, result.outcome());
                            Platform.runLater(() -> {
                                pane.hideBusy();
                                pane.setTransientStatus(Strings.get("status.removalCanceled"), true);
                            });
                            return;
                        }
                        fileStatus = Strings.format("status.removedFileKept", name, path);
                        fileOutcome = "kept, trash failed (" + result.outcome() + ")";
                    }
                } else {
                    fileStatus = Strings.format("status.removedFileKept", name, path);
                    fileOutcome = "kept by choice";
                }
            }
            try {
                project.removeImage(entry, c.dataToTrash());
                project.syncChanges();
            } catch (IOException | RuntimeException e) {
                logger.error("Could not remove entry {}: {}", id, e.getMessage(), e);
                Platform.runLater(() -> {
                    pane.hideBusy();
                    Dialogs.showErrorNotification(Strings.get("notify.title"), Strings.get("notify.removeFailed"));
                });
                return;
            }
            String dataOutcome = "kept by choice";
            String status = fileStatus != null ? fileStatus : Strings.format("status.removed", name);
            if (c.dataToTrash()) {
                if (BulkJobs.dataFolderRemains(entry)) {
                    dataOutcome = "kept, trash failed";
                    logger.warn("Data folder of entry {} was not moved to the trash; it remains at {}",
                            id, entry.getEntryPath());
                    if (fileStatus == null) {
                        status = Strings.format("status.removedDataKept", name, entry.getEntryPath());
                    }
                } else {
                    dataOutcome = "moved to trash";
                }
            }
            logger.info("Project+ remove current image finished: entry {} removed, data folder {}, file {}, elapsed={} ms",
                    id, dataOutcome, fileOutcome, System.currentTimeMillis() - start);
            pane.checkedIds().remove(id);
            String finalStatus = status;
            Platform.runLater(() -> {
                pane.hideBusy();
                qupath.refreshProject();
                pane.refreshAfterChange();
                pane.setTransientStatus(finalStatus, false);
            });
        });
    }

    private static boolean callFx(java.util.concurrent.Callable<Boolean> dialog) {
        try {
            Boolean r = FXUtils.callOnApplicationThread(dialog);
            return r != null && r;
        } catch (Exception e) {
            logger.error("Dialog failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
