package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The rules the table's piece menu offers for a click on one character of a name. Each offer
 * is built from the name as the rules above its insertion point leave it, so its text, its
 * count and its effect match what the new rule will do. Pure; safe on any thread.
 */
public final class PieceOffers {

    /** Characters that may sit between two pieces in a match, as in {@code _20x} or {@code  (1)}. */
    private static final String SEPARATORS = NamePieces.SEPARATORS;

    /**
     * One proposed change.
     *
     * @param at where the rule goes, or the rule it replaces
     * @param insert true to insert at {@code at}, false to replace the rule there
     * @param rule the new or changed rule
     * @param text the text it hides, for a Hide text or Hide the start offer; else null
     * @param piece the piece number it hides, 0-based, for a Keep pieces offer; else -1
     * @param count how many names it changes
     * @param cutsInto how many names it cuts into the middle of a longer piece
     * @param newlyShared how many images it leaves with a short name another image also shows,
     *     that had one of their own before
     */
    public record Offer(int at, boolean insert, NameRule rule, String text, int piece, int count, int cutsInto,
                        int newlyShared) {
    }

    private PieceOffers() {
    }

    /**
     * "Hide this text anywhere": the clicked piece of the short name, with the separator in
     * front (or behind, for the first piece) so no doubled separator is left. The rule goes at
     * the end, so it runs on the short name.
     *
     * @param set the working rules
     * @param names every full name in the project
     * @param full the clicked name
     * @param fullIndex the clicked character of the full name
     * @return the offer, or null when the character is hidden or is the final extension
     */
    public static Offer hideText(NameRuleSet set, List<String> names, String full, int fullIndex) {
        int at = set.rules().size();
        Mapped in = set.applyBefore(full, at);
        String s = in.text();
        NamePieces.Piece piece = pieceAt(s, workingIndex(in, fullIndex));
        if (piece == null || piece.kind() == NamePieces.Kind.EXTENSION && piece.end() == s.length()) {
            return null;
        }
        String text;
        if (piece.start() > 0 && SEPARATORS.indexOf(s.charAt(piece.start() - 1)) >= 0) {
            text = s.substring(piece.start() - 1, piece.end());
        } else if (piece.start() == 0 && piece.end() < s.length() && SEPARATORS.indexOf(s.charAt(piece.end())) >= 0) {
            text = s.substring(0, piece.end() + 1);
        } else {
            text = piece.text(s);
        }
        HideTextRule rule = new HideTextRule(text, false, true);
        int count = 0;
        int cuts = 0;
        for (String n : names) {
            String t = set.applyBefore(n, at).text();
            if (rule.occursIn(t)) {
                count++;
                if (cutsIntoPiece(t, text)) {
                    cuts++;
                }
            }
        }
        return new Offer(at, true, rule, text, -1, count, cuts, newlyShared(set, inserted(set, at, rule), names));
    }

    /**
     * "Hide the start through this piece": replaces the first Hide the start rule, or goes
     * first, and is built from the name as the rules above that position leave it.
     *
     * @return the offer, or null when the character is hidden there or is not in a word piece
     */
    public static Offer hideStart(NameRuleSet set, List<String> names, String full, int fullIndex) {
        int at = 0;
        boolean insert = true;
        boolean on = true;
        for (int i = 0; i < set.rules().size(); i++) {
            if (set.rules().get(i) instanceof PrefixRule p) {
                at = i;
                insert = false;
                on = p.on();
                break;
            }
        }
        Mapped in = set.applyBefore(full, at);
        String s = in.text();
        NamePieces.Piece piece = pieceAt(s, workingIndex(in, fullIndex));
        if (piece == null || piece.kind() != NamePieces.Kind.WORD) {
            return null;
        }
        int end = piece.end() < s.length() && SEPARATORS.indexOf(s.charAt(piece.end())) >= 0 ? piece.end() + 1 : piece.end();
        String prefix = s.substring(0, end);
        PrefixRule rule = new PrefixRule(prefix, on);
        int count = 0;
        for (String n : names) {
            if (set.applyBefore(n, at).text().startsWith(prefix)) {
                count++;
            }
        }
        NameRuleSet after = insert ? inserted(set, at, rule) : replaced(set, at, rule);
        return new Offer(at, insert, rule, prefix, -1, count, 0, newlyShared(set, after, names));
    }

    /**
     * "Hide piece N in every name": unticks the piece in the first Keep pieces rule, or adds
     * one at the end, with pieces counted as the rules above it leave the name.
     *
     * @return the offer, or null when the character is hidden there or the name has one piece
     */
    public static Offer hidePiece(NameRuleSet set, List<String> names, String full, int fullIndex) {
        List<NameRule> rules = set.rules();
        int at = rules.size();
        String sep = "_";
        KeepPiecesRule existing = null;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i) instanceof KeepPiecesRule k && !k.separator().isEmpty()) {
                at = i;
                sep = k.separator();
                existing = k;
                break;
            }
        }
        Mapped in = set.applyBefore(full, at);
        int k = workingIndex(in, fullIndex);
        if (k < 0) {
            return null;
        }
        List<int[]> ranges = KeepPiecesRule.split(in.text(), sep);
        int piece = -1;
        for (int p = 0; p < ranges.size(); p++) {
            if (k >= ranges.get(p)[0] && k < ranges.get(p)[1]) {
                piece = p;
                break;
            }
        }
        if (piece < 0 || ranges.size() < 2) {
            return null;
        }
        KeepPiecesRule rule;
        if (existing != null) {
            rule = existing.withPieceHidden(piece);
        } else {
            int n = ranges.size();
            SortedSet<Integer> kept = new TreeSet<>();
            for (int i = 0; i < n; i++) {
                if (i != piece) {
                    kept.add(i);
                }
            }
            boolean last = piece != n - 1;
            rule = new KeepPiecesRule(sep, kept, n, last, last, true);
        }
        NameRuleSet after = existing != null ? replaced(set, at, rule) : inserted(set, at, rule);
        int count = 0;
        for (String n : names) {
            if (!set.apply(n).display().equals(after.apply(n).display())) {
                count++;
            }
        }
        return new Offer(at, existing == null, rule, null, piece, count, 0, newlyShared(set, after, names));
    }

    /**
     * @param before the rules now
     * @param after the rules with an offer taken
     * @param names every full name
     * @return how many images show a shared short name after, that showed their own before
     */
    public static int newlyShared(NameRuleSet before, NameRuleSet after, List<String> names) {
        NameRuleSet.Report b = before.applyAll(names);
        NameRuleSet.Report a = after.applyAll(names);
        int n = 0;
        for (int i = 0; i < names.size(); i++) {
            if (a.sharedCount(a.results().get(i).display()) > 1 && b.sharedCount(b.results().get(i).display()) == 1) {
                n++;
            }
        }
        return n;
    }

    /**
     * @param s a name
     * @param text a text to hide
     * @return true when some occurrence of the text starts or ends inside a piece of the name,
     *     as {@code 1_} does in {@code 27471_90.0}
     */
    public static boolean cutsIntoPiece(String s, String text) {
        if (text.isEmpty()) {
            return false;
        }
        Set<Integer> starts = new HashSet<>();
        Set<Integer> ends = new HashSet<>();
        for (NamePieces.Piece p : NamePieces.of(s)) {
            starts.add(p.start());
            ends.add(p.end());
        }
        int i = s.indexOf(text);
        while (i >= 0) {
            int j = i + text.length();
            boolean left = starts.contains(i) || starts.contains(i + 1) && isSeparator(s.charAt(i));
            boolean right = ends.contains(j) || ends.contains(j - 1) && isSeparator(s.charAt(j - 1));
            if (!left || !right) {
                return true;
            }
            i = s.indexOf(text, j);
        }
        return false;
    }

    private static boolean isSeparator(char c) {
        return SEPARATORS.indexOf(c) >= 0;
    }

    /** @return the working-text index of a full-name character, or -1 when a rule hid it */
    private static int workingIndex(Mapped in, int fullIndex) {
        for (int j = 0; j < in.text().length(); j++) {
            if (in.origin(j) == fullIndex) {
                return j;
            }
        }
        return -1;
    }

    /** A click on a separator takes the piece after it, or the one before at the end. */
    private static NamePieces.Piece pieceAt(String s, int index) {
        if (index < 0) {
            return null;
        }
        NamePieces.Piece before = null;
        for (NamePieces.Piece p : NamePieces.of(s)) {
            if (index >= p.start() && index < p.end() || p.start() > index) {
                return p;
            }
            before = p;
        }
        return before;
    }

    private static NameRuleSet inserted(NameRuleSet set, int at, NameRule rule) {
        List<NameRule> rules = new ArrayList<>(set.rules());
        rules.add(Math.min(at, rules.size()), rule);
        return set.withRules(rules);
    }

    private static NameRuleSet replaced(NameRuleSet set, int at, NameRule rule) {
        List<NameRule> rules = new ArrayList<>(set.rules());
        rules.set(at, rule);
        return set.withRules(rules);
    }
}
