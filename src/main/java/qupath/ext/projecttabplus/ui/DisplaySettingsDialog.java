package qupath.ext.projecttabplus.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import qupath.ext.projecttabplus.model.DisplayProfile;
import qupath.ext.projecttabplus.model.DisplayProfile.ExpandOnLoad;
import qupath.ext.projecttabplus.model.DisplayProfile.IndicatorStyle;
import qupath.ext.projecttabplus.model.DisplayProfile.ThumbnailSize;
import qupath.ext.projecttabplus.model.GroupingKey;
import qupath.ext.projecttabplus.model.GroupingKey.Builtin;
import qupath.ext.projecttabplus.model.GroupingSpec;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * The non-modal display settings window: grouping levels, fields and appearance, applied live
 * to the tab as they change. Closes itself on project switch.
 */
final class DisplaySettingsDialog {

    private final ProjectTabPlusPane pane;
    private final Stage stage = new Stage();
    private final ListView<GroupingKey> levels = new ListView<>();
    private final MenuButton addLevel = new MenuButton(Strings.get("settings.addLevel"));
    private final Label counts = new Label();
    private final FlowPane fieldsPane = new FlowPane(12, 6);
    private final ToggleGroup expandGroup = new ToggleGroup();
    private final ToggleGroup thumbGroup = new ToggleGroup();
    private final ToggleGroup indicatorGroup = new ToggleGroup();
    private final PauseTransition countDebounce = new PauseTransition(Duration.millis(300));
    private boolean loading;

    DisplaySettingsDialog(ProjectTabPlusPane pane) {
        this.pane = pane;
        stage.initOwner(pane.qupath().getStage());
        stage.initModality(Modality.NONE);
        stage.setMinWidth(320);
        stage.setMinHeight(380);

        VBox content = new VBox(6,
                SectionBuilder.createSection(Strings.get("settings.section.grouping"), true, buildGrouping()),
                SectionBuilder.createSection(Strings.get("settings.section.fields"), false, fieldsPane),
                SectionBuilder.createSection(Strings.get("settings.section.appearance"), false, buildAppearance()));
        content.setPadding(new Insets(6));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        Button save = new Button(Strings.get("settings.saveToProfile"));
        save.setTooltip(Tooltips.of(Strings.get("settings.saveToProfile.tooltip")));
        save.disableProperty().bind(pane.profileModifiedProperty().not());
        save.setOnAction(e -> pane.saveLiveToProfile());
        Button close = new Button(Strings.get("settings.close"));
        close.setCancelButton(true);
        close.setOnAction(e -> stage.close());
        HBox buttons = new HBox(8, save, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(6));

        BorderPane root = new BorderPane(scroll);
        root.setBottom(buttons);
        stage.setScene(new Scene(root, 470, 480));
        stage.setOnHidden(e -> pane.settingsDialogClosed());
        pane.profileModifiedProperty().addListener((obs, o, n) -> updateTitle());
        countDebounce.setOnFinished(e -> recount());
        reloadFromLive();
    }

    private VBox buildGrouping() {
        Label label = new Label(Strings.get("settings.levels.label"));
        levels.setTooltip(Tooltips.of(Strings.get("settings.levels.tooltip")));
        levels.setPrefHeight(96);
        levels.setMinWidth(60);
        levels.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GroupingKey item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ". " + item.displayName());
            }
        });
        addLevel.setTooltip(Tooltips.of(Strings.get("settings.addLevel.tooltip")));
        addLevel.setOnShowing(e -> fillAddMenu());
        Button remove = new Button(Strings.get("settings.removeLevel"));
        remove.setTooltip(Tooltips.of(Strings.get("settings.removeLevel.tooltip")));
        remove.disableProperty().bind(levels.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            int i = levels.getSelectionModel().getSelectedIndex();
            if (i >= 0) {
                levels.getItems().remove(i);
                applyGrouping();
            }
        });
        Button up = new Button(Strings.get("settings.up"));
        up.setTooltip(Tooltips.of(Strings.get("settings.upDown.tooltip")));
        up.disableProperty().bind(levels.getSelectionModel().selectedIndexProperty().lessThan(1));
        up.setOnAction(e -> move(-1));
        Button down = new Button(Strings.get("settings.down"));
        down.setTooltip(Tooltips.of(Strings.get("settings.upDown.tooltip")));
        down.setOnAction(e -> move(1));
        for (Button b : new Button[] {remove, up, down}) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        addLevel.setMaxWidth(Double.MAX_VALUE);
        addLevel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox side = new VBox(4, addLevel, remove, up, down);
        side.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox row = new HBox(6, levels, side);
        HBox.setHgrow(levels, Priority.ALWAYS);
        counts.setTooltip(Tooltips.of(Strings.get("settings.counts.tooltip")));
        counts.setWrapText(true);

        FlowPane expandRow = radioRow(Strings.get("settings.expand"));
        for (ExpandOnLoad v : ExpandOnLoad.values()) {
            RadioButton rb = new RadioButton(Strings.get("expand." + v.name()));
            rb.setToggleGroup(expandGroup);
            rb.setUserData(v);
            rb.setTooltip(Tooltips.of(Strings.get("settings.expand.tooltip")));
            rb.setOnAction(e -> {
                if (!loading) {
                    pane.profile().setExpandOnLoad(v);
                    pane.onLiveProfileChanged(true);
                }
            });
            expandRow.getChildren().add(rb);
        }
        return new VBox(6, label, row, counts, expandRow);
    }

    private VBox buildAppearance() {
        FlowPane thumbRow = radioRow(Strings.get("settings.thumbnail"));
        for (ThumbnailSize v : ThumbnailSize.values()) {
            RadioButton rb = new RadioButton(Strings.get("thumbnailSize." + v.name()));
            rb.setToggleGroup(thumbGroup);
            rb.setUserData(v);
            rb.setTooltip(Tooltips.of(Strings.get("settings.thumbnail.tooltip")));
            rb.setOnAction(e -> {
                if (!loading) {
                    pane.profile().setThumbnailSize(v);
                    pane.onLiveProfileChanged(false);
                }
            });
            thumbRow.getChildren().add(rb);
        }
        FlowPane indRow = radioRow(Strings.get("settings.indicators"));
        for (IndicatorStyle v : IndicatorStyle.values()) {
            RadioButton rb = new RadioButton(Strings.get("indicators." + v.name()));
            rb.setToggleGroup(indicatorGroup);
            rb.setUserData(v);
            rb.setTooltip(Tooltips.of(Strings.get("settings.indicators.tooltip")));
            rb.setOnAction(e -> {
                if (!loading) {
                    pane.profile().setIndicators(v);
                    pane.onLiveProfileChanged(false);
                }
            });
            indRow.getChildren().add(rb);
        }
        return new VBox(6, thumbRow, indRow);
    }

    /** A label followed by radio buttons that wrap onto a second line when the dialog is narrow. */
    private static FlowPane radioRow(String labelText) {
        Label label = new Label(labelText);
        label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        FlowPane row = new FlowPane(8, 4, label);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setRowValignment(javafx.geometry.VPos.CENTER);
        return row;
    }

    private void fillAddMenu() {
        addLevel.getItems().clear();
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        List<GroupingKey> used = levels.getItems();
        for (Builtin b : Builtin.values()) {
            if (masked && b.isNameLevel()) {
                continue;
            }
            addLevel.getItems().add(levelItem(GroupingKey.builtin(b), used));
        }
        addLevel.getItems().add(new SeparatorMenuItem());
        if (pane.metadataKeys().isEmpty()) {
            MenuItem none = new MenuItem(Strings.get("settings.noKeys"));
            none.setDisable(true);
            addLevel.getItems().add(none);
        }
        for (String key : pane.metadataKeys()) {
            addLevel.getItems().add(levelItem(GroupingKey.metadata(key), used));
        }
    }

    private MenuItem levelItem(GroupingKey key, List<GroupingKey> used) {
        MenuItem item = new MenuItem(key.displayName());
        item.setMnemonicParsing(false);
        item.setDisable(used.contains(key));
        item.setOnAction(e -> {
            levels.getItems().add(key);
            applyGrouping();
        });
        return item;
    }

    private void move(int delta) {
        int i = levels.getSelectionModel().getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= levels.getItems().size()) {
            return;
        }
        GroupingKey k = levels.getItems().remove(i);
        levels.getItems().add(j, k);
        levels.getSelectionModel().select(j);
        applyGrouping();
    }

    private void applyGrouping() {
        pane.profile().setGroupingSpec(new GroupingSpec(new ArrayList<>(levels.getItems())));
        pane.onLiveProfileChanged(true);
        countDebounce.playFromStart();
    }

    private void applyFields() {
        List<String> selected = new ArrayList<>();
        for (var node : fieldsPane.getChildren()) {
            if (node instanceof CheckBox cb && cb.isSelected()) {
                selected.add(((GroupingKey) cb.getUserData()).serialized());
            }
        }
        pane.profile().setFields(selected);
        pane.onLiveProfileChanged(true);
    }

    private void recount() {
        GroupingSpec spec = new GroupingSpec(new ArrayList<>(levels.getItems()));
        int total = pane.allRows().size();
        if (spec.isFlat()) {
            counts.setText(Strings.format("settings.counts.flat", total));
            return;
        }
        pane.countGroups(spec, list -> {
            List<String> parts = new ArrayList<>();
            for (Integer n : list) {
                parts.add(Integer.toString(n));
            }
            parts.add(Strings.format("settings.counts.images", total));
            int leaves = list.isEmpty() ? 1 : list.get(list.size() - 1);
            counts.setText(Strings.format("settings.counts", String.join(" > ", parts), leaves));
        });
    }

    /** Repopulate every control from the pane's live profile. */
    void reloadFromLive() {
        loading = true;
        try {
            DisplayProfile p = pane.profile();
            levels.getItems().setAll(p.groupingSpec().levels());
            rebuildFields(p);
            selectToggle(expandGroup, p.getExpandOnLoad());
            selectToggle(thumbGroup, p.getThumbnailSize());
            selectToggle(indicatorGroup, p.getIndicators());
            updateTitle();
            countDebounce.playFromStart();
        } finally {
            loading = false;
        }
    }

    private void rebuildFields(DisplayProfile p) {
        fieldsPane.getChildren().clear();
        List<GroupingKey> selected = p.fieldKeys();
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        for (Builtin b : Builtin.values()) {
            if (masked && b.isNameLevel()) {
                continue;
            }
            GroupingKey key = GroupingKey.builtin(b);
            fieldsPane.getChildren().add(fieldBox(key, b.displayName() + " " + Strings.get("settings.field.builtinSuffix"),
                    selected.contains(key)));
        }
        for (String k : pane.metadataKeys()) {
            GroupingKey key = GroupingKey.metadata(k);
            fieldsPane.getChildren().add(fieldBox(key, k, selected.contains(key)));
        }
    }

    private CheckBox fieldBox(GroupingKey key, String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setUserData(key);
        cb.setSelected(selected);
        cb.setTooltip(Tooltips.of(Strings.get("settings.field.tooltip")));
        cb.setOnAction(e -> {
            if (!loading) {
                applyFields();
            }
        });
        return cb;
    }

    private static void selectToggle(ToggleGroup group, Object value) {
        for (var toggle : group.getToggles()) {
            toggle.setSelected(toggle.getUserData() == value);
        }
    }

    private void updateTitle() {
        String name = pane.savedProfile() == null ? "" : pane.savedProfile().getName();
        if (pane.profileModifiedProperty().get()) {
            name = Strings.format("profile.modifiedSuffix", name);
        }
        stage.setTitle(Strings.format("settings.title", name));
    }

    /** The project's keys changed: rebuild the field boxes and recount. */
    void projectChanged() {
        loading = true;
        try {
            rebuildFields(pane.profile());
        } finally {
            loading = false;
        }
        countDebounce.playFromStart();
    }

    void show() {
        if (!stage.isShowing()) {
            stage.show();
        } else {
            stage.toFront();
        }
    }

    void close() {
        stage.close();
    }
}
