package qupath.ext.projecttabplus.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;

/** The non-modal legend: what the edges, badges and search keywords mean, with labeled swatches. */
final class IndicatorLegendDialog {

    private IndicatorLegendDialog() {
    }

    static void show(QuPathGUI qupath) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(4));
        box.getChildren().add(wrapped(Strings.get("legend.intro")));
        box.getChildren().add(swatch(Strings.get("legend.annotations"),
                ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get())));
        box.getChildren().add(swatch(Strings.get("legend.detections"), ThemeColors.detectionsPaint()));
        box.getChildren().add(swatch(Strings.get("legend.tma"),
                ColorToolsFX.getCachedColor(PathPrefs.colorTMAProperty().get())));
        box.getChildren().add(wrapped(Strings.get("legend.solid")));
        box.getChildren().add(wrapped(Strings.get("legend.none")));
        box.getChildren().add(wrapped(Strings.get("legend.hover")));
        box.getChildren().add(wrapped(Strings.get("legend.search")));
        box.setPrefWidth(420);
        Dialogs.builder()
                .title(Strings.get("legend.title"))
                .content(box)
                .owner(qupath.getStage())
                .nonModal()
                .buttons(new ButtonType(Strings.get("legend.close"), javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE))
                .resizable()
                .show();
    }

    private static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    private static HBox swatch(String text, javafx.scene.paint.Color color) {
        Region r = new Region();
        r.setMinSize(14, 14);
        r.setPrefSize(14, 14);
        r.setMaxSize(14, 14);
        r.setStyle(String.format("-fx-background-color: rgb(%d, %d, %d); -fx-border-color: %s;",
                (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255), ThemeColors.BORDER));
        HBox h = new HBox(8, r, wrapped(text));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
