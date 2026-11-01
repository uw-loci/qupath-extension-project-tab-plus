import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.List;
import javafx.stage.Window;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Bulk clear with one checked entry open in the viewer: the clear confirmation closes it first,
 * both entries end with zero saved objects, no stale data.qpdata.bkp is left behind (QuPath 0.7
 * deletes it after a good save, so there is no undo), and the viewer is empty.
 *
 * <p>args: projectDir
 */
public final class BulkClearOpenEntryScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        Project<BufferedImage> project = HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.waitForShown(pane, 4, 15000);
        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        ProjectImageEntry<BufferedImage> open = entries.get(1);
        ProjectImageEntry<BufferedImage> closed = entries.get(2);
        int objectsBefore = open.readHierarchy().nObjects();
        HarnessSupport.note("entry " + open.getImageName() + " holds " + objectsBefore + " saved object(s)");
        HarnessSupport.check(objectsBefore > 0, "the open entry starts with saved objects");

        HarnessSupport.runFx(() -> qupath.openImageEntry(open));
        HarnessSupport.waitUntil(() -> HarnessSupport.onFx(qupath::getImageData) != null, 15000);
        HarnessSupport.runFx(() -> {
            pane.checkboxModeProperty().set(true);
            pane.checkedIds().add(open.getID());
            pane.checkedIds().add(closed.getID());
        });
        HarnessSupport.fireFx(() -> pane.bulkCommands().clearCheckedData());
        Window d5 = HarnessSupport.waitForWindow("Project+ - Clear saved data from 2 images", 10000);
        HarnessSupport.check(d5 != null, "D5 opens with the count in the title");
        if (d5 == null) {
            HarnessSupport.finish();
            return;
        }
        List<String> buttons = HarnessSupport.buttonTexts(d5);
        HarnessSupport.note("D5 buttons: " + buttons);
        HarnessSupport.check(buttons.contains("Cancel*"), "Cancel is the default button of D5");
        HarnessSupport.check(HarnessSupport.pressButton(d5, "Clear saved data"), "pressed Clear saved data");
        // A hidden viewer may mark a freshly opened image as changed; answer QuPath's prompt with No.
        Window save = HarnessSupport.waitForWindow("Save changes", 3000);
        if (save != null) {
            HarnessSupport.note("QuPath asked to save changes; answering No");
            HarnessSupport.pressButton(save, "No");
        }

        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(qupath::getImageData) == null, 10000),
                "the open image was closed before clearing");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> open.readHierarchy().nObjects() == 0, 30000),
                "the open entry's saved data has no objects afterwards");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> closed.readHierarchy().nObjects() == 0, 30000),
                "the closed entry's saved data has no objects afterwards");
        // QuPath 0.7 deletes data.qpdata.bkp once the new file is written, so no backup remains.
        HarnessSupport.check(!Files.exists(open.getEntryPath().resolve("data.qpdata.bkp")),
                "no stale data.qpdata.bkp is left behind (QuPath removes it after a good save)");
        HarnessSupport.check(open.hasImageData(), "the entry still has saved data (just empty)");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText).startsWith("Cleared saved data for 2 images"), 10000),
                "the status line reports the clear");
        HarnessSupport.check(project.getImageList().size() == 4, "no entry was removed");

        HarnessSupport.finish();
    }
}
