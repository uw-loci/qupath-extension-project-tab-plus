package qupath.ext.projecttabplus.model.names;

import java.util.Arrays;

/**
 * A name part-way through the rules: the working text, where each of its characters came from
 * in the full name, and which rule hid each character of the full name. Immutable.
 */
public final class Mapped {

    /** Marks a full-name character that no rule hid. */
    public static final int KEPT = -1;

    private final String full;
    private final String text;
    private final int[] origin;
    private final int[] removedBy;
    private final boolean noPiece;

    private Mapped(String full, String text, int[] origin, int[] removedBy, boolean noPiece) {
        this.full = full;
        this.text = text;
        this.origin = origin;
        this.removedBy = removedBy;
        this.noPiece = noPiece;
    }

    /**
     * @param full a full image name
     * @return the name before any rule
     */
    public static Mapped of(String full) {
        int n = full.length();
        int[] origin = new int[n];
        for (int i = 0; i < n; i++) {
            origin[i] = i;
        }
        int[] removed = new int[n];
        Arrays.fill(removed, KEPT);
        return new Mapped(full, full, origin, removed, false);
    }

    /** @return the full name */
    public String full() {
        return full;
    }

    /** @return the name as the rules so far leave it */
    public String text() {
        return text;
    }

    /**
     * @param fullIndex a character index in the full name
     * @return the index of the rule that hid it, or {@link #KEPT}
     */
    public int removedBy(int fullIndex) {
        return removedBy[fullIndex];
    }

    /** @return true when a Keep pieces rule found none of its pieces in this name */
    public boolean noPiece() {
        return noPiece;
    }

    /**
     * @param workingIndex a character index in {@link #text()}
     * @return its index in the full name
     */
    public int origin(int workingIndex) {
        return origin[workingIndex];
    }

    /**
     * Hide characters of the working text.
     *
     * @param drop one flag per working character; true hides it
     * @param ruleIndex the rule doing it, recorded against the full-name characters
     * @return the result, or this when nothing is dropped
     */
    Mapped drop(boolean[] drop, int ruleIndex) {
        int kept = 0;
        for (boolean d : drop) {
            if (!d) {
                kept++;
            }
        }
        if (kept == text.length()) {
            return this;
        }
        StringBuilder sb = new StringBuilder(kept);
        int[] newOrigin = new int[kept];
        int[] newRemoved = removedBy.clone();
        int j = 0;
        for (int i = 0; i < drop.length; i++) {
            if (drop[i]) {
                newRemoved[origin[i]] = ruleIndex;
            } else {
                sb.append(text.charAt(i));
                newOrigin[j++] = origin[i];
            }
        }
        return new Mapped(full, sb.toString(), newOrigin, newRemoved, noPiece);
    }

    /** @return a copy flagged as having none of a Keep pieces rule's pieces */
    Mapped withNoPiece() {
        return new Mapped(full, text, origin, removedBy, true);
    }

    /** @return a copy of the per-full-character rule indices */
    int[] removedByArray() {
        return removedBy.clone();
    }
}
