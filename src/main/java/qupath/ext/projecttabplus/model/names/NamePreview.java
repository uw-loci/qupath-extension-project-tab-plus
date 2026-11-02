package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the rules window shows for one state of the working rules: the project report,
 * and for the selected rule the names as they reach it and, for a Keep pieces rule, how those
 * names split. Pure; computed on the tree thread.
 */
public final class NamePreview {

    private final NameRuleSet.Report report;
    private final int selected;
    private final List<String> inputs;
    private final int[] pieceCounts;
    private final List<Map<String, Integer>> positions;
    private final Map<String, Integer> lastTokens;
    private final int sampleIndex;
    private final boolean separatorPresent;
    private final String leftover;
    private final int leftoverCount;
    private final String leftoverExample;

    private NamePreview(NameRuleSet set, List<String> names, int selected) {
        this.report = set.applyAll(names);
        this.selected = selected >= 0 && selected < set.rules().size() ? selected : -1;
        List<String> in = new ArrayList<>();
        int[] counts = new int[0];
        List<Map<String, Integer>> pos = new ArrayList<>();
        Map<String, Integer> last = new HashMap<>();
        int sample = -1;
        boolean sepPresent = false;
        String left = null;
        int leftCount = 0;
        String leftExample = null;
        if (this.selected >= 0) {
            NameRule rule = set.rules().get(this.selected);
            for (String n : names) {
                in.add(set.applyBefore(n, this.selected).text());
            }
            if (rule instanceof KeepPiecesRule k && !k.separator().isEmpty()) {
                counts = new int[in.size()];
                int most = -1;
                for (int i = 0; i < in.size(); i++) {
                    String s = in.get(i);
                    List<int[]> ranges = KeepPiecesRule.split(s, k.separator());
                    counts[i] = ranges.size();
                    sepPresent |= ranges.size() > 1;
                    if (ranges.size() > most) {
                        most = ranges.size();
                        sample = i;
                    }
                    for (int p = 0; p < ranges.size(); p++) {
                        while (pos.size() <= p) {
                            pos.add(new HashMap<>());
                        }
                        int[] r = ranges.get(p);
                        pos.get(p).merge(s.substring(r[0], r[1]), 1, Integer::sum);
                    }
                    int[] r = ranges.get(ranges.size() - 1);
                    last.merge(s.substring(r[0], r[1]), 1, Integer::sum);
                }
            } else if (rule instanceof HideExtensionRule e) {
                Map<String, Integer> found = new LinkedHashMap<>();
                Map<String, String> example = new HashMap<>();
                for (int i = 0; i < in.size(); i++) {
                    String s = in.get(i);
                    if (HideExtensionRule.extensionStart(s, e.doubleExtension()) >= 0) {
                        continue;
                    }
                    String l = HideExtensionRule.leftoverExtension(s);
                    if (l != null) {
                        found.merge(l, 1, Integer::sum);
                        example.putIfAbsent(l, names.get(i));
                    }
                }
                for (Map.Entry<String, Integer> f : found.entrySet()) {
                    if (f.getValue() > leftCount) {
                        left = f.getKey();
                        leftCount = f.getValue();
                        leftExample = example.get(left);
                    }
                }
            }
        }
        this.inputs = Collections.unmodifiableList(in);
        this.pieceCounts = counts;
        this.positions = pos;
        this.lastTokens = last;
        this.sampleIndex = sample;
        this.separatorPresent = sepPresent;
        this.leftover = left;
        this.leftoverCount = leftCount;
        this.leftoverExample = leftExample;
    }

    /**
     * @param set the working rules, plus any ticked suggestions at the end
     * @param names every full name, in project order
     * @param selected the selected rule, or -1
     * @return the preview
     */
    public static NamePreview compute(NameRuleSet set, List<String> names, int selected) {
        return new NamePreview(set, names, selected);
    }

    /** @return the short names and counts */
    public NameRuleSet.Report report() {
        return report;
    }

    /** @return the selected rule, or -1 */
    public int selected() {
        return selected;
    }

    /** @return the names as they reach the selected rule; empty when none is selected */
    public List<String> inputs() {
        return inputs;
    }

    /**
     * @param row a name's position
     * @return how many pieces it has at the selected Keep pieces rule, or 0
     */
    public int pieceCount(int row) {
        return row >= 0 && row < pieceCounts.length ? pieceCounts[row] : 0;
    }

    /** @return the fewest pieces any name has, or 0 */
    public int minPieces() {
        int min = Integer.MAX_VALUE;
        for (int c : pieceCounts) {
            min = Math.min(min, c);
        }
        return pieceCounts.length == 0 ? 0 : min;
    }

    /** @return the most pieces any name has, or 0 */
    public int maxPieces() {
        int max = 0;
        for (int c : pieceCounts) {
            max = Math.max(max, c);
        }
        return max;
    }

    /**
     * @param threshold a piece count
     * @return how many names have fewer pieces
     */
    public int fewerThan(int threshold) {
        int n = 0;
        for (int c : pieceCounts) {
            if (c < threshold) {
                n++;
            }
        }
        return n;
    }

    /** @return the first name with the most pieces, or -1 */
    public int sampleIndex() {
        return sampleIndex;
    }

    /** @return true when the separator splits at least one name */
    public boolean separatorPresent() {
        return separatorPresent;
    }

    /**
     * @param piece a piece position, 0-based
     * @return each text at that position and how many names have it, most common first
     */
    public List<Map.Entry<String, Integer>> tokensAt(int piece) {
        return sorted(piece >= 0 && piece < positions.size() ? positions.get(piece) : Map.of());
    }

    /** @return each last piece and how many names end with it, most common first */
    public List<Map.Entry<String, Integer>> lastTokens() {
        return sorted(lastTokens);
    }

    /**
     * A position holds different things when some of its texts contain a letter and some do
     * not, as where {@code biref} sits in one name and the counter {@code 002} in the next.
     *
     * @param piece a piece position, 0-based
     * @return true when that position mixes words and numbers
     */
    public boolean varies(int piece) {
        boolean letter = false;
        boolean noLetter = false;
        for (Map.Entry<String, Integer> e : tokensAt(piece)) {
            if (e.getKey().chars().anyMatch(Character::isLetter)) {
                letter = true;
            } else {
                noLetter = true;
            }
        }
        return letter && noLetter;
    }

    /** @return the extension most often left before a note, such as {@code .svs}, or null */
    public String leftover() {
        return leftover;
    }

    /** @return how many names keep {@link #leftover()} */
    public int leftoverCount() {
        return leftoverCount;
    }

    /** @return a full name that keeps {@link #leftover()}, or null */
    public String leftoverExample() {
        return leftoverExample;
    }

    private static List<Map.Entry<String, Integer>> sorted(Map<String, Integer> m) {
        List<Map.Entry<String, Integer>> out = new ArrayList<>(m.entrySet());
        out.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        return out;
    }
}
