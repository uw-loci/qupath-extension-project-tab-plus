package qupath.ext.projecttabplus.ui;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.ext.projecttabplus.core.DeleteEligibility;
import qupath.fx.dialogs.Dialogs;

/** D1, D2 and D3: the remove-current-image confirmations. Every string is from the design. */
public final class RemoveImageDialogs {

    /**
     * The D1 answer.
     *
     * @param dataToTrash move the saved-data folder to the trash
     * @param fileToTrash also move the image file (a second confirmation follows)
     */
    public record RemoveChoice(boolean dataToTrash, boolean fileToTrash) {
    }

    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private RemoveImageDialogs() {
    }

    /**
     * D1.
     *
     * @param owner the owner window
     * @param imageName the image name
     * @param eligibility whether the file may be offered
     * @param fileSize the file size in bytes, or -1
     * @return the choice, or empty on cancel
     */
    public static Optional<RemoveChoice> showRemoveCurrent(Window owner, String imageName,
                                                           DeleteEligibility eligibility, long fileSize) {
        CheckBox data = new CheckBox(Strings.get("d1.dataCheckbox"));
        data.setSelected(false);
        data.setTooltip(Tooltips.of(Strings.get("d1.dataCheckbox.tooltip")));
        data.setWrapText(true);
        CheckBox file = new CheckBox(Strings.get("d1.fileCheckbox"));
        file.setSelected(false);
        file.setWrapText(true);
        Label fileLine = new Label();
        fileLine.setWrapText(true);
        fileLine.getStyleClass().add("ptp-fields");
        if (eligibility.isEligible()) {
            file.setTooltip(Tooltips.of(Strings.get("d1.fileCheckbox.tooltip")));
            fileLine.setText(Strings.format("d1.fileLine", eligibility.path(), formatSize(fileSize)));
        } else {
            file.setDisable(true);
            fileLine.setText(reasonText(eligibility));
        }
        Label body = new Label(Strings.get("d1.body"));
        body.setWrapText(true);
        VBox box = new VBox(8, body, data, file, fileLine);
        box.setPrefWidth(440);

        ButtonType remove = new ButtonType(Strings.get("d1.remove"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d1.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("d1.title"))
                .headerText(Strings.format("d1.header", imageName))
                .content(box)
                .owner(owner)
                .modality(Modality.APPLICATION_MODAL)
                .buttons(remove, cancel)
                .build();
        DialogButtons.setDefault(dialog, remove);
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != remove) {
            return Optional.empty();
        }
        return Optional.of(new RemoveChoice(data.isSelected(), eligibility.isEligible() && file.isSelected()));
    }

    /** @return the inline reason text for an ineligible file */
    static String reasonText(DeleteEligibility e) {
        return switch (e.reason()) {
            case ELIGIBLE -> "";
            case COMPANION_FORMAT -> Strings.format("d1.reason.companion", e.extension().isEmpty() ? "" : e.extension());
            case SHARED_URI -> Strings.plural("d1.reason.shared.one", "d1.reason.shared.many", e.sharedCount());
            case NOT_FOUND -> Strings.get("d1.reason.notFound");
            case NOT_LOCAL, NO_URI -> Strings.get("d1.reason.notLocal");
            case NETWORK -> Strings.get("d1.reason.network");
            case MULTIPLE_URIS -> Strings.get("d1.reason.multiple");
        };
    }

    /**
     * D2: the second, distinct confirmation. Keep file is the default and Escape.
     *
     * @param owner the owner window
     * @param path the file
     * @param fileSize bytes, or -1
     * @param modifiedMillis last-modified time, or -1
     * @return true to move the file to the trash
     */
    public static boolean showMoveFileToTrash(Window owner, Path path, long fileSize, long modifiedMillis) {
        Label pathLabel = new Label(path.toString());
        pathLabel.setWrapText(true);
        String modified = modifiedMillis < 0 ? "-"
                : MODIFIED.format(Instant.ofEpochMilli(modifiedMillis).atZone(ZoneId.systemDefault()));
        Label details = new Label(Strings.format("d2.details", formatSize(fileSize), modified));
        Label body = new Label(Strings.get("d2.body"));
        body.setWrapText(true);
        VBox box = new VBox(8, pathLabel, details, body);
        box.setPrefWidth(440);
        ButtonType keep = new ButtonType(Strings.get("d2.keep"), ButtonData.CANCEL_CLOSE);
        ButtonType trash = new ButtonType(Strings.get("d2.trash"), ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("d2.title"))
                .headerText(Strings.get("d2.header"))
                .content(box)
                .owner(owner)
                .modality(Modality.APPLICATION_MODAL)
                .warning()
                .buttons(keep, trash)
                .build();
        DialogButtons.setDefault(dialog, keep);
        installTooltip(dialog, keep, Strings.get("d2.keep.tooltip"));
        installTooltip(dialog, trash, Strings.get("d2.trash.tooltip"));
        Optional<ButtonType> r = dialog.showAndWait();
        return r.isPresent() && r.get() == trash;
    }

    /**
     * D3: the trash step failed or is unsupported.
     *
     * @param owner the owner window
     * @param path the file that stayed
     * @param reason the short reason
     * @return true to remove the image from the project anyway
     */
    public static boolean showTrashFailed(Window owner, Path path, String reason) {
        Label pathLabel = new Label(path.toString());
        pathLabel.setWrapText(true);
        Label reasonLabel = new Label(Strings.format("d3.reason", reason));
        reasonLabel.setWrapText(true);
        Label body = new Label(Strings.get("d3.body"));
        body.setWrapText(true);
        VBox box = new VBox(8, pathLabel, reasonLabel, body);
        box.setPrefWidth(440);
        ButtonType remove = new ButtonType(Strings.get("d3.remove"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d3.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("d3.title"))
                .headerText(Strings.get("d3.header"))
                .content(box)
                .owner(owner)
                .modality(Modality.APPLICATION_MODAL)
                .buttons(remove, cancel)
                .build();
        DialogButtons.setDefault(dialog, remove);
        Optional<ButtonType> r = dialog.showAndWait();
        return r.isPresent() && r.get() == remove;
    }

    private static void installTooltip(Dialog<ButtonType> dialog, ButtonType type, String text) {
        var node = dialog.getDialogPane().lookupButton(type);
        if (node instanceof javafx.scene.control.Control c) {
            c.setTooltip(Tooltips.of(text));
        }
    }

    /**
     * @param bytes a size, or negative for unknown
     * @return a short human-readable size
     */
    static String formatSize(long bytes) {
        if (bytes < 0) {
            return "-";
        }
        if (bytes < 1024) {
            return Strings.format("size.bytes", bytes);
        }
        if (bytes < 1024L * 1024) {
            return Strings.format("size.kb", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return Strings.format("size.mb", bytes / (1024.0 * 1024));
        }
        return Strings.format("size.gb", bytes / (1024.0 * 1024 * 1024));
    }
}
