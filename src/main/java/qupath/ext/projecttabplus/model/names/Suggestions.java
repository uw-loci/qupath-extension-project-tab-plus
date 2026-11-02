package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rules proposed from a project's names: the shared start, text repeated in at least half the
 * names (such as {@code _20x}), the file extension, and an extension left before QuPath's notes
 * (as in {@code .svs (rotated 270)} or {@code .ome.tif (1)}). Pure; the names passed in are the names as the current rules
 * leave them.
 */
public final class Suggestions {

    /**
     * Share of names a "most names" prefix must reach. High, because a wrong prefix hides the
     * patient number of the commonest patient.
     */
    public static final double MIN_SHARE = 0.8;

    /**
     * Share of names a repeated text must reach. Lower than {@link #MIN_SHARE}: a suggestion is
     * an opt-in tick, and one more slide image without the text must not drop it.
     */
    public static final double TEXT_SHARE = 0.5;

    /** Shortest prefix worth proposing. */
    public static final int MIN_PREFIX = 3;

    /** Most repeated-text rows proposed. */
    public static final int MAX_TEXT = 3;

    /** Most extension endings listed in the extension row. */
    public static final int MAX_TAILS = 3;

    private static final String BOUNDARIES = "_- ";
    private static final String TOKEN_SEPARATORS = "_ ";

    /** What a suggestion proposes. */
    public enum Kind {
        PREFIX, TEXT, EXTENSION, LEFTOVER
    }

    /**
     * One proposed rule.
     *
     * @param kind which card row it is
     * @param rule the rule, on
     * @param reach how many names it changes
     * @param total how many names were looked at
     * @param detail the prefix or text; the endings list for EXTENSION; the leftover for LEFTOVER
     * @param example a sample for LEFTOVER (such as {@code .svs (rotated 270)}), else null
     */
    public record Suggestion(Kind kind, NameRule rule, int reach, int total, String detail, String example) {
    }

    /**
     * A proposed start of the name.
     *
     * @param prefix the text
     * @param count names that start with it
     * @param total names looked at
     */
    public record Prefix(String prefix, int count, int total) {

        /** @return true when every name starts with it */
        public boolean all() {
            return count == total;
        }
    }

    private Suggestions() {
    }

    /**
     * @param names the names as the current rules leave them
     * @return the proposals, in the order they should be added to the list
     */
    public static List<Suggestion> suggest(List<String> names) {
        List<Suggestion> out = new ArrayList<>();
        int total = names.size();
        if (total == 0) {
            return out;
        }
        Prefix prefix = prefix(names);
        if (prefix != null) {
            out.add(new Suggestion(Kind.PREFIX, new PrefixRule(prefix.prefix(), true), prefix.count(), total,
                    prefix.prefix(), null));
        }
        out.addAll(repeatedText(names, prefix == null ? null : prefix.prefix()));

        Set<String> tails = new LinkedHashSet<>();
        boolean anyDouble = false;
        int extReach = 0;
        for (String n : names) {
            int start = HideExtensionRule.extensionStart(n, true);
            if (start >= 0) {
                extReach++;
                String tail = n.substring(start);
                tails.add(tail);
                if (tail.indexOf('.', 1) > 0) {
                    anyDouble = true;
                }
            }
        }
        if (extReach > 0) {
            List<String> listed = new ArrayList<>(tails).subList(0, Math.min(MAX_TAILS, tails.size()));
            out.add(new Suggestion(Kind.EXTENSION, new HideExtensionRule(anyDouble, true), extReach, total,
                    String.join(", ", listed), null));
        }

        Map<String, Integer> leftovers = new HashMap<>();
        Map<String, String> examples = new HashMap<>();
        for (String n : names) {
            String left = HideExtensionRule.leftoverExtension(n, true);
            if (left != null) {
                leftovers.merge(left, 1, Integer::sum);
                examples.putIfAbsent(left, n.substring(HideExtensionRule.leftoverStart(n, true)));
            }
        }
        leftovers.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> out.add(new Suggestion(Kind.LEFTOVER, new HideTextRule(e.getKey(), false, true),
                        e.getValue(), total, e.getKey(), examples.get(e.getKey()))));
        return out;
    }

    /**
     * The start to hide: the longest start every name shares, cut back to the last {@code _},
     * {@code -} or space so {@code _1} and {@code _10} do not leave a stray digit; when that is
     * shorter than {@value #MIN_PREFIX} characters, the longest such start shared by at least
     * {@link #MIN_SHARE} of the names.
     *
     * @param names the names
     * @return the proposal, or null when there are fewer than two names or nothing is shared
     */
    public static Prefix prefix(List<String> names) {
        int total = names.size();
        if (total < 2) {
            return null;
        }
        String lcp = names.get(0);
        for (String n : names) {
            int k = 0;
            int max = Math.min(lcp.length(), n.length());
            while (k < max && lcp.charAt(k) == n.charAt(k)) {
                k++;
            }
            lcp = lcp.substring(0, k);
            if (lcp.isEmpty()) {
                break;
            }
        }
        String trimmed = trimToBoundary(lcp);
        if (trimmed.length() >= MIN_PREFIX) {
            return new Prefix(trimmed, total, total);
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String n : names) {
            for (int i = 0; i < n.length() - 1; i++) {
                if (BOUNDARIES.indexOf(n.charAt(i)) >= 0) {
                    counts.merge(n.substring(0, i + 1), 1, Integer::sum);
                }
            }
        }
        int need = (int) Math.ceil(MIN_SHARE * total);
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String c = e.getKey();
            int n = e.getValue();
            if (n < need || n < 2 || c.length() < MIN_PREFIX) {
                continue;
            }
            if (best == null || c.length() > best.length() || c.length() == best.length() && c.compareTo(best) < 0) {
                best = c;
                bestCount = n;
            }
        }
        return best == null ? null : new Prefix(best, bestCount, total);
    }

    /** Cut back to the last boundary character, keeping it; never the whole of a name. */
    private static String trimToBoundary(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (BOUNDARIES.indexOf(s.charAt(i)) >= 0) {
                return s.substring(0, i + 1);
            }
        }
        return "";
    }

    /**
     * Pieces split at {@code _} and space, with the separator in front, that contain a letter,
     * sit outside the prefix, the extension and any QuPath notes after it, and appear in at least
     * {@link #TEXT_SHARE} of names.
     */
    private static List<Suggestion> repeatedText(List<String> names, String prefix) {
        int total = names.size();
        Map<String, Integer> counts = new HashMap<>();
        for (String n : names) {
            String s = n;
            if (prefix != null && s.startsWith(prefix)) {
                s = s.substring(prefix.length());
            }
            int ext = HideExtensionRule.extensionStart(s, true);
            if (ext < 0) {
                ext = HideExtensionRule.leftoverStart(s, true);
            }
            if (ext >= 0) {
                s = s.substring(0, ext);
            }
            Set<String> seen = new HashSet<>();
            int i = 0;
            while (i < s.length()) {
                if (TOKEN_SEPARATORS.indexOf(s.charAt(i)) < 0) {
                    i++;
                    continue;
                }
                int j = i + 1;
                while (j < s.length() && TOKEN_SEPARATORS.indexOf(s.charAt(j)) < 0) {
                    j++;
                }
                String token = s.substring(i, j);
                // A token that is the whole remaining name would empty it.
                if (j > i + 1 && hasLetter(token) && token.length() < s.length()) {
                    seen.add(token);
                }
                i = j;
            }
            for (String t : seen) {
                counts.merge(t, 1, Integer::sum);
            }
        }
        int need = (int) Math.ceil(TEXT_SHARE * total);
        List<Suggestion> out = new ArrayList<>();
        counts.entrySet().stream()
                .filter(e -> e.getValue() >= need && e.getValue() >= 2)
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TEXT)
                .forEach(e -> out.add(new Suggestion(Kind.TEXT, new HideTextRule(e.getKey(), false, true),
                        e.getValue(), total, e.getKey(), null)));
        return out;
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
