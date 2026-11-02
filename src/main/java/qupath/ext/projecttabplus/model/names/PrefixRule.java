package qupath.ext.projecttabplus.model.names;

/**
 * Hide a fixed start of the name. Capitals must match; a name that does not start with the
 * text is left as it is.
 *
 * @param prefix the text to hide
 * @param on whether the rule is on
 */
public record PrefixRule(String prefix, boolean on) implements NameRule {

    public static final String KIND = "prefix";

    public PrefixRule {
        prefix = prefix == null ? "" : prefix;
    }

    @Override
    public NameRule withOn(boolean on) {
        return new PrefixRule(prefix, on);
    }

    @Override
    public boolean finished() {
        return !prefix.isEmpty();
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public Mapped apply(Mapped in, int index) {
        String s = in.text();
        if (prefix.isEmpty() || !s.startsWith(prefix)) {
            return in;
        }
        boolean[] drop = new boolean[s.length()];
        for (int i = 0; i < prefix.length(); i++) {
            drop[i] = true;
        }
        return in.drop(drop, index);
    }
}
