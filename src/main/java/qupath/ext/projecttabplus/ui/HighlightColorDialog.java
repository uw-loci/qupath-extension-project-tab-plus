package qupath.ext.projecttabplus.ui;

import java.util.Locale;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.ext.projecttabplus.preferences.ProjectTabPlusPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;

/**
 * The current-image highlight color, reachable from the row context menu and the gear menu.
 * Edits the same two preferences as the Edit > Preferences > Project+ entries and applies at
 * once; Close is the only button.
 */
final class HighlightColorDialog {

    private static Dialog<ButtonType> open;

    private HighlightColorDialog() {
    }

    static void show(QuPathGUI qupath) {
        if (open != null && open.isShowing()) {
            Window w = open.getDialogPane().getScene() == null ? null : open.getDialogPane().getScene().getWindow();
            if (w != null) {
                w.requestFocus();
            }
            return;
        }
        BooleanProperty useCustom = ProjectTabPlusPreferences.useCustomHighlightColorProperty();
        IntegerProperty colorPref = ProjectTabPlusPreferences.currentImageHighlightColorProperty();

        CheckBox theme = new CheckBox(Strings.get("highlight.useTheme"));
        theme.setSelected(!useCustom.get());
        theme.setTooltip(Tooltips.of(Strings.get("highlight.useTheme.tooltip")));
        theme.selectedProperty().addListener((obs, o, n) -> useCustom.set(!n));
        ChangeListener<Boolean> customListener = (obs, o, n) -> theme.setSelected(!n);
        useCustom.addListener(customListener);

        ColorPicker picker = new ColorPicker(toColor(colorPref.get()));
        picker.setTooltip(Tooltips.of(Strings.get("highlight.color.tooltip")));
        picker.disableProperty().bind(theme.selectedProperty());
        picker.setOnAction(e -> colorPref.set(toArgb(picker.getValue())));
        ChangeListener<Number> colorListener = (obs, o, n) -> {
            Color c = toColor(n.intValue());
            if (!c.equals(picker.getValue())) {
                picker.setValue(c);
            }
        };
        colorPref.addListener(colorListener);
        Label colorLabel = new Label(Strings.get("highlight.color"));
        colorLabel.setLabelFor(picker);
        HBox colorRow = new HBox(8, colorLabel, picker);
        colorRow.setAlignment(Pos.CENTER_LEFT);

        Label sampleName = new Label(Strings.get("highlight.sample"));
        sampleName.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sampleName, Priority.ALWAYS);
        Label chip = new Label(Strings.get("row.openChip"));
        chip.getStyleClass().add("ptp-open-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        HBox sample = new HBox(4, sampleName, chip);
        sample.setAlignment(Pos.CENTER_LEFT);
        sample.setPadding(new Insets(4, 8, 4, 8));
        Runnable paintSample = () -> sample.setStyle("-fx-font-weight: bold; -fx-background-color: "
                + (useCustom.get() ? rgba(colorPref.get()) : ThemeColors.INFO_BG) + ";");
        paintSample.run();
        ChangeListener<Object> repaint = (obs, o, n) -> paintSample.run();
        useCustom.addListener(repaint);
        colorPref.addListener(repaint);

        VBox box = new VBox(8, theme, colorRow, new Label(Strings.get("highlight.sampleCaption")), sample);
        box.setPadding(new Insets(4));
        box.setPrefWidth(360);

        ButtonType close = new ButtonType(Strings.get("highlight.close"), ButtonData.CANCEL_CLOSE);
        open = Dialogs.builder()
                .title(Strings.get("highlight.title"))
                .content(box)
                .owner(qupath.getStage())
                .modality(Modality.WINDOW_MODAL)
                .buttons(close)
                .build();
        DialogButtons.setDefault(open, close);
        open.setOnHidden(e -> {
            useCustom.removeListener(customListener);
            useCustom.removeListener(repaint);
            colorPref.removeListener(colorListener);
            colorPref.removeListener(repaint);
            open = null;
        });
        open.show();
    }

    private static Color toColor(int argb) {
        return Color.rgb(ColorTools.red(argb), ColorTools.green(argb), ColorTools.blue(argb),
                ColorTools.alpha(argb) / 255.0);
    }

    private static int toArgb(Color c) {
        return ColorTools.packARGB((int) Math.round(c.getOpacity() * 255), (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    private static String rgba(int argb) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.2f)", ColorTools.red(argb), ColorTools.green(argb),
                ColorTools.blue(argb), ColorTools.alpha(argb) / 255.0);
    }
}
