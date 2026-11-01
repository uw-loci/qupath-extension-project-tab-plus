package qupath.ext.projecttabplus.core;

import java.awt.image.BufferedImage;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeView;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Decides when the tree is rebuilt. PROJECT, MANUAL, MASK_NAMES and PROFILE rebuild at once;
 * FILTER is debounced 150 ms and ROOT_HOOK (the built-in tree replacing its root, used only as a
 * trigger) 250 ms; TAB_SELECTED rebuilds only when the project's image list changed by identity
 * or size since the last build.
 */
public final class RefreshCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(RefreshCoordinator.class);

    /** Why a rebuild was requested. */
    public enum Reason {
        PROJECT, FILTER, ROOT_HOOK, MANUAL, TAB_SELECTED, MASK_NAMES, PROFILE
    }

    private final QuPathGUI qupath;
    private final Runnable rebuild;
    private final PauseTransition filterDebounce = new PauseTransition(Duration.millis(150));
    private final PauseTransition hookDebounce = new PauseTransition(Duration.millis(250));
    private final ChangeListener<Object> hookListener = (obs, o, n) -> hookDebounce.playFromStart();
    private TreeView<?> hookedTree;
    private List<ProjectImageEntry<BufferedImage>> lastList = List.of();

    /**
     * @param qupath the QuPath instance
     * @param rebuild what to run, on the FX thread, when a rebuild is due
     */
    public RefreshCoordinator(QuPathGUI qupath, Runnable rebuild) {
        this.qupath = qupath;
        this.rebuild = rebuild;
        filterDebounce.setOnFinished(e -> rebuild.run());
        hookDebounce.setOnFinished(e -> rebuild.run());
        PathPrefs.maskImageNamesProperty().addListener((obs, o, n) -> request(Reason.MASK_NAMES));
    }

    /**
     * Ask for a rebuild. Safe from any thread. FILTER and ROOT_HOOK are debounced; TAB_SELECTED
     * rebuilds only when the image list changed; every other reason cancels pending debounces
     * and rebuilds now.
     *
     * @param reason what changed
     */
    public void request(Reason reason) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> request(reason));
            return;
        }
        switch (reason) {
            case FILTER -> filterDebounce.playFromStart();
            case ROOT_HOOK -> hookDebounce.playFromStart();
            case TAB_SELECTED -> {
                if (imageListChanged()) {
                    rebuild.run();
                }
            }
            default -> {
                filterDebounce.stop();
                hookDebounce.stop();
                rebuild.run();
            }
        }
    }

    /**
     * Remember the list a build used, so a later tab selection can tell whether it moved.
     *
     * @param list the image list at build time
     */
    public void rememberList(List<ProjectImageEntry<BufferedImage>> list) {
        lastList = List.copyOf(list);
    }

    private boolean imageListChanged() {
        Project<BufferedImage> project = qupath.getProject();
        List<ProjectImageEntry<BufferedImage>> now = project == null ? List.of() : project.getImageList();
        if (now.size() != lastList.size()) {
            return true;
        }
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i) != lastList.get(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Listen to the built-in Project tab's tree root, which QuPath replaces on every refresh.
     * Looked up once; silently does nothing when the pane cannot be found.
     *
     * @param analysisTabPane the analysis pane
     */
    public void hookBuiltInTree(TabPane analysisTabPane) {
        if (hookedTree != null || analysisTabPane == null) {
            return;
        }
        for (Tab tab : analysisTabPane.getTabs()) {
            Node content = tab.getContent();
            if (content != null && content.getStyleClass().contains("project-browser")) {
                Node tree = content.lookup(".tree-view");
                if (tree instanceof TreeView<?> tv) {
                    hookedTree = tv;
                    tv.rootProperty().addListener(hookListener);
                    logger.debug("Hooked the built-in project tree root");
                }
                return;
            }
        }
    }

    /** Detach the built-in tree hook and stop the timers. */
    public void dispose() {
        if (hookedTree != null) {
            hookedTree.rootProperty().removeListener(hookListener);
            hookedTree = null;
        }
        filterDebounce.stop();
        hookDebounce.stop();
    }
}
