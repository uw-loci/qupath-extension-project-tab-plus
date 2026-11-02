package qupath.ext.projecttabplus.model.names;

/**
 * Hide every non-overlapping occurrence of a literal text, left to right. No pattern
 * language: a period or a bracket is just that character.
 *
 * @param text the text to hide
 * @param ignoreCase whether capitals may differ
 * @param on whether the rule is on
 */
public record HideTextRule(String text, boolean ignoreCase, boolean on) implements NameRule {

    public static final String KIND = "hideText";

    public HideTextRule {
        text = text == null ? "" : text;
    }

    @Override
    public NameRule withOn(boolean on) {
        return new HideTextRule(text, ignoreCase, on);
    }

    @Override
    public boolean finished() {
        return !text.isEmpty();
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * @param s a name
     * @return true when the text occurs in it
     */
    public boolean occursIn(String s) {
        return indexIn(s, 0) >= 0;
    }

    private int indexIn(String s, int from) {
        int n = text.length();
        for (int i = from; i + n <= s.length(); i++) {
            if (s.regionMatches(ignoreCase, i, text, 0, n)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Mapped apply(Mapped in, int index) {
        if (text.isEmpty()) {
            return in;
        }
        String s = in.text();
        boolean[] drop = null;
        int i = indexIn(s, 0);
        while (i >= 0) {
            if (drop == null) {
                drop = new boolean[s.length()];
            }
            for (int k = i; k < i + text.length(); k++) {
                drop[k] = true;
            }
            i = indexIn(s, i + text.length());
        }
        return drop == null ? in : in.drop(drop, index);
    }
}
