package qupath.ext.projecttabplus.ui;

import javafx.scene.control.Tooltip;

/** Tooltips that wrap instead of running across the screen as one line. */
public final class Tooltips {

    /** Widest a tooltip gets before it wraps, in px. */
    static final double MAX_WIDTH = 400;

    private Tooltips() {
    }

    /**
     * @param text the tooltip text
     * @return a tooltip that wraps at {@link #MAX_WIDTH}
     */
    public static Tooltip of(String text) {
        Tooltip t = new Tooltip(text);
        t.setWrapText(true);
        t.setMaxWidth(MAX_WIDTH);
        return t;
    }
}
