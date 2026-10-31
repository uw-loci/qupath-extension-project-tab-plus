package qupath.ext.projecttabplus.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A named display setup: grouping levels, expansion, fields under the name, thumbnail size and
 * indicator style. Mutable so the live state can be edited; {@link #copy()} snapshots it.
 */
public final class DisplayProfile {

    /** How much of the tree opens when the project loads or the grouping changes. */
    public enum ExpandOnLoad {
        ALL("all"), FIRST("first"), NONE("none");

        private final String token;

        ExpandOnLoad(String token) {
            this.token = token;
        }

        /** @return the stored form */
        public String token() {
            return token;
        }

        static ExpandOnLoad parse(String s, ExpandOnLoad fallback) {
            if (s == null) {
                return fallback;
            }
            for (ExpandOnLoad v : values()) {
                if (v.token.equalsIgnoreCase(s) || v.name().equalsIgnoreCase(s)) {
                    return v;
                }
            }
            return fallback;
        }
    }

    /** Thumbnail slot size; the same values as the built-in tab. */
    public enum ThumbnailSize {
        HIDDEN(20, 20), SMALL(50, 40), MEDIUM(100, 80), LARGE(150, 120);

        private final double width;
        private final double height;

        ThumbnailSize(double width, double height) {
            this.width = width;
            this.height = height;
        }

        /** @return slot width in pixels */
        public double width() {
            return width;
        }

        /** @return slot height in pixels */
        public double height() {
            return height;
        }

        static ThumbnailSize parse(String s, ThumbnailSize fallback) {
            if (s == null) {
                return fallback;
            }
            for (ThumbnailSize v : values()) {
                if (v.name().equalsIgnoreCase(s)) {
                    return v;
                }
            }
            return fallback;
        }
    }

    /** How saved-data presence is drawn. */
    public enum IndicatorStyle {
        EDGES, BADGES, OFF;

        static IndicatorStyle parse(String s, IndicatorStyle fallback) {
            if (s == null) {
                return fallback;
            }
            for (IndicatorStyle v : values()) {
                if (v.name().equalsIgnoreCase(s)) {
                    return v;
                }
            }
            return fallback;
        }
    }

    private String name;
    private final List<String> grouping = new ArrayList<>();
    private ExpandOnLoad expandOnLoad = ExpandOnLoad.FIRST;
    private final List<String> fields = new ArrayList<>();
    private ThumbnailSize thumbnailSize = ThumbnailSize.SMALL;
    private IndicatorStyle indicators = IndicatorStyle.EDGES;

    /** @param name the profile name */
    public DisplayProfile(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /** @return a deep copy */
    public DisplayProfile copy() {
        DisplayProfile p = new DisplayProfile(name);
        p.grouping.addAll(grouping);
        p.expandOnLoad = expandOnLoad;
        p.fields.addAll(fields);
        p.thumbnailSize = thumbnailSize;
        p.indicators = indicators;
        return p;
    }

    /**
     * Copy every setting except the name from another profile.
     *
     * @param other the source
     */
    public void copySettingsFrom(DisplayProfile other) {
        grouping.clear();
        grouping.addAll(other.grouping);
        expandOnLoad = other.expandOnLoad;
        fields.clear();
        fields.addAll(other.fields);
        thumbnailSize = other.thumbnailSize;
        indicators = other.indicators;
    }

    /** @return the name */
    public String getName() {
        return name;
    }

    /** @param name the new name */
    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /** @return the stored grouping levels, outermost first */
    public List<String> getGrouping() {
        return Collections.unmodifiableList(grouping);
    }

    /** @param levels the stored grouping levels */
    public void setGrouping(List<String> levels) {
        grouping.clear();
        grouping.addAll(levels);
    }

    /** @return the grouping levels as a spec */
    public GroupingSpec groupingSpec() {
        return GroupingSpec.parse(grouping);
    }

    /** @param spec the grouping levels */
    public void setGroupingSpec(GroupingSpec spec) {
        setGrouping(spec.serialized());
    }

    /** @return the expansion rule */
    public ExpandOnLoad getExpandOnLoad() {
        return expandOnLoad;
    }

    /** @param expandOnLoad the expansion rule */
    public void setExpandOnLoad(ExpandOnLoad expandOnLoad) {
        this.expandOnLoad = Objects.requireNonNull(expandOnLoad);
    }

    /** @return the stored field keys shown under the image name */
    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    /** @param fields the stored field keys */
    public void setFields(List<String> fields) {
        this.fields.clear();
        this.fields.addAll(fields);
    }

    /** @return the fields as keys */
    public List<GroupingKey> fieldKeys() {
        return fields.stream().filter(f -> f != null && !f.isBlank()).map(GroupingKey::parse).toList();
    }

    /** @return the thumbnail size */
    public ThumbnailSize getThumbnailSize() {
        return thumbnailSize;
    }

    /** @param thumbnailSize the thumbnail size */
    public void setThumbnailSize(ThumbnailSize thumbnailSize) {
        this.thumbnailSize = Objects.requireNonNull(thumbnailSize);
    }

    /** @return the indicator style */
    public IndicatorStyle getIndicators() {
        return indicators;
    }

    /** @param indicators the indicator style */
    public void setIndicators(IndicatorStyle indicators) {
        this.indicators = Objects.requireNonNull(indicators);
    }

    /**
     * @param projectKeys the metadata keys the current project has
     * @return the metadata keys this profile groups or displays by that the project lacks
     */
    public List<String> missingMetadataKeys(Set<String> projectKeys) {
        List<String> missing = new ArrayList<>();
        for (String s : grouping) {
            GroupingKey k = GroupingKey.parse(s);
            if (!k.isBuiltin() && !projectKeys.contains(k.metadataKey()) && !missing.contains(k.metadataKey())) {
                missing.add(k.metadataKey());
            }
        }
        for (String s : fields) {
            GroupingKey k = GroupingKey.parse(s);
            if (!k.isBuiltin() && !projectKeys.contains(k.metadataKey()) && !missing.contains(k.metadataKey())) {
                missing.add(k.metadataKey());
            }
        }
        return missing;
    }

    /** @return true when every setting except the name matches */
    public boolean sameSettingsAs(DisplayProfile other) {
        return other != null
                && grouping.equals(other.grouping)
                && expandOnLoad == other.expandOnLoad
                && fields.equals(other.fields)
                && thumbnailSize == other.thumbnailSize
                && indicators == other.indicators;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DisplayProfile other)) {
            return false;
        }
        return name.equals(other.name) && sameSettingsAs(other);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, grouping, expandOnLoad, fields, thumbnailSize, indicators);
    }

    @Override
    public String toString() {
        return name;
    }
}
