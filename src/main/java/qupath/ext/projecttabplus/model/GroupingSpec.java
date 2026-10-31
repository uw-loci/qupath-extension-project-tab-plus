package qupath.ext.projecttabplus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The ordered grouping levels, outermost first.
 *
 * @param levels the levels; empty means a flat list
 */
public record GroupingSpec(List<GroupingKey> levels) {

    /** A flat list. */
    public static final GroupingSpec FLAT = new GroupingSpec(List.of());

    public GroupingSpec {
        levels = Collections.unmodifiableList(new ArrayList<>(levels));
    }

    /**
     * @param sortKey the built-in tab's {@code _SORT_KEY} project metadata value
     * @return a one-level spec, or {@link #FLAT}
     */
    public static GroupingSpec fromSortKey(String sortKey) {
        GroupingKey key = GroupingKey.fromSortKey(sortKey);
        return key == null ? FLAT : new GroupingSpec(List.of(key));
    }

    /**
     * @param serialized stored level strings
     * @return the spec
     */
    public static GroupingSpec parse(List<String> serialized) {
        List<GroupingKey> keys = new ArrayList<>();
        for (String s : serialized) {
            if (s != null && !s.isBlank()) {
                keys.add(GroupingKey.parse(s));
            }
        }
        return new GroupingSpec(keys);
    }

    /** @return the stored form of each level */
    public List<String> serialized() {
        return levels.stream().map(GroupingKey::serialized).toList();
    }

    /**
     * @param namesMasked whether QuPath is masking image names
     * @return this spec without the name levels when names are masked
     */
    public GroupingSpec effective(boolean namesMasked) {
        if (!namesMasked) {
            return this;
        }
        return new GroupingSpec(levels.stream().filter(k -> !k.isNameLevel()).toList());
    }

    /** @return true when there are no levels */
    public boolean isFlat() {
        return levels.isEmpty();
    }
}
