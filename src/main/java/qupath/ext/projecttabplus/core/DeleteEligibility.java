package qupath.ext.projecttabplus.core;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether the Remove dialog may offer to move an image file to the trash. Only a
 * single local file that no other entry shares, in a format that keeps everything in that one
 * file, is eligible; everything else carries a reason for the dialog to print.
 *
 * @param reason why the file is or is not eligible
 * @param path the resolved file, or null when there is none
 * @param extension the lower-case extension including the dot, or empty
 * @param sharedCount how many other entries use the same file (for SHARED_URI)
 */
public record DeleteEligibility(Reason reason, Path path, String extension, int sharedCount) {

    /** The verdicts. */
    public enum Reason {
        ELIGIBLE, NO_URI, MULTIPLE_URIS, NOT_LOCAL, NETWORK, NOT_FOUND, COMPANION_FORMAT, SHARED_URI
    }

    /** Formats stored as exactly one file. */
    static final Set<String> SINGLE_FILE_EXTENSIONS = Set.of(
            ".svs", ".ndpi", ".scn", ".bif", ".qptiff", ".tif", ".tiff", ".jpg", ".jpeg", ".png");

    /** @return true when the file may be offered */
    public boolean isEligible() {
        return reason == Reason.ELIGIBLE;
    }

    /**
     * @param uris the entry URIs
     * @param otherEntriesWithSameUri how many other entries in the project use the same URI
     * @return the verdict
     */
    public static DeleteEligibility check(Collection<URI> uris, int otherEntriesWithSameUri) {
        if (uris == null || uris.isEmpty()) {
            return new DeleteEligibility(Reason.NO_URI, null, "", 0);
        }
        if (uris.size() > 1) {
            return new DeleteEligibility(Reason.MULTIPLE_URIS, null, "", 0);
        }
        URI uri = uris.iterator().next();
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            return new DeleteEligibility(Reason.NOT_LOCAL, null, "", 0);
        }
        if (isNetworkLocation(uri)) {
            return new DeleteEligibility(Reason.NETWORK, null, "", 0);
        }
        Path path;
        try {
            path = Path.of(uri);
        } catch (RuntimeException e) {
            return new DeleteEligibility(Reason.NOT_LOCAL, null, "", 0);
        }
        if (isUncPath(path)) {
            return new DeleteEligibility(Reason.NETWORK, path, "", 0);
        }
        String ext = extensionOf(path.getFileName().toString());
        if (!Files.isRegularFile(path)) {
            return new DeleteEligibility(Reason.NOT_FOUND, path, ext, 0);
        }
        if (!SINGLE_FILE_EXTENSIONS.contains(ext) || hasCompanions(path, ext)) {
            return new DeleteEligibility(Reason.COMPANION_FORMAT, path, ext, 0);
        }
        if (otherEntriesWithSameUri > 0) {
            return new DeleteEligibility(Reason.SHARED_URI, path, ext, otherEntriesWithSameUri);
        }
        return new DeleteEligibility(Reason.ELIGIBLE, path, ext, 0);
    }

    /**
     * @param uris the entry URIs
     * @param allOtherUris the URIs of every other entry in the project, flattened
     * @return the verdict
     */
    public static DeleteEligibility check(Collection<URI> uris, List<URI> allOtherUris) {
        int shared = 0;
        if (uris != null && uris.size() == 1) {
            URI mine = uris.iterator().next();
            for (URI other : allOtherUris) {
                if (mine.equals(other)) {
                    shared++;
                }
            }
        }
        return check(uris, shared);
    }

    /**
     * A {@code file:} URI with a host part ({@code file://server/share/x.svs}) names a network
     * location, which has no Recycle Bin: Windows deletes such a file for good instead.
     *
     * @param uri a {@code file:} URI
     * @return true when the URI carries an authority
     */
    static boolean isNetworkLocation(URI uri) {
        String authority = uri.getAuthority();
        return authority != null && !authority.isEmpty();
    }

    /**
     * @param path a resolved path
     * @return true when its string form is a UNC path ({@code \\server\share\...}); a mapped
     *         drive letter is not detected
     */
    static boolean isUncPath(Path path) {
        return path.toString().startsWith("\\\\");
    }

    /**
     * A {@code .tif} next to a {@code *.companion.ome} file, or an {@code .ome.tif} next to
     * other {@code .ome.tif} files, is part of a set QuPath does not list.
     */
    static boolean hasCompanions(Path path, String ext) {
        if (!ext.equals(".tif") && !ext.equals(".tiff")) {
            return false;
        }
        Path dir = path.getParent();
        if (dir == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean omeTiff = name.endsWith(".ome.tif") || name.endsWith(".ome.tiff");
        try (DirectoryStream<Path> siblings = Files.newDirectoryStream(dir)) {
            for (Path sibling : siblings) {
                if (sibling.equals(path)) {
                    continue;
                }
                String s = sibling.getFileName().toString().toLowerCase(Locale.ROOT);
                if (s.endsWith(".companion.ome")) {
                    return true;
                }
                if (omeTiff && (s.endsWith(".ome.tif") || s.endsWith(".ome.tiff"))) {
                    return true;
                }
            }
        } catch (IOException e) {
            return true;
        }
        return false;
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
