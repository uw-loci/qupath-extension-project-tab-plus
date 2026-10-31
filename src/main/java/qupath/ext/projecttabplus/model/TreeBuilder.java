package qupath.ext.projecttabplus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Builds the grouped tree from a list of images, one bucket pass per level. Pure and
 * thread-agnostic; call it off the FX thread.
 */
public final class TreeBuilder {

    /** Separator inside a group path; never shown. */
    public static final char PATH_SEPARATOR = '\u001f';

    /**
     * The result of a build.
     *
     * @param root the hidden root
     * @param pathById entry ID to the path of the group holding it
     * @param shown images that passed the filter
     * @param total images given to the builder
     */
    public record BuiltTree(GroupRow root, Map<String, String> pathById, int shown, int total) {

        public BuiltTree {
            pathById = Collections.unmodifiableMap(pathById);
        }
    }

    private TreeBuilder() {
    }

    /**
     * @param images every image, in project order
     * @param spec the grouping levels
     * @param filter which images to keep; null keeps all
     * @return the tree, with empty groups dropped
     */
    public static BuiltTree build(List<ImageRow> images, GroupingSpec spec, Predicate<ImageRow> filter) {
        List<ImageRow> kept = new ArrayList<>(images.size());
        for (ImageRow row : images) {
            if (filter == null || filter.test(row)) {
                kept.add(row);
            }
        }
        Map<String, String> pathById = new HashMap<>();
        GroupRow root = group(kept, spec.levels(), 0, "", pathById);
        return new BuiltTree(root, pathById, kept.size(), images.size());
    }

    /**
     * Count the groups each level would create: the answer to "did I just make 400 groups of
     * one image?".
     *
     * @param images every image
     * @param spec the grouping levels
     * @return one count per level, outermost first
     */
    public static List<Integer> groupCounts(List<ImageRow> images, GroupingSpec spec) {
        List<Integer> counts = new ArrayList<>();
        List<List<ImageRow>> buckets = List.of(images);
        for (GroupingKey key : spec.levels()) {
            List<List<ImageRow>> next = new ArrayList<>();
            for (List<ImageRow> bucket : buckets) {
                next.addAll(bucket(bucket, key).values());
            }
            counts.add(next.size());
            buckets = next;
        }
        return counts;
    }

    private static GroupRow group(List<ImageRow> images, List<GroupingKey> levels, int depth,
                                  String path, Map<String, String> pathById) {
        GroupingKey key = depth == 0 ? null : levels.get(depth - 1);
        String value = depth == 0 ? "" : path.substring(path.lastIndexOf(PATH_SEPARATOR) + 1);
        List<Row> children = new ArrayList<>();
        if (depth >= levels.size()) {
            for (ImageRow row : images) {
                children.add(row);
                pathById.put(row.id(), path);
            }
        } else {
            GroupingKey level = levels.get(depth);
            for (Map.Entry<String, List<ImageRow>> bucket : bucket(images, level).entrySet()) {
                String childPath = path.isEmpty() && depth == 0
                        ? bucket.getKey()
                        : path + PATH_SEPARATOR + bucket.getKey();
                children.add(group(bucket.getValue(), levels, depth + 1, childPath, pathById));
            }
        }
        return new GroupRow(key, value, path, depth, children, images.size());
    }

    /** Bucket by value, sorted case-insensitively with the unassigned group last. */
    private static Map<String, List<ImageRow>> bucket(List<ImageRow> images, GroupingKey key) {
        Map<String, List<ImageRow>> buckets = new HashMap<>();
        for (ImageRow row : images) {
            buckets.computeIfAbsent(key.valueOf(row.info()), v -> new ArrayList<>()).add(row);
        }
        List<String> values = new ArrayList<>(buckets.keySet());
        values.sort((a, b) -> {
            boolean ua = GroupingKey.UNASSIGNED.equals(a);
            boolean ub = GroupingKey.UNASSIGNED.equals(b);
            if (ua != ub) {
                return ua ? 1 : -1;
            }
            int c = a.compareToIgnoreCase(b);
            return c != 0 ? c : a.compareTo(b);
        });
        Map<String, List<ImageRow>> sorted = new LinkedHashMap<>();
        for (String v : values) {
            sorted.put(v, buckets.get(v));
        }
        return sorted;
    }
}
