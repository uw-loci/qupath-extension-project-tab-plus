package qupath.ext.projecttabplus.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;

/**
 * Dialog-bar button helpers. Makes the default button explicit (JavaFX marks every OK_DONE-style
 * button as default, so a dialog whose safe answer is Cancel needs the flag moved by hand) and
 * puts tooltips on bar buttons, which the builder cannot reach.
 */
final class DialogButtons {

    private DialogButtons() {
    }

    /**
     * Make exactly one button the default (Enter) button.
     *
     * @param dialog the dialog
     * @param defaultType the button that Enter should fire
     */
    static void setDefault(Dialog<?> dialog, ButtonType defaultType) {
        for (ButtonType type : dialog.getDialogPane().getButtonTypes()) {
            Node node = dialog.getDialogPane().lookupButton(type);
            if (node instanceof Button button) {
                button.setDefaultButton(type == defaultType);
            }
        }
    }

    /**
     * Put a tooltip on a dialog-bar button.
     *
     * @param dialog the built dialog
     * @param type the button
     * @param text the tooltip text
     */
    static void installTooltip(Dialog<?> dialog, ButtonType type, String text) {
        Node node = dialog.getDialogPane().lookupButton(type);
        if (node instanceof Control c) {
            c.setTooltip(Tooltips.of(text));
        }
    }
}
