package qupath.ext.projecttabplus.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.projecttabplus.core.BulkJobs;
import qupath.ext.projecttabplus.core.ObjectPresence;
import qupath.ext.projecttabplus.core.RefreshCoordinator;
import qupath.ext.projecttabplus.core.RefreshCoordinator.Reason;
import qupath.ext.projecttabplus.core.RemoveImageCommand;
import qupath.ext.projecttabplus.core.SummaryReader;
import qupath.ext.projecttabplus.core.ThumbnailLoader;
import qupath.ext.projecttabplus.model.DisplayProfile;
import qupath.ext.projecttabplus.model.DisplayProfile.IndicatorStyle;
import qupath.ext.projecttabplus.model.DisplayProfile.ThumbnailSize;
import qupath.ext.projecttabplus.model.EntryFilter;
import qupath.ext.projecttabplus.model.EntryInfo;
import qupath.ext.projecttabplus.model.GroupRow;
import qupath.ext.projecttabplus.model.GroupingKey;
import qupath.ext.projecttabplus.model.GroupingSpec;
import qupath.ext.projecttabplus.model.ImageRow;
import qupath.ext.projecttabplus.model.ProfileStore;
import qupath.ext.projecttabplus.model.Row;
import qupath.ext.projecttabplus.model.TreeBuilder;
import qupath.ext.projecttabplus.preferences.ProjectTabPlusPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.images.ImageData;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

// The delayed busy indicator (BUSY_DELAY, showBusy, hideBusy) is adapted from
// qupath-extension-classify-object-subset (Apache-2.0,
// github.com/uw-loci/qupath-extension-classify-object-subset), modified 2026-10/11.

/**
 * The Project+ tab content: toolbar, busy strip, grouped tree, status line and search bar.
 * Owns the checked set, the live display profile and the three background threads. Only
 * {@code setRoot}, cell updates and dialogs run on the FX thread; every disk read runs on
 * {@code ptp-tree}, {@code ptp-summary} or {@code ptp-bulk}.
 */
public final class ProjectTabPlusPane extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(ProjectTabPlusPane.class);

    /** Project metadata key holding the last profile used with the project. */
    public static final String PROJECT_PROFILE_KEY = "_PROJECT_TAB_PLUS_PROFILE";

    /** The built-in tab's project metadata key for its sort choice; read only. */
    static final String SORT_KEY = "_SORT_KEY";

    static final String STYLE_CLASS = "project-tab-plus";

    private static final Duration TRANSIENT_STATUS = Duration.seconds(4);
    private static final Duration BUSY_DELAY = Duration.millis(300);
    private static final Duration LIVE_DEBOUNCE = Duration.millis(500);
    private static final int ICON_SIZE = 16;

    private final QuPathGUI qupath;
    private final ProfileStore profileStore;
    private final ObservableList<DisplayProfile> profiles = FXCollections.observableArrayList();
    private DisplayProfile savedProfile;
    private DisplayProfile liveProfile;
    private final BooleanProperty profileModified = new SimpleBooleanProperty(false);

    private final SummaryReader summaryReader = new SummaryReader();
    private final ThumbnailLoader thumbnailLoader = new ThumbnailLoader();
    private final ExecutorService treeExecutor =
            Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("ptp-tree", true));
    private final ExecutorService summaryExecutor =
            Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("ptp-summary", true));
    private final ExecutorService bulkExecutor =
            Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("ptp-bulk", true));
    private final RefreshCoordinator coordinator;
    private final AtomicInteger generation = new AtomicInteger();
    private final Set<String> summaryRequested = ConcurrentHashMap.newKeySet();
    private final Map<URI, Boolean> uriMissing = new ConcurrentHashMap<>();

    // Tree state
    private final TreeView<Row> tree = new TreeView<>();
    private final Map<String, TreeItem<Row>> itemById = new HashMap<>();
    private final Map<String, Boolean> expansionByPath = new HashMap<>();
    private final Map<String, GroupRow> groupByPath = new HashMap<>();
    private List<ImageRow> allRows = List.of();
    private TreeBuilder.BuiltTree lastBuilt;
    private Set<String> metadataKeys = new TreeSet<>();
    private Map<String, Set<String>> valuesByKey = Map.of();
    private String pendingJumpId;
    /** A project's remembered profile name that this machine's profiles file lacks, until reported. */
    private String missingProfileName;
    private boolean suppressFilterRebuild;

    // Checkbox mode
    private final BooleanProperty checkboxMode = new SimpleBooleanProperty(false);
    private final ObservableSet<String> checkedIds = FXCollections.observableSet(new LinkedHashSet<>());

    // Current image
    private String currentEntryId;
    private ImageData<BufferedImage> currentImageData;
    private ObjectPresence livePresence;
    private final PauseTransition liveDebounce = new PauseTransition(LIVE_DEBOUNCE);
    // Plugins fire hierarchy events from worker threads; the debounce is an FX animation.
    private final PathObjectHierarchyListener hierarchyListener = e -> {
        if (Platform.isFxApplicationThread()) {
            liveDebounce.playFromStart();
        } else {
            Platform.runLater(liveDebounce::playFromStart);
        }
    };
    private final PauseTransition changedPoll = new PauseTransition(Duration.seconds(2));

    // Controls
    private final ComboBox<DisplayProfile> profileCombo = new ComboBox<>(profiles);
    private final MenuButton gear = new MenuButton();
    private final Button jumpButton = new Button();
    /** Carries the Jump tooltip while the button is disabled: a disabled node gets no hover. */
    private final StackPane jumpHolder = new StackPane(jumpButton);
    private Tooltip jumpDisabledTooltip;
    private final Button groupByButton = new Button();
    private final ToggleButton checkboxToggle = new ToggleButton();
    private final Button refreshButton = new Button();
    private final Button legendButton = new Button();
    private final VBox busyStrip = new VBox(2);
    private final Label busyLabel = new Label();
    private final ProgressBar busyBar = new ProgressBar();
    private final Button busyCancel = new Button(Strings.get("busy.cancel"));
    private final PauseTransition busyDelay = new PauseTransition(BUSY_DELAY);
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private final Label status = new Label();
    private final PauseTransition statusRevert = new PauseTransition(TRANSIENT_STATUS);
    private boolean statusSticky;
    private final TextField searchField = new TextField();
    private final ComboBox<String> keyCombo = new ComboBox<>();
    private final ComboBox<String> valueCombo = new ComboBox<>();
    private final FlowPane searchBar = new FlowPane(4, 4);
    private final Label bulkCount = new Label();
    private final MenuButton bulkActions = new MenuButton(Strings.get("bulk.actions"));
    private final VBox bulkBar = new VBox(4);
    private final VBox placeholder = new VBox(8);
    private final Label placeholderText = new Label();
    private final ContextMenu contextMenu = new ContextMenu();
    private boolean contextMenuShowing;
    private boolean updatingCombos;
    private boolean updatingProfileCombo;
    private DisplaySettingsDialog settingsDialog;
    private final BulkCommands bulkCommands;
    private final EntryActions entryActions;

    /**
     * @param qupath the QuPath instance
     * @param profileStore where profiles are read and written
     */
    public ProjectTabPlusPane(QuPathGUI qupath, ProfileStore profileStore) {
        this.qupath = qupath;
        this.profileStore = profileStore;
        this.coordinator = new RefreshCoordinator(qupath, this::rebuild);
        this.bulkCommands = new BulkCommands(this);
        this.entryActions = new EntryActions(this);
        getStyleClass().add(STYLE_CLASS);
        getStylesheets().add(Objects.requireNonNull(
                ProjectTabPlusPane.class.getResource("project-tab-plus.css")).toExternalForm());

        loadProfiles();
        buildToolbar();
        buildBusyStrip();
        buildTree();
        buildBottom();
        buildPlaceholder();
        buildContextMenu();
        installKeyboard();

        qupath.projectProperty().addListener((obs, o, n) -> onProjectChanged(n));
        qupath.imageDataProperty().addListener((obs, o, n) -> onImageDataChanged(o, n));
        PathPrefs.colorDefaultObjectsProperty().addListener((obs, o, n) -> tree.refresh());
        PathPrefs.colorTMAProperty().addListener((obs, o, n) -> tree.refresh());
        ProjectTabPlusPreferences.useCustomHighlightColorProperty().addListener((obs, o, n) -> tree.refresh());
        ProjectTabPlusPreferences.currentImageHighlightColorProperty().addListener((obs, o, n) -> tree.refresh());
        liveDebounce.setOnFinished(e -> recomputeLivePresence());
        changedPoll.setOnFinished(e -> pollChangedFlag());

        onProjectChanged(qupath.getProject());
        onImageDataChanged(null, qupath.getImageData());
    }

    // ------------------------------------------------------------------ construction

    private void buildToolbar() {
        profileCombo.setMinWidth(90);
        profileCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(profileCombo, Priority.ALWAYS);
        profileCombo.setTooltip(Tooltips.of(Strings.get("toolbar.profile.tooltip")));
        profileCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DisplayProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        profileCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(DisplayProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(profileModified.get() ? Strings.format("profile.modifiedSuffix", item.getName()) : item.getName());
                }
            }
        });
        profileModified.addListener((obs, o, n) -> {
            DisplayProfile sel = profileCombo.getValue();
            updatingProfileCombo = true;
            try {
                profileCombo.setValue(null);
                profileCombo.setValue(sel);
            } finally {
                updatingProfileCombo = false;
            }
        });
        profileCombo.valueProperty().addListener((obs, o, n) -> {
            if (updatingProfileCombo || n == null || n == savedProfile) {
                return;
            }
            switchProfile(n, o);
        });

        gear.setGraphic(IconFactory.createNode(ICON_SIZE, ICON_SIZE, PathIcons.COG));
        gear.setTooltip(Tooltips.of(Strings.get("toolbar.gear.tooltip")));
        buildGearMenu();
        HBox row1 = new HBox(4, profileCombo, gear);
        row1.setAlignment(Pos.CENTER_LEFT);

        iconButton(jumpButton, PathIcons.MAP_PIN, "toolbar.jump.tooltip", "toolbar.jump.accessible");
        jumpButton.setOnAction(e -> jumpToCurrent(true));
        iconButton(groupByButton, PathIcons.TABLE, "toolbar.groupBy.tooltip", "toolbar.groupBy.accessible");
        groupByButton.setOnAction(e -> showDisplaySettings());
        checkboxToggle.setGraphic(CheckboxIcon.create(ICON_SIZE));
        checkboxToggle.setTooltip(Tooltips.of(Strings.get("toolbar.checkboxMode.tooltip")));
        checkboxToggle.setAccessibleText(Strings.get("toolbar.checkboxMode.accessible"));
        checkboxToggle.setMinSize(24, 24);
        checkboxMode.bindBidirectional(checkboxToggle.selectedProperty());
        checkboxMode.addListener((obs, o, n) -> {
            tree.refresh();
            updateStatus();
        });
        iconButton(refreshButton, PathIcons.REFRESH, "toolbar.refresh.tooltip", "toolbar.refresh.accessible");
        refreshButton.setOnAction(e -> refreshManually());
        iconButton(legendButton, PathIcons.HELP, "toolbar.legend.tooltip", "toolbar.legend.accessible");
        legendButton.setOnAction(e -> IndicatorLegendDialog.show(qupath));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row2 = new HBox(4, jumpHolder, groupByButton, checkboxToggle, refreshButton, spacer, legendButton);
        row2.setAlignment(Pos.CENTER_LEFT);

        VBox toolbar = new VBox(4, row1, row2, busyStrip);
        toolbar.setPadding(new Insets(4));
        setTop(toolbar);
    }

    private static void iconButton(Button b, PathIcons icon, String tooltipKey, String accessibleKey) {
        b.setGraphic(IconFactory.createNode(ICON_SIZE, ICON_SIZE, icon));
        b.setTooltip(Tooltips.of(Strings.get(tooltipKey)));
        b.setAccessibleText(Strings.get(accessibleKey));
        b.setMinSize(24, 24);
    }

    private void buildGearMenu() {
        MenuItem save = new MenuItem(Strings.get("menu.saveProfile"));
        save.disableProperty().bind(profileModified.not());
        save.setOnAction(e -> saveLiveToProfile());
        MenuItem saveAs = new MenuItem(Strings.get("menu.saveProfileAs"));
        saveAs.setOnAction(e -> ProfileDialogs.saveAs(this));
        MenuItem rename = new MenuItem(Strings.get("menu.renameProfile"));
        rename.setOnAction(e -> ProfileDialogs.rename(this, savedProfile));
        MenuItem delete = new MenuItem(Strings.get("menu.deleteProfile"));
        delete.setOnAction(e -> ProfileDialogs.delete(this, savedProfile));
        MenuItem manage = new MenuItem(Strings.get("menu.manageProfiles"));
        manage.setOnAction(e -> ProfileManagerDialog.show(this));

        MenuItem edit = new MenuItem(Strings.get("menu.editDisplaySettings"));
        edit.setOnAction(e -> showDisplaySettings());
        Menu thumbs = new Menu(Strings.get("menu.thumbnailSize"));
        ToggleGroup thumbGroup = new ToggleGroup();
        for (ThumbnailSize size : ThumbnailSize.values()) {
            RadioMenuItem item = new RadioMenuItem(Strings.get("thumbnailSize." + size.name()));
            item.setToggleGroup(thumbGroup);
            item.setUserData(size);
            item.setOnAction(e -> {
                if (liveProfile.getThumbnailSize() != size) {
                    liveProfile.setThumbnailSize(size);
                    onLiveProfileChanged(false);
                }
            });
            thumbs.getItems().add(item);
        }
        Menu indicators = new Menu(Strings.get("menu.indicators"));
        ToggleGroup indicatorGroup = new ToggleGroup();
        for (IndicatorStyle style : IndicatorStyle.values()) {
            RadioMenuItem item = new RadioMenuItem(Strings.get("indicators." + style.name()));
            item.setToggleGroup(indicatorGroup);
            item.setUserData(style);
            item.setOnAction(e -> {
                if (liveProfile.getIndicators() != style) {
                    liveProfile.setIndicators(style);
                    onLiveProfileChanged(false);
                }
            });
            indicators.getItems().add(item);
        }

        MenuItem create = actionItem("menu.createProject", qupath.getCommonActions().PROJECT_NEW);
        MenuItem open = actionItem("menu.openProject", qupath.getCommonActions().PROJECT_OPEN);
        MenuItem add = actionItem("menu.addImages", qupath.getCommonActions().PROJECT_ADD_IMAGES);

        MenuItem removeCurrent = new MenuItem(Strings.get("menu.removeCurrent"));
        removeCurrent.setOnAction(e -> removeCurrentImage());
        // No accelerator: a MenuButton item's accelerator is registered scene-wide, and Ctrl+J
        // is meant to act only while focus is inside the tab (installKeyboard). The chord is
        // named in the item text instead.
        MenuItem jump = new MenuItem(Strings.get(GeneralTools.isMac() ? "menu.jumpToCurrent.mac" : "menu.jumpToCurrent"));
        jump.setOnAction(e -> jumpToCurrent(true));

        CheckMenuItem showBuiltIn = new CheckMenuItem(Strings.get("menu.showBuiltInTab"));
        showBuiltIn.setSelected(!ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().get());
        ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().addListener(
                (obs, o, n) -> showBuiltIn.setSelected(!n));
        showBuiltIn.setOnAction(e -> ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(!showBuiltIn.isSelected()));
        MenuItem legend = new MenuItem(Strings.get("menu.indicatorLegend"));
        legend.setOnAction(e -> IndicatorLegendDialog.show(qupath));

        gear.getItems().setAll(save, saveAs, rename, delete, manage, new SeparatorMenuItem(),
                edit, thumbs, indicators, new SeparatorMenuItem(),
                create, open, add, new SeparatorMenuItem(),
                removeCurrent, jump, new SeparatorMenuItem(),
                showBuiltIn, legend);
        gear.setOnShowing(e -> {
            boolean isDefault = savedProfile == null || ProfileStore.DEFAULT_NAME.equals(savedProfile.getName());
            rename.setDisable(isDefault);
            delete.setDisable(isDefault);
            removeCurrent.setDisable(currentEntryId == null);
            jump.setDisable(currentEntryId == null);
            for (MenuItem item : thumbs.getItems()) {
                ((RadioMenuItem) item).setSelected(item.getUserData() == liveProfile.getThumbnailSize());
            }
            for (MenuItem item : indicators.getItems()) {
                ((RadioMenuItem) item).setSelected(item.getUserData() == liveProfile.getIndicators());
            }
        });
    }

    /** A menu item with our own label that fires one of QuPath's common actions. */
    private static MenuItem actionItem(String textKey, Action action) {
        MenuItem item = new MenuItem(Strings.get(textKey));
        item.setOnAction(e -> action.handle(new javafx.event.ActionEvent(item, null)));
        item.disableProperty().bind(action.disabledProperty());
        return item;
    }

    private void buildBusyStrip() {
        busyStrip.getStyleClass().add("ptp-busy-strip");
        busyLabel.setMinWidth(0);
        busyLabel.setMaxWidth(Double.MAX_VALUE);
        busyCancel.setMinWidth(Region.USE_PREF_SIZE);
        busyBar.setMaxWidth(Double.MAX_VALUE);
        busyBar.setPrefHeight(6);
        HBox.setHgrow(busyBar, Priority.ALWAYS);
        busyCancel.setTooltip(Tooltips.of(Strings.get("busy.cancel.tooltip")));
        busyCancel.setOnAction(e -> cancelRequested.set(true));
        HBox busyRow = new HBox(6, busyBar, busyCancel);
        busyRow.setAlignment(Pos.CENTER_LEFT);
        busyStrip.getChildren().addAll(busyLabel, busyRow);
        busyStrip.setVisible(false);
        busyStrip.setManaged(false);
        busyDelay.setOnFinished(e -> {
            busyStrip.setVisible(true);
            busyStrip.setManaged(true);
        });
    }

    private void buildTree() {
        tree.setShowRoot(false);
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tree.setCellFactory(tv -> new EntryTreeCell(this));
        tree.setContextMenu(contextMenu);
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() > 1) {
                TreeItem<Row> item = tree.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() instanceof ImageRow row) {
                    qupath.openImageEntry(row.entry());
                    e.consume();
                }
            }
        });
        tree.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            TreeItem<Row> item = tree.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.ENTER && item != null) {
                if (item.getValue() instanceof ImageRow row) {
                    qupath.openImageEntry(row.entry());
                } else {
                    item.setExpanded(!item.isExpanded());
                }
                e.consume();
            } else if (e.getCode() == KeyCode.SPACE && checkboxMode.get() && item != null) {
                toggleItemChecked(item);
                e.consume();
            }
        });
        showCenter(tree);
        checkedIds.addListener((javafx.collections.SetChangeListener<String>) c -> updateStatus());
    }

    private void buildBottom() {
        status.getStyleClass().add("ptp-status");
        // Warnings such as a missing profile are longer than a 300 px pane: wrap rather than cut them.
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setTooltip(Tooltips.of(Strings.get("status.tooltip")));
        status.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                clearFilters();
            }
        });

        searchField.setPromptText(Strings.get("search.prompt"));
        searchField.setTooltip(Tooltips.of(Strings.get("search.tooltip")));
        searchField.setPrefColumnCount(14);
        searchField.textProperty().addListener((obs, o, n) -> {
            if (!suppressFilterRebuild) {
                coordinator.request(Reason.FILTER);
            }
        });
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
                tree.requestFocus();
                e.consume();
            }
        });
        keyCombo.setTooltip(Tooltips.of(Strings.get("filter.key.tooltip")));
        valueCombo.setTooltip(Tooltips.of(Strings.get("filter.value.tooltip")));
        keyCombo.getItems().setAll(Strings.get("filter.anyKey"));
        keyCombo.getSelectionModel().selectFirst();
        valueCombo.getItems().setAll(Strings.get("filter.anyValue"));
        valueCombo.getSelectionModel().selectFirst();
        keyCombo.valueProperty().addListener((obs, o, n) -> {
            if (!updatingCombos) {
                refreshValueCombo();
                coordinator.request(Reason.FILTER);
            }
        });
        valueCombo.valueProperty().addListener((obs, o, n) -> {
            if (!updatingCombos && !suppressFilterRebuild) {
                coordinator.request(Reason.FILTER);
            }
        });
        keyCombo.setPrefWidth(128);
        valueCombo.setPrefWidth(128);
        // Wide enough for the whole prompt at 13 px; beside the key combo it still fits a 300 px pane.
        searchField.setPrefWidth(156);
        searchField.setMaxWidth(Double.MAX_VALUE);
        searchBar.getChildren().addAll(searchField, keyCombo, valueCombo);
        searchBar.setPadding(new Insets(2, 4, 4, 4));

        bulkCount.setMinWidth(Region.USE_PREF_SIZE);
        // The bar texts carry mnemonics (Alt+C, Alt+L, Alt+A); the chords are in installKeyboard.
        Button checkAll = new Button(Strings.get("bulk.checkAllShown"));
        checkAll.setMnemonicParsing(true);
        checkAll.setTooltip(Tooltips.of(Strings.get("bulk.checkAllShown.tooltip")));
        checkAll.setOnAction(e -> checkAllShown());
        Button clear = new Button(Strings.get("bulk.clear"));
        clear.setMnemonicParsing(true);
        clear.setTooltip(Tooltips.of(Strings.get("bulk.clear.tooltip")));
        clear.setOnAction(e -> checkedIds.clear());
        bulkActions.setMnemonicParsing(true);
        bulkActions.setTooltip(Tooltips.of(Strings.get("bulk.actions.tooltip")));
        bulkActions.disableProperty().bind(Bindings.isEmpty(checkedIds));
        MenuItem remove = new MenuItem(Strings.get("bulk.action.remove"));
        remove.setOnAction(e -> bulkCommands.removeChecked());
        MenuItem clearData = new MenuItem(Strings.get("bulk.action.clearData"));
        clearData.setOnAction(e -> bulkCommands.clearCheckedData());
        MenuItem export = new MenuItem(Strings.get("bulk.action.export"));
        export.setOnAction(e -> bulkCommands.exportChecked());
        MenuItem openImage = new MenuItem(Strings.get("bulk.action.openImage"));
        openImage.disableProperty().bind(Bindings.size(checkedIds).isNotEqualTo(1));
        openImage.setOnAction(e -> {
            List<ProjectImageEntry<BufferedImage>> checked = checkedEntries();
            if (checked.size() == 1) {
                qupath.openImageEntry(checked.get(0));
            }
        });
        bulkActions.getItems().setAll(remove, clearData, export, new SeparatorMenuItem(), openImage);
        FlowPane bulkRow = new FlowPane(4, 4, bulkCount, checkAll, clear, bulkActions);
        bulkRow.setAlignment(Pos.CENTER_LEFT);
        bulkBar.getChildren().add(bulkRow);
        bulkBar.setPadding(new Insets(2, 4, 2, 4));
        bulkBar.visibleProperty().bind(checkboxMode);
        bulkBar.managedProperty().bind(checkboxMode);

        VBox bottom = new VBox(status, bulkBar, searchBar);
        setBottom(bottom);
    }

    private void buildPlaceholder() {
        placeholder.getStyleClass().add("ptp-placeholder");
        placeholder.setAlignment(Pos.CENTER);
        placeholderText.setWrapText(true);
        placeholderText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        Button create = new Button(Strings.get("menu.createProject"));
        create.setOnAction(e -> qupath.getCommonActions().PROJECT_NEW.handle(new javafx.event.ActionEvent(create, null)));
        Button open = new Button(Strings.get("menu.openProject"));
        open.setOnAction(e -> qupath.getCommonActions().PROJECT_OPEN.handle(new javafx.event.ActionEvent(open, null)));
        HBox buttons = new HBox(8, create, open);
        buttons.setAlignment(Pos.CENTER);
        Label hint = new Label(Strings.get("placeholder.dragHint"));
        hint.setWrapText(true);
        placeholder.getChildren().addAll(placeholderText, buttons, hint);
        placeholder.setUserData(buttons);
        placeholder.getProperties().put("hint", hint);
    }

    private void showPlaceholder(String text, boolean projectButtons) {
        placeholderText.setText(text);
        Node buttons = (Node) placeholder.getUserData();
        Node hint = (Node) placeholder.getProperties().get("hint");
        buttons.setVisible(projectButtons);
        buttons.setManaged(projectButtons);
        hint.setVisible(projectButtons);
        hint.setManaged(projectButtons);
        showCenter(placeholder);
    }

    /**
     * Swap the center node while keeping the Tab order toolbar, center, bottom: BorderPane appends a
     * re-set center after the bottom bar, which put the tree last in keyboard traversal.
     */
    private void showCenter(Node center) {
        if (getCenter() == center) {
            return;
        }
        setCenter(center);
        Node bottom = getBottom();
        if (bottom != null) {
            setBottom(null);
            setBottom(bottom);
        }
    }

    private void buildContextMenu() {
        MenuItem open = new MenuItem(Strings.get("context.openImage"));
        open.setOnAction(e -> selectedEntries().stream().findFirst().ifPresent(qupath::openImageEntry));
        MenuItem jump = new MenuItem(Strings.get("context.jump"));
        jump.setOnAction(e -> jumpToCurrent(true));
        MenuItem remove = new MenuItem(Strings.get("context.removeImage.one"));
        remove.setOnAction(e -> bulkCommands.removeEntries(selectedEntries()));
        MenuItem removeCurrent = new MenuItem(Strings.get("context.removeCurrent"));
        removeCurrent.setOnAction(e -> removeCurrentImage());
        MenuItem duplicate = new MenuItem(Strings.get("context.duplicate.one"));
        duplicate.setOnAction(e -> entryActions.duplicate(selectedEntries()));
        MenuItem rename = new MenuItem(Strings.get("context.rename"));
        rename.setOnAction(e -> selectedEntries().stream().findFirst().ifPresent(entryActions::rename));
        MenuItem addMetadata = new MenuItem(Strings.get("context.addMetadata"));
        addMetadata.setOnAction(e -> entryActions.addMetadata(selectedEntries()));
        MenuItem description = new MenuItem(Strings.get("context.editDescription"));
        description.setOnAction(e -> selectedEntries().stream().findFirst().ifPresent(entryActions::editDescription));
        MenuItem refreshThumb = new MenuItem(Strings.get("context.refreshThumbnail"));
        refreshThumb.setOnAction(e -> selectedEntries().stream().findFirst().ifPresent(entryActions::refreshThumbnail));
        MenuItem check = new MenuItem(Strings.get("context.check"));
        check.setOnAction(e -> setSelectedChecked(true));
        MenuItem uncheck = new MenuItem(Strings.get("context.uncheck"));
        uncheck.setOnAction(e -> setSelectedChecked(false));
        Menu openDir = new Menu(Strings.get("context.openDirectory"));
        MenuItem dirProject = new MenuItem(Strings.get("context.openDirectory.project"));
        dirProject.setOnAction(e -> entryActions.browseProject());
        MenuItem dirEntry = new MenuItem(Strings.get("context.openDirectory.entry"));
        dirEntry.setOnAction(e -> selectedEntries().stream().findFirst().ifPresent(entryActions::browseEntry));
        MenuItem dirImage = new MenuItem(Strings.get("context.openDirectory.image"));
        dirImage.setOnAction(e -> selectedEntries().stream().findFirst().ifPresent(entryActions::browseImage));
        openDir.getItems().setAll(dirProject, dirEntry, dirImage);
        Action maskAction = new Action(Strings.get("context.maskNames"));
        maskAction.selectedProperty().bindBidirectional(PathPrefs.maskImageNamesProperty());
        CheckMenuItem mask = ActionUtils.createCheckMenuItem(maskAction);
        MenuItem groupBy = new MenuItem(Strings.get("context.groupBy"));
        groupBy.setOnAction(e -> showDisplaySettings());

        SeparatorMenuItem sep1 = new SeparatorMenuItem();
        SeparatorMenuItem sep2 = new SeparatorMenuItem();
        SeparatorMenuItem sep3 = new SeparatorMenuItem();
        SeparatorMenuItem sep4 = new SeparatorMenuItem();
        contextMenu.getItems().setAll(open, jump, sep1, remove, removeCurrent, duplicate, rename, addMetadata,
                description, refreshThumb, sep2, check, uncheck, sep3, openDir, sep4, mask, groupBy);
        contextMenu.setOnShowing(e -> {
            contextMenuShowing = true;
            List<ProjectImageEntry<BufferedImage>> entries = selectedEntries();
            TreeItem<Row> selected = tree.getSelectionModel().getSelectedItem();
            boolean isImage = selected != null && selected.getValue() instanceof ImageRow;
            boolean isGroup = selected != null && selected.getValue() instanceof GroupRow;
            boolean any = selected != null;
            int n = entries.size();
            open.setVisible(isImage);
            jump.setVisible(any);
            jump.setDisable(currentEntryId == null);
            sep1.setVisible(any);
            remove.setVisible(isImage);
            remove.setText(n == 1 ? Strings.get("context.removeImage.one") : Strings.format("context.removeImage.many", n));
            removeCurrent.setVisible(isImage);
            removeCurrent.setDisable(currentEntryId == null);
            duplicate.setVisible(isImage);
            duplicate.setText(n == 1 ? Strings.get("context.duplicate.one") : Strings.format("context.duplicate.many", n));
            rename.setVisible(isImage && n == 1);
            addMetadata.setVisible(isImage);
            description.setVisible(isImage && n == 1);
            refreshThumb.setVisible(isImage && n == 1);
            boolean checks = checkboxMode.get() && any;
            sep2.setVisible(checks);
            check.setVisible(checks);
            uncheck.setVisible(checks);
            sep3.setVisible(any);
            dirEntry.setVisible(isImage);
            dirImage.setVisible(isImage);
            sep4.setVisible(any || isGroup);
            mask.setVisible(true);
            groupBy.setVisible(true);
        });
        contextMenu.setOnHidden(e -> contextMenuShowing = false);
    }

    private void installKeyboard() {
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F && e.isShortcutDown() && !e.isShiftDown()) {
                searchField.requestFocus();
                searchField.selectAll();
                e.consume();
            } else if (e.getCode() == KeyCode.J && e.isShortcutDown() && !e.isShiftDown()) {
                jumpToCurrent(true);
                e.consume();
            } else if (e.getCode() == KeyCode.F5) {
                refreshManually();
                e.consume();
            } else if (e.getCode() == KeyCode.D && e.isShortcutDown() && e.isShiftDown() && checkboxMode.get()) {
                checkedIds.clear();
                e.consume();
            } else if (e.getCode() == KeyCode.N && e.isShortcutDown() && e.isShiftDown() && checkboxMode.get()) {
                // Unassigned in QuPath 0.7.0's accelerator table (checked 2026-11-01).
                checkAllShown();
                e.consume();
            }
        });
    }

    // ------------------------------------------------------------------ profiles

    private void loadProfiles() {
        profiles.setAll(profileStore.load());
        DisplayProfile def = profiles.get(0);
        if (!profileStore.lastLoadFoundFile()) {
            // First run: seed Default with QuPath's own thumbnail size and write the file, so
            // the size the user picks later is what every restart reads back.
            def.setThumbnailSize(quPathThumbnailSize());
            persistProfiles();
        }
        savedProfile = def;
        liveProfile = def.copy();
        updatingProfileCombo = true;
        profileCombo.setValue(def);
        updatingProfileCombo = false;
    }

    /** QuPath's own "Project thumbnail size" preference, used for the Default profile on first run. */
    private static ThumbnailSize quPathThumbnailSize() {
        try {
            // The built-in tab stores its enum under this key; a string view of the same key
            // reads it without touching the deprecated Preferences object.
            String s = PathPrefs.createPersistentPreference("projectThumbnailSize", "SMALL").get();
            if (s != null) {
                return ThumbnailSize.valueOf(s.toUpperCase(java.util.Locale.ROOT));
            }
        } catch (RuntimeException e) {
            logger.debug("Could not read QuPath's project thumbnail size: {}", e.getMessage());
        }
        return ThumbnailSize.SMALL;
    }

    private void switchProfile(DisplayProfile target, DisplayProfile previous) {
        if (profileModified.get() && savedProfile != null) {
            ProfileDialogs.UnsavedChoice choice = ProfileDialogs.askUnsaved(this, savedProfile.getName());
            if (choice == ProfileDialogs.UnsavedChoice.CANCEL) {
                updatingProfileCombo = true;
                profileCombo.setValue(previous);
                updatingProfileCombo = false;
                return;
            }
            if (choice == ProfileDialogs.UnsavedChoice.SAVE) {
                saveLiveToProfile();
            }
        }
        selectProfile(target, true);
    }

    /**
     * Make a profile the live one.
     *
     * @param profile the profile to show
     * @param persist whether to record it as the last-used profile and the project's profile
     */
    void selectProfile(DisplayProfile profile, boolean persist) {
        savedProfile = profile;
        liveProfile = profile.copy();
        profileModified.set(false);
        updatingProfileCombo = true;
        profileCombo.setValue(profile);
        updatingProfileCombo = false;
        if (persist) {
            ProjectTabPlusPreferences.lastProfileProperty().set(profile.getName());
            storeProjectProfile(profile.getName());
        }
        if (settingsDialog != null) {
            settingsDialog.reloadFromLive();
        }
        coordinator.request(Reason.PROFILE);
    }

    private void storeProjectProfile(String name) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            return;
        }
        String old = project.getMetadata().get(PROJECT_PROFILE_KEY);
        if (Objects.equals(old, name)) {
            return;
        }
        project.getMetadata().put(PROJECT_PROFILE_KEY, name);
        bulkExecutor.submit(() -> {
            try {
                project.syncChanges();
            } catch (IOException e) {
                logger.warn("Could not save the project's profile name: {}", e.getMessage());
            }
        });
    }

    /** Write the live state into the selected profile and the file. */
    void saveLiveToProfile() {
        if (savedProfile == null) {
            return;
        }
        savedProfile.copySettingsFrom(liveProfile);
        profileModified.set(false);
        persistProfiles();
        setTransientStatus(Strings.format("status.profileSaved", savedProfile.getName()), false);
    }

    /**
     * Save the profile list to disk at once, reporting a failure as a notification. This is the
     * one deliberate disk write on the calling (FX) thread: the file is a few hundred bytes, and
     * a write queued behind a long bulk job was lost when QuPath quit before the job ended.
     */
    void persistProfiles() {
        List<DisplayProfile> snapshot = profiles.stream().map(DisplayProfile::copy).toList();
        try {
            profileStore.save(snapshot);
        } catch (IOException e) {
            logger.error("Could not write {}: {}", profileStore.getFile(), e.getMessage(), e);
            Dialogs.showErrorNotification(Strings.get("notify.title"),
                    Strings.format("profile.saveError", e.getMessage()));
        }
    }

    /**
     * Called by the display settings dialog and the gear radios after they changed the live
     * profile.
     *
     * @param groupingChanged whether the grouping or fields changed (needs a rebuild) rather than
     *                        only the appearance (needs a refresh)
     */
    void onLiveProfileChanged(boolean groupingChanged) {
        profileModified.set(!liveProfile.sameSettingsAs(savedProfile));
        if (groupingChanged) {
            coordinator.request(Reason.PROFILE);
        } else {
            tree.refresh();
        }
    }

    /** @return the live (possibly modified) profile */
    DisplayProfile profile() {
        return liveProfile;
    }

    /** @return the selected saved profile */
    DisplayProfile savedProfile() {
        return savedProfile;
    }

    /** @return the profile list, in combo order */
    ObservableList<DisplayProfile> profiles() {
        return profiles;
    }

    /** @return the profiles file */
    Path profilesFile() {
        return profileStore.getFile();
    }

    /** @return whether the live state differs from the saved profile */
    BooleanProperty profileModifiedProperty() {
        return profileModified;
    }

    /** Re-select the combo value after a rename or reorder so its text is current. */
    void refreshProfileCombo() {
        updatingProfileCombo = true;
        DisplayProfile sel = savedProfile;
        profileCombo.setValue(null);
        profileCombo.setValue(sel);
        updatingProfileCombo = false;
    }

    // ------------------------------------------------------------------ project and rebuild

    private void onProjectChanged(Project<BufferedImage> project) {
        checkedIds.clear();
        expansionByPath.clear();
        thumbnailLoader.clear();
        summaryReader.clear();
        summaryRequested.clear();
        uriMissing.clear();
        livePresence = null;
        if (settingsDialog != null) {
            settingsDialog.close();
        }
        if (project != null) {
            String stored = project.getMetadata().get(PROJECT_PROFILE_KEY);
            String name = stored != null ? stored : ProjectTabPlusPreferences.lastProfileProperty().get();
            Optional<DisplayProfile> match = profiles.stream().filter(p -> p.getName().equals(name)).findFirst();
            missingProfileName = stored != null && match.isEmpty() ? stored : null;
            DisplayProfile target = match.orElse(profiles.get(0));
            if (target == savedProfile && !profileModified.get()) {
                coordinator.request(Reason.PROJECT);
            } else {
                selectProfile(target, false);
            }
        } else {
            coordinator.request(Reason.PROJECT);
        }
        coordinator.hookBuiltInTree(qupath.getAnalysisTabPane());
    }

    /**
     * The live grouping, with one transient exception: while the Default profile is selected and
     * groups by nothing, the built-in tab's sort key ({@code _SORT_KEY}) is applied as its one
     * level at build time. It is never written into the profile, so it cannot be saved by
     * accident or carried to the next project.
     */
    private GroupingSpec effectiveGroupingSpec(Project<BufferedImage> project, boolean masked) {
        GroupingSpec spec = liveProfile.groupingSpec();
        if (spec.isFlat() && savedProfile != null && ProfileStore.DEFAULT_NAME.equals(savedProfile.getName())) {
            spec = GroupingSpec.fromSortKey(project.getMetadata().get(SORT_KEY));
        }
        return spec.effective(masked);
    }

    /** Refresh button and F5: forget failed thumbnails and summaries, then rebuild. */
    void refreshManually() {
        thumbnailLoader.clearFailed();
        summaryReader.clear();
        summaryRequested.clear();
        uriMissing.clear();
        coordinator.request(Reason.MANUAL);
        int n = qupath.getProject() == null ? 0 : qupath.getProject().getImageList().size();
        setTransientStatus(Strings.plural("status.refreshed.one", "status.refreshed.many", n), false);
    }

    /** Rebuild after an action changed the project (remove, duplicate, rename, metadata). */
    public void refreshAfterChange() {
        coordinator.request(Reason.MANUAL);
    }

    /** Called when the tab is selected: rebuild only if the image list moved. */
    public void onTabSelected() {
        coordinator.request(Reason.TAB_SELECTED);
    }

    private EntryFilter currentFilter() {
        String key = keyCombo.getValue();
        if (key == null || key.equals(Strings.get("filter.anyKey"))) {
            key = null;
        }
        String value = valueCombo.getValue();
        if (value == null || value.equals(Strings.get("filter.anyValue"))) {
            value = null;
        }
        return EntryFilter.of(searchField.getText(), key, value);
    }

    private void rebuild() {
        Project<BufferedImage> project = qupath.getProject();
        int gen = generation.incrementAndGet();
        if (project == null) {
            allRows = List.of();
            lastBuilt = null;
            itemById.clear();
            tree.setRoot(null);
            showPlaceholder(Strings.get("placeholder.noProject"), true);
            setToolbarEnabled(false);
            searchBar.setVisible(false);
            searchBar.setManaged(false);
            status.setText("");
            updateJumpState();
            return;
        }
        setToolbarEnabled(true);
        searchBar.setVisible(true);
        searchBar.setManaged(true);
        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        coordinator.rememberList(entries);
        boolean masked = PathPrefs.maskImageNamesProperty().get();
        GroupingSpec spec = effectiveGroupingSpec(project, masked);
        EntryFilter filter = currentFilter();
        boolean needsType = spec.levels().stream().anyMatch(k -> k.builtin() == GroupingKey.Builtin.IMAGE_TYPE)
                || liveProfile.fieldKeys().stream().anyMatch(k -> k.builtin() == GroupingKey.Builtin.IMAGE_TYPE);
        boolean sweepFirst = needsType || !filter.hasKeywords().isEmpty();
        String currentId = currentEntryId;
        ObjectPresence live = livePresence;
        String selectedId = selectedEntryId();
        showBusy(Strings.get("busy.rebuilding"), -1, 0, false);
        treeExecutor.submit(() -> {
            try {
                if (gen != generation.get()) {
                    return; // a newer request is queued behind this one
                }
                if (sweepFirst) {
                    sweepSummaries(entries, gen);
                }
                List<ImageRow> rows = new ArrayList<>(entries.size());
                for (ProjectImageEntry<BufferedImage> entry : entries) {
                    if (gen != generation.get()) {
                        return;
                    }
                    ObjectPresence p = sweepFirst ? summaryReader.get(entry) : summaryReader.peek(entry.getID());
                    String type = p == null ? null : p.imageType();
                    rows.add(new ImageRow(EntryInfo.from(entry, type, this::isUriMissing), entry));
                }
                if (masked) {
                    Collections.shuffle(rows);
                }
                Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                Map<String, Set<String>> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (ImageRow row : rows) {
                    for (Map.Entry<String, String> m : row.info().metadata().entrySet()) {
                        keys.add(m.getKey());
                        values.computeIfAbsent(m.getKey(), k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER))
                                .add(m.getValue() == null || m.getValue().isBlank() ? GroupingKey.UNASSIGNED : m.getValue());
                    }
                }
                for (ImageRow row : rows) {
                    for (String k : keys) {
                        if (!row.info().metadata().containsKey(k)) {
                            values.get(k).add(GroupingKey.UNASSIGNED);
                        }
                    }
                }
                TreeBuilder.BuiltTree built = TreeBuilder.build(rows, spec, r -> {
                    ObjectPresence p = r.id().equals(currentId) && live != null ? live : summaryReader.peek(r.id());
                    return filter.test(new EntryFilter.Candidate(r.entry(), r.info(), p));
                });
                Platform.runLater(() -> {
                    if (gen != generation.get()) {
                        return;
                    }
                    applyBuild(rows, built, keys, values, selectedId, filter);
                });
                if (!sweepFirst) {
                    summaryExecutor.submit(() -> {
                        try {
                            sweepSummaries(entries, gen);
                            Platform.runLater(() -> {
                                if (gen == generation.get()) {
                                    tree.refresh();
                                }
                            });
                        } catch (RuntimeException e) {
                            logger.error("Reading saved-data summaries failed: {}", e.getMessage(), e);
                        }
                    });
                }
            } catch (RuntimeException e) {
                logger.error("Rebuilding the Project+ tree failed: {}", e.getMessage(), e);
            }
        });
    }

    private boolean isUriMissing(URI uri) {
        if (!"file".equalsIgnoreCase(uri.getScheme()) || PathPrefs.skipProjectUriChecksProperty().get()) {
            return false;
        }
        return uriMissing.computeIfAbsent(uri, u -> {
            try {
                return Files.notExists(Path.of(u));
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    /** Read every summary on the calling (background) thread, driving the busy strip. */
    private void sweepSummaries(List<ProjectImageEntry<BufferedImage>> entries, int gen) {
        int total = entries.size();
        int i = 0;
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            if (gen != generation.get()) {
                return;
            }
            if (summaryReader.peek(entry.getID()) == null) {
                final int done = i;
                if (done % 25 == 0) {
                    Platform.runLater(() -> showBusy(Strings.format("busy.summaries", done, total), done, total, false));
                }
                summaryReader.get(entry);
            }
            i++;
        }
        Platform.runLater(this::hideBusy);
    }

    private void applyBuild(List<ImageRow> rows, TreeBuilder.BuiltTree built, Set<String> keys,
                            Map<String, Set<String>> values, String selectedId, EntryFilter filter) {
        hideBusy();
        allRows = rows;
        lastBuilt = built;
        metadataKeys = keys;
        valuesByKey = values;
        itemById.clear();
        groupByPath.clear();
        checkedIds.retainAll(rows.stream().map(ImageRow::id).collect(java.util.stream.Collectors.toSet()));
        TreeItem<Row> root = toItem(built.root());
        tree.setRoot(root);
        root.setExpanded(true);
        if (rows.isEmpty()) {
            showPlaceholder(Strings.get("placeholder.noImages"), false);
        } else if (built.shown() == 0) {
            showPlaceholder(Strings.get("placeholder.noMatches"), false);
        } else {
            showCenter(tree);
        }
        refreshKeyCombo();
        updateStatus();
        updateJumpState();
        warnMissingProfileKeys();
        if (missingProfileName != null) {
            setStickyStatus(Strings.format("status.profileNotOnMachine", missingProfileName));
            missingProfileName = null;
        }
        if (pendingJumpId != null) {
            String id = pendingJumpId;
            pendingJumpId = null;
            selectAndScroll(id);
        } else if (selectedId != null && itemById.containsKey(selectedId)) {
            TreeItem<Row> item = itemById.get(selectedId);
            tree.getSelectionModel().clearSelection();
            tree.getSelectionModel().select(item);
        }
        if (settingsDialog != null) {
            settingsDialog.projectChanged();
        }
    }

    private TreeItem<Row> toItem(GroupRow group) {
        TreeItem<Row> item = new TreeItem<>(group);
        for (Row child : group.children()) {
            if (child instanceof GroupRow g) {
                item.getChildren().add(toItem(g));
            } else if (child instanceof ImageRow row) {
                TreeItem<Row> leaf = new TreeItem<>(row);
                itemById.put(row.id(), leaf);
                item.getChildren().add(leaf);
            }
        }
        if (!group.isRoot()) {
            groupByPath.put(group.path(), group);
            Boolean remembered = expansionByPath.get(group.path());
            boolean expanded = remembered != null ? remembered : switch (liveProfile.getExpandOnLoad()) {
                case ALL -> true;
                case FIRST -> group.depth() == 1;
                case NONE -> false;
            };
            item.setExpanded(expanded);
            item.expandedProperty().addListener((obs, o, n) -> {
                expansionByPath.put(group.path(), n);
                tree.refresh();
            });
        }
        return item;
    }

    private void warnMissingProfileKeys() {
        List<String> missing = liveProfile.missingMetadataKeys(metadataKeys);
        if (!missing.isEmpty() && !allRows.isEmpty()) {
            setStickyStatus(Strings.format("status.profileMissingKey", liveProfile.getName(), missing.get(0)));
        }
    }

    private void setToolbarEnabled(boolean enabled) {
        groupByButton.setDisable(!enabled);
        checkboxToggle.setDisable(!enabled);
        refreshButton.setDisable(!enabled);
    }

    private void refreshKeyCombo() {
        updatingCombos = true;
        try {
            String selected = keyCombo.getValue();
            List<String> items = new ArrayList<>();
            items.add(Strings.get("filter.anyKey"));
            items.addAll(metadataKeys);
            keyCombo.getItems().setAll(items);
            if (selected != null && items.contains(selected)) {
                keyCombo.setValue(selected);
            } else {
                keyCombo.getSelectionModel().selectFirst();
            }
            refreshValueCombo();
        } finally {
            updatingCombos = false;
        }
    }

    private void refreshValueCombo() {
        boolean was = updatingCombos;
        updatingCombos = true;
        try {
            String selected = valueCombo.getValue();
            List<String> items = new ArrayList<>();
            items.add(Strings.get("filter.anyValue"));
            String key = keyCombo.getValue();
            if (key != null && valuesByKey.containsKey(key)) {
                items.addAll(valuesByKey.get(key));
            }
            valueCombo.getItems().setAll(items);
            if (selected != null && items.contains(selected)) {
                valueCombo.setValue(selected);
            } else {
                valueCombo.getSelectionModel().selectFirst();
            }
        } finally {
            updatingCombos = was;
        }
    }

    private void clearFilters() {
        suppressFilterRebuild = true;
        try {
            searchField.clear();
            updatingCombos = true;
            keyCombo.getSelectionModel().selectFirst();
            refreshValueCombo();
            updatingCombos = false;
        } finally {
            suppressFilterRebuild = false;
        }
        coordinator.request(Reason.FILTER);
    }

    // ------------------------------------------------------------------ current image

    private void onImageDataChanged(ImageData<BufferedImage> old, ImageData<BufferedImage> now) {
        if (old != null) {
            old.getHierarchy().removeListener(hierarchyListener);
        }
        currentImageData = now;
        Project<BufferedImage> project = qupath.getProject();
        ProjectImageEntry<BufferedImage> entry = now == null || project == null ? null : project.getEntry(now);
        currentEntryId = entry == null ? null : entry.getID();
        livePresence = null;
        if (now != null) {
            now.getHierarchy().addListener(hierarchyListener);
            recomputeLivePresence();
            changedPoll.playFromStart();
        } else {
            changedPoll.stop();
        }
        tree.refresh();
        updateStatus();
        updateJumpState();
        if (currentEntryId != null && itemById.containsKey(currentEntryId)) {
            selectAndScroll(currentEntryId);
        }
    }

    private void recomputeLivePresence() {
        ImageData<BufferedImage> imageData = currentImageData;
        String id = currentEntryId;
        if (imageData == null || id == null) {
            return;
        }
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        summaryExecutor.submit(() -> {
            try {
                ObjectPresence p = SummaryReader.fromHierarchy(hierarchy, imageData.getImageType(), imageData.isChanged());
                Platform.runLater(() -> {
                    if (imageData == currentImageData) {
                        livePresence = p;
                        tree.refresh();
                    }
                });
            } catch (RuntimeException e) {
                // A plugin may be filling the hierarchy while we walk it; the next event retries.
                logger.error("Counting the open image's objects failed: {}", e.getMessage(), e);
            }
        });
    }

    /** Saving an image flips {@code isChanged()} without a hierarchy event; poll it cheaply. */
    private void pollChangedFlag() {
        ImageData<BufferedImage> imageData = currentImageData;
        if (imageData == null) {
            return;
        }
        if (livePresence != null && livePresence.unsaved() != imageData.isChanged()) {
            summaryReader.invalidate(currentEntryId);
            recomputeLivePresence();
        }
        changedPoll.playFromStart();
    }

    private void updateJumpState() {
        boolean noImage = qupath.getImageData() == null;
        boolean enabled = currentEntryId != null;
        jumpButton.setDisable(!enabled);
        // The enabled tooltip lives on the button; the reason it is disabled lives on the holder,
        // because a disabled node receives no mouse events and so never shows its own tooltip.
        if (jumpDisabledTooltip != null) {
            Tooltip.uninstall(jumpHolder, jumpDisabledTooltip);
            jumpDisabledTooltip = null;
        }
        if (!enabled) {
            jumpDisabledTooltip = Tooltips.of(noImage
                    ? Strings.get("toolbar.jump.noImage") : Strings.get("toolbar.jump.notInProject"));
            Tooltip.install(jumpHolder, jumpDisabledTooltip);
        }
    }

    /**
     * Expand, select and scroll to the current image's row.
     *
     * @param clearFilterIfHidden when the row is hidden by the search or combos, clear them,
     *                            rebuild and then jump; the automatic jump on image change passes false
     */
    void jumpToCurrent(boolean clearFilterIfHidden) {
        String id = currentEntryId;
        if (id == null) {
            return;
        }
        if (itemById.containsKey(id)) {
            selectAndScroll(id);
            setTransientStatus(Strings.format("status.jumped", nameOf(id)), false);
            return;
        }
        if (clearFilterIfHidden) {
            pendingJumpId = id;
            setStickyStatus(Strings.format("status.jumpCleared", nameOf(id)));
            clearFilters();
        }
    }

    private String nameOf(String id) {
        TreeItem<Row> item = itemById.get(id);
        if (item != null && item.getValue() instanceof ImageRow row) {
            return row.info().name();
        }
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            for (ProjectImageEntry<BufferedImage> e : project.getImageList()) {
                if (e.getID().equals(id)) {
                    return e.getImageName();
                }
            }
        }
        return id;
    }

    private void selectAndScroll(String id) {
        TreeItem<Row> item = itemById.get(id);
        if (item == null) {
            return;
        }
        for (TreeItem<Row> p = item.getParent(); p != null; p = p.getParent()) {
            p.setExpanded(true);
        }
        tree.getSelectionModel().clearSelection();
        tree.getSelectionModel().select(item);
        Platform.runLater(() -> {
            int row = tree.getRow(item);
            if (row >= 0) {
                tree.scrollTo(Math.max(0, row - 2));
            }
        });
    }

    // ------------------------------------------------------------------ checkbox mode

    /** @return whether checkbox mode is on */
    boolean isCheckboxMode() {
        return checkboxMode.get();
    }

    /** @return the checked entry IDs */
    public ObservableSet<String> checkedIds() {
        return checkedIds;
    }

    void setItemChecked(TreeItem<Row> item, boolean checked) {
        if (item.getValue() instanceof ImageRow row) {
            if (checked) {
                checkedIds.add(row.id());
            } else {
                checkedIds.remove(row.id());
            }
        } else if (item.getValue() instanceof GroupRow group) {
            for (String id : idsUnder(group)) {
                if (checked) {
                    checkedIds.add(id);
                } else {
                    checkedIds.remove(id);
                }
            }
        }
        tree.refresh();
    }

    private void toggleItemChecked(TreeItem<Row> item) {
        if (item.getValue() instanceof ImageRow row) {
            setItemChecked(item, !checkedIds.contains(row.id()));
        } else if (item.getValue() instanceof GroupRow group) {
            int[] c = checkedUnder(group);
            setItemChecked(item, c[0] < c[1]);
        }
    }

    private void setSelectedChecked(boolean checked) {
        for (TreeItem<Row> item : new ArrayList<>(tree.getSelectionModel().getSelectedItems())) {
            setItemChecked(item, checked);
        }
    }

    private void checkAllShown() {
        checkedIds.addAll(itemById.keySet());
        tree.refresh();
    }

    private static List<String> idsUnder(GroupRow group) {
        List<String> ids = new ArrayList<>();
        collectIds(group, ids);
        return ids;
    }

    private static void collectIds(GroupRow group, List<String> into) {
        for (Row child : group.children()) {
            if (child instanceof ImageRow row) {
                into.add(row.id());
            } else if (child instanceof GroupRow g) {
                collectIds(g, into);
            }
        }
    }

    /** @return {checked, total} images under the group that pass the current filter */
    int[] checkedUnder(GroupRow group) {
        List<String> ids = idsUnder(group);
        int n = 0;
        for (String id : ids) {
            if (checkedIds.contains(id)) {
                n++;
            }
        }
        return new int[] {n, ids.size()};
    }

    /** @return {annotations, detections, tma} present in any image under the group */
    boolean[] presenceUnder(GroupRow group) {
        boolean[] any = new boolean[3];
        for (String id : idsUnder(group)) {
            ObjectPresence p = id.equals(currentEntryId) && livePresence != null ? livePresence : summaryReader.peek(id);
            if (p == null) {
                continue;
            }
            any[0] |= p.hasAnnotations();
            any[1] |= p.hasDetections();
            any[2] |= p.hasTmaCores();
            if (any[0] && any[1] && any[2]) {
                break;
            }
        }
        return any;
    }

    /** @return the checked entries, in project order */
    List<ProjectImageEntry<BufferedImage>> checkedEntries() {
        List<ProjectImageEntry<BufferedImage>> out = new ArrayList<>();
        for (ImageRow row : allRows) {
            if (checkedIds.contains(row.id())) {
                out.add(row.entry());
            }
        }
        return out;
    }

    /** @return the entries of the selected rows, descending into selected groups */
    List<ProjectImageEntry<BufferedImage>> selectedEntries() {
        LinkedHashSet<ProjectImageEntry<BufferedImage>> out = new LinkedHashSet<>();
        for (TreeItem<Row> item : tree.getSelectionModel().getSelectedItems()) {
            if (item == null) {
                continue;
            }
            if (item.getValue() instanceof ImageRow row) {
                out.add(row.entry());
            } else if (item.getValue() instanceof GroupRow group) {
                for (String id : idsUnder(group)) {
                    TreeItem<Row> leaf = itemById.get(id);
                    if (leaf != null && leaf.getValue() instanceof ImageRow r) {
                        out.add(r.entry());
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private String selectedEntryId() {
        TreeItem<Row> item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof ImageRow row ? row.id() : null;
    }

    // ------------------------------------------------------------------ cell services

    /** @return the presence for a row: live for the current image, else the cached summary */
    ObjectPresence presence(ImageRow row) {
        if (row.id().equals(currentEntryId) && livePresence != null) {
            return livePresence;
        }
        ObjectPresence p = summaryReader.peek(row.id());
        if (p == null) {
            if (summaryRequested.add(row.id())) {
                ProjectImageEntry<BufferedImage> entry = row.entry();
                summaryExecutor.submit(() -> {
                    try {
                        summaryReader.get(entry);
                        Platform.runLater(tree::refresh);
                    } catch (RuntimeException e) {
                        logger.error("Reading the summary of entry {} failed: {}", entry.getID(), e.getMessage(), e);
                    }
                });
            }
            return ObjectPresence.UNKNOWN;
        }
        return p;
    }

    /** @return the cached thumbnail, requesting a load when absent */
    Image thumbnail(ImageRow row) {
        Image img = thumbnailLoader.cached(row.id());
        if (img == null && !thumbnailLoader.hasFailed(row.id())) {
            thumbnailLoader.request(row.entry(), id -> tree.refresh());
        }
        return img;
    }

    boolean thumbnailFailed(ImageRow row) {
        return thumbnailLoader.hasFailed(row.id());
    }

    /** Drop a cached thumbnail (after Refresh thumbnail). */
    void invalidateThumbnail(String entryId) {
        thumbnailLoader.invalidate(entryId);
        tree.refresh();
    }

    boolean isCurrent(ImageRow row) {
        return row.id().equals(currentEntryId);
    }

    /**
     * @param groupItem a group's tree item
     * @return true when the current image sits under this group but its row is hidden, because
     *         this group or a group below it is collapsed; every ancestor above the collapse
     *         answers true, so the highlight forms a trail down to the collapsed row
     */
    boolean isCurrentHiddenUnder(TreeItem<Row> groupItem) {
        if (currentEntryId == null || groupItem == null) {
            return false;
        }
        TreeItem<Row> leaf = itemById.get(currentEntryId);
        if (leaf == null) {
            return false;
        }
        boolean under = false;
        boolean hidden = false;
        for (TreeItem<Row> p = leaf.getParent(); p != null; p = p.getParent()) {
            if (p == groupItem) {
                under = true;
            }
            if (p.getParent() != null && !p.isExpanded()) {
                hidden = true;
            }
        }
        return under && hidden;
    }

    boolean isContextMenuShowing() {
        return contextMenuShowing;
    }

    /** @return an inline style for the current row when the custom highlight is on, else null */
    String customHighlightStyle() {
        if (!ProjectTabPlusPreferences.useCustomHighlightColorProperty().get()) {
            return null;
        }
        int argb = ProjectTabPlusPreferences.currentImageHighlightColorProperty().get();
        return String.format("-fx-background-color: rgba(%d, %d, %d, %.2f);",
                ColorTools.red(argb), ColorTools.green(argb), ColorTools.blue(argb),
                ColorTools.alpha(argb) / 255.0);
    }

    // ------------------------------------------------------------------ status and busy

    private void updateStatus() {
        if (statusSticky || statusRevert.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            // A transient message is showing; it reverts to the totals by itself.
            return;
        }
        status.setText(idleStatusText());
        ThemeColors.textFill(status, "-fx-text-base-color");
        int n = checkedIds.size();
        bulkCount.setText(Strings.plural("bulk.count.one", "bulk.count.many", n, allRows.size()));
    }

    private String idleStatusText() {
        if (lastBuilt == null) {
            return "";
        }
        int total = lastBuilt.total();
        int shown = lastBuilt.shown();
        int checked = checkedIds.size();
        if (shown != total) {
            return checked > 0
                    ? Strings.format("status.filteredChecked", shown, total, checked)
                    : Strings.format("status.filtered", shown, total);
        }
        StringBuilder sb = new StringBuilder(Strings.plural("status.idle.one", "status.idle.many", total));
        if (currentEntryId != null) {
            sb.append(Strings.format("status.open", 1));
        }
        if (checked > 0) {
            sb.append(Strings.format("status.checked", checked));
        }
        return sb.toString();
    }

    /**
     * Show a message for four seconds, then return to the totals.
     *
     * @param text the message
     * @param warning whether to use the warning color
     */
    public void setTransientStatus(String text, boolean warning) {
        statusSticky = false;
        status.setText(text);
        ThemeColors.textFill(status, warning ? ThemeColors.WARNING : "-fx-text-base-color");
        statusRevert.setOnFinished(e -> updateStatus());
        statusRevert.playFromStart();
    }

    /** Show a warning that stays until the next action. */
    void setStickyStatus(String text) {
        statusRevert.stop();
        status.setText(text);
        ThemeColors.textFill(status, ThemeColors.WARNING);
        statusSticky = true;
        PauseTransition release = new PauseTransition(Duration.seconds(8));
        release.setOnFinished(e -> {
            statusSticky = false;
            updateStatus();
        });
        release.playFromStart();
    }

    /**
     * Show the busy strip (after a short delay so quick jobs never flash it).
     *
     * @param text the strip text
     * @param done progress numerator, or -1 for indeterminate
     * @param total progress denominator
     * @param cancelable whether the Cancel button is shown
     */
    void showBusy(String text, int done, int total, boolean cancelable) {
        busyLabel.setText(text);
        busyBar.setProgress(done < 0 || total <= 0 ? ProgressBar.INDETERMINATE_PROGRESS : (double) done / total);
        busyCancel.setVisible(cancelable);
        busyCancel.setManaged(cancelable);
        if (!busyStrip.isVisible() && busyDelay.getStatus() != javafx.animation.Animation.Status.RUNNING) {
            busyDelay.playFromStart();
        }
    }

    /** Hide the busy strip. */
    void hideBusy() {
        busyDelay.stop();
        busyStrip.setVisible(false);
        busyStrip.setManaged(false);
    }

    /** Reset the cancel flag before a job; the strip's Cancel button sets it. */
    void resetCancel() {
        cancelRequested.set(false);
    }

    boolean isCancelRequested() {
        return cancelRequested.get();
    }

    // ------------------------------------------------------------------ actions

    private void removeCurrentImage() {
        Project<BufferedImage> project = qupath.getProject();
        ImageData<BufferedImage> imageData = qupath.getImageData();
        if (project == null || imageData == null) {
            return;
        }
        ProjectImageEntry<BufferedImage> entry = project.getEntry(imageData);
        if (entry == null) {
            return;
        }
        new RemoveImageCommand(qupath, this).run(entry);
    }

    private void showDisplaySettings() {
        if (qupath.getProject() == null) {
            return;
        }
        if (settingsDialog == null) {
            settingsDialog = new DisplaySettingsDialog(this);
        }
        settingsDialog.show();
    }

    /** Called by the settings dialog when it closes. */
    void settingsDialogClosed() {
        settingsDialog = null;
    }

    /** @return the metadata keys of the current project, sorted */
    Set<String> metadataKeys() {
        return metadataKeys;
    }

    /** @return every image of the project as built last, unfiltered */
    List<ImageRow> allRows() {
        return allRows;
    }

    /**
     * Run a group count on the tree thread.
     *
     * @param spec the grouping to count
     * @param onDone receives the counts on the FX thread
     */
    void countGroups(GroupingSpec spec, Consumer<List<Integer>> onDone) {
        List<ImageRow> rows = allRows;
        treeExecutor.submit(() -> {
            try {
                List<Integer> counts = TreeBuilder.groupCounts(rows, spec);
                Platform.runLater(() -> onDone.accept(counts));
            } catch (RuntimeException e) {
                logger.error("Counting groups failed: {}", e.getMessage(), e);
            }
        });
    }

    /** @return the QuPath instance */
    public QuPathGUI qupath() {
        return qupath;
    }

    /** @return the single-thread executor for remove, clear, export and trash */
    public ExecutorService bulkExecutor() {
        return bulkExecutor;
    }

    /** @return the summary reader, for invalidation after a job */
    public SummaryReader summaryReader() {
        return summaryReader;
    }

    /** @return the tree, for the harness and tests */
    public TreeView<Row> tree() {
        return tree;
    }

    /** @return the entry ID of the current image, or null */
    public String currentEntryId() {
        return currentEntryId;
    }

    /** @return the search field */
    public TextField searchField() {
        return searchField;
    }

    /** @return the checkbox-mode property */
    public BooleanProperty checkboxModeProperty() {
        return checkboxMode;
    }

    /** @return the IDs of every image shown after the last build */
    public Set<String> shownIds() {
        return Collections.unmodifiableSet(itemById.keySet());
    }

    /** @return the status line text */
    public String statusText() {
        return status.getText();
    }

    /** @return the busy strip, for tests */
    Node busyStrip() {
        return busyStrip;
    }

    /** @return the bulk command helper */
    public BulkCommands bulkCommands() {
        return bulkCommands;
    }

    /** @return the names of the profiles, in combo order */
    public List<String> profileNames() {
        return profiles.stream().map(DisplayProfile::getName).toList();
    }

    /**
     * Select a profile by name, discarding unsaved live changes.
     *
     * @param name the profile name
     * @return true when found
     */
    public boolean selectProfileNamed(String name) {
        for (DisplayProfile p : profiles) {
            if (p.getName().equals(name)) {
                selectProfile(p, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Edit the live profile (grouping, fields, appearance) and apply it to the tab, as the
     * display settings dialog does.
     *
     * @param edit the change
     */
    public void editLiveProfile(Consumer<DisplayProfile> edit) {
        edit.accept(liveProfile);
        onLiveProfileChanged(true);
    }

    /**
     * Save the live state as a new or replaced profile and select it.
     *
     * @param name the profile name
     */
    public void saveLiveAsProfile(String name) {
        DisplayProfile target = ProfileDialogs.find(this, name);
        if (target == null) {
            target = new DisplayProfile(name);
            profiles.add(target);
        }
        target.copySettingsFrom(liveProfile);
        selectProfile(target, true);
        persistProfiles();
    }

    /** @return the profiles file on disk */
    public Path profilesFilePath() {
        return profileStore.getFile();
    }

    /** @return the tree root's group values at the top level, for tests */
    public List<String> topLevelGroups() {
        List<String> out = new ArrayList<>();
        if (tree.getRoot() != null) {
            for (TreeItem<Row> item : tree.getRoot().getChildren()) {
                if (item.getValue() instanceof GroupRow g) {
                    out.add(g.value());
                }
            }
        }
        return out;
    }

    /** @return the current rebuild generation, which increases on every rebuild request */
    public int generation() {
        return generation.get();
    }

    /** @return true while the busy strip is shown */
    public boolean isBusy() {
        return busyStrip.isVisible();
    }

    /** Stop the background threads. */
    public void dispose() {
        coordinator.dispose();
        treeExecutor.shutdownNow();
        summaryExecutor.shutdownNow();
        bulkExecutor.shutdownNow();
        thumbnailLoader.shutdown();
        changedPoll.stop();
    }

    /** @return a job progress callback that drives the busy strip with the given text key */
    BulkJobs.Progress busyProgress(String textKey) {
        return (done, total, name) -> Platform.runLater(
                () -> showBusy(Strings.format(textKey, done + 1, total), done, total, true));
    }
}
