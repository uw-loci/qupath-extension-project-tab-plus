package qupath.ext.projecttabplus.ui;

import javafx.application.ColorScheme;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import qupath.lib.gui.prefs.QuPathStyleManager;

// Adapted from qupath-extension-image-export-toolkit (QuIET, Apache-2.0,
// github.com/uw-loci/qupath-extension-image-export-toolkit), modified 2026-10/11.

/**
 * Theme-aware colors as CSS fragments: each is a {@code ladder()} on the theme's
 * {@code -fx-background}, so it follows a theme switch live.
 */
public final class ThemeColors {

    private ThemeColors() {
    }

    /**
     * Set a node's text fill, keeping the rest of its inline style.
     *
     * @param node the node
     * @param color a CSS color expression, normally a constant from this class
     */
    public static void textFill(Node node, String color) {
        String style = node.getStyle() == null ? "" : node.getStyle();
        style = style.replaceAll("-fx-text-fill:[^;]*;?\\s*", "").trim();
        node.setStyle(style + (style.isEmpty() || style.endsWith(";") ? "" : ";") + " -fx-text-fill: " + color + ";");
    }

    private static String ladder(String onDark, String onLight) {
        return "ladder(-fx-background, " + onDark + " 49%, " + onLight + " 50%)";
    }

    /** Secondary text: hints, notes, captions. */
    public static final String MUTED = ladder("#ADADAD", "#666666");

    /** Proceeding is possible but worth a second look. */
    public static final String WARNING = ladder("#FFB74D", "#9E4E00");

    /** Failures and hard blocks. */
    public static final String ERROR = ladder("#FF8A80", "#C62828");

    /** Selection / focus accent for borders and glows. */
    public static final String ACCENT = ladder("#4DA3FF", "#0078D7");

    /** Informational callout background; the current-image row tint. */
    public static final String INFO_BG = ladder("#2C3E57", "#E8F0FE");

    private static final String BORDER_DARK = "#5A5A5A";
    private static final String BORDER_LIGHT = "#CCCCCC";

    /** Hairline borders around cards and boxes. */
    public static final String BORDER = ladder(BORDER_DARK, BORDER_LIGHT);

    /** @return the hairline border color as a paint for canvas drawing, for the current theme */
    public static Color borderPaint() {
        return Color.web(isDark() ? BORDER_DARK : BORDER_LIGHT);
    }

    private static final String DETECTIONS_DARK = "#80CBC4";
    private static final String DETECTIONS_LIGHT = "#00796B";

    /** The detections indicator: a teal that stays apart from the red annotation default. */
    public static final String DETECTIONS = ladder(DETECTIONS_DARK, DETECTIONS_LIGHT);

    /** @return the detections teal as a paint for canvas drawing, for the current theme */
    public static Color detectionsPaint() {
        return Color.web(isDark() ? DETECTIONS_DARK : DETECTIONS_LIGHT);
    }

    /** @return true when QuPath is using a dark style */
    public static boolean isDark() {
        try {
            return QuPathStyleManager.getStyleColorScheme() == ColorScheme.DARK;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
