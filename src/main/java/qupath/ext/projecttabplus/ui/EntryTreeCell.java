package qupath.ext.projecttabplus.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import qupath.ext.projecttabplus.core.ObjectPresence;
import qupath.ext.projecttabplus.model.DisplayProfile;
import qupath.ext.projecttabplus.model.DisplayProfile.IndicatorStyle;
import qupath.ext.projecttabplus.model.DisplayProfile.ThumbnailSize;
import qupath.ext.projecttabplus.model.GroupRow;
import qupath.ext.projecttabplus.model.GroupingKey;
import qupath.ext.projecttabplus.model.ImageRow;
import qupath.ext.projecttabplus.model.Row;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;

/**
 * One tree row: an optional checkbox, the thumbnail with indicator edges, the name with the
 * {@code [open]} chip, and the optional fields and badge lines. Reads nothing from disk; the
 * thumbnail and summary come from the pane's caches, which fill in from their own threads.
 */
final class EntryTreeCell extends TreeCell<Row> {

    static final String STYLE_CURRENT = "ptp-current-image";
    static final String STYLE_CURRENT_ANCESTOR = "ptp-current-ancestor";
    static final String STYLE_NO_DATA = "no-saved-data";
    static final String STYLE_MISSING = "missing-uri";

    private static final double EDGE = 3.0;
    private static final double TOOLTIP_THUMB = 250;

    private final ProjectTabPlusPane pane;

    private final CheckBox check = new CheckBox();
    private final Canvas canvas = new Canvas();
    private final StackPane thumbPane = new StackPane();
    private final Node missingIcon = IconFactory.createNode(15, 15, PathIcons.WARNING);
    private final Node failedIcon = IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER);
    private final Label name = new Label();
    private final Label chip = new Label(Strings.get("row.openChip"));
    private final Label groupChip = new Label(Strings.get("row.openInsideChip"));
    private final Label fields = new Label();
    private final HBox badges = new HBox(6);
    private final VBox textBox = new VBox(1);
    private final HBox imageBox = new HBox(6);

    private final Label groupKey = new Label();
    private final Label groupText = new Label();
    private final Label groupChips = new Label();
    private final HBox groupBox = new HBox(6);

    private final Tooltip tooltip = Tooltips.of("");
    private final ImageView tooltipImage = new ImageView();

    private String shownThumbnailId;

    EntryTreeCell(ProjectTabPlusPane pane) {
        this.pane = pane;

        // A tiny preferred width keeps the virtual flow from widening every cell to the longest row;
        // the cell is laid out at the viewport width and the labels below ellipsize instead.
        setPrefWidth(1);

        check.setFocusTraversable(false);
        check.setOnAction(e -> {
            TreeItem<Row> item = getTreeItem();
            if (item != null) {
                pane.setItemChecked(item, check.isSelected());
            }
        });

        canvas.getStyleClass().add("ptp-thumbnail");
        thumbPane.getChildren().addAll(canvas, missingIcon, failedIcon);
        thumbPane.setAlignment(Pos.CENTER);
        missingIcon.getStyleClass().add(STYLE_MISSING);
        Tooltip.install(missingIcon, Tooltips.of(Strings.get("row.missingFile")));
        Tooltip.install(failedIcon, Tooltips.of(Strings.get("row.thumbnailFailed")));

        // Names, keys and values are shown verbatim: an underscore is never a mnemonic marker.
        for (Label l : new Label[] {name, fields, groupKey, groupText, groupChips}) {
            l.setMnemonicParsing(false);
        }
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(name, Priority.ALWAYS);
        chip.getStyleClass().add("ptp-open-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        HBox nameRow = new HBox(4, name, chip);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        fields.getStyleClass().add("ptp-fields");
        fields.setMinWidth(0);
        fields.setTextOverrun(OverrunStyle.ELLIPSIS);
        badges.getStyleClass().add("ptp-badges");

        textBox.getChildren().addAll(nameRow, fields, badges);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setMinWidth(0);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        imageBox.setAlignment(Pos.CENTER_LEFT);
        imageBox.setMinWidth(0);

        groupKey.getStyleClass().add("ptp-group-key");
        groupKey.setMinWidth(Region.USE_PREF_SIZE);
        groupText.setMinWidth(0);
        groupText.setTextOverrun(OverrunStyle.ELLIPSIS);
        groupChips.getStyleClass().add("ptp-group-chip");
        groupChips.setMinWidth(Region.USE_PREF_SIZE);
        groupChip.getStyleClass().add("ptp-open-chip");
        groupChip.setMinWidth(Region.USE_PREF_SIZE);
        groupBox.setMinWidth(0);
        groupBox.setAlignment(Pos.CENTER_LEFT);

        tooltipImage.setFitWidth(TOOLTIP_THUMB);
        tooltipImage.setFitHeight(TOOLTIP_THUMB);
        tooltipImage.setPreserveRatio(true);
    }

    @Override
    protected void updateItem(Row item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().removeAll(STYLE_CURRENT, STYLE_CURRENT_ANCESTOR, STYLE_NO_DATA, STYLE_MISSING);
        setStyle("");
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            shownThumbnailId = null;
            return;
        }
        setText(null);
        if (item instanceof GroupRow group) {
            updateGroup(group);
        } else if (item instanceof ImageRow row) {
            updateImage(row);
        }
    }

    private void updateGroup(GroupRow group) {
        shownThumbnailId = null;
        groupBox.getChildren().clear();
        boolean mode = pane.isCheckboxMode();
        if (mode) {
            int[] counts = pane.checkedUnder(group);
            check.setIndeterminate(counts[0] > 0 && counts[0] < counts[1]);
            check.setSelected(counts[1] > 0 && counts[0] == counts[1]);
            check.setTooltip(Tooltips.of(Strings.get("bulk.groupCheckbox.tooltip")));
            check.setAccessibleText(group.value());
            groupBox.getChildren().add(check);
        }
        if (group.key() != null) {
            groupKey.setText(Strings.format("row.groupKeyPrefix", group.key().displayName()));
            groupBox.getChildren().add(groupKey);
        }
        groupText.setText(group.value() + " (" + group.imageCount() + ")");
        groupBox.getChildren().add(groupText);
        TreeItem<Row> treeItem = getTreeItem();
        boolean collapsed = treeItem != null && !treeItem.isExpanded();
        boolean currentInside = pane.isCurrentHiddenUnder(treeItem);
        if (currentInside) {
            getStyleClass().add(STYLE_CURRENT_ANCESTOR);
            String custom = pane.customHighlightStyle();
            if (custom != null) {
                setStyle(custom);
            }
            groupBox.getChildren().add(groupChip);
        }
        if (collapsed && pane.profile().getIndicators() != IndicatorStyle.OFF) {
            boolean[] any = pane.presenceUnder(group);
            groupChips.setText((any[0] ? Strings.get("row.badge.annotations") : "-") + " "
                    + (any[1] ? Strings.get("row.badge.detections") : "-") + " "
                    + (any[2] ? Strings.get("row.badge.tma") : "-"));
            groupBox.getChildren().add(groupChips);
        }
        setGraphic(groupBox);
        tooltip.setGraphic(null);
        String groupTip = group.key() == null ? group.value() : group.key().displayName() + ": " + group.value();
        tooltip.setText(currentInside ? groupTip + "\n" + Strings.get("row.tooltip.openInside") : groupTip);
        setTooltip(pane.isContextMenuShowing() ? null : tooltip);
    }

    private void updateImage(ImageRow row) {
        DisplayProfile profile = pane.profile();
        boolean current = pane.isCurrent(row);
        ObjectPresence presence = pane.presence(row);
        if (current) {
            getStyleClass().add(STYLE_CURRENT);
            String custom = pane.customHighlightStyle();
            if (custom != null) {
                setStyle(custom);
            }
        }
        if (!row.info().hasImageData()) {
            getStyleClass().add(STYLE_NO_DATA);
        }
        if (row.info().fileMissing()) {
            getStyleClass().add(STYLE_MISSING);
        }

        imageBox.getChildren().clear();
        if (pane.isCheckboxMode()) {
            check.setIndeterminate(false);
            check.setSelected(pane.checkedIds().contains(row.id()));
            check.setTooltip(null);
            check.setAccessibleText(row.info().name());
            imageBox.getChildren().add(check);
        }

        ThumbnailSize size = profile.getThumbnailSize();
        canvas.setWidth(size.width());
        canvas.setHeight(size.height());
        thumbPane.setPrefSize(size.width(), size.height());
        thumbPane.setMinSize(size.width(), size.height());
        canvas.setOpacity(row.info().fileMissing() ? 0.2 : 1.0);
        missingIcon.setVisible(row.info().fileMissing());
        failedIcon.setVisible(false);
        Image thumb = null;
        if (size != ThumbnailSize.HIDDEN) {
            thumb = pane.thumbnail(row);
            failedIcon.setVisible(thumb == null && pane.thumbnailFailed(row));
        }
        shownThumbnailId = row.id();
        paint(thumb, presence, profile.getIndicators() == IndicatorStyle.EDGES);
        imageBox.getChildren().add(thumbPane);

        name.setText(row.info().name());
        chip.setVisible(current);
        chip.setManaged(current);

        String fieldsText = fieldsLine(row, profile);
        fields.setText(fieldsText);
        fields.setVisible(!fieldsText.isEmpty());
        fields.setManaged(!fieldsText.isEmpty());

        badges.getChildren().clear();
        boolean showBadges = profile.getIndicators() == IndicatorStyle.BADGES;
        if (showBadges) {
            fillBadges(row, presence);
        }
        badges.setVisible(showBadges);
        badges.setManaged(showBadges);

        imageBox.getChildren().add(textBox);
        setGraphic(imageBox);

        tooltip.setText(tooltipText(row, presence, current));
        tooltipImage.setImage(thumb);
        tooltip.setGraphic(thumb == null ? null : tooltipImage);
        setTooltip(pane.isContextMenuShowing() ? null : tooltip);
    }

    /** @return the entry ID whose thumbnail this cell is showing, or null */
    String shownThumbnailId() {
        return shownThumbnailId;
    }

    private static String fieldsLine(ImageRow row, DisplayProfile profile) {
        StringBuilder sb = new StringBuilder();
        for (GroupingKey key : profile.fieldKeys()) {
            if (key.isNameLevel() && PathPrefs.maskImageNamesProperty().get()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(key.displayName()).append(": ").append(key.valueOf(row.info()));
        }
        return sb.toString();
    }

    private void fillBadges(ImageRow row, ObjectPresence presence) {
        if (!row.info().hasImageData() && !presence.isKnown()) {
            badges.getChildren().add(mutedBadge(Strings.get("row.badge.noSavedData")));
            return;
        }
        if (presence.unreadable()) {
            badges.getChildren().add(mutedBadge(Strings.get("row.tooltip.unreadable")));
            return;
        }
        if (!presence.isKnown()) {
            badges.getChildren().add(mutedBadge(Strings.get("row.badge.notLoaded")));
            return;
        }
        if (presence.state() == ObjectPresence.State.NONE) {
            badges.getChildren().add(mutedBadge(Strings.get("row.badge.noObjects")));
            return;
        }
        String star = presence.unsaved() ? "*" : "";
        badges.getChildren().add(badge(Strings.get("row.badge.annotations"), presence.annotations(), star,
                ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get())));
        badges.getChildren().add(badge(Strings.get("row.badge.detections"), presence.detectionsLike(), star,
                ThemeColors.detectionsPaint()));
        badges.getChildren().add(badge(Strings.get("row.badge.tma"), presence.tmaCores(), star,
                ColorToolsFX.getCachedColor(PathPrefs.colorTMAProperty().get())));
    }

    private static Label mutedBadge(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("ptp-fields");
        return l;
    }

    private static Label badge(String letter, long count, String star, Color color) {
        Label l = new Label(letter + " " + (count > 0 ? abbreviate(count) + star : Strings.get("row.badge.absent")));
        l.getStyleClass().add("ptp-badge");
        l.setStyle("-fx-border-color: " + toWeb(color) + ";");
        return l;
    }

    static String abbreviate(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        if (n < 10_000) {
            return String.format("%.1fk", n / 1000.0);
        }
        if (n < 1_000_000) {
            return (n / 1000) + "k";
        }
        return String.format("%.1fM", n / 1_000_000.0);
    }

    private static String toWeb(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    private void paint(Image image, ObjectPresence presence, boolean edges) {
        GuiTools.paintImage(canvas, image);
        if (!edges || !presence.isKnown() || presence.state() != ObjectPresence.State.PRESENT) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.setLineWidth(EDGE);
        if (presence.unsaved()) {
            gc.setLineDashes(3, 3);
        } else {
            gc.setLineDashes((double[]) null);
        }
        if (presence.hasAnnotations()) {
            gc.setStroke(ColorToolsFX.getCachedColor(PathPrefs.colorDefaultObjectsProperty().get()));
            gc.strokeLine(0, EDGE / 2, w, EDGE / 2);
        }
        if (presence.hasDetections()) {
            Hatch.paint(gc, w - Hatch.STRIP_WIDTH, 0, Hatch.STRIP_WIDTH, h, ThemeColors.detectionsPaint(),
                    presence.unsaved());
        }
        if (presence.hasTmaCores()) {
            gc.setStroke(ColorToolsFX.getCachedColor(PathPrefs.colorTMAProperty().get()));
            gc.strokeLine(0, h - EDGE / 2, w, h - EDGE / 2);
        }
        gc.setLineDashes((double[]) null);
    }

    static String tooltipText(ImageRow row, ObjectPresence presence, boolean current) {
        StringBuilder sb = new StringBuilder();
        if (current) {
            sb.append(presence.unsaved() ? Strings.get("row.tooltip.openUnsaved") : Strings.get("row.tooltip.open"));
            if (presence.isKnown()) {
                sb.append('\n').append(presence.unsaved()
                        ? Strings.get("row.tooltip.liveCounts")
                        : Strings.format("row.tooltip.savedData", countsText(presence)));
            }
        } else if (!row.info().hasImageData() && !presence.isKnown()) {
            sb.append(Strings.get("row.tooltip.noSavedData"));
        } else if (presence.unreadable()) {
            sb.append(Strings.get("row.tooltip.unreadable"));
        } else if (!presence.isKnown()) {
            sb.append(Strings.get("row.tooltip.notLoaded"));
        } else if (presence.state() == ObjectPresence.State.NONE) {
            sb.append(Strings.get("row.tooltip.noObjects"));
        } else {
            sb.append(Strings.format("row.tooltip.savedData", countsText(presence)));
        }
        if (row.info().fileMissing()) {
            sb.append('\n').append(Strings.get("row.tooltip.missingFile"));
        }
        if (row.entry() != null) {
            sb.append("\n\n").append(row.entry().getSummary());
        }
        return sb.toString();
    }

    static String countsText(ObjectPresence p) {
        StringBuilder sb = new StringBuilder();
        append(sb, p.annotations(), "row.count.annotations.one", "row.count.annotations.many");
        append(sb, p.detections(), "row.count.detections.one", "row.count.detections.many");
        append(sb, p.cells(), "row.count.cells.one", "row.count.cells.many");
        append(sb, p.tiles(), "row.count.tiles.one", "row.count.tiles.many");
        append(sb, p.tmaCores(), "row.count.tma.one", "row.count.tma.many");
        return sb.length() == 0 ? Strings.get("row.badge.noObjects") : sb.toString();
    }

    private static void append(StringBuilder sb, long n, String one, String many) {
        if (n <= 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(n == 1 ? Strings.format(one, n) : Strings.format(many, n));
    }
}
