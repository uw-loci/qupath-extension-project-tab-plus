package qupath.ext.projecttabplus.core;

import java.util.Locale;
import java.util.Set;
import qupath.lib.common.GeneralTools;

/**
 * File names for the GeoJSON export: the image name made safe for every platform, a collision
 * suffix carrying the entry ID, and the Windows reserved names avoided.
 */
public final class ExportNaming {

    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    private static final String FALLBACK_STEM = "image";

    private ExportNaming() {
    }

    /**
     * @param compress whether the file is gzipped
     * @return {@code .geojson} or {@code .geojson.gz}
     */
    public static String extension(boolean compress) {
        return compress ? ".geojson.gz" : ".geojson";
    }

    /**
     * @param imageName the image name as shown (masked when QuPath masks names)
     * @return the name with invalid characters stripped, trimmed, never blank, never a Windows
     *         reserved name
     */
    public static String safeStem(String imageName) {
        String stem = imageName == null ? "" : GeneralTools.stripInvalidFilenameChars(imageName).trim();
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
     * @param imageName the image name
     * @param entryId the entry ID, appended as {@code " (<id>)"} on a collision
     * @param compress whether the file is gzipped
     * @param taken the names already chosen in this export; updated
     * @return the file name
     */
    public static String fileName(String imageName, String entryId, boolean compress, Set<String> taken) {
        String stem = safeStem(imageName);
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
