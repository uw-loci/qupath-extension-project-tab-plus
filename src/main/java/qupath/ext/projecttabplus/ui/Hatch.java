package qupath.ext.projecttabplus.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;

/**
 * Diagonal hatching (////) for the detections indicator, so it differs from the solid
 * annotation edge by shape and not only by color.
 */
final class Hatch {

    /**
     * Width of the hatched strip, in pixels. Wider than the 3 px solid edges because a
     * 45-degree mark needs the room to read as a slash at the Small thumbnail size.
     */
    static final double STRIP_WIDTH = 5.0;

    /** Distance along the strip between two hatch marks, in pixels. */
    static final double PERIOD = 6.0;

    /** Stroke width of one hatch mark, in pixels. */
    static final double LINE_WIDTH = 2.0;

    private Hatch() {
    }

    /**
     * Fills a rectangle with 45-degree hatch marks, clipped to the rectangle.
     *
     * @param gc the graphics context to draw on; its state is saved and restored
     * @param x left of the rectangle
     * @param y top of the rectangle
     * @param w width; also the horizontal run of each mark
     * @param h height
     * @param color the mark color
     * @param sparse true doubles the spacing (the unsaved-changes rendering, the hatched
     *        counterpart of a dashed edge)
     */
    static void paint(GraphicsContext gc, double x, double y, double w, double h, Color color, boolean sparse) {
        double step = sparse ? PERIOD * 2 : PERIOD;
        gc.save();
        gc.beginPath();
        gc.rect(x, y, w, h);
        gc.clip();
        gc.setStroke(color);
        gc.setLineWidth(LINE_WIDTH);
        gc.setLineCap(StrokeLineCap.BUTT);
        gc.setLineDashes((double[]) null);
        for (double yy = y - w; yy < y + h + w; yy += step) {
            gc.strokeLine(x, yy + w, x + w, yy);
        }
        gc.restore();
    }
}
