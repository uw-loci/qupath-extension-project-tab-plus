package qupath.ext.projecttabplus.model;

import java.awt.image.BufferedImage;
import qupath.lib.projects.ProjectImageEntry;

/**
 * An image leaf.
 *
 * @param info the entry snapshot the tree was built from
 * @param entry the live project entry (null only in unit tests)
 */
public record ImageRow(EntryInfo info, ProjectImageEntry<BufferedImage> entry) implements Row {

    /** @return the entry ID */
    public String id() {
        return info.id();
    }
}
