import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import qupath.ext.projecttabplus.model.DisplayProfile;
import qupath.ext.projecttabplus.model.GroupingKey;
import qupath.ext.projecttabplus.model.GroupingSpec;
import qupath.ext.projecttabplus.model.ProfileStore;
import qupath.ext.projecttabplus.ui.ProjectTabPlusPane;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * A profile saved from the tab lands in profiles.json, reloads through a fresh ProfileStore
 * with every setting intact, and regroups the tree when selected again.
 *
 * <p>args: projectDir
 */
public final class ProfileRoundTripScenario {

    public static void main(String[] args) throws Exception {
        QuPathGUI qupath = HarnessSupport.hiddenGui();
        Project<BufferedImage> project = HarnessSupport.openProject(qupath, args[0]);
        ProjectTabPlusPane pane = HarnessSupport.pane(qupath);
        HarnessSupport.waitForShown(pane, 4, 15000);

        List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
        String[] patients = {"12", "12", "13", "13"};
        String[] stains = {"HE", "PPM", "HE", "PPM"};
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).getMetadata().put("Patient", patients[i]);
            entries.get(i).getMetadata().put("Stain", stains[i]);
        }
        project.syncChanges();
        HarnessSupport.runFx(pane::refreshAfterChange);
        HarnessSupport.waitForShown(pane, 4, 10000);

        // The hidden GUI shares the user's profiles file; put it back as it was at the end.
        Path file = pane.profilesFilePath();
        byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (original == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.write(file, original);
                }
            } catch (java.io.IOException e) {
                System.err.println("Could not restore " + file + ": " + e.getMessage());
            }
        }));

        String name = "Harness by patient";
        HarnessSupport.runFx(() -> pane.editLiveProfile(p -> {
            p.setGroupingSpec(new GroupingSpec(List.of(GroupingKey.metadata("Patient"), GroupingKey.metadata("Stain"))));
            p.setFields(List.of("Stain", "builtin:imageType"));
            p.setThumbnailSize(DisplayProfile.ThumbnailSize.MEDIUM);
            p.setIndicators(DisplayProfile.IndicatorStyle.BADGES);
            p.setExpandOnLoad(DisplayProfile.ExpandOnLoad.ALL);
        }));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> List.of("12", "13").equals(HarnessSupport.onFx(pane::topLevelGroups)), 10000),
                "grouping by Patient then Stain shows the two patients at the top level");
        HarnessSupport.runFx(() -> pane.saveLiveAsProfile(name));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> Files.exists(file)
                && Files.readString(file, StandardCharsets.UTF_8).contains(name), 10000),
                "profiles.json holds the saved profile");
        HarnessSupport.note("profiles file: " + file);

        List<DisplayProfile> reloaded = new ProfileStore(file).load();
        DisplayProfile back = reloaded.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
        HarnessSupport.check(back != null, "a fresh ProfileStore reads the profile back");
        if (back != null) {
            HarnessSupport.check(List.of("Patient", "Stain").equals(back.getGrouping()), "grouping survived the round trip");
            HarnessSupport.check(List.of("Stain", "builtin:imageType").equals(back.getFields()), "fields survived");
            HarnessSupport.check(back.getThumbnailSize() == DisplayProfile.ThumbnailSize.MEDIUM, "thumbnail size survived");
            HarnessSupport.check(back.getIndicators() == DisplayProfile.IndicatorStyle.BADGES, "indicator style survived");
            HarnessSupport.check(back.getExpandOnLoad() == DisplayProfile.ExpandOnLoad.ALL, "expand rule survived");
        }
        HarnessSupport.check(ProfileStore.DEFAULT_NAME.equals(reloaded.get(0).getName()), "Default is still first");

        HarnessSupport.runFx(() -> pane.selectProfileNamed(ProfileStore.DEFAULT_NAME));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> HarnessSupport.onFx(pane::topLevelGroups).isEmpty(), 10000),
                "selecting Default returns to the flat list");
        HarnessSupport.runFx(() -> pane.selectProfileNamed(name));
        HarnessSupport.check(HarnessSupport.waitUntil(() -> List.of("12", "13").equals(HarnessSupport.onFx(pane::topLevelGroups)), 10000),
                "selecting the saved profile regroups the tree");
        HarnessSupport.check(name.equals(project.getMetadata().get(ProjectTabPlusPane.PROJECT_PROFILE_KEY)),
                "the project remembers the last profile under _PROJECT_TAB_PLUS_PROFILE");

        HarnessSupport.finish();
    }
}
