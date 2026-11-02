package qupath.ext.projecttabplus.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import org.controlsfx.control.textfield.TextFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

// Re-implemented from QuPath's ProjectBrowser (GPL-3.0, github.com/qupath/qupath, 0.7.0),
// modified 2026-10/11.

/**
 * The per-entry actions the built-in tab offers (duplicate, rename, add metadata, edit
 * description, refresh thumbnail, open directory), re-implemented against the public API so the
 * tab loses nothing when the built-in tab is hidden.
 */
final class EntryActions {

    private static final Logger logger = LoggerFactory.getLogger(EntryActions.class);
    private static final int THUMBNAIL_WIDTH = 1000;
    private static final int THUMBNAIL_HEIGHT = 600;

    private final ProjectTabPlusPane pane;
    private final QuPathGUI qupath;

    EntryActions(ProjectTabPlusPane pane) {
        this.pane = pane;
        this.qupath = pane.qupath();
    }

    void duplicate(List<ProjectImageEntry<BufferedImage>> entries) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null || entries.isEmpty()) {
            return;
        }
        boolean single = entries.size() == 1;
        String name = "";
        if (single) {
            name = GeneralTools.generateDistinctName(entries.get(0).getImageName(),
                    project.getImageList().stream().map(ProjectImageEntry::getImageName).collect(Collectors.toSet()));
        }
        ParameterList params = new ParameterList()
                .addStringParameter("name", single ? Strings.get("action.duplicate.name") : Strings.get("action.duplicate.append"), name)
                .addBooleanParameter("copyData", Strings.get("action.duplicate.copyData"), true);
        if (!GuiTools.showParameterDialog(single ? Strings.get("action.duplicate.title.one")
                : Strings.get("action.duplicate.title.many"), params)) {
            return;
        }
        boolean copyData = params.getBooleanParameterValue("copyData");
        name = params.getStringParameterValue("name");
        if (!single && !name.isBlank()) {
            name = " " + name.strip();
        }
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            try {
                ProjectImageEntry<BufferedImage> copy = project.addDuplicate(entry, copyData);
                if (copy != null && !name.isBlank()) {
                    copy.setImageName(single ? name : copy.getImageName() + name);
                }
            } catch (Exception ex) {
                Dialogs.showErrorNotification(Strings.get("notify.title"),
                        Strings.format("action.duplicate.error", entry.getImageName()));
                logger.error("Error duplicating {}: {}", entry.getImageName(), ex.getMessage(), ex);
            }
        }
        syncAndRefresh(project);
    }

    void rename(ProjectImageEntry<BufferedImage> entry) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null || entry == null) {
            return;
        }
        String name = Dialogs.showInputDialog(Strings.get("action.rename.title"), Strings.get("action.rename.prompt"),
                entry.getImageName());
        if (name == null || name.trim().isEmpty() || name.equals(entry.getImageName())) {
            return;
        }
        entry.setImageName(name);
        for (var viewer : qupath.getAllViewers()) {
            ImageData<BufferedImage> imageData = viewer.getImageData();
            if (imageData == null || !Objects.equals(entry, project.getEntry(imageData))) {
                continue;
            }
            var server = imageData.getServer();
            if (!name.equals(server.getMetadata().getName())) {
                var metadata = new ImageServerMetadata.Builder(server.getMetadata()).name(name).build();
                imageData.updateServerMetadata(metadata);
                imageData.getHierarchy().fireHierarchyChangedEvent(this);
            }
        }
        qupath.refreshTitle();
        syncAndRefresh(project);
    }

    void addMetadata(List<ProjectImageEntry<BufferedImage>> entries) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null || entries.isEmpty()) {
            logger.error("Add metadata: no project or no image selected");
            Dialogs.showErrorMessage(Strings.get("action.metadata.title"), Strings.get("action.noSelection"));
            return;
        }
        TextField keyField = new TextField();
        TextFields.bindAutoCompletion(keyField, pane.metadataKeys().stream().sorted().toList());
        keyField.setTooltip(Tooltips.of(Strings.get("action.metadata.key.tooltip")));
        TextField valueField = new TextField();
        valueField.setTooltip(Tooltips.of(Strings.get("action.metadata.value.tooltip")));
        Label keyLabel = new Label(Strings.get("action.metadata.key"));
        Label valueLabel = new Label(Strings.get("action.metadata.value"));
        keyLabel.setLabelFor(keyField);
        valueLabel.setLabelFor(valueField);
        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(5);
        grid.add(keyLabel, 0, 0);
        grid.add(keyField, 1, 0);
        grid.add(valueLabel, 0, 1);
        grid.add(valueField, 1, 1);
        ProjectImageEntry<BufferedImage> only = entries.size() == 1 ? entries.get(0) : null;
        if (only != null && !only.getMetadata().isEmpty()) {
            TextArea current = new TextArea(only.getMetadataSummaryString().isEmpty()
                    ? Strings.get("action.metadata.none") : only.getMetadataSummaryString());
            current.setEditable(false);
            current.setPrefRowCount(3);
            current.setTooltip(Tooltips.of(Strings.get("action.metadata.current.tooltip")));
            Label currentLabel = new Label(Strings.get("action.metadata.current"));
            currentLabel.setLabelFor(current);
            grid.add(currentLabel, 0, 2);
            grid.add(current, 1, 2);
        }
        String header = only != null ? Strings.format("action.metadata.header.one", only.getImageName())
                : Strings.format("action.metadata.header.many", entries.size());
        Optional<ButtonType> r = Dialogs.builder()
                .title(Strings.get("action.metadata.title"))
                .headerText(header)
                .content(grid)
                .owner(qupath.getStage())
                .modality(Modality.WINDOW_MODAL)
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        if (key.isEmpty()) {
            logger.warn("Attempted to set a metadata value with an empty key");
            return;
        }
        String value = valueField.getText() == null ? "" : valueField.getText();
        for (ProjectImageEntry<BufferedImage> entry : entries) {
            entry.getMetadata().put(key, value);
        }
        syncAndRefresh(project);
    }

    void editDescription(ProjectImageEntry<BufferedImage> entry) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null || entry == null) {
            logger.error("Edit description: no project or no image selected");
            Dialogs.showErrorMessage(Strings.get("action.description.title"), Strings.get("action.noSelection"));
            return;
        }
        TextArea editor = new TextArea(entry.getDescription());
        editor.setWrapText(true);
        editor.setTooltip(Tooltips.of(Strings.get("action.description.tooltip")));
        editor.setPromptText(Strings.format("action.description.prompt", entry.getImageName()));
        Dialog<ButtonType> dialog = Dialogs.builder()
                .title(Strings.get("action.description.title"))
                .headerText(entry.getImageName())
                .content(editor)
                .owner(qupath.getStage())
                .modality(Modality.WINDOW_MODAL)
                .buttons(ButtonType.OK, ButtonType.CANCEL)
                .resizable()
                .build();
        Platform.runLater(editor::requestFocus);
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK && editor.getText() != null) {
            String text = editor.getText();
            entry.setDescription(text.isEmpty() ? null : text);
            syncAndRefresh(project);
        }
    }

    void refreshThumbnail(ProjectImageEntry<BufferedImage> entry) {
        Project<BufferedImage> project = qupath.getProject();
        ImageData<BufferedImage> imageData = qupath.getImageData();
        if (project == null || entry == null || imageData == null || project.getEntry(imageData) != entry) {
            Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("action.refreshThumbnail.notOpen"));
            return;
        }
        BufferedImage thumb = resizeForThumbnail(qupath.getViewer().getRGBThumbnail());
        pane.bulkExecutor().submit(() -> {
            try {
                entry.setThumbnail(thumb);
            } catch (IOException e) {
                logger.error("Error writing thumbnail: {}", e.getMessage(), e);
            }
            Platform.runLater(() -> pane.invalidateThumbnail(entry.getID()));
        });
    }

    private static BufferedImage resizeForThumbnail(BufferedImage img) {
        double scale = Math.min((double) THUMBNAIL_WIDTH / img.getWidth(), (double) THUMBNAIL_HEIGHT / img.getHeight());
        if (scale > 1) {
            return img;
        }
        BufferedImage out = new BufferedImage((int) (img.getWidth() * scale), (int) (img.getHeight() * scale), img.getType());
        Graphics2D g2d = out.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(img, 0, 0, out.getWidth(), out.getHeight(), null);
        g2d.dispose();
        return out;
    }

    void browseProject() {
        Project<BufferedImage> project = qupath.getProject();
        if (project != null && project.getPath() != null) {
            Path path = project.getPath();
            GuiTools.browseDirectory((java.nio.file.Files.isDirectory(path) ? path : path.getParent()).toFile());
        }
    }

    void browseEntry(ProjectImageEntry<BufferedImage> entry) {
        Path path = entry == null ? null : entry.getEntryPath();
        if (path != null) {
            GuiTools.browseDirectory(path.toFile());
        }
    }

    void browseImage(ProjectImageEntry<BufferedImage> entry) {
        if (entry == null) {
            return;
        }
        try {
            Collection<URI> uris = entry.getURIs();
            if (!uris.isEmpty()) {
                Path path = GeneralTools.toPath(uris.iterator().next());
                if (path != null) {
                    GuiTools.browseDirectory(path.toFile());
                }
            }
        } catch (IOException e) {
            logger.debug("Error converting server path to file path", e);
        }
    }

    /** Save the project on ptp-bulk (no disk writes on the FX thread), then refresh both tabs on FX. */
    private void syncAndRefresh(Project<BufferedImage> project) {
        pane.bulkExecutor().submit(() -> {
            String error = null;
            try {
                project.syncChanges();
            } catch (IOException e) {
                logger.error("Error saving project changes: {}", e.getMessage(), e);
                error = e.getMessage();
            }
            String message = error;
            Platform.runLater(() -> {
                if (message != null) {
                    Dialogs.showErrorNotification(Strings.get("notify.title"), message);
                }
                qupath.refreshProject();
                pane.refreshAfterChange();
            });
        });
    }
}
