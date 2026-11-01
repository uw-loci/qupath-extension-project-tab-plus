package qupath.ext.projecttabplus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {

    @TempDir
    Path dir;

    @Test
    void missingFileYieldsDefaultOnly() {
        ProfileStore store = new ProfileStore(dir.resolve("none").resolve("profiles.json"));
        assertFalse(store.lastLoadFoundFile());
        List<DisplayProfile> p = store.load();
        assertEquals(1, p.size());
        assertEquals(ProfileStore.DEFAULT_NAME, p.get(0).getName());
        assertFalse(store.lastLoadFoundFile());
    }

    @Test
    void loadReportsWhetherTheFileExisted() throws Exception {
        ProfileStore store = new ProfileStore(dir.resolve("profiles.json"));
        store.load();
        assertFalse(store.lastLoadFoundFile());
        DisplayProfile def = new DisplayProfile("Default");
        def.setThumbnailSize(DisplayProfile.ThumbnailSize.LARGE);
        store.save(List.of(def));
        List<DisplayProfile> back = store.load();
        assertTrue(store.lastLoadFoundFile());
        assertEquals(DisplayProfile.ThumbnailSize.LARGE, back.get(0).getThumbnailSize());
        // An unreadable file still counts as found: the user's choices are not overwritten.
        Files.writeString(store.getFile(), "{ broken");
        store.load();
        assertTrue(store.lastLoadFoundFile());
    }

    @Test
    void roundTripKeepsEverySettingAndOrder() throws Exception {
        ProfileStore store = new ProfileStore(ProfileStore.defaultFile(dir));
        DisplayProfile a = new DisplayProfile("By patient");
        a.setGrouping(List.of("Patient", "Stain", "builtin:imageType"));
        a.setFields(List.of("Stain", "Angle"));
        a.setExpandOnLoad(DisplayProfile.ExpandOnLoad.ALL);
        a.setThumbnailSize(DisplayProfile.ThumbnailSize.LARGE);
        a.setIndicators(DisplayProfile.IndicatorStyle.BADGES);
        DisplayProfile b = new DisplayProfile("Review queue");
        store.save(List.of(new DisplayProfile("Default"), a, b));

        assertTrue(Files.exists(dir.resolve("project-tab-plus").resolve("profiles.json")));
        assertFalse(Files.exists(dir.resolve("project-tab-plus").resolve("profiles.json.tmp")));
        String json = Files.readString(store.getFile(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"version\": 1"));
        assertTrue(json.contains("\"expandOnLoad\": \"all\""));

        List<DisplayProfile> back = store.load();
        assertEquals(List.of("Default", "By patient", "Review queue"),
                back.stream().map(DisplayProfile::getName).toList());
        assertEquals(a, back.get(1));
        assertEquals(DisplayProfile.ThumbnailSize.LARGE, back.get(1).getThumbnailSize());
        assertEquals(DisplayProfile.IndicatorStyle.BADGES, back.get(1).getIndicators());
        assertEquals(List.of("Stain", "Angle"), back.get(1).getFields());
    }

    @Test
    void defaultIsInsertedFirstWhenMissingOrMisplaced() throws Exception {
        ProfileStore store = new ProfileStore(dir.resolve("profiles.json"));
        store.save(List.of(new DisplayProfile("Other"), new DisplayProfile("Default")));
        List<DisplayProfile> back = store.load();
        assertEquals("Default", back.get(0).getName());
        assertEquals("Other", back.get(1).getName());

        Files.writeString(store.getFile(), "{\"version\":1,\"profiles\":[{\"name\":\"Only\"}]}");
        back = store.load();
        assertEquals(List.of("Default", "Only"), back.stream().map(DisplayProfile::getName).toList());
    }

    @Test
    void unknownKeysAndBadValuesDegradeToDefaults() throws Exception {
        ProfileStore store = new ProfileStore(dir.resolve("profiles.json"));
        Files.writeString(store.getFile(), """
                {"version": 7, "future": true, "profiles": [
                  {"name": "Odd", "grouping": ["Scanner"], "expandOnLoad": "sideways",
                   "thumbnailSize": "HUGE", "indicators": 42, "extra": {"a": 1}},
                  {"grouping": ["no name"]},
                  {"name": "Odd", "grouping": ["duplicate"]}
                ]}
                """);
        List<DisplayProfile> back = store.load();
        assertEquals(2, back.size());
        DisplayProfile odd = back.get(1);
        assertEquals("Odd", odd.getName());
        assertEquals(List.of("Scanner"), odd.getGrouping());
        assertEquals(DisplayProfile.ExpandOnLoad.FIRST, odd.getExpandOnLoad());
        assertEquals(DisplayProfile.ThumbnailSize.SMALL, odd.getThumbnailSize());
        assertEquals(DisplayProfile.IndicatorStyle.EDGES, odd.getIndicators());
        assertEquals(List.of("Scanner"), odd.missingMetadataKeys(Set.of("Patient")));
        assertEquals(List.of(), odd.missingMetadataKeys(Set.of("Scanner")));
    }

    @Test
    void malformedFileFallsBackToDefault() throws Exception {
        ProfileStore store = new ProfileStore(dir.resolve("profiles.json"));
        Files.writeString(store.getFile(), "{ this is not json");
        List<DisplayProfile> back = store.load();
        assertEquals(1, back.size());
        assertEquals("Default", back.get(0).getName());
    }

    @Test
    void modifiedDetectionIgnoresName() {
        DisplayProfile a = new DisplayProfile("A");
        DisplayProfile b = a.copy();
        b.setName("B");
        assertTrue(a.sameSettingsAs(b));
        b.setThumbnailSize(DisplayProfile.ThumbnailSize.HIDDEN);
        assertFalse(a.sameSettingsAs(b));
        a.copySettingsFrom(b);
        assertTrue(a.sameSettingsAs(b));
        assertEquals("A", a.getName());
    }
}
