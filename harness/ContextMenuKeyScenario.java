import java.util.ArrayList;
import java.util.List;
import javafx.event.Event;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import qupath.ext.projecttabplus.model.ImageRow;
import qupath.ext.projecttabplus.model.Row;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;

/**
 * Keyboard access to the row context menu. JavaFX builds a keyboard ContextMenuEvent only from a
 * native glass menu event, which not every platform sends, so the pane turns Shift+F10 and the
 * Menu key into one itself. This scenario fires those KeyEvents at the tree with an image row
 * selected and checks that a keyboard-triggered CONTEXT_MENU_REQUESTED reaches the tree and is
 * consumed there (fireEvent dispatches a copy, so the KeyEvent's own consumed flag is not
 * observable), and that plain F10 produces none. The popup itself cannot open in the hidden
 * GUI (the tab content has no scene), so its position is not checked here.
 *
 * <p>args: projectDir
 */
public final class ContextMenuKeyScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.check(HarnessSupport.waitForShown(pane, 4, 15000), "the tree shows every project image (4)");
        TreeView<Row> tree = pane.tree();
        ContextMenu menu = HarnessSupport.onFx(tree::getContextMenu);
        HarnessSupport.check(menu != null, "the tree carries the row context menu");

        TreeItem<Row> image = HarnessSupport.onFx(() -> firstImage(tree.getRoot()));
        HarnessSupport.check(image != null, "an image row exists");
        HarnessSupport.runFx(() -> tree.getSelectionModel().select(image));

        List<ContextMenuEvent> seen = new ArrayList<>();
        // Registered after the pane's own filter, so it sees the event after that filter consumed it.
        HarnessSupport.runFx(() -> tree.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, seen::add));

        KeyEvent shiftF10 = key(KeyCode.F10, true);
        HarnessSupport.runFx(() -> Event.fireEvent(tree, shiftF10));
        HarnessSupport.check(seen.size() == 1 && seen.get(0).isKeyboardTrigger(),
                "Shift+F10 raises one keyboard-triggered context menu request at the tree");
        HarnessSupport.check(seen.size() == 1 && seen.get(0).isConsumed(),
                "the pane's filter consumes the request (the pointer-positioned default never runs)");

        KeyEvent menuKey = key(KeyCode.CONTEXT_MENU, false);
        HarnessSupport.runFx(() -> Event.fireEvent(tree, menuKey));
        HarnessSupport.check(seen.size() == 2 && seen.get(1).isKeyboardTrigger(),
                "the Menu key raises a keyboard-triggered context menu request too");

        KeyEvent plainF10 = key(KeyCode.F10, false);
        HarnessSupport.runFx(() -> Event.fireEvent(tree, plainF10));
        HarnessSupport.check(seen.size() == 2, "plain F10 raises nothing");

        ContextMenuEvent mouse = new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 0, 0, 0, 0, false, null);
        HarnessSupport.runFx(() -> Event.fireEvent(tree, mouse));
        HarnessSupport.check(seen.size() == 3 && !mouse.isConsumed(),
                "a pointer-triggered request passes through untouched (right-click keeps its default)");

        HarnessSupport.note("the popup cannot open in the hidden GUI (tree scene = "
                + HarnessSupport.onFx(tree::getScene) + "); its placement at the row is owed a visible check");
        HarnessSupport.finish();
    }

    private static KeyEvent key(KeyCode code, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false);
    }

    private static TreeItem<Row> firstImage(TreeItem<Row> item) {
        if (item == null) {
            return null;
        }
        if (item.getValue() instanceof ImageRow) {
            return item;
        }
        for (TreeItem<Row> child : item.getChildren()) {
            TreeItem<Row> found = firstImage(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
