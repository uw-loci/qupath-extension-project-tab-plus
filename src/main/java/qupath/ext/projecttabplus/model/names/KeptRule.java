package qupath.ext.projecttabplus.model.names;

/**
 * A stored or pasted rule this build cannot read: a kind from a newer Project+, or a rule
 * whose fields do not parse. It changes no name, cannot be edited or turned on or off, and is
 * written back exactly as it was read, so a newer version's rules survive a save here.
 *
 * @param json the rule's JSON text, as read
 * @param label the rule's kind for display: ASCII, at most {@value #MAX_LABEL} characters, "?" when it has none
 * @param newer true when the kind is unknown to this build, false when a known kind could not be read
 */
public record KeptRule(String json, String label, boolean newer) implements NameRule {

    /** Most characters of a kind name shown or logged. */
    public static final int MAX_LABEL = 24;

    public KeptRule {
        json = json == null ? "null" : json;
        label = sanitize(label);
    }

    /**
     * @param text any text, such as a kind name taken from the clipboard
     * @return the text with every character outside printable ASCII replaced by '?', cut to
     *         {@value #MAX_LABEL} characters; "?" for null or empty
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "?";
        }
        StringBuilder sb = new StringBuilder(Math.min(text.length(), MAX_LABEL + 3));
        for (int i = 0; i < text.length() && sb.length() < MAX_LABEL; i++) {
            char c = text.charAt(i);
            sb.append(c >= 0x20 && c < 0x7F ? c : '?');
        }
        if (text.length() > MAX_LABEL) {
            sb.append("...");
        }
        return sb.toString();
    }

    /** @return false: a kept rule is never applied */
    @Override
    public boolean on() {
        return false;
    }

    /** @return this rule: a kept rule cannot be turned on or off */
    @Override
    public NameRule withOn(boolean on) {
        return this;
    }

    /** @return true, so a kept rule never blocks Apply */
    @Override
    public boolean finished() {
        return true;
    }

    @Override
    public String kind() {
        return label;
    }

    @Override
    public Mapped apply(Mapped in, int index) {
        return in;
    }
}
