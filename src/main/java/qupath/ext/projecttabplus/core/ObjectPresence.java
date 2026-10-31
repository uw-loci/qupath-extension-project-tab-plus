package qupath.ext.projecttabplus.core;

/**
 * What an image's saved data holds, as counts per object type.
 *
 * @param state UNKNOWN (no summary), NONE (saved, no objects) or PRESENT
 * @param annotations annotation count
 * @param detections detection count, excluding cells and tiles
 * @param cells cell count
 * @param tiles tile count
 * @param tmaCores TMA core count
 * @param imageType the saved image type name, or null
 * @param unsaved true when the counts come from a viewer with unsaved changes
 * @param unreadable true when a summary exists but could not be parsed
 */
public record ObjectPresence(State state, long annotations, long detections, long cells, long tiles,
                             long tmaCores, String imageType, boolean unsaved, boolean unreadable) {

    /** The three states an image can be in. */
    public enum State {
        UNKNOWN, NONE, PRESENT
    }

    /** No summary available. */
    public static final ObjectPresence UNKNOWN =
            new ObjectPresence(State.UNKNOWN, 0, 0, 0, 0, 0, null, false, false);

    /** A summary exists but could not be read. */
    public static final ObjectPresence UNREADABLE =
            new ObjectPresence(State.UNKNOWN, 0, 0, 0, 0, 0, null, false, true);

    /**
     * @param annotations annotation count
     * @param detections detection count
     * @param cells cell count
     * @param tiles tile count
     * @param tmaCores TMA core count
     * @param imageType image type name or null
     * @param unsaved whether the counts are live and unsaved
     * @return a presence whose state follows the counts
     */
    public static ObjectPresence of(long annotations, long detections, long cells, long tiles, long tmaCores,
                                    String imageType, boolean unsaved) {
        boolean any = annotations > 0 || detections > 0 || cells > 0 || tiles > 0 || tmaCores > 0;
        return new ObjectPresence(any ? State.PRESENT : State.NONE, annotations, detections, cells, tiles,
                tmaCores, imageType, unsaved, false);
    }

    /** @return true when annotations are present */
    public boolean hasAnnotations() {
        return annotations > 0;
    }

    /** @return true when detections, cells or tiles are present */
    public boolean hasDetections() {
        return detectionsLike() > 0;
    }

    /** @return true when TMA cores are present */
    public boolean hasTmaCores() {
        return tmaCores > 0;
    }

    /** @return detections + cells + tiles */
    public long detectionsLike() {
        return detections + cells + tiles;
    }

    /** @return true when the state is known (saved data with or without objects) */
    public boolean isKnown() {
        return state != State.UNKNOWN;
    }
}
