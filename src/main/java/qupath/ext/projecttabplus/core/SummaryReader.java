package qupath.ext.projecttabplus.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Reads QuPath's per-entry {@code summary.json} into an {@link ObjectPresence}, cached by file
 * modification time. Never opens the data file itself. Call it off the FX thread.
 */
public final class SummaryReader {

    private static final Logger logger = LoggerFactory.getLogger(SummaryReader.class);

    /** The summary file name QuPath writes next to the data file. */
    public static final String SUMMARY_FILE = "summary.json";

    private record Cached(long mtime, ObjectPresence presence) {
    }

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    /**
     * @param entry a project entry
     * @return the saved summary, cached until the file changes
     */
    public ObjectPresence get(ProjectImageEntry<?> entry) {
        Path dir = entry.getEntryPath();
        if (dir == null) {
            return ObjectPresence.UNKNOWN;
        }
        Path file = dir.resolve(SUMMARY_FILE);
        long mtime;
        try {
            mtime = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException e) {
            mtime = -1L;
        }
        Cached c = cache.get(entry.getID());
        if (c != null && c.mtime == mtime) {
            return c.presence;
        }
        ObjectPresence p = read(file);
        cache.put(entry.getID(), new Cached(mtime, p));
        return p;
    }

    /**
     * @param entryId an entry ID
     * @return the cached presence, or null when never read
     */
    public ObjectPresence peek(String entryId) {
        Cached c = cache.get(entryId);
        return c == null ? null : c.presence;
    }

    /** @param entryId the entry whose cache to drop */
    public void invalidate(String entryId) {
        cache.remove(entryId);
    }

    /** Drop every cached summary. */
    public void clear() {
        cache.clear();
    }

    /**
     * @param file a {@code summary.json}
     * @return the presence; UNKNOWN when the file is missing, UNREADABLE when it cannot be parsed
     */
    public static ObjectPresence read(Path file) {
        if (file == null || !Files.exists(file)) {
            return ObjectPresence.UNKNOWN;
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read saved-data summary {}: {}", file, e.getMessage());
            return ObjectPresence.UNREADABLE;
        }
    }

    /**
     * @param json the summary contents
     * @return the presence
     * @throws IllegalArgumentException when the JSON is not a summary
     */
    public static ObjectPresence parse(String json) {
        JsonElement rootEl = JsonParser.parseString(json);
        if (!rootEl.isJsonObject()) {
            throw new IllegalArgumentException("summary root is not an object");
        }
        JsonObject root = rootEl.getAsJsonObject();
        String imageType = null;
        JsonElement typeEl = root.get("imageType");
        if (typeEl != null && typeEl.isJsonPrimitive()) {
            imageType = displayImageType(typeEl.getAsString());
        }
        JsonElement hierEl = root.get("hierarchy");
        if (hierEl == null || !hierEl.isJsonObject()) {
            return ObjectPresence.of(0, 0, 0, 0, 0, imageType, false);
        }
        JsonObject hier = hierEl.getAsJsonObject();
        JsonElement countsEl = hier.get("objectTypeCounts");
        long annotations = 0;
        long detections = 0;
        long cells = 0;
        long tiles = 0;
        long tma = 0;
        if (countsEl != null && countsEl.isJsonObject()) {
            JsonObject counts = countsEl.getAsJsonObject();
            annotations = count(counts, "Annotations");
            detections = count(counts, "Detections");
            cells = count(counts, "Cells");
            tiles = count(counts, "Tiles");
            tma = count(counts, "TMA cores");
        }
        return ObjectPresence.of(annotations, detections, cells, tiles, tma, imageType, false);
    }

    private static long count(JsonObject counts, String key) {
        JsonElement el = counts.get(key);
        if (el == null || !el.isJsonPrimitive()) {
            return 0;
        }
        try {
            return el.getAsLong();
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * @param stored the enum name QuPath stores
     * @return QuPath's display text for the type, or the stored text when it is not a known type
     */
    public static String displayImageType(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return ImageData.ImageType.valueOf(stored).toString();
        } catch (IllegalArgumentException e) {
            return stored;
        }
    }

    /**
     * Count the live hierarchy of an open image.
     *
     * @param hierarchy the hierarchy
     * @param imageType the image type, or null
     * @param unsaved whether the image has unsaved changes
     * @return the presence
     */
    public static ObjectPresence fromHierarchy(PathObjectHierarchy hierarchy, ImageData.ImageType imageType,
                                               boolean unsaved) {
        long annotations = 0;
        long detections = 0;
        long cells = 0;
        long tiles = 0;
        long tma = 0;
        for (PathObject o : hierarchy.getAllObjects(false)) {
            if (o.isAnnotation()) {
                annotations++;
            } else if (o.isTMACore()) {
                tma++;
            } else if (o.isCell()) {
                cells++;
            } else if (o.isTile()) {
                tiles++;
            } else if (o.isDetection()) {
                detections++;
            }
        }
        return ObjectPresence.of(annotations, detections, cells, tiles, tma,
                imageType == null ? null : imageType.toString(), unsaved);
    }
}
