package qupath.ext.projecttabplus.ui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import qupath.ext.projecttabplus.model.ImageRow;
import qupath.ext.projecttabplus.model.names.HideExtensionRule;
import qupath.ext.projecttabplus.model.names.HideTextRule;
import qupath.ext.projecttabplus.model.names.KeepPiecesRule;
import qupath.ext.projecttabplus.model.names.NamePreview;
import qupath.ext.projecttabplus.model.names.NameRule;
import qupath.ext.projecttabplus.model.names.NameRuleSet;
import qupath.ext.projecttabplus.model.names.NameRulesJson;
import qupath.ext.projecttabplus.model.names.PrefixRule;
import qupath.ext.projecttabplus.model.names.ShortName;
import qupath.ext.projecttabplus.model.names.Suggestions;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.Project;

// The live preview (debounced recompute off the FX thread, a count line over every entry) follows
// RegexExtractionDialog and RegexPreviewModel in qupath-extension-project-metadata-browser
// (GPL-3.0, github.com/uw-loci/qupath-extension-project-metadata-browser), rewritten 2026-11.

/**
 * The non-modal name rules window: the rule list with its editor above, every project name with
 * its short name below. Edits change a working copy that the table previews; Apply hands it to
 * the tab and the project. Closes itself on project switch.
 */
final class NameRulesDialog implements NameRuleEditors.Host {

    /** Which rows the table lists. */
    enum Filter {
        ALL("names.filter.all"), CHANGED("names.filter.changed"), UNCHANGED("names.filter.unchanged"),
        LOOK("names.filter.look"), SKIPPED("names.filter.skipped"), FEWER("names.filter.fewer");

        private final String key;

        Filter(String key) {
            this.key = key;
        }
    }

    /** A filter choice with its live count. */
    record FilterItem(Filter filter, int count) {

        @Override
        public String toString() {
            return Strings.format(filter.key, count);
        }
    }

    /** One project entry in the table; updated in place by each preview. */
    static final class PreviewRow {

        final int index;
        final String id;
        final String full;
        ShortName shortName;
        int shared = 1;
        boolean fewer;
        boolean skipped;
        String input;

        PreviewRow(int index, String id, String full) {
            this.index = index;
            this.id = id;
            this.full = full;
            this.shortName = ShortName.unchanged(full);
        }

        boolean look() {
            return shared > 1 || shortName.kept();
        }

        /** @return the Note text */
        String note() {
            if (shared > 1) {
                return Strings.format("names.note.shared", shared);
            }
            if (shortName.emptyFallback()) {
                return Strings.get("names.note.kept");
            }
            if (shortName.noPiece()) {
                return Strings.get("names.note.noPiece");
            }
            if (fewer) {
                return Strings.get("names.note.fewer");
            }
            if (!shortName.changed()) {
                return Strings.get("names.note.unchanged");
            }
            return "";
        }
    }

    static final String TITLE_KEY = "names.title";
    private static final String HELP_URL = "https://github.com/uw-loci/qupath-extension-project-tab-plus/blob/main/"
            + "docs/user-guide.md#shorten-how-names-are-shown";
    private static final Duration DEBOUNCE = Duration.millis(250);

    private final ProjectTabPlusPane pane;
    private final Stage stage = new Stage();
    private final ObservableList<NameRule> rules = FXCollections.observableArrayList();
    private NameRuleSet applied;

    private final ListView<NameRule> list = new ListView<>(rules);
    private final MenuButton addRule = new MenuButton(Strings.get("names.add"));
    private final Button suggest = new Button(Strings.get("names.suggest"));
    private final Button remove = new Button(Strings.get("names.remove"));
    private final Button up = new Button(Strings.get("names.up"));
    private final Button down = new Button(Strings.get("names.down"));
    private final CheckBox showTruncated = new CheckBox();
    private final StackPane editorHolder = new StackPane();
    private final ScrollPane editorScroll = new ScrollPane(editorHolder);
    private final Label counts = new Label();
    private final ComboBox<FilterItem> filter = new ComboBox<>();
    private final Label tableHint = new Label();
    private final ObservableList<PreviewRow> rows = FXCollections.observableArrayList();
    private final FilteredList<PreviewRow> filtered = new FilteredList<>(rows);
    private final TableView<PreviewRow> table = new TableView<>();
    private final Button copy = new Button(Strings.get("names.copy"));
    private final Button paste = new Button(Strings.get("names.paste"));
    private final Label status = new Label();
    private final Button reset = new Button(Strings.get("names.reset"));
    private final Button apply = new Button(Strings.get("names.apply"));
    private final Button close = new Button(Strings.get("names.close"));
    private final Label readOnlyBanner = new Label(Strings.get("names.banner.readOnly"));
    private final Label maskedBanner = new Label(Strings.get("names.banner.masked"));
    private final SplitPane split = new SplitPane();
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private final ChangeListener<Boolean> maskListener = (obs, o, n) -> onMaskChanged();

    private NameRuleEditors.Editor editor;
    private NameRuleEditors.SuggestionCard card;
    private boolean cardMode;
    private NamePreview last;
    private int previewGeneration;
    private boolean replacing;
    private boolean updatingFilter;
    private String eventText;
    private boolean eventWarning;
    private boolean closingForProject;

    NameRulesDialog(ProjectTabPlusPane pane) {
        this.pane = pane;
        this.applied = pane.nameRulesProperty().get();
        stage.initOwner(pane.qupath().getStage());
        stage.initModality(Modality.NONE);
        stage.setTitle(Strings.get(TITLE_KEY));
        stage.setMinWidth(640);
        stage.setMinHeight(520);

        VBox ruleColumn = buildRuleColumn();
        editorScroll.setFitToWidth(true);
        editorScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        editorHolder.setPadding(new Insets(4, 8, 4, 8));
        editorHolder.setAlignment(Pos.TOP_LEFT);
        // A small preferred width: the editor takes the spare width without squeezing the rule list.
        editorScroll.setPrefWidth(420);
        editorScroll.setMinWidth(300);
        HBox.setHgrow(editorScroll, Priority.ALWAYS);
        HBox band = new HBox(8, ruleColumn, editorScroll);
        band.setPadding(new Insets(6));
        VBox.setVgrow(band, Priority.ALWAYS);
        styleBanner(readOnlyBanner);
        readOnlyBanner.setVisible(false);
        readOnlyBanner.setManaged(false);
        VBox top = new VBox(4, readOnlyBanner, band);
        top.setMinHeight(150);

        VBox tableSection = buildTableSection();
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().setAll(top, tableSection);
        SplitPane.setResizableWithParent(top, false);

        styleBanner(maskedBanner);
        StackPane.setAlignment(maskedBanner, Pos.TOP_CENTER);
        StackPane.setMargin(maskedBanner, new Insets(24));
        StackPane center = new StackPane(split, maskedBanner);

        BorderPane root = new BorderPane(center);
        root.setBottom(buildBottomBar());
        root.getStyleClass().addAll(ProjectTabPlusPane.STYLE_CLASS, "ptp-name-rules");
        Scene scene = new Scene(root, 900, 680);
        scene.getStylesheets().add(Objects.requireNonNull(
                ProjectTabPlusPane.class.getResource("project-tab-plus.css")).toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            e.consume();
            requestClose();
        });
        stage.setOnHidden(e -> {
            PathPrefs.maskImageNamesProperty().removeListener(maskListener);
            showTruncated.selectedProperty().unbindBidirectional(pane.showTruncatedNamesProperty());
            debounce.stop();
            pane.nameRulesDialogClosed(this);
        });
        debounce.setOnFinished(e -> runPreview());
        PathPrefs.maskImageNamesProperty().addListener(maskListener);

        rules.setAll(applied.rules());
        loadRows();
        checkWritable();
        onMaskChanged();
        if (rules.isEmpty()) {
            enterCard();
        } else {
            list.getSelectionModel().select(0);
        }
        refreshState();
    }

    // ------------------------------------------------------------------ construction

    private static void styleBanner(Label l) {
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setPadding(new Insets(6, 8, 6, 8));
        l.setStyle("-fx-background-color: " + ThemeColors.INFO_BG + "; -fx-background-radius: 3;");
    }

    private VBox buildRuleColumn() {
        Label label = new Label(Strings.get("names.list.label"));
        label.setLabelFor(list);
        list.setTooltip(Tooltips.of(Strings.get("names.list.tooltip")));
        Label empty = NameRuleEditors.wrapped(Strings.get("names.list.empty"));
        empty.setPadding(new Insets(8));
        list.setPlaceholder(empty);
        list.setCellFactory(lv -> new RuleCell());
        list.setPrefHeight(150);
        VBox.setVgrow(list, Priority.ALWAYS);
        list.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            if (!replacing) {
                onRuleSelected(n.intValue());
            }
        });
        list.addEventFilter(KeyEvent.KEY_PRESSED, this::onListKey);

        addRule.setTooltip(Tooltips.of(Strings.get("names.add.tooltip")));
        addRule.getItems().setAll(
                kindItem("names.kind.prefix", () -> new PrefixRule("", true)),
                kindItem("names.kind.text", () -> new HideTextRule("", false, true)),
                kindItem("names.kind.ext", () -> new HideExtensionRule(true, true)),
                kindItem("names.kind.pieces", () -> new KeepPiecesRule("_", null, 0, true, true, true)));
        suggest.setTooltip(Tooltips.of(Strings.get("names.suggest.tooltip")));
        suggest.setOnAction(e -> enterCard());
        remove.setTooltip(Tooltips.of(Strings.get("names.remove.tooltip")));
        remove.setOnAction(e -> removeSelected());
        up.setTooltip(Tooltips.of(Strings.get("names.up.tooltip")));
        up.setOnAction(e -> move(-1));
        down.setTooltip(Tooltips.of(Strings.get("names.down.tooltip")));
        down.setOnAction(e -> move(1));
        remove.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        up.disableProperty().bind(list.getSelectionModel().selectedIndexProperty().lessThan(1));
        down.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            int i = list.getSelectionModel().getSelectedIndex();
            return i < 0 || i >= rules.size() - 1;
        }, list.getSelectionModel().selectedIndexProperty(), rules));
        FlowPane buttons = new FlowPane(4, 4, addRule, suggest, remove, up, down);

        showTruncated.setMnemonicParsing(false);
        showTruncated.setWrapText(true);
        showTruncated.setTooltip(Tooltips.of(Strings.get("names.showTruncated.tooltip")));
        showTruncated.selectedProperty().bindBidirectional(pane.showTruncatedNamesProperty());

        VBox column = new VBox(4, label, list, buttons, showTruncated);
        column.setPrefWidth(370);
        column.setMinWidth(260);
        return column;
    }

    private MenuItem kindItem(String key, java.util.function.Supplier<NameRule> make) {
        MenuItem item = new MenuItem(Strings.get(key));
        item.setOnAction(e -> insertRule(rules.size(), make.get()));
        return item;
    }

    private VBox buildTableSection() {
        counts.setWrapText(true);
        counts.setMinHeight(Region.USE_PREF_SIZE);
        counts.setTooltip(Tooltips.of(Strings.get("names.counts.tooltip")));
        Label show = new Label(Strings.get("names.filter.label"));
        show.setLabelFor(filter);
        show.setMinWidth(Region.USE_PREF_SIZE);
        filter.setTooltip(Tooltips.of(Strings.get("names.filter.tooltip")));
        filter.valueProperty().addListener((obs, o, n) -> {
            if (!updatingFilter) {
                applyFilter();
            }
        });
        tableHint.setWrapText(true);
        ThemeColors.textFill(tableHint, ThemeColors.MUTED);
        HBox filterRow = new HBox(8, show, filter, tableHint);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        TableColumn<PreviewRow, PreviewRow> fullCol = new TableColumn<>(Strings.get("names.col.full"));
        fullCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        fullCol.setCellFactory(c -> new HighlightedNameCell(this));
        fullCol.setComparator((a, b) -> a.full.compareToIgnoreCase(b.full));
        TableColumn<PreviewRow, String> shownCol = new TableColumn<>(Strings.get("names.col.shown"));
        shownCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().shortName.display()));
        shownCol.setCellFactory(c -> new ShownCell());
        TableColumn<PreviewRow, String> noteCol = new TableColumn<>(Strings.get("names.col.note"));
        noteCol.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().note()));
        noteCol.setCellFactory(c -> new NoteCell());
        header(fullCol, "names.col.full", "names.col.full.tooltip", 580, 200);
        header(shownCol, "names.col.shown", "names.col.shown.tooltip", 280, 150);
        header(noteCol, "names.col.note", "names.col.note.tooltip", 140, 100);
        table.getColumns().setAll(List.of(fullCol, shownCol, noteCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        SortedList<PreviewRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(new Label(Strings.get("names.filter.empty")));
        table.setMinHeight(140);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && editor instanceof NameRuleEditors.PiecesEditor pe) {
                pe.setSample(n.input);
            } else if (last != null && editor != null) {
                editor.update(last);
            }
        });
        table.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.CONTEXT_MENU || e.getCode() == KeyCode.F10 && e.isShiftDown()) {
                PreviewRow row = table.getSelectionModel().getSelectedItem();
                if (row != null) {
                    HighlightedNameCell.showKeyboardMenu(this, row, table);
                }
                e.consume();
            }
        });
        VBox section = new VBox(6, counts, filterRow, table);
        section.setPadding(new Insets(6));
        section.setMinHeight(220);
        return section;
    }

    /** Column headers are labels, so an underscore in a heading never becomes a mnemonic. */
    private static void header(TableColumn<PreviewRow, ?> col, String textKey, String tooltipKey, double pref, double min) {
        Label l = new Label(Strings.get(textKey));
        l.setMnemonicParsing(false);
        l.setTooltip(Tooltips.of(Strings.get(tooltipKey)));
        col.setText(null);
        col.setGraphic(l);
        col.setPrefWidth(pref);
        col.setMinWidth(min);
    }

    private HBox buildBottomBar() {
        copy.setTooltip(Tooltips.of(Strings.get("names.copy.tooltip")));
        copy.setOnAction(e -> copyRules());
        paste.setTooltip(Tooltips.of(Strings.get("names.paste.tooltip")));
        paste.setOnAction(e -> pasteRules());
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setMinWidth(0);
        status.setMnemonicParsing(false);
        HBox.setHgrow(status, Priority.ALWAYS);
        Button help = new Button("?");
        help.setTooltip(Tooltips.of(Strings.get("names.help.tooltip")));
        help.setAccessibleText(Strings.get("names.help.accessible"));
        help.setOnAction(e -> QuPathGUI.openInBrowser(HELP_URL));
        reset.setTooltip(Tooltips.of(Strings.get("names.reset.tooltip")));
        reset.setOnAction(e -> resetRules());
        apply.setTooltip(Tooltips.of(Strings.get("names.apply.tooltip")));
        apply.setDefaultButton(true);
        apply.setOnAction(e -> applyRules());
        close.setTooltip(Tooltips.of(Strings.get("names.close.tooltip")));
        close.setCancelButton(true);
        close.setOnAction(e -> requestClose());
        for (Button b : new Button[] {copy, paste, help, reset, apply, close}) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        HBox bar = new HBox(6, copy, paste, status, help, reset, apply, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6));
        return bar;
    }

    // ------------------------------------------------------------------ rule list

    private final class RuleCell extends ListCell<NameRule> {

        private final CheckBox check = new CheckBox();
        private final Label text = new Label();
        private final Label count = new Label();
        private final HBox box;

        RuleCell() {
            setMnemonicParsing(false);
            text.setMnemonicParsing(false);
            text.setMinWidth(0);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);
            count.setMinWidth(Region.USE_PREF_SIZE);
            check.setTooltip(Tooltips.of(Strings.get("names.ruleCheck.tooltip")));
            check.setOnAction(e -> {
                int i = getIndex();
                if (i >= 0 && i < rules.size()) {
                    replaceRule(i, rules.get(i).withOn(check.isSelected()));
                }
            });
            box = new HBox(6, check, text, count);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMinWidth(0);
            setPrefWidth(1);
        }

        @Override
        protected void updateItem(NameRule item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            int i = getIndex();
            String sentence = sentence(item);
            check.setSelected(item.on());
            check.setAccessibleText(sentence + ", " + Strings.get(item.on() ? "names.a11y.on" : "names.a11y.off"));
            text.setText((i + 1) + ". " + (item.on() ? "" : Strings.get("names.rule.off") + " ") + sentence);
            text.getStyleClass().remove("ptp-rule-off");
            if (!item.on()) {
                text.getStyleClass().add("ptp-rule-off");
            }
            int changed = last != null ? last.report().changedBy(i) : 0;
            NameRuleEditors.warn(count, false);
            if (!item.finished()) {
                count.setText(Strings.get("names.rule.unfinished"));
                NameRuleEditors.warn(count, true);
            } else if (!item.on()) {
                count.setText("");
            } else if (last == null) {
                count.setText("");
            } else if (changed == 0) {
                count.setText(Strings.get("names.rule.noEffect"));
                NameRuleEditors.warn(count, true);
            } else {
                count.setText(Integer.toString(changed));
            }
            setTooltip(Tooltips.of(Strings.format("names.rule.tooltip", sentence, changed, rows.size())));
            setGraphic(box);
        }
    }

    /** @return the rule's sentence for the list, the tooltip and the piece menu */
    static String sentence(NameRule rule) {
        return switch (rule) {
            case PrefixRule p -> Strings.format("names.rule.prefix", p.prefix());
            case HideTextRule t -> Strings.format(t.ignoreCase() ? "names.rule.textIgnore" : "names.rule.text", t.text());
            case HideExtensionRule e -> Strings.get(e.doubleExtension() ? "names.rule.extDouble" : "names.rule.ext");
            case KeepPiecesRule k -> Strings.format("names.rule.pieces", pieceList(k),
                    NameRuleEditors.separatorLabel(k.separator()).trim());
        };
    }

    private static String pieceList(KeepPiecesRule k) {
        List<String> parts = new ArrayList<>();
        for (int i : k.kept()) {
            parts.add(k.keepLater() && i == k.pieces() - 1
                    ? Strings.format("names.rule.pieces.later", i + 1) : Integer.toString(i + 1));
        }
        if (k.keepLast()) {
            parts.add(Strings.get("names.rule.pieces.lastWord"));
        }
        if (parts.isEmpty()) {
            return Strings.get("names.rule.pieces.none");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Strings.format("names.rule.pieces.and", String.join(", ", parts.subList(0, parts.size() - 1)),
                parts.get(parts.size() - 1));
    }

    private void onListKey(KeyEvent e) {
        int i = list.getSelectionModel().getSelectedIndex();
        if (i < 0) {
            return;
        }
        if (e.getCode() == KeyCode.SPACE) {
            replaceRule(i, rules.get(i).withOn(!rules.get(i).on()));
            e.consume();
        } else if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
            removeSelected();
            e.consume();
        } else if (e.isShortcutDown() && (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN)) {
            move(e.getCode() == KeyCode.UP ? -1 : 1);
            e.consume();
        }
    }

    private void onRuleSelected(int i) {
        if (i < 0) {
            if (!cardMode) {
                editor = null;
                editorHolder.getChildren().clear();
            }
            schedulePreview();
            return;
        }
        cardMode = false;
        card = null;
        editor = NameRuleEditors.create(this, i);
        editorHolder.getChildren().setAll(editor.node());
        tableHint.setText(Strings.get(editor instanceof NameRuleEditors.PiecesEditor
                ? "names.hint.sample" : "names.hint.piece"));
        if (last != null && last.selected() == i) {
            editor.update(last);
        }
        schedulePreview();
    }

    private void removeSelected() {
        int i = list.getSelectionModel().getSelectedIndex();
        if (i < 0) {
            return;
        }
        rules.remove(i);
        edited();
        if (rules.isEmpty()) {
            enterCard();
        } else {
            list.getSelectionModel().select(Math.min(i, rules.size() - 1));
        }
    }

    private void move(int delta) {
        int i = list.getSelectionModel().getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= rules.size()) {
            return;
        }
        NameRule r = rules.remove(i);
        rules.add(j, r);
        list.getSelectionModel().select(j);
        edited();
    }

    // ------------------------------------------------------------------ Host

    @Override
    public NameRule rule(int index) {
        return rules.get(index);
    }

    @Override
    public void replaceRule(int index, NameRule rule) {
        if (index < 0 || index >= rules.size() || rules.get(index).equals(rule)) {
            return;
        }
        int selected = list.getSelectionModel().getSelectedIndex();
        replacing = true;
        try {
            rules.set(index, rule);
            if (selected >= 0) {
                list.getSelectionModel().select(selected);
            }
        } finally {
            replacing = false;
        }
        edited();
    }

    @Override
    public void insertRule(int index, NameRule rule) {
        int at = Math.max(0, Math.min(index, rules.size()));
        rules.add(at, rule);
        cardMode = false;
        card = null;
        list.getSelectionModel().clearSelection();
        list.getSelectionModel().select(at);
        list.scrollTo(at);
        edited();
        focusEditor();
    }

    private void focusEditor() {
        if (editor instanceof NameRuleEditors.PrefixEditor pe) {
            pe.field().requestFocus();
        } else if (editor instanceof NameRuleEditors.TextEditor te) {
            te.field().requestFocus();
        }
    }

    @Override
    public void showFilter(Filter f) {
        for (FilterItem item : filter.getItems()) {
            if (item.filter() == f) {
                filter.setValue(item);
                return;
            }
        }
    }

    @Override
    public int selectedRow() {
        PreviewRow r = table.getSelectionModel().getSelectedItem();
        return r == null ? -1 : r.index;
    }

    @Override
    public NameRuleSet workingSet() {
        return new NameRuleSet(true, new ArrayList<>(rules));
    }

    @Override
    public void suggestPrefix(int index, Consumer<Suggestions.Prefix> onDone) {
        NameRuleSet set = workingSet();
        List<String> names = fullNames();
        pane.previewNames(() -> {
            List<String> in = new ArrayList<>(names.size());
            for (String n : names) {
                in.add(set.applyBefore(n, index).text());
            }
            return Optional.ofNullable(Suggestions.prefix(in));
        }, p -> onDone.accept(p.orElse(null)));
    }

    // ------------------------------------------------------------------ suggestions

    private void enterCard() {
        cardMode = true;
        editor = null;
        replacing = true;
        try {
            list.getSelectionModel().clearSelection();
        } finally {
            replacing = false;
        }
        tableHint.setText(Strings.get("names.hint.piece"));
        NameRuleSet set = workingSet();
        List<String> names = fullNames();
        pane.previewNames(() -> {
            List<String> shown = new ArrayList<>(names.size());
            for (String n : names) {
                shown.add(set.apply(n).display());
            }
            return Suggestions.suggest(shown);
        }, found -> {
            if (!cardMode) {
                return;
            }
            card = new NameRuleEditors.SuggestionCard(found, names.size(), !rules.isEmpty(), this::schedulePreview,
                    this::addSuggestions, this::leaveCard);
            editorHolder.getChildren().setAll(card.node());
            schedulePreview();
        });
    }

    private void addSuggestions(List<NameRule> ticked) {
        if (ticked.isEmpty()) {
            return;
        }
        int first = rules.size();
        rules.addAll(ticked);
        cardMode = false;
        card = null;
        list.getSelectionModel().select(first);
        edited();
        setEvent(Strings.plural("names.status.added.one", "names.status.added.many", ticked.size()), false);
    }

    private void leaveCard() {
        cardMode = false;
        card = null;
        if (!rules.isEmpty()) {
            list.getSelectionModel().select(0);
        }
    }

    // ------------------------------------------------------------------ preview

    private List<String> fullNames() {
        List<String> names = new ArrayList<>(rows.size());
        for (PreviewRow r : rows) {
            names.add(r.full);
        }
        return names;
    }

    /** Reload the rows from the tab's last build, keeping them when nothing changed. */
    private void loadRows() {
        List<ImageRow> source = pane.allRows();
        boolean same = source.size() == rows.size();
        for (int i = 0; same && i < source.size(); i++) {
            same = source.get(i).id().equals(rows.get(i).id) && source.get(i).info().name().equals(rows.get(i).full);
        }
        if (same) {
            return;
        }
        List<PreviewRow> fresh = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            ImageRow r = source.get(i);
            fresh.add(new PreviewRow(i, r.id(), r.info().name()));
        }
        rows.setAll(fresh);
        schedulePreview();
    }

    /** The tab rebuilt: pick up added, removed or renamed images. Masked names are never loaded. */
    void entriesChanged() {
        if (!PathPrefs.maskImageNamesProperty().get()) {
            loadRows();
        }
    }

    private void schedulePreview() {
        debounce.playFromStart();
    }

    private NameRuleSet previewSet() {
        List<NameRule> all = new ArrayList<>(rules);
        if (cardMode && card != null) {
            all.addAll(card.ticked());
        }
        return new NameRuleSet(true, all);
    }

    private void runPreview() {
        NameRuleSet set = previewSet();
        List<String> names = fullNames();
        int sel = cardMode ? -1 : list.getSelectionModel().getSelectedIndex();
        int gen = ++previewGeneration;
        pane.previewNames(() -> NamePreview.compute(set, names, sel), p -> {
            if (gen == previewGeneration && p.report().total() == rows.size()) {
                applyPreview(p);
            }
        });
    }

    private void applyPreview(NamePreview p) {
        last = p;
        NameRuleSet.Report report = p.report();
        int sel = p.selected();
        NameRule selRule = sel >= 0 && sel < rules.size() ? rules.get(sel) : null;
        for (PreviewRow r : rows) {
            r.shortName = report.results().get(r.index);
            r.shared = report.sharedCount(r.shortName.display());
            r.input = p.inputs().isEmpty() ? null : p.inputs().get(r.index);
            r.fewer = selRule instanceof KeepPiecesRule k && p.pieceCount(r.index) < k.pieces();
            r.skipped = selRule != null && !(selRule instanceof KeepPiecesRule)
                    && !NameRuleEditors.changes(selRule, r.input);
        }
        counts.setText(countLine(report));
        updateFilterItems(selRule);
        applyFilter();
        table.refresh();
        list.refresh();
        if (editor != null && sel == editor.index) {
            editor.update(p);
        }
        refreshState();
    }

    private String countLine(NameRuleSet.Report r) {
        StringBuilder sb = new StringBuilder();
        int pending = cardMode && card != null ? card.ticked().size() : 0;
        if (pending > 0) {
            sb.append(Strings.plural("names.count.preview.one", "names.count.preview.many", pending,
                    r.changed(), r.total()));
        } else if (r.changed() == 0) {
            sb.append(Strings.get("names.count.none"));
        } else {
            sb.append(Strings.format("names.count.changed", r.changed(), r.total()));
        }
        if (r.changed() > 0) {
            sb.append(' ').append(r.sharedNames() == 0 ? Strings.get("names.count.unique")
                    : Strings.plural("names.count.shared.one", "names.count.shared.many", r.sharedNames(), r.sharedRows()));
            if (r.kept() > 0) {
                sb.append(' ').append(Strings.plural("names.count.kept.one", "names.count.kept.many", r.kept()));
            }
            sb.append(' ').append(Strings.format("names.count.length", r.longestAfter(), r.longestBefore()));
        }
        return sb.toString();
    }

    private void updateFilterItems(NameRule selRule) {
        int changed = 0;
        int look = 0;
        int skipped = 0;
        int fewer = 0;
        for (PreviewRow r : rows) {
            changed += r.shortName.changed() ? 1 : 0;
            look += r.look() ? 1 : 0;
            skipped += r.skipped ? 1 : 0;
            fewer += r.fewer ? 1 : 0;
        }
        List<FilterItem> items = new ArrayList<>(List.of(new FilterItem(Filter.ALL, rows.size()),
                new FilterItem(Filter.CHANGED, changed), new FilterItem(Filter.UNCHANGED, rows.size() - changed),
                new FilterItem(Filter.LOOK, look)));
        if (selRule instanceof KeepPiecesRule) {
            items.add(new FilterItem(Filter.FEWER, fewer));
        } else if (selRule != null) {
            items.add(new FilterItem(Filter.SKIPPED, skipped));
        }
        Filter current = filter.getValue() == null ? Filter.ALL : filter.getValue().filter();
        updatingFilter = true;
        try {
            filter.getItems().setAll(items);
            FilterItem match = items.stream().filter(i -> i.filter() == current).findFirst().orElse(items.get(0));
            filter.setValue(match);
        } finally {
            updatingFilter = false;
        }
    }

    private void applyFilter() {
        Filter f = filter.getValue() == null ? Filter.ALL : filter.getValue().filter();
        Predicate<PreviewRow> p = switch (f) {
            case ALL -> r -> true;
            case CHANGED -> r -> r.shortName.changed();
            case UNCHANGED -> r -> !r.shortName.changed();
            case LOOK -> PreviewRow::look;
            case SKIPPED -> r -> r.skipped;
            case FEWER -> r -> r.fewer;
        };
        filtered.setPredicate(r -> p.test(r));
    }

    // ------------------------------------------------------------------ state

    private boolean isDirty() {
        return !new ArrayList<>(rules).equals(applied.rules());
    }

    private boolean hasUnfinished() {
        return rules.stream().anyMatch(r -> !r.finished());
    }

    private void edited() {
        eventText = null;
        schedulePreview();
        list.refresh();
        refreshState();
    }

    private void setEvent(String text, boolean warning) {
        eventText = text;
        eventWarning = warning;
        refreshState();
    }

    private void refreshState() {
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        boolean dirty = isDirty();
        boolean unfinished = hasUnfinished();
        apply.setDisable(masked || !dirty || unfinished);
        reset.setDisable(masked || !dirty);
        copy.setDisable(masked || rules.stream().noneMatch(NameRule::finished));
        paste.setDisable(masked);
        String text;
        boolean warning;
        if (eventText != null) {
            text = eventText;
            warning = eventWarning;
        } else if (unfinished) {
            text = Strings.get("names.status.unfinished");
            warning = true;
        } else if (dirty) {
            text = Strings.get("names.status.dirty");
            warning = false;
        } else {
            text = "";
            warning = false;
        }
        status.setText(text);
        ThemeColors.textFill(status, warning ? ThemeColors.WARNING : "-fx-text-base-color");
        updateToggle();
    }

    private void updateToggle() {
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        String key;
        if (masked) {
            key = "context.showTruncated.masked";
        } else if (!applied.anyOn()) {
            key = "context.showTruncated.noRules";
        } else {
            key = "context.showTruncated";
        }
        showTruncated.setText(Strings.get(key));
        showTruncated.setDisable(masked || !applied.anyOn());
    }

    private void onMaskChanged() {
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        split.setVisible(!masked);
        maskedBanner.setVisible(masked);
        maskedBanner.setManaged(masked);
        refreshState();
    }

    private void checkWritable() {
        Project<BufferedImage> project = pane.qupath().getProject();
        pane.previewNames(() -> ProjectTabPlusPane.projectWritable(project), ok -> {
            readOnlyBanner.setVisible(!ok);
            readOnlyBanner.setManaged(!ok);
        });
    }

    // ------------------------------------------------------------------ actions

    private void applyRules() {
        if (!isDirty() || hasUnfinished() || PathPrefs.maskImageNamesProperty().get()) {
            return;
        }
        List<NameRule> working = new ArrayList<>(rules);
        NameRuleSet set = new NameRuleSet(new NameRuleSet(true, working).anyOn(), working);
        applied = set;
        setEvent(Strings.get("names.status.saving"), false);
        pane.requestCollisionNotice();
        pane.applyNameRules(set, ok -> {
            if (stage.isShowing()) {
                setEvent(Strings.get(ok ? "names.status.saved" : "names.status.saveFailed"), !ok);
            }
        });
        refreshState();
    }

    private void resetRules() {
        if (!isDirty()) {
            return;
        }
        ButtonType resetType = new ButtonType(Strings.get("names.n2.reset"), ButtonData.OK_DONE);
        ButtonType keep = new ButtonType(Strings.get("names.n2.keep"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = prompt("names.n2.title", Strings.get("names.n2.body"), resetType, keep);
        DialogButtons.setDefault(dialog, keep);
        DialogButtons.installTooltip(dialog, resetType, Strings.get("names.n2.reset.tooltip"));
        DialogButtons.installTooltip(dialog, keep, Strings.get("names.n2.keep.tooltip"));
        if (dialog.showAndWait().orElse(keep) != resetType) {
            return;
        }
        rules.setAll(applied.rules());
        if (rules.isEmpty()) {
            enterCard();
        } else {
            list.getSelectionModel().clearSelection();
            list.getSelectionModel().select(0);
        }
        edited();
        setEvent(Strings.get("names.status.reset"), false);
    }

    private Dialog<ButtonType> prompt(String titleKey, String body, ButtonType... buttons) {
        return Dialogs.builder()
                .title(Strings.get(titleKey))
                .contentText(body)
                .owner(stage)
                .modality(Modality.WINDOW_MODAL)
                .buttons(buttons)
                .build();
    }

    private void copyRules() {
        List<NameRule> done = rules.stream().filter(NameRule::finished).toList();
        if (done.isEmpty()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(NameRulesJson.write(new NameRuleSet(true, done)));
        Clipboard.getSystemClipboard().setContent(content);
        setEvent(Strings.plural("names.status.copied.one", "names.status.copied.many", done.size()), false);
    }

    private void pasteRules() {
        String text = Clipboard.getSystemClipboard().getString();
        if (text == null || text.isBlank()) {
            setEvent(Strings.get("names.status.pasteEmpty"), true);
            return;
        }
        NameRulesJson.Parsed parsed = NameRulesJson.parse(text);
        if (!parsed.valid() || parsed.found() == 0) {
            setEvent(Strings.get("names.status.pasteInvalid"), true);
            return;
        }
        List<NameRule> pasted = parsed.rules().rules();
        if (pasted.isEmpty()) {
            setEvent(Strings.format("names.status.pastedPart", 0, parsed.found()), true);
            return;
        }
        boolean replace = true;
        if (!rules.isEmpty()) {
            ButtonType replaceType = new ButtonType(Strings.get("names.n3.replace"), ButtonData.OK_DONE);
            ButtonType after = new ButtonType(Strings.get("names.n3.after"), ButtonData.OTHER);
            ButtonType cancel = new ButtonType(Strings.get("names.n3.cancel"), ButtonData.CANCEL_CLOSE);
            Dialog<ButtonType> dialog = prompt("names.n3.title",
                    Strings.plural("names.n3.body.one", "names.n3.body.many", rules.size()), replaceType, after, cancel);
            DialogButtons.setDefault(dialog, replaceType);
            DialogButtons.installTooltip(dialog, replaceType, Strings.get("names.n3.replace.tooltip"));
            DialogButtons.installTooltip(dialog, after, Strings.get("names.n3.after.tooltip"));
            DialogButtons.installTooltip(dialog, cancel, Strings.get("names.n3.cancel.tooltip"));
            ButtonType answer = dialog.showAndWait().orElse(cancel);
            if (answer == cancel) {
                return;
            }
            replace = answer == replaceType;
        }
        int first = replace ? 0 : rules.size();
        if (replace) {
            rules.setAll(pasted);
        } else {
            rules.addAll(pasted);
        }
        cardMode = false;
        card = null;
        list.getSelectionModel().clearSelection();
        list.getSelectionModel().select(first);
        edited();
        if (parsed.skipped().isEmpty()) {
            setEvent(Strings.plural("names.status.pasted.one", "names.status.pasted.many", pasted.size()), false);
        } else {
            setEvent(Strings.format("names.status.pastedPart", pasted.size(), parsed.found()), true);
        }
    }

    private void requestClose() {
        if (!isDirty()) {
            stage.close();
            return;
        }
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        boolean unfinished = hasUnfinished() || masked;
        ButtonType applyType = new ButtonType(Strings.get("names.n1.apply"), ButtonData.OK_DONE);
        ButtonType discard = new ButtonType(Strings.get("names.n1.discard"), ButtonData.NO);
        ButtonType keep = new ButtonType(Strings.get("names.n1.keep"), ButtonData.CANCEL_CLOSE);
        Dialog<ButtonType> dialog = unfinished
                ? prompt("names.n1.title", Strings.get(masked ? "names.n1.bodyMasked" : "names.n1.bodyUnfinished"),
                        discard, keep)
                : prompt("names.n1.title", Strings.get("names.n1.body"), applyType, discard, keep);
        DialogButtons.setDefault(dialog, unfinished ? keep : applyType);
        if (!unfinished) {
            DialogButtons.installTooltip(dialog, applyType, Strings.get("names.n1.apply.tooltip"));
        }
        DialogButtons.installTooltip(dialog, discard, Strings.get("names.n1.discard.tooltip"));
        DialogButtons.installTooltip(dialog, keep, Strings.get("names.n1.keep.tooltip"));
        ButtonType answer = dialog.showAndWait().orElse(keep);
        if (answer == applyType) {
            applyRules();
            stage.close();
        } else if (answer == discard) {
            stage.close();
        }
    }

    // ------------------------------------------------------------------ for the pane and the cell

    void show() {
        if (!stage.isShowing()) {
            stage.show();
            split.setDividerPositions(270.0 / Math.max(1, split.getHeight()));
        } else {
            stage.toFront();
        }
    }

    /**
     * Close without a prompt because the project changed.
     *
     * @return true when unapplied edits were dropped
     */
    boolean closeForProjectChange() {
        boolean dropped = isDirty();
        closingForProject = true;
        stage.close();
        return dropped;
    }

    boolean isClosingForProject() {
        return closingForProject;
    }

    /** @return the window, for the harness */
    Stage stage() {
        return stage;
    }

    /** @return the window's own controls, for the harness */
    TableView<PreviewRow> table() {
        return table;
    }

    String countText() {
        return counts.getText();
    }

    String statusText() {
        return status.getText();
    }

    List<NameRule> workingRules() {
        return List.copyOf(rules);
    }

    NameRuleEditors.Editor editor() {
        return editor;
    }

    boolean cardMode() {
        return cardMode;
    }

    NameRuleEditors.SuggestionCard card() {
        return card;
    }

    ListView<NameRule> ruleList() {
        return list;
    }

    Button applyButton() {
        return apply;
    }

    /** @return the selected rule, or -1 */
    int selectedRule() {
        return list.getSelectionModel().getSelectedIndex();
    }

    /** Select a rule, from the piece menu. */
    void selectRule(int index) {
        if (index >= 0 && index < rules.size()) {
            list.getSelectionModel().select(index);
        }
    }

    /** @return every row, in project order */
    List<PreviewRow> rows() {
        return rows;
    }

    /** Shown as column: the short name, or the full name in muted text when the rules emptied it. */
    private static final class ShownCell extends TableCell<PreviewRow, String> {

        ShownCell() {
            setMnemonicParsing(false);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            PreviewRow row = empty ? null : getTableRow() == null ? null : getTableRow().getItem();
            if (row == null || item == null) {
                setText(null);
                setTooltip(null);
                setAccessibleText(null);
                setStyle("");
                return;
            }
            setText(item);
            setStyle(row.shortName.kept() ? "-fx-text-fill: " + ThemeColors.MUTED + ";" : "");
            setTooltip(Tooltips.of(Strings.format("names.cell.shown.tooltip", item)));
            setAccessibleText(Strings.format("names.a11y.shownAs", item));
        }
    }

    /** Note column: text cues, warning colored, never color alone. */
    private final class NoteCell extends TableCell<PreviewRow, String> {

        NoteCell() {
            setMnemonicParsing(false);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("ptp-warn-note");
            PreviewRow row = empty ? null : getTableRow() == null ? null : getTableRow().getItem();
            if (row == null || item == null || item.isEmpty()) {
                setText(null);
                setTooltip(null);
                setAccessibleText(null);
                return;
            }
            setText(item);
            String tip;
            String a11y = item;
            if (row.shared > 1) {
                getStyleClass().add("ptp-warn-note");
                List<String> others = new ArrayList<>();
                for (PreviewRow r : rows) {
                    if (r != row && r.shortName.display().equals(row.shortName.display())) {
                        others.add(r.full);
                    }
                }
                tip = Strings.format("names.note.shared.tooltip", String.join(", ", others));
                a11y = Strings.format("names.a11y.shared", row.shared);
            } else if (row.shortName.emptyFallback()) {
                getStyleClass().add("ptp-warn-note");
                tip = Strings.get("names.note.kept.tooltip");
                a11y = Strings.get("names.a11y.kept");
            } else if (row.shortName.noPiece()) {
                getStyleClass().add("ptp-warn-note");
                tip = Strings.get("names.note.noPiece.tooltip");
                a11y = Strings.get("names.a11y.noPiece");
            } else if (row.fewer) {
                tip = Strings.get("names.note.fewer.tooltip");
            } else {
                tip = Strings.get("names.note.unchanged.tooltip");
            }
            setTooltip(Tooltips.of(tip));
            setAccessibleText(a11y);
        }
    }

    /** Whether the hidden runs a user clicks should explain themselves or offer to hide. */
    boolean piecesEditorShowing() {
        return editor instanceof NameRuleEditors.PiecesEditor;
    }

    /** The piece menu asked to untick a chip of the Keep pieces rule at a position. */
    void hidePieceOf(int ruleIndex, int piece) {
        NameRule r = rules.get(ruleIndex);
        if (!(r instanceof KeepPiecesRule k)) {
            return;
        }
        java.util.SortedSet<Integer> kept = new java.util.TreeSet<>(k.kept());
        int pieces = Math.max(k.pieces(), piece + 1);
        if (k.keepLater()) {
            for (int i = k.pieces(); i < pieces; i++) {
                kept.add(i);
            }
        }
        kept.remove(piece);
        replaceRule(ruleIndex, new KeepPiecesRule(k.separator(), kept, pieces, k.keepLast(), kept.contains(pieces - 1), k.on()));
        selectRule(ruleIndex);
    }

    /** Turn one rule off, from the piece menu. */
    void turnOff(int ruleIndex) {
        if (ruleIndex >= 0 && ruleIndex < rules.size()) {
            replaceRule(ruleIndex, rules.get(ruleIndex).withOn(false));
        }
    }

    /** @return the tooltip shown on a node, for the harness */
    static String tooltipOf(Node n) {
        Tooltip t = n instanceof javafx.scene.control.Control c ? c.getTooltip() : null;
        return t == null ? null : t.getText();
    }
}
