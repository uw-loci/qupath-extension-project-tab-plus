package qupath.ext.projecttabplus.model.names;

import java.util.List;
import java.util.Locale;

/**
 * Hide the file extension: the text after the last period, when it is 1 to 7 letters and
 * digits with at least one letter (so {@code .qptiff} counts and {@code slide_7.0} keeps
 * {@code .0}). With {@code doubleExtension}, a second ending is hidden too, but only one from
 * {@link #DOUBLE_ENDINGS}, so {@code .5x} or {@code .v2} before the extension stay.
 *
 * @param doubleExtension whether to hide {@code .ome} in {@code .ome.tif}
 * @param on whether the rule is on
 */
public record HideExtensionRule(boolean doubleExtension, boolean on) implements NameRule {

    public static final String KIND = "hideExtension";

    /** The first parts of a double extension the tick hides, lower case. */
    public static final List<String> DOUBLE_ENDINGS = List.of("ome", "tar");

    static final int MAX_LENGTH = 7;

    @Override
    public NameRule withOn(boolean on) {
        return new HideExtensionRule(doubleExtension, on);
    }

    @Override
    public boolean finished() {
        return true;
    }

    @Override
    public String kind() {
        return KIND;
    }

    /**
     * @param token text after a period
     * @return true when it reads as a file extension
     */
    public static boolean isExtensionToken(String token) {
        if (token.isEmpty() || token.length() > MAX_LENGTH) {
            return false;
        }
        boolean letter = false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                letter = true;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return letter;
    }

    /**
     * @param s a name
     * @param dbl whether to take a double extension
     * @return the index of the period that starts the extension to hide, or -1
     */
    public static int extensionStart(String s, boolean dbl) {
        int dot = s.lastIndexOf('.');
        if (dot <= 0 || !isExtensionToken(s.substring(dot + 1))) {
            return -1;
        }
        if (dbl) {
            int prev = s.lastIndexOf('.', dot - 1);
            if (prev > 0 && DOUBLE_ENDINGS.contains(s.substring(prev + 1, dot).toLowerCase(Locale.ROOT))) {
                return prev;
            }
        }
        return dot;
    }

    @Override
    public Mapped apply(Mapped in, int index) {
        String s = in.text();
        int start = extensionStart(s, doubleExtension);
        if (start < 0) {
            return in;
        }
        boolean[] drop = new boolean[s.length()];
        for (int i = start; i < s.length(); i++) {
            drop[i] = true;
        }
        return in.drop(drop, index);
    }

    /**
     * The extension left behind when text follows it, as in {@code name.svs (rotated 270)}:
     * the text after the last period is not an extension, but its first word is, followed by
     * a space.
     *
     * @param s a name
     * @return the leftover with its period, such as {@code .svs}, or null
     */
    public static String leftoverExtension(String s) {
        int dot = s.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String tail = s.substring(dot + 1);
        if (isExtensionToken(tail)) {
            return null;
        }
        int space = tail.indexOf(' ');
        if (space <= 0) {
            return null;
        }
        String word = tail.substring(0, space);
        return isExtensionToken(word) ? "." + word : null;
    }
}
