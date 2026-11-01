package qupath.ext.projecttabplus.core;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPOutputStream;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.projecttabplus.ui.Strings;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.io.PathIO;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * The three checkbox-mode jobs: remove entries, clear saved data, export GeoJSON. Each runs
 * serially over its entries, reports progress, stops after the current entry when canceled, and
 * collects per-entry failures into one outcome. No dialogs, no FX; the caller closes viewers
 * first and reports afterwards. Every job logs one INFO line at start and one at the end, with
 * entry IDs rather than image names; the export also writes {@link ExportManifest#FILE_NAME}.
 */
public final class BulkJobs {

    private static final Logger logger = LoggerFactory.getLogger(BulkJobs.class);

    /** Progress callback, called from the job thread. */
    @FunctionalInterface
    public interface Progress {
        /**
         * @param done entries finished so far
         * @param total entries in the job
         * @param name the entry about to be processed
         */
        void update(int done, int total, String name);
    }

    /**
     * One entry that could not be processed.
     *
     * @param entryId the entry ID, or empty for a project-level failure
     * @param name the image name, for the result dialog
     * @param reason a short reason from {@code strings.properties}
     */
    public record Failure(String entryId, String name, String reason) {
    }

    /**
     * The result of a job.
     *
     * @param total entries in the job
     * @param done entries processed successfully
     * @param skipped entries skipped, for any reason (no saved data, or the file already existed)
     * @param skippedExisting entries skipped because the target file existed; a subset of
     *                        {@code skipped}
     * @param failures entries that failed
     * @param canceled true when the user stopped the job
     */
    public record Outcome(int total, int done, int skipped, int skippedExisting, List<Failure> failures,
                          boolean canceled) {

        public Outcome {
            failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }

        /** @return true when nothing failed and nothing was canceled */
        public boolean isClean() {
            return failures.isEmpty() && !canceled;
        }

        /** @return entries processed plus skipped plus failed */
        public int attempted() {
            return done + skipped + failures.size();
        }

        /** @return true when nothing was exported or failed and at least one existing file was skipped */
        public boolean onlySkippedExisting() {
            return done == 0 && skippedExisting > 0 && failures.isEmpty();
        }
    }

    /** What to do with an export file that already exists. */
    public enum OverwritePolicy {
        OVERWRITE, SKIP
    }

    /**
     * GeoJSON export settings.
     *
     * @param folder the output folder
     * @param compress write {@code .geojson.gz}
     * @param prettyPrint indent the JSON
     * @param excludeMeasurements drop per-object measurements
     * @param overwrite what to do with existing files
     * @param masked whether QuPath masks image names: files are then named by entry ID and the
     *               manifest omits image names
     */
    public record ExportOptions(Path folder, boolean compress, boolean prettyPrint, boolean excludeMeasurements,
                                OverwritePolicy overwrite, boolean masked) {
    }

    private BulkJobs() {
    }

    /**
     * Remove entries from the project, optionally moving each data folder to the trash. The
     * project is synchronized once at the end, even on cancel.
     *
     * @param project the project
     * @param entries the entries to remove
     * @param dataToTrash move each entry's data folder to the trash
     * @param progress progress callback
     * @param cancel polled before every entry
     * @return the outcome
     */
    public static Outcome removeEntries(Project<BufferedImage> project, List<ProjectImageEntry<BufferedImage>> entries,
                                        boolean dataToTrash, Progress progress, BooleanSupplier cancel) {
        long start = System.currentTimeMillis();
        logger.info("Project+ remove started: {} entries, data folders to trash: {}, entry IDs: {}",
                entries.size(), dataToTrash, ids(entries));
        List<Failure> failures = new ArrayList<>();
        int done = 0;
        int foldersKept = 0;
        boolean canceled = false;
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            if (cancel.getAsBoolean()) {
                canceled = true;
                break;
            }
            progress.update(done, entries.size(), entry.getImageName());
            try {
                project.removeImage(entry, dataToTrash);
                done++;
                if (dataToTrash && dataFolderRemains(entry)) {
                    foldersKept++;
                    logger.warn("Data folder of entry {} was not moved to the trash; it remains at {}",
                            entry.getID(), entry.getEntryPath());
                }
            } catch (RuntimeException e) {
                logger.error("Could not remove entry {}: {}", entry.getID(), e.getMessage(), e);
                failures.add(new Failure(entry.getID(), entry.getImageName(), Strings.get("reason.removeFailed")));
            }
        }
        try {
            project.syncChanges();
        } catch (IOException e) {
            logger.error("Could not save the project after removing entries: {}", e.getMessage(), e);
            failures.add(new Failure("", project.getName(), Strings.get("reason.projectSaveFailed")));
        }
        logger.info("Project+ remove finished: done={}, failed={}, canceled={}, data folders kept={}, elapsed={} ms{}",
                done, failures.size(), canceled, foldersKept, System.currentTimeMillis() - start, failureList(failures));
        return new Outcome(entries.size(), done, 0, 0, failures, canceled);
    }

    /**
     * Whether the entry's data folder still exists after a removal that asked for the trash.
     * QuPath hands the move to the Swing event thread when it is called from another thread, so
     * that thread is flushed first; checking at once would see the folder before it has gone.
     *
     * @param entry the removed entry
     * @return true when the folder is still on disk
     */
    public static boolean dataFolderRemains(ProjectImageEntry<BufferedImage> entry) {
        Path path = entry.getEntryPath();
        if (path == null) {
            return false;
        }
        flushSwingEventThread();
        return Files.exists(path);
    }

    /**
     * Return once every task already queued on the Swing event thread has run. Does nothing on
     * that thread itself; an interruption or a failing queued task is logged and falls through.
     */
    static void flushSwingEventThread() {
        if (SwingUtilities.isEventDispatchThread()) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Interrupted while waiting for the Swing event thread");
        } catch (InvocationTargetException e) {
            logger.debug("A queued Swing task failed: {}", e.getCause() == null ? e : e.getCause());
        }
    }

    /**
     * Clear every object (and the TMA grid) from each entry's saved data and save it back.
     * Entries without saved data are skipped; an entry whose data file cannot be read is counted
     * as failed and left untouched, never overwritten with an empty one. QuPath deletes its
     * {@code data.qpdata.bkp} as soon as the new file is written, so there is no undo.
     *
     * @param entries the entries
     * @param progress progress callback
     * @param cancel polled before every entry
     * @return the outcome
     */
    public static Outcome clearData(List<ProjectImageEntry<BufferedImage>> entries, Progress progress,
                                    BooleanSupplier cancel) {
        long start = System.currentTimeMillis();
        logger.info("Project+ clear saved data started: {} entries, entry IDs: {}", entries.size(), ids(entries));
        List<Failure> failures = new ArrayList<>();
        int done = 0;
        int skipped = 0;
        boolean canceled = false;
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            if (cancel.getAsBoolean()) {
                canceled = true;
                break;
            }
            progress.update(done + skipped, entries.size(), entry.getImageName());
            if (!entry.hasImageData()) {
                skipped++;
                continue;
            }
            Failure failure = clearOne(entry);
            if (failure == null) {
                done++;
            } else {
                failures.add(failure);
            }
        }
        logger.info("Project+ clear saved data finished: done={}, skipped={}, failed={}, canceled={}, elapsed={} ms{}",
                done, skipped, failures.size(), canceled, System.currentTimeMillis() - start, failureList(failures));
        return new Outcome(entries.size(), done, skipped, 0, failures, canceled);
    }

    /**
     * Three phases with their own reasons: read the saved hierarchy (proves the data file is
     * readable), read the full image data (needs the image), then clear and save.
     *
     * @return null on success, else the failure
     */
    private static Failure clearOne(ProjectImageEntry<BufferedImage> entry) {
        String id = entry.getID();
        PathObjectHierarchy saved;
        try {
            saved = entry.readHierarchy();
        } catch (IOException | RuntimeException e) {
            logger.error("Could not read the data file of entry {}; not cleared: {}", id, e.getMessage(), e);
            return new Failure(id, entry.getImageName(), Strings.get("reason.clearDataUnreadable"));
        }
        if (saved.nObjects() == 0) {
            logger.debug("Entry {} has saved data with no objects; nothing to clear", id);
            return null;
        }
        ImageData<BufferedImage> imageData = null;
        try {
            try {
                imageData = entry.readImageData();
            } catch (IOException | RuntimeException e) {
                logger.error("Could not open the image of entry {}; not cleared: {}", id, e.getMessage(), e);
                return new Failure(id, entry.getImageName(), Strings.get("reason.clearImageUnreachable"));
            }
            if (imageData.getHierarchy().nObjects() != saved.nObjects()) {
                // QuPath answers a failed read with a fresh, empty ImageData (type UNSET) rather
                // than an exception; saving that would wipe the image type and stain settings.
                logger.error("Entry {} read back {} objects where the data file holds {}; not cleared",
                        id, imageData.getHierarchy().nObjects(), saved.nObjects());
                return new Failure(id, entry.getImageName(), Strings.get("reason.clearDataUnreadable"));
            }
            try {
                imageData.getHierarchy().clearAll();
                entry.saveImageData(imageData);
            } catch (IOException | RuntimeException e) {
                logger.error("Could not save the cleared data of entry {}: {}", id, e.getMessage(), e);
                return new Failure(id, entry.getImageName(), Strings.get("reason.clearSaveFailed"));
            }
            return null;
        } finally {
            if (imageData != null) {
                try {
                    imageData.close();
                } catch (Exception e) {
                    logger.warn("Error closing image data of entry {}: {}", id, e.getMessage());
                }
            }
        }
    }

    /**
     * The file name each entry would get, in order, without touching the disk.
     *
     * @param entries the entries
     * @param compress whether files are gzipped
     * @param masked whether QuPath masks image names (files are then named by entry ID)
     * @return entry ID to file name
     */
    public static Map<String, String> plannedFileNames(List<ProjectImageEntry<BufferedImage>> entries, boolean compress,
                                                       boolean masked) {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> taken = ExportNaming.newTakenSet();
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            names.put(entry.getID(), ExportNaming.fileName(entry.getImageName(), entry.getID(), compress, masked, taken));
        }
        return names;
    }

    /**
     * @param folder the output folder
     * @param fileNames planned file names
     * @return how many already exist in the folder
     */
    public static int countExisting(Path folder, Collection<String> fileNames) {
        int n = 0;
        for (String name : fileNames) {
            if (Files.exists(folder.resolve(name))) {
                n++;
            }
        }
        return n;
    }

    /**
     * Export one GeoJSON file per entry, each written through a temp file and moved into place.
     * Entries with no saved data and no live objects produce no file and count as skipped;
     * existing files follow the overwrite policy. When at least one file was written,
     * {@link ExportManifest#FILE_NAME} is written into the folder, replacing any earlier one.
     *
     * @param entries the entries
     * @param liveObjects objects snapshotted from open viewers, by entry ID; used instead of the
     *                    saved data for those entries
     * @param options export settings
     * @param progress progress callback
     * @param cancel polled before every entry
     * @return the outcome
     */
    public static Outcome exportGeoJson(List<ProjectImageEntry<BufferedImage>> entries,
                                        Map<String, Collection<PathObject>> liveObjects, ExportOptions options,
                                        Progress progress, BooleanSupplier cancel) {
        long start = System.currentTimeMillis();
        logger.info("Project+ export started: {} entries, folder: {}, compress: {}, prettyPrint: {}, "
                        + "excludeMeasurements: {}, existing files: {}, names masked: {}, entry IDs: {}",
                entries.size(), options.folder(), options.compress(), options.prettyPrint(),
                options.excludeMeasurements(), options.overwrite(), options.masked(), ids(entries));
        List<Failure> failures = new ArrayList<>();
        Map<String, String> names = plannedFileNames(entries, options.compress(), options.masked());
        List<PathIO.GeoJsonExportOptions> geo = new ArrayList<>();
        geo.add(PathIO.GeoJsonExportOptions.FEATURE_COLLECTION);
        if (options.prettyPrint()) {
            geo.add(PathIO.GeoJsonExportOptions.PRETTY_JSON);
        }
        if (options.excludeMeasurements()) {
            geo.add(PathIO.GeoJsonExportOptions.EXCLUDE_MEASUREMENTS);
        }
        PathIO.GeoJsonExportOptions[] geoArray = geo.toArray(new PathIO.GeoJsonExportOptions[0]);
        List<ExportManifest.FileRecord> written = new ArrayList<>();
        int done = 0;
        int skipped = 0;
        int skippedExisting = 0;
        boolean canceled = false;
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            if (cancel.getAsBoolean()) {
                canceled = true;
                break;
            }
            progress.update(done + skipped, entries.size(), entry.getImageName());
            Collection<PathObject> live = liveObjects == null ? null : liveObjects.get(entry.getID());
            if (live == null && !entry.hasImageData()) {
                skipped++;
                continue;
            }
            String fileName = names.get(entry.getID());
            Path target = options.folder().resolve(fileName);
            if (Files.exists(target) && options.overwrite() == OverwritePolicy.SKIP) {
                skipped++;
                skippedExisting++;
                continue;
            }
            Collection<PathObject> objects = live;
            if (objects == null) {
                try {
                    objects = entry.readHierarchy().getAllObjects(false);
                } catch (IOException | RuntimeException e) {
                    logger.error("Could not read the data file of entry {}: {}", entry.getID(), e.getMessage(), e);
                    failures.add(new Failure(entry.getID(), entry.getImageName(), Strings.get("reason.exportDataUnreadable")));
                    continue;
                }
            }
            try {
                writeGeoJson(target, objects, options.compress(), geoArray);
                done++;
                written.add(new ExportManifest.FileRecord(entry.getID(), fileName,
                        options.masked() ? null : entry.getImageName(),
                        live != null ? ExportManifest.SOURCE_LIVE : ExportManifest.SOURCE_SAVED, objects.size()));
            } catch (IOException | RuntimeException e) {
                logger.error("Could not write the export file of entry {}: {}", entry.getID(), e.getMessage(), e);
                failures.add(new Failure(entry.getID(), entry.getImageName(), exportWriteReason(e)));
            }
        }
        if (!written.isEmpty()) {
            ExportManifest manifest = new ExportManifest(GeneralTools.getVersion(), ExportManifest.currentExtensionVersion(),
                    ExportManifest.now(), options.folder().toString(), options.compress(), options.prettyPrint(),
                    options.excludeMeasurements(), options.masked(), written);
            try {
                manifest.write(options.folder());
            } catch (IOException | RuntimeException e) {
                logger.error("Could not write {} to {}: {}", ExportManifest.FILE_NAME, options.folder(), e.getMessage(), e);
                failures.add(new Failure("", ExportManifest.FILE_NAME, Strings.get("reason.manifestFailed")));
            }
        }
        logger.info("Project+ export finished: done={}, skipped={} (existing {}), failed={}, canceled={}, "
                        + "folder: {}, elapsed={} ms{}",
                done, skipped, skippedExisting, failures.size(), canceled, options.folder(),
                System.currentTimeMillis() - start, failureList(failures));
        return new Outcome(entries.size(), done, skipped, skippedExisting, failures, canceled);
    }

    /** Write to {@code <target>.tmp} in the same folder, then move it into place. */
    private static void writeGeoJson(Path target, Collection<PathObject> objects, boolean compress,
                                     PathIO.GeoJsonExportOptions[] geoArray) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            try (OutputStream raw = Files.newOutputStream(tmp);
                 OutputStream out = compress ? new GZIPOutputStream(new BufferedOutputStream(raw)) : raw) {
                PathIO.exportObjectsAsGeoJSON(out, objects, geoArray);
            }
            replace(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Move a fully written temp file over its target, atomically where the file system allows
     * and with a plain replace where it does not (network shares, FAT).
     *
     * @param tmp the complete temp file
     * @param target the file to replace
     * @throws IOException when neither move works
     */
    static void replace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String exportWriteReason(Exception e) {
        if (e instanceof AccessDeniedException || e instanceof FileSystemException) {
            return Strings.get("reason.exportLocked");
        }
        return Strings.get("reason.exportWriteFailed");
    }

    private static String ids(List<ProjectImageEntry<BufferedImage>> entries) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entries.get(i).getID());
        }
        return sb.append(']').toString();
    }

    /** @return {@code "; failures: [id: reason, ...]"} or an empty string */
    private static String failureList(List<Failure> failures) {
        if (failures.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("; failures: [");
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Failure f = failures.get(i);
            sb.append(f.entryId().isEmpty() ? f.name() : f.entryId()).append(": ").append(f.reason());
        }
        return sb.append(']').toString();
    }
}
