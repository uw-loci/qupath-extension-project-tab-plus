import java.awt.image.BufferedImage;
import java.util.List;
import javafx.stage.Window;
import qupath.ext.projecttabplus.core.RemoveImageCommand;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObjects;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.roi.ROIs;

/**
 * Remove current image with a dirty viewer, canceling at QuPath's save prompt: nothing is
 * removed, the viewer keeps the image, and the status line says so.
 *
 * <p>args: projectDir
 */
public final class RemoveCurrentDirtyCancelScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        Project<BufferedImage> project = HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.waitForShown(pane, 4, 15000);
        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        ProjectImageEntry<BufferedImage> entry = entries.get(0);
        int before = entries.size();

        HarnessSupport.runFx(() -> qupath.openImageEntry(entry));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(qupath::getImageData) != null, 15000),
                "the image is open in the hidden viewer");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> entry.getID().equals(HarnessSupport.onFx(pane::currentEntryId)), 5000),
                "the tab knows which entry is the current image");

        ImageData<BufferedImage> imageData = HarnessSupport.onFx(qupath::getImageData);
        HarnessSupport.runFx(() -> imageData.getHierarchy().addObject(
                PathObjects.createAnnotationObject(ROIs.createRectangleROI(10, 10, 50, 50, null))));
        HarnessSupport.check(imageData.isChanged(), "the viewer is dirty after adding an annotation");

        HarnessSupport.runFx(() -> new RemoveImageCommand(qupath, pane).run(entry));
        Window d1 = HarnessSupport.waitForWindow("Project+ - Remove current image", 10000);
        HarnessSupport.check(d1 != null, "D1 (Project+ - Remove current image) opens");
        if (d1 == null) {
            HarnessSupport.finish();
            return;
        }
        List<String> buttons = HarnessSupport.buttonTexts(d1);
        HarnessSupport.note("D1 buttons: " + buttons);
        HarnessSupport.check(buttons.contains("Remove*"), "Remove is the default button of D1");
        HarnessSupport.check(HarnessSupport.pressButton(d1, "Remove"), "pressed Remove");

        Window save = HarnessSupport.waitForWindow("Save changes", 10000);
        HarnessSupport.check(save != null, "QuPath's own save prompt appears for the dirty image");
        if (save != null) {
            HarnessSupport.note("save prompt buttons: " + HarnessSupport.buttonTexts(save));
            HarnessSupport.check(HarnessSupport.pressButton(save, "Cancel"), "pressed Cancel on the save prompt");
            HarnessSupport.waitClosed(save, 5000);
        }
        Thread.sleep(1500);
        HarnessSupport.check(project.getImageList().size() == before, "nothing was removed from the project");
        HarnessSupport.check(project.getImageList().contains(entry), "the entry is still in the project");
        HarnessSupport.check(HarnessSupport.onFx(qupath::getImageData) == imageData, "the viewer still shows the image");
        HarnessSupport.check(imageData.isChanged(), "the unsaved changes are still there");
        String status = HarnessSupport.onFx(pane::statusText);
        HarnessSupport.note("status: " + status);
        HarnessSupport.check("Removal canceled".equals(status), "the status line reads Removal canceled");
        HarnessSupport.check(!HarnessSupport.windowTitles().toString().contains("Project+ -"),
                "no Project+ dialog is left open");

        HarnessSupport.finish();
    }
}
