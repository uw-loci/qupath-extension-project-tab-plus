package qupath.ext.projecttabplus.core;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import qupath.lib.common.GeneralTools;

/**
 * File names for the GeoJSON export: the image name made safe for every platform and capped
 * in length, a collision suffix carrying the entry ID, and the Windows reserved names avoided.
 * While QuPath masks image names the stem is the entry ID, which is stable across sessions
 * (a masked name is a new random UUID every time the project is opened).
 */
public final class ExportNaming {

    /** Longest stem written, before the collision suffix and the extension. */
    public static final int MAX_STEM_LENGTH = 150;

    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private static final String FALLBACK_STEM = "image";

    private ExportNaming() {
    }

    /**
     * @return an empty taken-name set that compares names case-insensitively, because Windows
     *         and macOS file systems do
     */
    public static Set<String> newTakenSet() {
        return new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    /**
     * @param compress whether the file is gzipped
     * @return {@code .geojson} or {@code .geojson.gz}
     */
    public static String extension(boolean compress) {
        return compress ? ".geojson.gz" : ".geojson";
    }

    /**
     * @param imageName the image name as shown
     * @return the name with invalid characters stripped, trimmed, never blank, never a Windows
     *         reserved name, at most {@link #MAX_STEM_LENGTH} characters
     */
    public static String safeStem(String imageName) {
        String stem = imageName == null ? "" : GeneralTools.stripInvalidFilenameChars(imageName).trim();
        if (stem.length() > MAX_STEM_LENGTH) {
            stem = stem.substring(0, MAX_STEM_LENGTH).trim();
        }
        while (stem.endsWith(".")) {
            stem = stem.substring(0, stem.length() - 1).trim();
        }
        if (stem.isEmpty()) {
            stem = FALLBACK_STEM;
        }
        String head = stem;
        int dot = stem.indexOf('.');
        if (dot > 0) {
            head = stem.substring(0, dot);
        }
        if (WINDOWS_RESERVED.contains(head.toLowerCase(Locale.ROOT))) {
            stem = FALLBACK_STEM + " " + stem;
        }
        return stem;
    }

    /**
     * Pick a file name that is not yet in {@code taken}, and add it.
     *
     * @param imageName the image name; ignored when {@code masked}
     * @param entryId the entry ID: the stem when {@code masked}, else appended as
     *                {@code " (<id>)"} on a collision
     * @param compress whether the file is gzipped
     * @param masked whether QuPath masks image names
     * @param taken the names already chosen in this export, from {@link #newTakenSet()}; updated
     * @return the file name
     */
    public static String fileName(String imageName, String entryId, boolean compress, boolean masked,
                                  Set<String> taken) {
        String stem = masked ? safeStem(entryId) : safeStem(imageName);
        String ext = extension(compress);
        String candidate = stem + ext;
        if (taken.contains(candidate)) {
            candidate = stem + " (" + entryId + ")" + ext;
            int n = 2;
            while (taken.contains(candidate)) {
                candidate = stem + " (" + entryId + ")" + " " + n + ext;
                n++;
            }
        }
        taken.add(candidate);
        return candidate;
    }
}
