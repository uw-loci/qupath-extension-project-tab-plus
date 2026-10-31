import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

/**
 * Shared plumbing for the Project+ scenarios: FX startup, the hidden GUI, finding the tab, and
 * driving a modal dialog from off the FX thread by window title and button text.
 */
final class HarnessSupport {

    static int failures = 0;

    private HarnessSupport() {
    }

    static void ok(String what) {
        System.out.println("[PASS] " + what);
    }

    static void bad(String what) {
        System.out.println("[FAIL] " + what);
        failures++;
    }

    static void check(boolean condition, String what) {
        if (condition) {
            ok(what);
        } else {
            bad(what);
        }
    }

    static void note(String what) {
        System.out.println("[....] " + what);
    }

    static void finish() {
        System.out.println();
        System.out.println(failures == 0 ? "SCENARIO PASSED" : "SCENARIO FAILED (" + failures + ")");
        Platform.exit();
        System.exit(failures == 0 ? 0 : 1);
    }

    static void startFx() throws Exception {
        CountDownLatch up = new CountDownLatch(1);
        try {
            Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        }
        if (!up.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX did not start");
        }
        Platform.setImplicitExit(false);
    }

    static <T> T onFx(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(120, TimeUnit.SECONDS);
    }

    /** Queue work on the FX thread without waiting: for actions that open a modal dialog. */
    static void fireFx(Runnable work) {
        Platform.runLater(work);
    }

    static void runFx(Runnable work) throws Exception {
        onFx(() -> {
            work.run();
            return null;
        });
    }

    static QuPathGUI hiddenGui() throws Exception {
        startFx();
        QuPathGUI qupath = onFx(QuPathGUI::createHiddenInstance);
        note("hidden QuPathGUI up, QuPath " + QuPathGUI.getVersion());
        // The extension inserts its tab in a later FX pulse than install.
        for (int i = 0; i < 50 && onFx(() -> findTab(qupath)) == null; i++) {
            Thread.sleep(100);
        }
        return qupath;
    }

    static Project<BufferedImage> openProject(QuPathGUI qupath, String projectDir) throws Exception {
        Project<BufferedImage> project = ProjectIO.loadProject(
                java.nio.file.Path.of(projectDir, "project.qpproj").toFile(), BufferedImage.class);
        runFx(() -> qupath.setProject(project));
        note("project loaded, " + project.getImageList().size() + " image(s)");
        return project;
    }

    static Tab findTab(QuPathGUI qupath) {
        TabPane tabPane = qupath.getAnalysisTabPane();
        if (tabPane == null) {
            return null;
        }
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getContent() instanceof ProjectTabPlusPane) {
                return tab;
            }
        }
        return null;
    }

    static Tab findBuiltInTab(QuPathGUI qupath) {
        for (Tab tab : qupath.getAnalysisTabPane().getTabs()) {
            Node c = tab.getContent();
            if (c != null && c.getStyleClass().contains("project-browser")) {
                return tab;
            }
        }
        return null;
    }

    static ProjectTabPlusPane pane(QuPathGUI qupath) throws Exception {
        Tab tab = onFx(() -> findTab(qupath));
        if (tab == null) {
            throw new IllegalStateException("The Project+ tab was not installed");
        }
        return (ProjectTabPlusPane) tab.getContent();
    }

    /** Wait until the tree shows the given number of images, or time out. */
    static boolean waitForShown(ProjectTabPlusPane pane, int count, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (onFx(() -> pane.shownIds().size()) == count) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    /** Wait for a window whose title starts with the prefix. */
    static Window waitForWindow(String titlePrefix, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            Window w = onFx(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof Stage stage && stage.isShowing() && stage.getTitle() != null
                            && stage.getTitle().startsWith(titlePrefix)) {
                        return stage;
                    }
                }
                return null;
            });
            if (w != null) {
                return w;
            }
            Thread.sleep(100);
        }
        return null;
    }

    static List<String> windowTitles() throws Exception {
        return onFx(() -> {
            List<String> titles = new ArrayList<>();
            for (Window window : Window.getWindows()) {
                if (window instanceof Stage stage && stage.isShowing()) {
                    titles.add(stage.getTitle() == null ? "untitled" : stage.getTitle());
                }
            }
            return titles;
        });
    }

    /** Fire the button with the given text in a window; returns false when not found. */
    static boolean pressButton(Window window, String text) throws Exception {
        return onFx(() -> {
            for (Node node : window.getScene().getRoot().lookupAll(".button")) {
                if (node instanceof Button button && text.equals(button.getText())) {
                    button.fire();
                    return true;
                }
            }
            return false;
        });
    }

    static List<String> buttonTexts(Window window) throws Exception {
        return onFx(() -> {
            List<String> texts = new ArrayList<>();
            for (Node node : window.getScene().getRoot().lookupAll(".button")) {
                if (node instanceof Button button) {
                    texts.add(button.getText() + (button.isDefaultButton() ? "*" : ""));
                }
            }
            return texts;
        });
    }

    static boolean waitClosed(Window window, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (!onFx(window::isShowing)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    static boolean waitUntil(Callable<Boolean> condition, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(condition.call())) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
