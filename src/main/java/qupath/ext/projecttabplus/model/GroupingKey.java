package qupath.ext.projecttabplus.model;

import java.util.Objects;
import qupath.ext.projecttabplus.ui.Strings;

/**
 * One grouping level or display field: a metadata key or a built-in field.
 */
public final class GroupingKey {

    /** Label for images that have no value for a level's key; matches the built-in tab. */
    public static final String UNASSIGNED = "(Unassigned)";

    private static final String BUILTIN_PREFIX = "builtin:";

    /** The built-in fields that need no metadata. */
    public enum Builtin {
        IMAGE_NAME("imageName", "level.builtin.imageName", true),
        FILE_NAME("fileName", "level.builtin.fileName", true),
        ENTRY_ID("entryId", "level.builtin.entryId", false),
        IMAGE_TYPE("imageType", "level.builtin.imageType", false);

        private final String token;
        private final String labelKey;
        private final boolean nameLevel;

        Builtin(String token, String labelKey, boolean nameLevel) {
            this.token = token;
            this.labelKey = labelKey;
            this.nameLevel = nameLevel;
        }

        /** @return the serialized form, e.g. {@code builtin:imageName} */
        public String serialized() {
            return BUILTIN_PREFIX + token;
        }

        /** @return the user-facing label */
        public String displayName() {
            return Strings.get(labelKey);
        }

        /** @return true when the field would reveal a masked image name */
        public boolean isNameLevel() {
            return nameLevel;
        }

        static Builtin fromToken(String token) {
            for (Builtin b : values()) {
                if (b.token.equalsIgnoreCase(token)) {
                    return b;
                }
            }
            return null;
        }
    }

    private final Builtin builtin;
    private final String metadataKey;

    private GroupingKey(Builtin builtin, String metadataKey) {
        this.builtin = builtin;
        this.metadataKey = metadataKey;
    }

    /**
     * @param key a project metadata key
     * @return a level grouping by that key
     */
    public static GroupingKey metadata(String key) {
        return new GroupingKey(null, Objects.requireNonNull(key));
    }

    /**
     * @param builtin a built-in field
     * @return a level grouping by that field
     */
    public static GroupingKey builtin(Builtin builtin) {
        return new GroupingKey(Objects.requireNonNull(builtin), null);
    }

    /**
     * Parse the serialized form: {@code builtin:<token>} or a raw metadata key. A
     * {@code builtin:} prefix with an unknown token is kept as a metadata key so nothing is lost.
     *
     * @param serialized the stored string
     * @return the key
     */
    public static GroupingKey parse(String serialized) {
        if (serialized.startsWith(BUILTIN_PREFIX)) {
            Builtin b = Builtin.fromToken(serialized.substring(BUILTIN_PREFIX.length()));
            if (b != null) {
                return builtin(b);
            }
        }
        return metadata(serialized);
    }

    /**
     * Map the built-in tab's project metadata {@code _SORT_KEY} value to a key.
     *
     * @param sortKey the stored value: {@code SORT_KEY[IMAGE_NAME|ENTRY_ID|URI]} or a metadata key
     * @return the key, or null when the value is null or blank
     */
    public static GroupingKey fromSortKey(String sortKey) {
        if (sortKey == null || sortKey.isBlank()) {
            return null;
        }
        return switch (sortKey) {
            case "SORT_KEY[IMAGE_NAME]" -> builtin(Builtin.IMAGE_NAME);
            case "SORT_KEY[ENTRY_ID]" -> builtin(Builtin.ENTRY_ID);
            case "SORT_KEY[URI]" -> builtin(Builtin.FILE_NAME);
            default -> metadata(sortKey);
        };
    }

    /** @return true for a built-in field */
    public boolean isBuiltin() {
        return builtin != null;
    }

    /** @return the built-in field, or null for a metadata key */
    public Builtin builtin() {
        return builtin;
    }

    /** @return the metadata key, or null for a built-in field */
    public String metadataKey() {
        return metadataKey;
    }

    /** @return true when the level would reveal a masked image name */
    public boolean isNameLevel() {
        return builtin != null && builtin.isNameLevel();
    }

    /** @return the stored form */
    public String serialized() {
        return builtin != null ? builtin.serialized() : metadataKey;
    }

    /** @return the user-facing label */
    public String displayName() {
        return builtin != null ? builtin.displayName() : metadataKey;
    }

    /**
     * @param info an entry snapshot
     * @return the entry's value for this level, or {@link #UNASSIGNED}; the image name is the
     *         shown (possibly short) name
     */
    public String valueOf(EntryInfo info) {
        if (builtin == null) {
            String v = info.metadata().get(metadataKey);
            return v == null || v.isBlank() ? UNASSIGNED : v;
        }
        return switch (builtin) {
            case IMAGE_NAME -> info.displayName();
            case FILE_NAME -> info.fileName();
            case ENTRY_ID -> info.id();
            case IMAGE_TYPE -> info.imageType() == null ? UNASSIGNED : info.imageType();
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GroupingKey other)) {
            return false;
        }
        return builtin == other.builtin && Objects.equals(metadataKey, other.metadataKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(builtin, metadataKey);
    }

    @Override
    public String toString() {
        return serialized();
    }
}
