package qupath.ext.projecttabplus.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;

/**
 * A row of half circles (half TMA cores) along the bottom edge for the TMA indicator, so it
 * differs from the solid annotation edge and the hatched detections edge by shape and not
 * only by color.
 */
final class Bumps {

    /** Radius of one half circle at the Small thumbnail width and below, in pixels. */
    static final double MIN_RADIUS = 3.5;

    /** Radius at the Large thumbnail width (150 px) and above, in pixels. */
    static final double MAX_RADIUS = 5.0;

    /** Gap between two bumps, in pixels. */
    static final double GAP = 2.0;

    /** Stroke width of an outlined bump, in pixels. */
    static final double LINE_WIDTH = 1.5;

    private Bumps() {
    }

    /**
     * Draws half circles, flat side down, sitting on the bottom edge of the rectangle and
     * clipped to it. The radius grows from {@link #MIN_RADIUS} at 50 px wide to
     * {@link #MAX_RADIUS} at 150 px; the first bump starts at the left edge and only bumps whose
     * center lies inside the rectangle are drawn, so a 20 px slot shows two.
     *
     * @param gc the graphics context to draw on; its state is saved and restored
     * @param x left of the rectangle
     * @param y top of the rectangle
     * @param w width
     * @param h height; the bumps sit on {@code y + h}
     * @param color the bump color
     * @param outline true draws outlines instead of filled bumps (the unsaved-changes rendering,
     *        the counterpart of a dashed edge)
     */
    static void paint(GraphicsContext gc, double x, double y, double w, double h, Color color, boolean outline) {
        gc.save();
        gc.beginPath();
        gc.rect(x, y, w, h);
        gc.clip();
        gc.setFill(color);
        gc.setStroke(color);
        gc.setLineWidth(LINE_WIDTH);
        gc.setLineDashes((double[]) null);
        double baseline = y + h;
        double radius = radiusFor(w);
        for (double cx = x + radius; cx < x + w; cx += 2 * radius + GAP) {
            if (outline) {
                // Inset by half the stroke so the arc is not clipped; CHORD closes the flat side.
                double r = radius - LINE_WIDTH / 2;
                gc.strokeArc(cx - r, baseline - LINE_WIDTH / 2 - r, 2 * r, 2 * r, 0, 180, ArcType.CHORD);
            } else {
                gc.fillArc(cx - radius, baseline - radius, 2 * radius, 2 * radius, 0, 180, ArcType.ROUND);
            }
        }
        gc.restore();
    }

    /**
     * @param width the strip width in pixels
     * @return the bump radius for that width: {@link #MIN_RADIUS} up to 50 px, {@link #MAX_RADIUS}
     *         from 150 px, linear between
     */
    static double radiusFor(double width) {
        double t = Math.max(0, Math.min(1, (width - 50) / 100));
        return MIN_RADIUS + t * (MAX_RADIUS - MIN_RADIUS);
    }
}
