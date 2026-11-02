import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.stage.Window;
import qupath.ext.projecttabplus.model.GroupingKey;
import qupath.ext.projecttabplus.model.GroupingSpec;
import qupath.ext.projecttabplus.model.ImageRow;
import qupath.ext.projecttabplus.model.Row;
import qupath.ext.projecttabplus.model.names.HideExtensionRule;
import qupath.ext.projecttabplus.model.names.HideTextRule;
import qupath.ext.projecttabplus.model.names.NameRule;
import qupath.ext.projecttabplus.model.names.NameRuleSet;
import qupath.ext.projecttabplus.model.names.NameRulesJson;
import qupath.ext.projecttabplus.model.names.PrefixRule;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Display-name rules end to end: rows show short names while tooltips' full names stay in the
 * snapshot, an image-name level groups by the short name, the toggle reverts, masking turns the
 * rules off and keeps the window shut, the rules survive a sync and a reload, and typing a
 * prefix in the window updates the count line, flags a shared short name, and Apply carries it
 * to the tab and its status line.
 *
 * <p>args: projectDir (a COPY; its entries are renamed)
 */
public final class DisplayNameRulesScenario {

    static final String TIF_A = "MetroHealth_FNA_PDAC_1_20x_38323_35919_-7.0_002.ome.tif";
    static final String TIF_B = "MetroHealth_FNA_PDAC_1_20x_38323_35919_7.0_biref_002.ome.tif";
    static final String SVS = "MetroHealth_FNA_PDAC_1.svs";
    static final String OTHER = "PDAC_1.svs";

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        Project<BufferedImage> project = HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.waitForShown(pane, 4, 15000);

        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        String[] names = {TIF_A, TIF_B, SVS, OTHER};
        for (int i = 0; i < 4; i++) {
            entries.get(i).setImageName(names[i]);
        }
        project.syncChanges();
        HarnessSupport.runFx(pane::refreshAfterChange);
        HarnessSupport.check(HarnessSupport.waitUntil(() -> fullNames(pane).contains(TIF_A), 10000),
                "entries renamed to the user's names");

        List<NameRule> four = List.of(new PrefixRule("MetroHealth_FNA_PDAC_", true),
                new HideTextRule("_20x", false, true), new HideExtensionRule(true, true),
                new HideTextRule(".svs", false, true));
        boolean[] saved = {false};
        HarnessSupport.runFx(() -> pane.applyNameRules(new NameRuleSet(true, four), ok -> saved[0] = ok));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> shownNames(pane).contains("1_38323_35919_-7.0_002"), 10000),
                "rows show short names: " + shownNames(pane));
        HarnessSupport.check(shownNames(pane).containsAll(List.of("1_38323_35919_7.0_biref_002", "1", "PDAC_1")),
                "every row is shortened");
        HarnessSupport.check(fullNames(pane).contains(TIF_A), "the snapshot keeps the full name for the tooltip and actions");
        HarnessSupport.check(TIF_A.equals(entries.get(0).getImageName()), "the entry itself is not renamed");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> saved[0], 10000), "the rules were confirmed in data/metadata.json");
        HarnessSupport.check(HarnessSupport.onFx(pane::statusText).contains("short names"),
                "the idle status says short names: " + HarnessSupport.onFx(pane::statusText));

        HarnessSupport.runFx(() -> pane.editLiveProfile(p -> p.setGroupingSpec(
                new GroupingSpec(List.of(GroupingKey.builtin(GroupingKey.Builtin.IMAGE_NAME))))));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::topLevelGroups).contains("PDAC_1"), 10000),
                "an image-name level groups by the short name: " + HarnessSupport.onFx(pane::topLevelGroups));

        HarnessSupport.runFx(() -> pane.showTruncatedNamesProperty().set(false));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::topLevelGroups).contains(OTHER), 10000),
                "turning Show truncated names off brings back the full names");
        HarnessSupport.check(HarnessSupport.waitUntil(() -> readMetadataFile(args[0]).contains("\\\"enabled\\\":false"), 10000),
                "the off state is written with the project");
        HarnessSupport.runFx(() -> pane.showTruncatedNamesProperty().set(true));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::topLevelGroups).contains("PDAC_1"), 10000),
                "and on again shows short names");
        HarnessSupport.runFx(() -> pane.editLiveProfile(p -> p.setGroupingSpec(GroupingSpec.FLAT)));

        HarnessSupport.runFx(() -> PathPrefs.maskImageNamesProperty().set(true));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> {
            List<String> shown = shownNames(pane);
            return !shown.isEmpty() && shown.equals(fullNames(pane)) && !shown.contains("PDAC_1");
        }, 10000), "masking turns the rules off: rows show QuPath's masked names");
        HarnessSupport.runFx(pane::showNameRules);
        Thread.sleep(500);
        HarnessSupport.check(HarnessSupport.waitForWindow("Project+ - Name rules", 300) == null,
                "the rules window does not open while names are masked");
        HarnessSupport.runFx(() -> PathPrefs.maskImageNamesProperty().set(false));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> shownNames(pane).contains("PDAC_1"), 10000),
                "unmasking brings the short names back");

        // Reload the project from disk: the rules come back from the metadata key.
        Project<BufferedImage> reloaded = ProjectIO.loadProject(Path.of(args[0], "project.qpproj").toFile(), BufferedImage.class);
        HarnessSupport.runFx(() -> qupath.setProject(reloaded));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> shownNames(pane).contains("1_38323_35919_-7.0_002"), 15000),
                "after a reload the rows show short names again");
        NameRuleSet back = HarnessSupport.onFx(() -> pane.nameRulesProperty().get());
        HarnessSupport.check(new NameRuleSet(true, four).equals(back), "the reloaded rules equal the applied ones");

        // The window: a typed prefix updates the count line and flags the shared short name.
        HarnessSupport.runFx(pane::showNameRules);
        Window w = HarnessSupport.waitForWindow("Project+ - Name rules", 10000);
        HarnessSupport.check(w != null, "Name rules... opens the window " + HarnessSupport.windowTitles());
        if (w != null) {
            HarnessSupport.check(HarnessSupport.waitUntil(() -> countLine(w).startsWith("4 of 4 names shortened"), 10000),
                    "the count line reads the project: " + countLine(w));
            HarnessSupport.check(countLine(w).contains("No two images share a short name"), "no shared names yet");
            TextField prefix = HarnessSupport.onFx(() -> {
                for (Node n : w.getScene().getRoot().lookupAll(".text-field")) {
                    if (n instanceof TextField f && "MetroHealth_FNA_PDAC_".equals(f.getText())) {
                        return f;
                    }
                }
                return null;
            });
            HarnessSupport.check(prefix != null, "the first rule's editor shows its prefix");
            if (prefix != null) {
                HarnessSupport.runFx(() -> prefix.setText("MetroHealth_FNA_"));
                HarnessSupport.check(HarnessSupport.waitUntil(
                        () -> countLine(w).contains("1 short name is shared by 2 images"), 10000),
                        "typing a prefix flags the shared short name: " + countLine(w));
                HarnessSupport.check(labelTexts(w).stream().anyMatch(t -> t.contains("Common to 3 of 4 names")),
                        "the editor reports how many names start with the text");
                HarnessSupport.check(shownNames(pane).contains("1"), "the tab still shows the applied rules before Apply");
                HarnessSupport.check(HarnessSupport.pressButton(w, "Apply"), "Apply pressed");
                HarnessSupport.check(HarnessSupport.waitUntil(() -> shownNames(pane).stream()
                        .filter("PDAC_1"::equals).count() == 2, 10000), "after Apply two rows share PDAC_1");
                HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText)
                        .startsWith("1 short name is shared by 2 images"), 10000),
                        "the tab status warns about the shared name: " + HarnessSupport.onFx(pane::statusText));
                HarnessSupport.check(HarnessSupport.waitUntil(() -> readMetadataFile(args[0]).contains("MetroHealth_FNA_\\\""), 10000),
                        "the new prefix is saved in data/metadata.json");
                HarnessSupport.check(HarnessSupport.waitUntil(() -> labelTexts(w).stream()
                        .anyMatch(t -> t.startsWith("Rules applied and saved")), 10000), "the window says the rules are saved");
            }
            HarnessSupport.check(HarnessSupport.pressButton(w, "Close"), "Close pressed");
            HarnessSupport.check(HarnessSupport.waitClosed(w, 5000), "an unchanged window closes without a prompt");
        }
        NameRulesJson.Parsed stored = NameRulesJson.parse(reloaded.getMetadata().get(ProjectTabPlusPane.NAME_RULES_KEY));
        HarnessSupport.check(stored.valid() && stored.rules().rules().size() == 4, "the project metadata key holds four rules");

        // Shared short names are reported when the project opens, not only after Apply.
        Project<BufferedImage> again = ProjectIO.loadProject(Path.of(args[0], "project.qpproj").toFile(), BufferedImage.class);
        HarnessSupport.runFx(() -> qupath.setProject(again));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText)
                .startsWith("1 short name is shared by 2 images"), 15000),
                "on open the tab status warns about the shared name: " + HarnessSupport.onFx(pane::statusText));

        // A rule from a newer Project+ survives this build: kept in place, version kept, through a toggle.
        String newer = "{\"version\":2,\"enabled\":true,\"rules\":[{\"kind\":\"regexReplace\",\"pattern\":\"_x\"},"
                + "{\"kind\":\"prefix\",\"text\":\"MetroHealth_FNA_PDAC_\"}]}";
        again.getMetadata().put(ProjectTabPlusPane.NAME_RULES_KEY, newer);
        again.syncChanges();
        Project<BufferedImage> third = ProjectIO.loadProject(Path.of(args[0], "project.qpproj").toFile(), BufferedImage.class);
        HarnessSupport.runFx(() -> qupath.setProject(third));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> shownNames(pane).contains("1_20x_38323_35919_-7.0_002.ome.tif"), 15000),
                "the readable rule still applies: " + shownNames(pane));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::statusText)
                .contains("from a newer Project+"), 5000), "the tab says a rule is kept: " + HarnessSupport.onFx(pane::statusText));
        HarnessSupport.runFx(() -> pane.showTruncatedNamesProperty().set(false));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> {
            String f = readMetadataFile(args[0]);
            return f.contains("\\\"enabled\\\":false") && f.contains("regexReplace") && f.contains("\\\"version\\\":2");
        }, 10000), "a toggle writes the newer rule and version 2 back: " + readMetadataFile(args[0]));
        HarnessSupport.runFx(() -> pane.showTruncatedNamesProperty().set(true));
        HarnessSupport.runFx(pane::showNameRules);
        Window kw = HarnessSupport.waitForWindow("Project+ - Name rules", 10000);
        HarnessSupport.check(kw != null, "the window opens on the kept rule");
        if (kw != null) {
            HarnessSupport.check(HarnessSupport.waitUntil(() -> labelTexts(kw).stream()
                    .anyMatch(t -> t.startsWith("1. Rule from a newer version of Project+")), 5000),
                    "the kept rule is listed as locked: " + labelTexts(kw));
            HarnessSupport.check(labelTexts(kw).contains("Rule kept as is"), "its editor explains it");
            // Esc from the rule list, where focus lands, closes an unchanged window.
            HarnessSupport.runFx(() -> {
                Node list = kw.getScene().getRoot().lookup(".list-view");
                list.requestFocus();
                javafx.event.Event.fireEvent(list, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                        "", "", javafx.scene.input.KeyCode.ESCAPE, false, false, false, false));
            });
            HarnessSupport.check(HarnessSupport.waitClosed(kw, 5000), "Esc in the rule list closes the window");
        }
        HarnessSupport.finish();
    }

    private static String readMetadataFile(String projectDir) throws Exception {
        Path f = Path.of(projectDir, "data", "metadata.json");
        return Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "";
    }

    private static List<ImageRow> rows(ProjectTabPlusPane pane) throws Exception {
        return HarnessSupport.onFx(() -> {
            List<ImageRow> out = new ArrayList<>();
            if (pane.tree().getRoot() != null) {
                collect(pane.tree().getRoot(), out);
            }
            return out;
        });
    }

    private static void collect(TreeItem<Row> item, List<ImageRow> out) {
        for (TreeItem<Row> child : item.getChildren()) {
            if (child.getValue() instanceof ImageRow r) {
                out.add(r);
            } else {
                collect(child, out);
            }
        }
    }

    private static List<String> shownNames(ProjectTabPlusPane pane) throws Exception {
        return rows(pane).stream().map(r -> r.info().displayName()).sorted().toList();
    }

    private static List<String> fullNames(ProjectTabPlusPane pane) throws Exception {
        return rows(pane).stream().map(r -> r.info().name()).sorted().toList();
    }

    private static List<String> labelTexts(Window w) throws Exception {
        return HarnessSupport.onFx(() -> {
            List<String> out = new ArrayList<>();
            for (Node n : w.getScene().getRoot().lookupAll(".label")) {
                if (n instanceof Label l && l.getText() != null && l.isVisible()) {
                    out.add(l.getText());
                }
            }
            return out;
        });
    }

    private static String countLine(Window w) throws Exception {
        for (String t : labelTexts(w)) {
            if (t.contains("names shortened") || t.startsWith("No name is changed")) {
                return t;
            }
        }
        return "";
    }
}
