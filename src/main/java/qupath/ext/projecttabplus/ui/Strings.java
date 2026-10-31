package qupath.ext.projecttabplus.ui;

import java.util.ResourceBundle;

/**
 * Access to the extension's display strings. Every user-visible string comes from
 * {@code strings.properties}, so the whole shipped vocabulary can be reviewed in one file.
 */
public final class Strings {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("qupath.ext.projecttabplus.ui.strings");

    private Strings() {
    }

    /**
     * @param key the resource key
     * @return the string for that key
     */
    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    /**
     * @param key the resource key
     * @param args format arguments
     * @return the formatted string
     */
    public static String format(String key, Object... args) {
        return String.format(BUNDLE.getString(key), args);
    }

    /**
     * Singular or plural form of a count.
     *
     * @param singularKey the key for a count of 1
     * @param pluralKey the key for any other count
     * @param n the count
     * @param args further format arguments, after the count
     * @return the formatted string
     */
    public static String plural(String singularKey, String pluralKey, int n, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = n;
        System.arraycopy(args, 0, all, 1, args.length);
        return String.format(BUNDLE.getString(n == 1 ? singularKey : pluralKey), all);
    }
}
