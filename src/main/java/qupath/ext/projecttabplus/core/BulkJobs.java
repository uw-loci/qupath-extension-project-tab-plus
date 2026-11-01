package qupath.ext.projecttabplus.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * first and reports afterwards.
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
     * @param name the image name
     * @param reason a short reason
     */
    public record Failure(String name, String reason) {
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
     */
    public record ExportOptions(Path folder, boolean compress, boolean prettyPrint, boolean excludeMeasurements,
                                OverwritePolicy overwrite) {
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
        List<Failure> failures = new ArrayList<>();
        int done = 0;
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
            } catch (RuntimeException e) {
                logger.error("Could not remove {}: {}", entry.getImageName(), e.getMessage(), e);
                failures.add(new Failure(entry.getImageName(), shortReason(e)));
            }
        }
        try {
            project.syncChanges();
        } catch (IOException e) {
            logger.error("Could not save the project after removing entries: {}", e.getMessage(), e);
            failures.add(new Failure(project.getName(), "project file could not be saved: " + shortReason(e)));
        }
        return new Outcome(entries.size(), done, 0, 0, failures, canceled);
    }

    /**
     * Clear every object (and the TMA grid) from each entry's saved data and save it back.
     * Entries without saved data are skipped. QuPath keeps the previous data file as
     * {@code data.qpdata.bkp} until the entry's next save.
     *
     * @param entries the entries
     * @param progress progress callback
     * @param cancel polled before every entry
     * @return the outcome
     */
    public static Outcome clearData(List<ProjectImageEntry<BufferedImage>> entries, Progress progress,
                                    BooleanSupplier cancel) {
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
            ImageData<BufferedImage> imageData = null;
            try {
                imageData = entry.readImageData();
                imageData.getHierarchy().clearAll();
                entry.saveImageData(imageData);
                done++;
            } catch (IOException | RuntimeException e) {
                logger.error("Could not clear saved data for {}: {}", entry.getImageName(), e.getMessage(), e);
                failures.add(new Failure(entry.getImageName(), "data file is unreadable (see log)"));
            } finally {
                if (imageData != null) {
                    try {
                        imageData.close();
                    } catch (Exception e) {
                        logger.warn("Error closing image data for {}: {}", entry.getImageName(), e.getMessage());
                    }
                }
            }
        }
        return new Outcome(entries.size(), done, skipped, 0, failures, canceled);
    }

    /**
     * The file name each entry would get, in order, without touching the disk.
     *
     * @param entries the entries
     * @param compress whether files are gzipped
     * @return entry ID to file name
     */
    public static Map<String, String> plannedFileNames(List<ProjectImageEntry<BufferedImage>> entries, boolean compress) {
        Map<String, String> names = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            names.put(entry.getID(), ExportNaming.fileName(entry.getImageName(), entry.getID(), compress, taken));
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
     * Export one GeoJSON file per entry. Entries with no saved data and no live objects produce
     * no file and count as skipped; existing files follow the overwrite policy.
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
        List<Failure> failures = new ArrayList<>();
        Map<String, String> names = plannedFileNames(entries, options.compress());
        List<PathIO.GeoJsonExportOptions> geo = new ArrayList<>();
        geo.add(PathIO.GeoJsonExportOptions.FEATURE_COLLECTION);
        if (options.prettyPrint()) {
            geo.add(PathIO.GeoJsonExportOptions.PRETTY_JSON);
        }
        if (options.excludeMeasurements()) {
            geo.add(PathIO.GeoJsonExportOptions.EXCLUDE_MEASUREMENTS);
        }
        PathIO.GeoJsonExportOptions[] geoArray = geo.toArray(new PathIO.GeoJsonExportOptions[0]);
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
            Path target = options.folder().resolve(names.get(entry.getID()));
            if (Files.exists(target) && options.overwrite() == OverwritePolicy.SKIP) {
                skipped++;
                skippedExisting++;
                continue;
            }
            try {
                Collection<PathObject> objects = live;
                if (objects == null) {
                    PathObjectHierarchy hierarchy = entry.readHierarchy();
                    objects = hierarchy.getAllObjects(false);
                }
                PathIO.exportObjectsAsGeoJSON(target, objects, geoArray);
                done++;
            } catch (IOException | RuntimeException e) {
                logger.error("Could not export {} to {}: {}", entry.getImageName(), target, e.getMessage(), e);
                failures.add(new Failure(entry.getImageName(), exportReason(e)));
            }
        }
        return new Outcome(entries.size(), done, skipped, skippedExisting, failures, canceled);
    }

    private static String exportReason(Exception e) {
        if (e instanceof java.nio.file.AccessDeniedException || e instanceof java.nio.file.FileSystemException) {
            return "output file is locked or not writable (see log)";
        }
        return "data file is unreadable (see log)";
    }

    private static String shortReason(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
