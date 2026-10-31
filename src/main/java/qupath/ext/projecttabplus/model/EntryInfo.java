package qupath.ext.projecttabplus.model;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Immutable snapshot of what the tree needs from one project entry, taken off the FX thread.
 *
 * @param id the entry ID
 * @param name the image name (already masked when QuPath masks names)
 * @param metadata a copy of the entry metadata
 * @param fileName the file name derived from the entry URIs, with QuPath's own labels for the
 *                 remote, multiple and unreadable cases
 * @param imageType the image type read from the saved summary, or null when unknown
 * @param hasImageData whether the entry has saved data
 */
public record EntryInfo(String id, String name, Map<String, String> metadata, String fileName,
                        String imageType, boolean hasImageData) {

    /** Label the built-in tab uses for an entry whose URI cannot be read. */
    public static final String UNDEFINED = "Undefined";

    /** Label the built-in tab uses for an entry stored in more than one file. */
    public static final String MULTIPLE_URIS = "Multiple URIs";

    public EntryInfo {
        metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * @param entry the project entry
     * @param imageType the image type from the saved summary, or null
     * @return a snapshot of the entry
     */
    public static EntryInfo from(ProjectImageEntry<?> entry, String imageType) {
        String fileName;
        try {
            fileName = fileNameFromUris(entry.getURIs());
        } catch (IOException e) {
            fileName = UNDEFINED;
        }
        return new EntryInfo(entry.getID(), entry.getImageName(), entry.getMetadata(), fileName,
                imageType, entry.hasImageData());
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
