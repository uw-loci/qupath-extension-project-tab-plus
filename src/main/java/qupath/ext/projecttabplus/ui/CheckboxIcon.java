package qupath.ext.projecttabplus.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.SVGPath;

/**
 * The checkbox-mode toolbar icon: a rounded square with a check mark, drawn in code so it
 * follows the theme through {@code -fx-text-base-color} like the text on the buttons around it.
 */
final class CheckboxIcon {

    /** Strokes resolve against the button's own color chain, so a pressed toggle still reads. */
    private static final String STROKE = "-fx-stroke: -fx-text-base-color; -fx-fill: transparent;";

    private CheckboxIcon() {
    }

    /**
     * @param size the icon's width and height in px
     * @return a node sized like the other toolbar icons
     */
    static Node create(double size) {
        double inset = size * 0.125;
        double box = size - 2 * inset;
        Rectangle frame = new Rectangle(inset, inset, box, box);
        frame.setArcWidth(size * 0.2);
        frame.setArcHeight(size * 0.2);
        frame.setStrokeWidth(Math.max(1.0, size / 12));
        frame.setStyle(STROKE);

        // A check mark inside the frame, on a 16 px grid, scaled to the requested size.
        SVGPath check = new SVGPath();
        check.setContent("M 4.5 8.3 L 7 10.8 L 11.6 5.6");
        check.setScaleX(size / 16);
        check.setScaleY(size / 16);
        check.setStrokeWidth(Math.max(1.5, size / 9));
        check.setStrokeLineCap(StrokeLineCap.ROUND);
        check.setStrokeLineJoin(StrokeLineJoin.ROUND);
        check.setStyle(STROKE);

        // An invisible square pins the group's bounds to the full icon size.
        Rectangle bounds = new Rectangle(0, 0, size, size);
        bounds.setStyle("-fx-fill: transparent; -fx-stroke: transparent;");
        return new Group(bounds, frame, check);
    }
}
