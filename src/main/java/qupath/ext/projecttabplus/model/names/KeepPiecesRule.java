package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Keep only chosen pieces of a name split at a separator. Pieces are numbered from 0 here
 * and from 1 in the dialog; trailing QuPath notes are not pieces and always stay. A name with fewer pieces keeps the chosen pieces it has; a name
 * with none of them is left as it is and flagged.
 *
 * @param separator the literal separator, one or more characters
 * @param kept piece indices to keep, 0-based
 * @param pieces how many piece chips the editor showed (the sample's piece count)
 * @param keepLast keep the last piece of every name, whatever its number
 * @param keepLater keep pieces past the last chip, so a longer name keeps its tail
 * @param on whether the rule is on
 */
public record KeepPiecesRule(String separator, SortedSet<Integer> kept, int pieces, boolean keepLast,
                             boolean keepLater, boolean on) implements NameRule {

    public static final String KIND = "keepPieces";

    public KeepPiecesRule {
        separator = separator == null ? "" : separator;
        kept = Collections.unmodifiableSortedSet(new TreeSet<>(kept == null ? Set.of() : kept));
        pieces = Math.max(0, pieces);
    }

    @Override
    public NameRule withOn(boolean on) {
        return new KeepPiecesRule(separator, kept, pieces, keepLast, keepLater, on);
    }

    @Override
    public boolean finished() {
        return !separator.isEmpty() && (!kept.isEmpty() || keepLast || keepLater);
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * Split a name into pieces. Trailing QuPath notes such as {@code " (1)"} or
     * {@code " (rotated 270)"} are not pieces: they are always kept, so a duplicate keeps the
     * note that tells it apart.
     *
     * @param s a name
     * @param sep a non-empty separator
     * @return each piece as {@code {start, end}}, empty pieces included
     */
    public static List<int[]> split(String s, String sep) {
        List<int[]> out = new ArrayList<>();
        int stemEnd = NamePieces.notesStart(s);
        int start = 0;
        int i = s.indexOf(sep);
        while (i >= 0 && i + sep.length() <= stemEnd) {
            out.add(new int[] {start, i});
            start = i + sep.length();
            i = s.indexOf(sep, start);
        }
        out.add(new int[] {start, stemEnd});
        return out;
    }

    /**
     * @param i a piece index, 0-based
     * @param n the name's piece count
     * @return true when this rule keeps that piece
     */
    public boolean keeps(int i, int n) {
        return kept.contains(i) || keepLast && i == n - 1 || keepLater && i >= pieces;
    }

    @Override
    public Mapped apply(Mapped in, int index) {
        if (separator.isEmpty()) {
            return in;
        }
        String s = in.text();
        List<int[]> ranges = split(s, separator);
        int n = ranges.size();
        boolean[] keep = new boolean[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            keep[i] = keeps(i, n);
            any |= keep[i];
        }
        if (!any) {
            return in.withNoPiece();
        }
        boolean[] drop = new boolean[s.length()];
        int stemEnd = ranges.get(n - 1)[1];
        java.util.Arrays.fill(drop, 0, stemEnd, true);
        boolean first = true;
        for (int i = 0; i < n; i++) {
            if (!keep[i]) {
                continue;
            }
            int[] r = ranges.get(i);
            if (!first) {
                for (int k = r[0] - separator.length(); k < r[0]; k++) {
                    drop[k] = false;
                }
            }
            for (int k = r[0]; k < r[1]; k++) {
                drop[k] = false;
            }
            first = false;
        }
        return in.drop(drop, index);
    }
}
