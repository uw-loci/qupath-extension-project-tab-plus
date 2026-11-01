import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import qupath.ext.projecttabplus.core.ExportManifest;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Bulk GeoJSON export with two entries sharing an image name: the second file carries the
 * entry ID, the manifest lists both, a second run shows the overwrite prompt, and Skip existing
 * leaves the files alone and reports that nothing was exported. Compress is off by default, so
 * the files are plain .geojson.
 *
 * <p>args: projectDir outputDir
 */
public final class ExportNameCollisionScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        Project<BufferedImage> project = HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.waitForShown(pane, 4, 15000);
        Path out = Path.of(args[1]).resolve("geojson");
        Files.createDirectories(out);
        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        ProjectImageEntry<BufferedImage> a = entries.get(0);
        ProjectImageEntry<BufferedImage> b = entries.get(1);
        String shared = "same name";
        a.setImageName(shared);
        b.setImageName(shared);
        project.syncChanges();
        HarnessSupport.runFx(pane::refreshAfterChange);
        HarnessSupport.waitForShown(pane, 4, 10000);

        runExport(qupath, pane, out, a, b, false);
        Path first = out.resolve(shared + ".geojson");
        Path second = out.resolve(shared + " (" + b.getID() + ").geojson");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> Files.exists(first) && Files.exists(second), 30000),
                "both files exist: the second one carries the entry ID");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText).startsWith("Exported 2 images to"), 10000),
                "the status line reports the export");
        long sizeFirst = Files.size(first);
        long mtimeFirst = Files.getLastModifiedTime(first).toMillis();
        HarnessSupport.check(sizeFirst > 20, "the first file is not empty");
        Path manifestPath = out.resolve(ExportManifest.FILE_NAME);
        HarnessSupport.check(Files.exists(manifestPath), "export-manifest.json was written beside the files");
        if (Files.exists(manifestPath)) {
            ExportManifest manifest = ExportManifest.parse(Files.readString(manifestPath));
            HarnessSupport.note("manifest: QuPath " + manifest.qupathVersion() + ", extension "
                    + manifest.extensionVersion() + ", " + manifest.timestamp() + ", " + manifest.files().size() + " file(s)");
            HarnessSupport.check(manifest.files().size() == 2, "the manifest lists two files");
            HarnessSupport.check(manifest.files().stream().anyMatch(f -> f.entryId().equals(a.getID())
                    && f.fileName().equals(first.getFileName().toString()) && shared.equals(f.imageName())
                    && f.source().equals(ExportManifest.SOURCE_SAVED) && f.objectCount() > 0),
                    "the manifest maps the first entry to its file, name, saved source and a count");
            HarnessSupport.check(manifest.files().stream().anyMatch(f -> f.entryId().equals(b.getID())
                    && f.fileName().equals(second.getFileName().toString())),
                    "the manifest maps the second entry to the suffixed file");
            HarnessSupport.check(!manifest.compress() && "0.7.0".equals(manifest.qupathVersion())
                    && !"unknown".equals(manifest.extensionVersion()), "the manifest records the options and versions");
        }
        Thread.sleep(1100);

        runExport(qupath, pane, out, a, b, true);
        HarnessSupport.check(Files.getLastModifiedTime(first).toMillis() == mtimeFirst,
                "Skip existing left the first file untouched");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText).startsWith("Skipped 2 existing files; nothing exported"), 10000),
                "the status line says every existing file was skipped and nothing exported");
        HarnessSupport.check(Files.getLastModifiedTime(manifestPath).toMillis() >= mtimeFirst
                && ExportManifest.parse(Files.readString(manifestPath)).files().size() == 2,
                "a run that wrote nothing leaves the earlier manifest in place");

        HarnessSupport.finish();
    }

    private static void runExport(QuPathGUI qupath, ProjectTabPlusPane pane, Path out,
                                  ProjectImageEntry<BufferedImage> a, ProjectImageEntry<BufferedImage> b,
                                  boolean expectOverwritePrompt) throws Exception {
        HarnessSupport.runFx(() -> {
            pane.checkboxModeProperty().set(true);
            pane.checkedIds().clear();
            pane.checkedIds().add(a.getID());
            pane.checkedIds().add(b.getID());
        });
        HarnessSupport.fireFx(() -> pane.bulkCommands().exportChecked());
        Window d6 = HarnessSupport.waitForWindow("Project+ - Export GeoJSON for 2 images", 10000);
        HarnessSupport.check(d6 != null, "D6 opens");
        if (d6 == null) {
            return;
        }
        HarnessSupport.runFx(() -> {
            for (Node node : d6.getScene().getRoot().lookupAll(".text-field")) {
                if (node instanceof TextField tf) {
                    tf.setText(out.toString());
                    break;
                }
            }
        });
        HarnessSupport.check(HarnessSupport.pressButton(d6, "Export"), "pressed Export");
        if (expectOverwritePrompt) {
            Window prompt = HarnessSupport.waitForWindow("Project+ - Files already exist", 10000);
            HarnessSupport.check(prompt != null, "the overwrite prompt appears on the second run");
            if (prompt != null) {
                List<String> buttons = HarnessSupport.buttonTexts(prompt);
                HarnessSupport.note("overwrite prompt buttons: " + buttons);
                HarnessSupport.check(buttons.contains("Skip existing*"), "Skip existing is the default");
                HarnessSupport.pressButton(prompt, "Skip existing");
            }
        }
    }
}
