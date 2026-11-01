package qupath.ext.projecttabplus.ui;

import java.util.Optional;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import qupath.ext.projecttabplus.model.DisplayProfile;
import qupath.ext.projecttabplus.model.ProfileStore;
import qupath.ext.projecttabplus.preferences.ProjectTabPlusPreferences;
import qupath.fx.dialogs.Dialogs;

/** Save-as, rename and delete for profiles, plus the unsaved-changes prompt. */
final class ProfileDialogs {

    /** The answer to the unsaved-changes prompt. */
    enum UnsavedChoice {
        SAVE, DISCARD, CANCEL
    }

    private ProfileDialogs() {
    }

    /** The unsaved-changes prompt shown before switching away from a modified profile. */
    static UnsavedChoice askUnsaved(ProjectTabPlusPane pane, String profileName) {
        ButtonType save = new ButtonType(Strings.get("profile.unsaved.save"), ButtonData.OK_DONE);
        ButtonType discard = new ButtonType(Strings.get("profile.unsaved.discard"), ButtonData.OTHER);
        ButtonType cancel = new ButtonType(Strings.get("d1.cancel"), ButtonData.CANCEL_CLOSE);
        Optional<ButtonType> r = Dialogs.builder()
                .title(Strings.get("profile.unsaved.title"))
                .contentText(Strings.format("profile.unsaved.body", profileName))
                .owner(pane.qupath().getStage())
                .modality(Modality.WINDOW_MODAL)
                .buttons(save, discard, cancel)
                .showAndWait();
        if (r.isEmpty() || r.get() == cancel) {
            return UnsavedChoice.CANCEL;
        }
        return r.get() == save ? UnsavedChoice.SAVE : UnsavedChoice.DISCARD;
    }

    /** Save the live state under a new name. */
    static void saveAs(ProjectTabPlusPane pane) {
        saveAs(pane, pane.qupath().getStage());
    }

    static void saveAs(ProjectTabPlusPane pane, Window owner) {
        String name = askName(owner, Strings.get("profile.saveAs.title"), Strings.get("profile.saveAs.prompt"), "");
        if (name == null) {
            return;
        }
        DisplayProfile existing = find(pane, name);
        if (existing != null && !askReplace(owner, name)) {
            return;
        }
        DisplayProfile target = existing;
        if (target == null) {
            target = new DisplayProfile(name);
            pane.profiles().add(target);
        }
        target.copySettingsFrom(pane.profile());
        pane.selectProfile(target, true);
        pane.persistProfiles();
        pane.setTransientStatus(Strings.format("status.profileSaved", name), false);
    }

    /** Duplicate a profile under a unique name and persist. */
    static DisplayProfile duplicate(ProjectTabPlusPane pane, DisplayProfile source) {
        String base = Strings.format("profile.copySuffix", source.getName());
        String name = base;
        int n = 2;
        while (find(pane, name) != null) {
            name = base + " " + n++;
        }
        DisplayProfile copy = source.copy();
        copy.setName(name);
        pane.profiles().add(copy);
        pane.persistProfiles();
        return copy;
    }

    /** Rename a profile (never Default). */
    static void rename(ProjectTabPlusPane pane, DisplayProfile profile) {
        rename(pane, profile, pane.qupath().getStage());
    }

    static void rename(ProjectTabPlusPane pane, DisplayProfile profile, Window owner) {
        if (profile == null || ProfileStore.DEFAULT_NAME.equals(profile.getName())) {
            return;
        }
        String name = askName(owner, Strings.get("profile.rename.title"), Strings.get("profile.rename.prompt"),
                profile.getName());
        if (name == null || name.equals(profile.getName())) {
            return;
        }
        DisplayProfile existing = find(pane, name);
        if (existing != null) {
            if (!askReplace(owner, name)) {
                return;
            }
            pane.profiles().remove(existing);
            if (existing == pane.savedProfile()) {
                pane.selectProfile(profile, false);
            }
        }
        profile.setName(name);
        if (profile == pane.savedProfile()) {
            ProjectTabPlusPreferences.lastProfileProperty().set(name);
        }
        pane.refreshProfileCombo();
        pane.persistProfiles();
    }

    /** Delete a profile (never Default) after a confirmation that defaults to Cancel. */
    static void delete(ProjectTabPlusPane pane, DisplayProfile profile) {
        delete(pane, profile, pane.qupath().getStage());
    }

    static void delete(ProjectTabPlusPane pane, DisplayProfile profile, Window owner) {
        if (profile == null || ProfileStore.DEFAULT_NAME.equals(profile.getName())) {
            return;
        }
        ButtonType del = new ButtonType(Strings.get("profile.delete.button"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d1.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("profile.delete.title"))
                .contentText(Strings.format("profile.delete.body", profile.getName()))
                .owner(owner)
                .modality(Modality.WINDOW_MODAL)
                .buttons(del, cancel)
                .build();
        DialogButtons.setDefault(dialog, cancel);
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != del) {
            return;
        }
        // Switch away BEFORE removing: dropping the combo's current item makes the combo pick a
        // neighbor, which would raise the unsaved-changes prompt for the profile just deleted.
        if (profile == pane.savedProfile()) {
            pane.selectProfile(pane.profiles().get(0), true);
        }
        pane.profiles().remove(profile);
        pane.persistProfiles();
    }

    private static boolean askReplace(Window owner, String name) {
        ButtonType replace = new ButtonType(Strings.get("profile.exists.replace"), ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(Strings.get("d1.cancel"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("profile.exists.title"))
                .contentText(Strings.format("profile.exists.body", name))
                .owner(owner)
                .modality(Modality.WINDOW_MODAL)
                .buttons(replace, cancel)
                .build();
        DialogButtons.setDefault(dialog, cancel);
        Optional<ButtonType> r = dialog.showAndWait();
        return r.isPresent() && r.get() == replace;
    }

    /** @return a validated name, or null when canceled */
    private static String askName(Window owner, String title, String prompt, String initial) {
        while (true) {
            TextField field = new TextField(initial);
            field.setTooltip(Tooltips.of(Strings.get("profile.saveAs.tooltip")));
            field.setPrefColumnCount(24);
            Label label = new Label(prompt);
            label.setLabelFor(field);
            VBox box = new VBox(6, label, field);
            Dialog<ButtonType> dialog = Dialogs.builder()
                    .title(title)
                    .content(box)
                    .owner(owner)
                    .modality(Modality.WINDOW_MODAL)
                    .buttons(ButtonType.OK, ButtonType.CANCEL)
                    .build();
            dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(
                    Bindings.createBooleanBinding(() -> !isValidName(field.getText()), field.textProperty()));
            Optional<ButtonType> r = dialog.showAndWait();
            if (r.isEmpty() || r.get() != ButtonType.OK) {
                return null;
            }
            String name = field.getText() == null ? "" : field.getText().trim();
            if (name.isEmpty()) {
                Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("profile.name.empty"));
                initial = name;
                continue;
            }
            if (ProfileStore.DEFAULT_NAME.equals(name)) {
                Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("profile.name.reserved"));
                initial = name;
                continue;
            }
            return name;
        }
    }

    private static boolean isValidName(String text) {
        String name = text == null ? "" : text.trim();
        return !name.isEmpty() && !ProfileStore.DEFAULT_NAME.equals(name);
    }

    static DisplayProfile find(ProjectTabPlusPane pane, String name) {
        for (DisplayProfile p : pane.profiles()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }
}
