package qupath.ext.projecttabplus.model;

import java.util.Collections;
import java.util.List;

/**
 * A group node: every image under it shares {@code value} for the grouping level {@code key}.
 *
 * @param key the grouping level, or null for the hidden root
 * @param value the shared value (or the unassigned label)
 * @param path the group values from the root joined by {@link TreeBuilder#PATH_SEPARATOR}, outermost
 *             first, empty for the root; the key that carries expansion state across rebuilds
 * @param depth 0 for the root, 1 for its children
 * @param children child groups or images
 * @param imageCount the number of images under this group at any depth
 */
public record GroupRow(GroupingKey key, String value, String path, int depth, List<Row> children,
                       int imageCount) implements Row {

    public GroupRow {
        children = Collections.unmodifiableList(children);
    }

    /** @return true when this is the hidden root */
    public boolean isRoot() {
        return key == null;
    }
}
