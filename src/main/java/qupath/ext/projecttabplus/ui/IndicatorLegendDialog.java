package qupath.ext.projecttabplus.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;

/** The non-modal legend: what the edges, badges and search keywords mean, with labeled swatches. */
final class IndicatorLegendDialog {

    private static final double SWATCH = 14;

    private static Dialog<ButtonType> open;

    private IndicatorLegendDialog() {
    }

    static void show(QuPathGUI qupath) {
        if (open != null && open.isShowing()) {
            Window w = open.getDialogPane().getScene() == null ? null : open.getDialogPane().getScene().getWindow();
            if (w != null) {
                w.requestFocus();
            }
            return;
        }
        VBox box = new VBox(6);
        box.setPadding(new Insets(4));
        box.getChildren().add(wrapped(Strings.get("legend.intro")));
        box.getChildren().add(swatch(Strings.get("legend.annotations"),
                ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get())));
        box.getChildren().add(hatchedSwatch(Strings.get("legend.detections"), ThemeColors.detectionsPaint()));
        box.getChildren().add(swatch(Strings.get("legend.tma"),
                ColorToolsFX.getCachedColor(PathPrefs.colorTMAProperty().get())));
        box.getChildren().add(wrapped(Strings.get("legend.solid")));
        box.getChildren().add(wrapped(Strings.get("legend.none")));
        box.getChildren().add(wrapped(Strings.get("legend.hover")));
        box.getChildren().add(wrapped(Strings.get("legend.search")));
        box.setPrefWidth(420);
        open = Dialogs.builder()
                .title(Strings.get("legend.title"))
                .content(box)
                .owner(qupath.getStage())
                .nonModal()
                .buttons(new ButtonType(Strings.get("legend.close"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE))
                .resizable()
                .build();
        open.show();
    }

    private static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    private static HBox hatchedSwatch(String text, javafx.scene.paint.Color color) {
        Canvas c = new Canvas(SWATCH, SWATCH);
        GraphicsContext gc = c.getGraphicsContext2D();
        Hatch.paint(gc, 0, 0, SWATCH, SWATCH, color, false);
        gc.setStroke(ThemeColors.borderPaint());
        gc.setLineWidth(1);
        gc.strokeRect(0.5, 0.5, SWATCH - 1, SWATCH - 1);
        HBox h = new HBox(8, c, wrapped(text));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static HBox swatch(String text, javafx.scene.paint.Color color) {
        Region r = new Region();
        r.setMinSize(SWATCH, SWATCH);
        r.setPrefSize(SWATCH, SWATCH);
        r.setMaxSize(SWATCH, SWATCH);
        r.setStyle(String.format("-fx-background-color: rgb(%d, %d, %d); -fx-border-color: %s;",
                (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255), ThemeColors.BORDER));
        HBox h = new HBox(8, r, wrapped(text));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
