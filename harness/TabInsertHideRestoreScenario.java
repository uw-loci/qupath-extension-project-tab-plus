import static java.util.Objects.requireNonNull;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import qupath.ext.projecttabplus.preferences.ProjectTabPlusPreferences;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;

/**
 * The Project+ tab sits right after the built-in Project tab; the preference detaches and
 * re-attaches the built-in tab at its remembered index without touching the project.
 *
 * <p>args: projectDir
 */
public final class TabInsertHideRestoreScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        TabPane tabPane = qupath.getAnalysisTabPane();

        Tab builtIn = HarnessSupport.onFx(() -> HarnessSupport.findBuiltInTab(qupath));
        Tab ours = HarnessSupport.onFx(() -> HarnessSupport.findTab(qupath));
        requireNonNull(builtIn, "built-in tab");
        int builtInIndex = tabPane.getTabs().indexOf(builtIn);
        HarnessSupport.check(tabPane.getTabs().indexOf(ours) == builtInIndex + 1,
                "the Project+ tab is inserted right after the built-in Project tab");
        HarnessSupport.check("Project+".equals(ours.getText()), "the tab is titled Project+");
        HarnessSupport.check(HarnessSupport.waitForShown(pane, 4, 15000),
                "the tree shows every project image (4)");

        HarnessSupport.runFx(() -> ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(true));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> !tabPane.getTabs().contains(builtIn), 3000),
                "hiding detaches the built-in Project tab");
        HarnessSupport.check(tabPane.getTabs().contains(ours), "the Project+ tab stays while the built-in one is hidden");
        HarnessSupport.check(qupath.getProject() != null && qupath.getProject().getImageList().size() == 4,
                "the project is untouched by hiding");
        HarnessSupport.check(HarnessSupport.onFx(() -> pane.statusText()).startsWith("Built-in Project tab hidden"),
                "the status line says the tab was hidden and how to get it back");

        HarnessSupport.runFx(() -> ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(false));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> tabPane.getTabs().indexOf(builtIn) == builtInIndex, 3000),
                "restoring puts the built-in tab back at its original index");
        HarnessSupport.check(tabPane.getTabs().indexOf(ours) == builtInIndex + 1,
                "the Project+ tab is still right after it");

        // Hiding twice and restoring twice must be idempotent.
        HarnessSupport.runFx(() -> ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(true));
        HarnessSupport.runFx(() -> ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(false));
        HarnessSupport.runFx(() -> ProjectTabPlusPreferences.hideBuiltInProjectTabProperty().set(false));
        HarnessSupport.check(tabPane.getTabs().stream().filter(t -> t == builtIn).count() == 1,
                "the built-in tab appears exactly once after repeated hide and restore");

        HarnessSupport.finish();
    }
}
