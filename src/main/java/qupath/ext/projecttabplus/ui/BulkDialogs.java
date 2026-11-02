package qupath.ext.projecttabplus.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.ext.projecttabplus.core.BulkJobs;
import qupath.ext.projecttabplus.core.ExportNaming;
import qupath.ext.projecttabplus.preferences.ProjectTabPlusPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.projects.ProjectImageEntry;

/**
 * The checkbox-mode dialogs: D4 remove confirmation, D5 clear-saved-data confirmation, D6 export
 * options, the overwrite prompt, and D7 the result shown only when something failed. D&lt;n&gt;
 * is the prefix of each dialog's keys in strings.properties.
 */
final class BulkDialogs {

    /** D6 answer. */
    record ExportChoice(Path folder, boolean compress, boolean prettyPrint, boolean excludeMeasurements) {
    }

    /** Which job a result dialog reports. */
    enum Kind {
        REMOVE, CLEAR, EXPORT
    }

    private static final int LIST_ROWS = 8;

    private BulkDialogs() {
    }

    /** One line of a D4/D5 list: the text shown and the full name for its tooltip. */
    record NameItem(String text, String fullName) {
    }

    /**
     * D4.
     *
     * @param shortNames entry ID to short name, for entries whose short name differs; empty when off
     * @return the saved-data-to-trash choice, or empty on cancel
     */
    static Optional<Boolean> confirmRemove(Window owner, List<ProjectImageEntry<BufferedImage>> entries,
                                           Set<String> openIds, Map<String, String> shortNames) {
        int n = entries.size();
        ListView<NameItem> list = nameList(entries, openIds, Set.of(), shortNames);
        Label openNote = new Label(Strings.plural("d4.openNote.one", "d4.openNote.many", openIds.size()));
        openNote.setWrapText(true);
        openNote.setVisible(!openIds.isEmpty());
        openNote.setManaged(!openIds.isEmpty());
        CheckBox data = new CheckBox(Strings.get("d4.dataCheckbox"));
        data.setSelected(false);
        data.setWrapText(true);
        data.setTooltip(Tooltips.of(Strings.get("d4.dataCheckbox.tooltip")));
        Label files = new Label(Strings.get("d4.filesNote"));
        files.setWrapText(true);
        VBox box = new VBox(8, list, openNote, data, files);
        box.setPrefWidth(440);
        ButtonType remove = new ButtonType(Strings.plural("d4.remove.one", "d4.remove.many", n), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d4.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.plural("d4.title.one", "d4.title.many", n))
                .headerText(Strings.plural("d4.header.one", "d4.header.many", n))
                .content(box)
                .owner(owner)
                .modality(Modality.APPLICATION_MODAL)
                .buttons(remove, cancel)
                .resizable()
                .build();
        DialogButtons.setDefault(dialog, cancel);
        DialogButtons.installTooltip(dialog, remove, Strings.get("d4.remove.tooltip"));
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != remove) {
            return Optional.empty();
        }
        return Optional.of(data.isSelected());
    }

    /**
     * D5.
     *
     * @param shortNames entry ID to short name, for entries whose short name differs; empty when off
     * @return true to clear
     */
    static boolean confirmClear(Window owner, List<ProjectImageEntry<BufferedImage>> entries,
                                Set<String> openIds, Set<String> skippedIds, Map<String, String> shortNames) {
        int n = entries.size();
        Label body = new Label(Strings.get("d5.body"));
        body.setWrapText(true);
        ListView<NameItem> list = nameList(entries, openIds, skippedIds, shortNames);
        Label skipped = new Label(Strings.plural("d5.skippedNote.one", "d5.skippedNote.many", skippedIds.size()));
        skipped.setWrapText(true);
        skipped.setVisible(!skippedIds.isEmpty());
        skipped.setManaged(!skippedIds.isEmpty());
        Label open = new Label(Strings.plural("d5.openNote.one", "d5.openNote.many", openIds.size()));
        open.setWrapText(true);
        open.setVisible(!openIds.isEmpty());
        open.setManaged(!openIds.isEmpty());
        Label backup = new Label(Strings.get("d5.backupNote"));
        backup.setWrapText(true);
        VBox box = new VBox(8, body, list, skipped, open, backup);
        box.setPrefWidth(440);
        ButtonType clear = new ButtonType(Strings.get("d5.clear"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d5.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.plural("d5.title.one", "d5.title.many", n))
                .headerText(Strings.plural("d5.header.one", "d5.header.many", n))
                .content(box)
                .owner(owner)
                .modality(Modality.APPLICATION_MODAL)
                .buttons(clear, cancel)
                .resizable()
                .build();
        DialogButtons.setDefault(dialog, cancel);
        DialogButtons.installTooltip(dialog, clear, Strings.get("d5.clear.tooltip"));
        Optional<ButtonType> r = dialog.showAndWait();
        return r.isPresent() && r.get() == clear;
    }

    /** D6. */
    static Optional<ExportChoice> exportOptions(Window owner, int n, int noData, int unsaved, boolean masked,
                                                Path initialFolder) {
        TextField folder = new TextField(initialFolder == null ? "" : initialFolder.toString());
        folder.setPrefColumnCount(28);
        HBox.setHgrow(folder, Priority.ALWAYS);
        folder.setTooltip(Tooltips.of(Strings.get("d6.folder.tooltip")));
        Button browse = new Button(Strings.get("d6.browse"));
        browse.setTooltip(Tooltips.of(Strings.get("d6.browse.tooltip")));
        Label folderLabel = new Label(Strings.get("d6.folder"));
        folderLabel.setMinWidth(Region.USE_PREF_SIZE);
        folderLabel.setLabelFor(folder);
        browse.setMinWidth(Region.USE_PREF_SIZE);
        HBox folderRow = new HBox(6, folderLabel, folder, browse);
        folderRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label folderState = new Label();
        folderState.setWrapText(true);
        ThemeColors.textFill(folderState, ThemeColors.WARNING);
        Label onePer = new Label();
        onePer.setWrapText(true);
        // The four options are remembered between sessions; Compress is off by default, as in
        // QuPath's own export.
        CheckBox compress = new CheckBox(Strings.get("d6.compress"));
        compress.setSelected(ProjectTabPlusPreferences.exportCompressProperty().get());
        compress.setTooltip(Tooltips.of(Strings.get("d6.compress.tooltip")));
        Runnable updateOnePer = () -> onePer.setText(Strings.format(
                masked ? "d6.oneFilePerMasked" : "d6.oneFilePer", ExportNaming.extension(compress.isSelected())));
        compress.selectedProperty().addListener((obs, o, nv) -> updateOnePer.run());
        updateOnePer.run();
        CheckBox pretty = new CheckBox(Strings.get("d6.pretty"));
        pretty.setSelected(ProjectTabPlusPreferences.exportPrettyPrintProperty().get());
        pretty.setTooltip(Tooltips.of(Strings.get("d6.pretty.tooltip")));
        CheckBox exclude = new CheckBox(Strings.get("d6.excludeMeasurements"));
        exclude.setSelected(ProjectTabPlusPreferences.exportExcludeMeasurementsProperty().get());
        exclude.setTooltip(Tooltips.of(Strings.get("d6.excludeMeasurements.tooltip")));
        VBox box = new VBox(8, folderRow, folderState, onePer, compress, pretty, exclude,
                wrapped(Strings.get("d6.manifestNote")));
        if (noData > 0) {
            box.getChildren().add(wrapped(Strings.plural("d6.noDataNote.one", "d6.noDataNote.many", noData)));
        }
        if (unsaved > 0) {
            box.getChildren().add(wrapped(Strings.plural("d6.unsavedNote.one", "d6.unsavedNote.many", unsaved)));
        }
        if (masked) {
            box.getChildren().add(wrapped(Strings.get("d6.maskedNote")));
        }
        box.setPrefWidth(460);
        ButtonType export = new ButtonType(Strings.get("d6.export"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d6.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.plural("d6.title.one", "d6.title.many", n))
                .content(box)
                .owner(owner)
                .modality(Modality.WINDOW_MODAL)
                .buttons(export, cancel)
                .resizable()
                .build();
        DialogButtons.setDefault(dialog, export);
        var exportButton = dialog.getDialogPane().lookupButton(export);
        Runnable validate = () -> {
            String state = folderProblem(folder.getText());
            folderState.setText(state == null ? "" : state);
            folderState.setVisible(state != null);
            folderState.setManaged(state != null);
            exportButton.setDisable(state != null);
        };
        folder.textProperty().addListener((obs, o, nv) -> validate.run());
        browse.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(Strings.get("d6.chooser.title"));
            File current = folder.getText().isBlank() ? null : new File(folder.getText());
            if (current != null && current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            File chosen = chooser.showDialog(dialog.getDialogPane().getScene().getWindow());
            if (chosen != null) {
                folder.setText(chosen.getAbsolutePath());
            }
        });
        validate.run();
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != export) {
            return Optional.empty();
        }
        return Optional.of(new ExportChoice(Path.of(folder.getText().trim()), compress.isSelected(),
                pretty.isSelected(), exclude.isSelected()));
    }

    /** @return null when the folder exists and is writable, else the inline reason */
    static String folderProblem(String text) {
        if (text == null || text.isBlank()) {
            return Strings.get("d6.folderMissing");
        }
        Path p;
        try {
            p = Path.of(text.trim());
        } catch (RuntimeException e) {
            return Strings.get("d6.folderMissing");
        }
        if (!Files.isDirectory(p)) {
            return Strings.get("d6.folderMissing");
        }
        if (!Files.isWritable(p)) {
            return Strings.get("d6.folderNotWritable");
        }
        return null;
    }

    /** The overwrite prompt. @return the policy, or empty on cancel */
    static Optional<BulkJobs.OverwritePolicy> overwritePrompt(Window owner, int existing, int total) {
        ButtonType overwrite = new ButtonType(Strings.get("d6.exists.overwrite"), ButtonData.OTHER);
        ButtonType skip = new ButtonType(Strings.get("d6.exists.skip"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d6.exists.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("d6.exists.title"))
                .contentText(Strings.plural("d6.exists.body.one", "d6.exists.body.many", existing, total))
                .owner(owner)
                .modality(Modality.WINDOW_MODAL)
                .buttons(overwrite, skip, cancel)
                .build();
        DialogButtons.setDefault(dialog, skip);
        DialogButtons.installTooltip(dialog, overwrite, Strings.get("d6.exists.overwrite.tooltip"));
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() == cancel) {
            return Optional.empty();
        }
        return Optional.of(r.get() == overwrite ? BulkJobs.OverwritePolicy.OVERWRITE : BulkJobs.OverwritePolicy.SKIP);
    }

    /**
     * D7: shown only when something failed. Non-modal.
     *
     * @param shortNames entry ID to short name, taken before the job; a line with one names both
     */
    static void showResult(Window owner, Kind kind, BulkJobs.Outcome outcome, Map<String, String> shortNames) {
        String titleKey = switch (kind) {
            case REMOVE -> "d7.title.remove";
            case CLEAR -> "d7.title.clear";
            case EXPORT -> "d7.title.export";
        };
        String headerKey = switch (kind) {
            case REMOVE -> "d7.header.remove";
            case CLEAR -> "d7.header.clear";
            case EXPORT -> "d7.header.export";
        };
        StringBuilder sb = new StringBuilder();
        boolean bothNames = false;
        for (BulkJobs.Failure f : outcome.failures()) {
            String shortName = shortNames.get(f.entryId());
            boolean both = shortName != null && !shortName.equals(f.name());
            bothNames |= both;
            String name = both ? Strings.format("d7.bothNames", shortName, f.name()) : f.name();
            sb.append(Strings.format("d7.failedLine", name, f.reason())).append('\n');
        }
        TextArea failed = new TextArea(sb.toString().trim());
        failed.setEditable(false);
        failed.setWrapText(true);
        // A line naming both the short and the full name usually wraps onto a second row.
        int perLine = bothNames ? 2 : 1;
        failed.setPrefRowCount(Math.min(LIST_ROWS, Math.max(3, perLine * outcome.failures().size())));
        failed.setTooltip(Tooltips.of(Strings.get("d7.failedList.tooltip")));
        VBox box = new VBox(6, new Label(Strings.get("d7.failedLabel")), failed, wrapped(Strings.get("d7.logHint")));
        box.setPrefWidth(bothNames ? 560 : 460);
        Dialogs.builder()
                .title(Strings.get(titleKey))
                .headerText(Strings.format(headerKey, outcome.done(), outcome.failures().size()))
                .content(box)
                .owner(owner)
                .nonModal()
                .buttons(new ButtonType(Strings.get("d7.close"), ButtonData.CANCEL_CLOSE))
                .resizable()
                .show();
    }

    /**
     * The D4/D5 list. Short names are shown while the rules are on, except that a short name
     * shared by two or more listed images shows in full on those rows: two identical lines in a
     * confirmation that removes or clears would not say which images are meant.
     */
    private static ListView<NameItem> nameList(List<ProjectImageEntry<BufferedImage>> entries, Set<String> openIds,
                                               Set<String> skippedIds, Map<String, String> shortNames) {
        Map<String, Integer> uses = new HashMap<>();
        for (ProjectImageEntry<BufferedImage> e : entries) {
            uses.merge(shownName(e, shortNames), 1, Integer::sum);
        }
        ListView<NameItem> list = new ListView<>();
        for (ProjectImageEntry<BufferedImage> e : entries) {
            String shown = shownName(e, shortNames);
            String text = uses.get(shown) > 1 ? e.getImageName() : shown;
            if (skippedIds.contains(e.getID())) {
                text += "    " + Strings.get("d5.skippedMark");
            } else if (openIds.contains(e.getID())) {
                text += "    " + Strings.get("d4.openMark");
            }
            list.getItems().add(new NameItem(text, e.getImageName()));
        }
        list.setCellFactory(lv -> new ListCell<>() {
            {
                setMnemonicParsing(false);
            }

            @Override
            protected void updateItem(NameItem item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.text());
                setTooltip(empty || item == null ? null : Tooltips.of(item.fullName()));
            }
        });
        // Room for a horizontal scroll bar: full names are often wider than the list.
        list.setPrefHeight(24.0 * Math.min(LIST_ROWS, Math.max(2, entries.size())) + 20);
        list.setPadding(Insets.EMPTY);
        list.setTooltip(Tooltips.of(shortNames.isEmpty() ? Strings.get("d4.list.tooltip")
                : Strings.get("d4.list.tooltip") + " " + Strings.get("d4.list.tooltip.shortNames")));
        return list;
    }

    private static String shownName(ProjectImageEntry<BufferedImage> e, Map<String, String> shortNames) {
        String s = shortNames.get(e.getID());
        return s == null ? e.getImageName() : s;
    }

    private static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }
}
