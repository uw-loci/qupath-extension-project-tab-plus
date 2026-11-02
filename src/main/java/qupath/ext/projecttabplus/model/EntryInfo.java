package qupath.ext.projecttabplus.model;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Immutable snapshot of what the tree needs from one project entry, taken off the FX thread.
 *
 * @param id the entry ID
 * @param name the image name (already masked when QuPath masks names)
 * @param displayName what the row shows: the short name while name rules are on, else {@code name}
 * @param metadata a copy of the entry metadata
 * @param fileName the file name derived from the entry URIs, with QuPath's own labels for the
 *                 remote, multiple and unreadable cases
 * @param imageType the image type read from the saved summary, or null when unknown
 * @param hasImageData whether the entry has saved data
 * @param fileMissing true when a local file URI points at nothing
 */
public record EntryInfo(String id, String name, String displayName, Map<String, String> metadata,
                        String fileName, String imageType, boolean hasImageData, boolean fileMissing) {

    /** Label the built-in tab uses for an entry whose URI cannot be read. */
    public static final String UNDEFINED = "Undefined";

    /** Label the built-in tab uses for an entry stored in more than one file. */
    public static final String MULTIPLE_URIS = "Multiple URIs";

    public EntryInfo {
        displayName = displayName == null ? name : displayName;
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * @param shortName the name to show instead of the full name
     * @return a copy showing that name
     */
    public EntryInfo withDisplayName(String shortName) {
        return new EntryInfo(id, name, shortName, metadata, fileName, imageType, hasImageData, fileMissing);
    }

    /**
     * @param entry the project entry
     * @param imageType the image type from the saved summary, or null
     * @param missingCheck says whether a URI points at a missing file, or null to skip the check;
     *                     may touch the disk, so call this off the FX thread
     * @return a snapshot of the entry
     */
    public static EntryInfo from(ProjectImageEntry<?> entry, String imageType, Predicate<URI> missingCheck) {
        String fileName;
        boolean missing = false;
        try {
            Collection<URI> uris = entry.getURIs();
            fileName = fileNameFromUris(uris);
            if (missingCheck != null) {
                for (URI uri : uris) {
                    if (missingCheck.test(uri)) {
                        missing = true;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            fileName = UNDEFINED;
        }
        String name = entry.getImageName();
        return new EntryInfo(entry.getID(), name, name, entry.getMetadata(), fileName,
                imageType, entry.hasImageData(), missing);
    }

    /**
     * The file-name label the built-in tab shows for a set of URIs.
     *
     * @param uris the entry URIs
     * @return the last path segment, {@code [remote] host/path}, {@link #MULTIPLE_URIS} or
     *         {@link #UNDEFINED}
     */
    public static String fileNameFromUris(Collection<URI> uris) {
        if (uris == null || uris.isEmpty()) {
            return UNDEFINED;
        }
        if (uris.size() > 1) {
            return MULTIPLE_URIS;
        }
        URI uri = uris.iterator().next();
        String path = uri.getPath();
        if (path == null) {
            return UNDEFINED;
        }
        if (uri.getAuthority() != null) {
            return "[remote] " + uri.getAuthority() + path;
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
