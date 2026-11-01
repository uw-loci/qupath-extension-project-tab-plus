import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.TextArea;
import javafx.stage.Window;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Bulk clear with one entry whose data.qpdata has been overwritten with text: that entry is
 * reported as failed with the read reason in D7, its data file and summary.json are left
 * untouched (QuPath would otherwise answer the failed read with an empty ImageData of type
 * UNSET, which a save would persist), and the readable entry is cleared.
 *
 * <p>args: projectDir
 */
public final class BulkClearUnreadableScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        Project<BufferedImage> project = HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.waitForShown(pane, 4, 15000);
        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        ProjectImageEntry<BufferedImage> corrupt = entries.get(0);
        ProjectImageEntry<BufferedImage> good = entries.get(1);
        HarnessSupport.check(good.readHierarchy().nObjects() > 0, "the readable entry starts with saved objects");

        Path dataFile = corrupt.getEntryPath().resolve("data.qpdata");
        Path summaryFile = corrupt.getEntryPath().resolve("summary.json");
        Files.writeString(dataFile, "this is not a data file", StandardCharsets.UTF_8);
        String summaryBefore = Files.exists(summaryFile) ? Files.readString(summaryFile, StandardCharsets.UTF_8) : null;
        long dataBefore = Files.size(dataFile);
        HarnessSupport.note("corrupted " + dataFile + " (" + dataBefore + " bytes); summary present: " + (summaryBefore != null));

        HarnessSupport.runFx(() -> {
            pane.checkboxModeProperty().set(true);
            pane.checkedIds().add(corrupt.getID());
            pane.checkedIds().add(good.getID());
        });
        HarnessSupport.fireFx(() -> pane.bulkCommands().clearCheckedData());
        Window d5 = HarnessSupport.waitForWindow("Project+ - Clear saved data from 2 images", 10000);
        HarnessSupport.check(d5 != null, "D5 opens for both entries");
        if (d5 == null) {
            HarnessSupport.finish();
            return;
        }
        HarnessSupport.check(HarnessSupport.pressButton(d5, "Clear saved data"), "pressed Clear saved data");

        Window d7 = HarnessSupport.waitForWindow("Project+ - Clearing finished with errors", 30000);
        HarnessSupport.check(d7 != null, "D7 reports that the clear finished with errors");
        if (d7 != null) {
            String failed = HarnessSupport.onFx(() -> {
                for (Node node : d7.getScene().getRoot().lookupAll(".text-area")) {
                    if (node instanceof TextArea area) {
                        return area.getText();
                    }
                }
                return "";
            });
            HarnessSupport.note("D7 failures: " + failed);
            HarnessSupport.check(failed.contains(corrupt.getImageName() + ": data file could not be read; not cleared (see log)"),
                    "the corrupt entry is listed with the read reason");
            HarnessSupport.check(!failed.contains(good.getImageName()), "the readable entry is not listed as failed");
            HarnessSupport.pressButton(d7, "Close");
        }
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText).startsWith("Cleared saved data for 1 of 2 images; 1 failed"), 10000),
                "the status line counts one cleared and one failed");
        HarnessSupport.check(Files.size(dataFile) == dataBefore
                        && "this is not a data file".equals(Files.readString(dataFile, StandardCharsets.UTF_8)),
                "the unreadable data file was not overwritten");
        String summaryAfter = Files.exists(summaryFile) ? Files.readString(summaryFile, StandardCharsets.UTF_8) : null;
        HarnessSupport.check(java.util.Objects.equals(summaryBefore, summaryAfter),
                "summary.json of the corrupt entry is unchanged (image type not reset)");
        HarnessSupport.check(good.readHierarchy().nObjects() == 0, "the readable entry was cleared");

        HarnessSupport.finish();
    }
}
