package qupath.ext.projecttabplus;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.projecttabplus.model.ProfileStore;
import qupath.ext.projecttabplus.preferences.ProjectTabPlusPreferences;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.ext.projecttabplus.ui.Strings;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.UserDirectoryManager;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Entry point: creates the Project+ tab beside QuPath's own Project tab, registers the
 * preferences, and applies the "hide built-in tab" preference in a later FX pulse than the
 * install so other extensions that look up the built-in tab find it first.
 */
public class ProjectTabPlusExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(ProjectTabPlusExtension.class);
    private static final String BUILT_IN_STYLE = "project-browser";

    private QuPathGUI qupath;
    private ProjectTabPlusPane pane;
    private Tab tab;
    private Tab builtInTab;
    private int builtInIndex = 0;
    private boolean installed;

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (installed) {
            return;
        }
        installed = true;
        this.qupath = qupath;
        ProjectTabPlusPreferences.installPreferences(qupath);
        Platform.runLater(this::installTab);
    }

    private void installTab() {
        TabPane tabPane = qupath.getAnalysisTabPane();
        if (tabPane == null) {
            logger.warn("Analysis tab pane is not available; the Project+ tab was not added");
            Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("notify.noTabPane"));
            return;
        }
        ProfileStore store = new ProfileStore(ProfileStore.defaultFile(
                UserDirectoryManager.getInstance().getUserPath()));
        pane = new ProjectTabPlusPane(qupath, store);
        tab = new Tab(Strings.get("tab.text"), pane);
        tab.setTooltip(new Tooltip(Strings.get("tab.tooltip")));
        tab.setClosable(false);

        builtInTab = findBuiltInTab(tabPane);
        int index = builtInTab == null ? 0 : tabPane.getTabs().indexOf(builtInTab) + 1;
        tabPane.getTabs().add(Math.min(index, tabPane.getTabs().size()), tab);
        FXUtils.makeTabUndockable(tab);

        BooleanBinding visibleForUpdates = Bindings.createBooleanBinding(
                () -> tab.getTabPane() == null || (tab.isSelected() && qupath.showAnalysisPaneProperty().get()),
                tab.tabPaneProperty(), tab.selectedProperty(), qupath.showAnalysisPaneProperty());
        visibleForUpdates.addListener((obs, o, n) -> {
            if (n) {
                pane.onTabSelected();
            }
        });

        ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().addListener((obs, o, n) -> applyHide(n));
        // A later pulse than the install, so an extension that looks the built-in tab up at
        // startup (Active Image Helper) finds it before it is detached.
        Platform.runLater(() -> {
            if (ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().get()) {
                applyHide(true);
            }
            if (ProjectTabPlusPreferences.selectTabOnStartupProperty().get() && qupath.getProject() != null
                    && tab.getTabPane() != null) {
                tab.getTabPane().getSelectionModel().select(tab);
            }
        });
        logger.info("Project+ tab installed");
    }

    private static Tab findBuiltInTab(TabPane tabPane) {
        for (Tab t : tabPane.getTabs()) {
            Node content = t.getContent();
            if (content != null && content.getStyleClass().contains(BUILT_IN_STYLE)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Detach or re-attach QuPath's own Project tab. The Tab object is kept, so QuPath keeps
     * using its ProjectBrowser internally; an undocked built-in tab cannot be hidden.
     */
    private void applyHide(boolean hide) {
        TabPane tabPane = qupath.getAnalysisTabPane();
        if (tabPane == null) {
            return;
        }
        if (builtInTab == null) {
            builtInTab = findBuiltInTab(tabPane);
        }
        if (builtInTab == null) {
            return;
        }
        if (hide) {
            if (!tabPane.getTabs().contains(builtInTab)) {
                if (builtInTab.getTabPane() == null && builtInTab.getContent() != null
                        && builtInTab.getContent().getScene() != null) {
                    // Undocked into its own window: nothing to hide.
                    Dialogs.showWarningNotification(Strings.get("notify.title"), Strings.get("notify.builtInUndocked"));
                    ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(false);
                }
                return;
            }
            builtInIndex = tabPane.getTabs().indexOf(builtInTab);
            boolean wasSelected = builtInTab.isSelected();
            tabPane.getTabs().remove(builtInTab);
            if (wasSelected && tab != null && tab.getTabPane() == tabPane) {
                tabPane.getSelectionModel().select(tab);
            }
            if (pane != null) {
                pane.setTransientStatus(Strings.get("status.builtInHidden"), false);
            }
            logger.info("Built-in Project tab hidden");
        } else {
            if (tabPane.getTabs().contains(builtInTab)) {
                return;
            }
            tabPane.getTabs().add(Math.min(builtInIndex, tabPane.getTabs().size()), builtInTab);
            if (pane != null) {
                pane.setTransientStatus(Strings.get("status.builtInShown"), false);
            }
            logger.info("Built-in Project tab shown");
        }
    }

    /** @return the tab, or null before install */
    public Tab getTab() {
        return tab;
    }

    /** @return the pane, or null before install */
    public ProjectTabPlusPane getPane() {
        return pane;
    }

    @Override
    public String getName() {
        return Strings.get("name");
    }

    @Override
    public String getDescription() {
        return "Project tab with multi-level metadata grouping, display profiles, jump to the current image, saved-data indicators and checkbox-mode bulk actions.";
    }

    @Override
    public Version getQuPathVersion() {
        return Version.parse("0.7.0");
    }
}
