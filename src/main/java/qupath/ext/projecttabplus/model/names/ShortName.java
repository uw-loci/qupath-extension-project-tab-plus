package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.List;

/**
 * One full name after the rules: what a row shows, and which rule hid each character.
 */
public final class ShortName {

    /**
     * A run of the full name, hidden or kept.
     *
     * @param start first character, inclusive
     * @param end last character, exclusive
     * @param ruleIndex the rule that hid it, or {@link Mapped#KEPT}
     */
    public record Run(int start, int end, int ruleIndex) {

        /** @return true when the run is hidden */
        public boolean hidden() {
            return ruleIndex != Mapped.KEPT;
        }
    }

    private final String full;
    private final String display;
    private final int[] removedBy;
    private final boolean emptyFallback;
    private final boolean noPiece;

    ShortName(Mapped m) {
        this.full = m.full();
        this.removedBy = m.removedByArray();
        this.noPiece = m.noPiece();
        this.emptyFallback = !full.isEmpty() && !identifies(m.text());
        this.display = emptyFallback ? full : m.text();
    }

    /**
     * @param text a short name
     * @return true when it has a letter or digit outside its file extension and QuPath notes,
     *     so it can tell one image from another; false for {@code ""}, {@code "_"} or {@code ".svs"}
     */
    public static boolean identifies(String text) {
        // A leading character, so a name that is only " (1)" or ".svs" still reads as a note or an extension.
        String t = "x" + text;
        String stem = t.substring(0, Math.max(1, NamePieces.notesStart(t)));
        int ext = HideExtensionRule.extensionStart(stem, true);
        if (ext > 0) {
            stem = stem.substring(0, ext);
        }
        stem = stem.substring(1);
        for (int i = 0; i < stem.length(); i++) {
            if (Character.isLetterOrDigit(stem.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param full a name no rule touched
     * @return its unchanged short name
     */
    public static ShortName unchanged(String full) {
        return new ShortName(Mapped.of(full));
    }

    /** @return the full name */
    public String full() {
        return full;
    }

    /** @return what the row shows: the short name, or the full name when the rules leave nothing that identifies it */
    public String display() {
        return display;
    }

    /** @return true when the rules would leave no letter or digit outside the extension, so the full name is shown */
    public boolean emptyFallback() {
        return emptyFallback;
    }

    /** @return true when a Keep pieces rule found none of its pieces in this name */
    public boolean noPiece() {
        return noPiece;
    }

    /** @return true when the shown name differs from the full name */
    public boolean changed() {
        return !display.equals(full);
    }

    /** @return true when the row keeps its full name for a reason worth a look */
    public boolean kept() {
        return emptyFallback || noPiece;
    }

    /**
     * @param ruleIndex a rule's position
     * @return true when that rule hid at least one character, even if the name fell back
     */
    public boolean hiddenBy(int ruleIndex) {
        for (int r : removedBy) {
            if (r == ruleIndex) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param fullIndex a character of the full name
     * @return the rule that hid it, or {@link Mapped#KEPT}; always KEPT when the name fell back
     */
    public int removedBy(int fullIndex) {
        return emptyFallback ? Mapped.KEPT : removedBy[fullIndex];
    }

    /** @return the full name as alternating kept and hidden runs; one kept run when it fell back */
    public List<Run> runs() {
        List<Run> out = new ArrayList<>();
        int n = full.length();
        int i = 0;
        while (i < n) {
            int r = removedBy(i);
            int j = i + 1;
            while (j < n && removedBy(j) == r) {
                j++;
            }
            out.add(new Run(i, j, r));
            i = j;
        }
        return out;
    }

    /** @return the hidden parts of the full name, in order */
    public List<String> hiddenParts() {
        List<String> out = new ArrayList<>();
        for (Run r : runs()) {
            if (r.hidden()) {
                out.add(full.substring(r.start(), r.end()));
            }
        }
        return out;
    }
}
