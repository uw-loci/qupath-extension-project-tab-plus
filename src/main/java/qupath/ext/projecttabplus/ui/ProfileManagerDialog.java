package qupath.ext.projecttabplus.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.ext.projecttabplus.model.DisplayProfile;
import qupath.ext.projecttabplus.model.GroupingSpec;
import qupath.ext.projecttabplus.model.ProfileStore;
import qupath.fx.dialogs.Dialogs;

/** Manage profiles: duplicate, rename, delete, reset Default, reorder. WINDOW_MODAL. */
final class ProfileManagerDialog {

    private ProfileManagerDialog() {
    }

    static void show(ProjectTabPlusPane pane) {
        ListView<DisplayProfile> list = new ListView<>(pane.profiles());
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DisplayProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item == pane.savedProfile()
                            ? item.getName() + "   " + Strings.get("profile.manage.inUse") : item.getName());
                }
            }
        });
        list.getSelectionModel().select(pane.savedProfile());

        Button duplicate = button("profile.manage.duplicate", "profile.manage.duplicate.tooltip");
        Button rename = button("profile.manage.rename", "profile.manage.rename.tooltip");
        Button delete = button("profile.manage.delete", "profile.manage.delete.tooltip");
        Button reset = button("profile.manage.reset", "profile.manage.reset.tooltip");
        Button up = button("profile.manage.moveUp", "profile.manage.move.tooltip");
        Button down = button("profile.manage.moveDown", "profile.manage.move.tooltip");
        VBox buttons = new VBox(4, duplicate, rename, delete, reset, up, down);

        Runnable syncButtons = () -> {
            DisplayProfile sel = list.getSelectionModel().getSelectedItem();
            boolean isDefault = sel != null && ProfileStore.DEFAULT_NAME.equals(sel.getName());
            int i = list.getSelectionModel().getSelectedIndex();
            duplicate.setDisable(sel == null);
            rename.setDisable(sel == null || isDefault);
            delete.setDisable(sel == null || isDefault);
            reset.setDisable(!isDefault);
            // Default stays at index 0 (ProfileStore.ensureDefault), so nothing moves above or into it.
            up.setDisable(i <= 1);
            down.setDisable(i < 1 || i >= pane.profiles().size() - 1);
        };
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> syncButtons.run());
        syncButtons.run();

        Label stored = new Label(Strings.get("profile.manage.storedIn"));
        TextField path = new TextField(pane.profilesFile().toString());
        path.setEditable(false);
        HBox.setHgrow(list, Priority.ALWAYS);
        HBox middle = new HBox(8, list, buttons);
        BorderPane content = new BorderPane(middle);
        content.setBottom(new VBox(4, stored, path));
        content.setPadding(new Insets(4));

        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("profile.manage.title"))
                .content(content)
                .owner(pane.qupath().getStage())
                .modality(Modality.WINDOW_MODAL)
                .buttons(new ButtonType(Strings.get("profile.manage.close"), ButtonData.CANCEL_CLOSE))
                .resizable()
                .size(440, 380)
                .build();
        Window owner = dialog.getDialogPane().getScene() == null ? pane.qupath().getStage()
                : dialog.getDialogPane().getScene().getWindow();

        duplicate.setOnAction(e -> {
            DisplayProfile sel = list.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DisplayProfile copy = ProfileDialogs.duplicate(pane, sel);
                list.getSelectionModel().select(copy);
                list.refresh();
            }
        });
        rename.setOnAction(e -> {
            DisplayProfile sel = list.getSelectionModel().getSelectedItem();
            ProfileDialogs.rename(pane, sel, ownerOf(dialog, owner));
            list.refresh();
        });
        delete.setOnAction(e -> {
            DisplayProfile sel = list.getSelectionModel().getSelectedItem();
            ProfileDialogs.delete(pane, sel, ownerOf(dialog, owner));
            list.refresh();
            syncButtons.run();
        });
        reset.setOnAction(e -> {
            DisplayProfile def = pane.profiles().get(0);
            DisplayProfile fresh = new DisplayProfile(ProfileStore.DEFAULT_NAME);
            if (pane.qupath().getProject() != null) {
                fresh.setGroupingSpec(GroupingSpec.fromSortKey(
                        pane.qupath().getProject().getMetadata().get(ProjectTabPlusPane.SORT_KEY)));
            }
            def.copySettingsFrom(fresh);
            if (def == pane.savedProfile()) {
                pane.selectProfile(def, false);
            }
            pane.persistProfiles();
            list.refresh();
        });
        up.setOnAction(e -> move(pane, list, -1, syncButtons));
        down.setOnAction(e -> move(pane, list, 1, syncButtons));

        dialog.showAndWait();
        pane.refreshProfileCombo();
    }

    private static Window ownerOf(Dialog<?> dialog, Window fallback) {
        return dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : fallback;
    }

    private static void move(ProjectTabPlusPane pane, ListView<DisplayProfile> list, int delta, Runnable sync) {
        int i = list.getSelectionModel().getSelectedIndex();
        int j = i + delta;
        if (i < 1 || j < 1 || j >= pane.profiles().size()) {
            return;
        }
        DisplayProfile p = pane.profiles().remove(i);
        pane.profiles().add(j, p);
        list.getSelectionModel().select(j);
        pane.persistProfiles();
        sync.run();
    }

    private static Button button(String textKey, String tooltipKey) {
        Button b = new Button(Strings.get(textKey));
        b.setTooltip(Tooltips.of(Strings.get(tooltipKey)));
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }
}
