package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportManifestTest {

    @TempDir
    Path dir;

    private static ExportManifest sample(boolean masked) {
        return new ExportManifest("0.7.0", "0.1.0", "2026-11-01T10:15:30+01:00", "/data/out",
                true, false, true, masked, List.of(
                new ExportManifest.FileRecord("a1", "liver.svs.geojson.gz", "liver.svs", ExportManifest.SOURCE_SAVED, 1531),
                new ExportManifest.FileRecord("b2", "b2.geojson.gz", "kidney.svs", ExportManifest.SOURCE_LIVE, 7)));
    }

    @Test
    void roundTripKeepsEveryField() {
        ExportManifest m = sample(false);
        ExportManifest back = ExportManifest.parse(m.toJson());
        assertEquals(m, back);
        assertEquals("0.7.0", back.qupathVersion());
        assertEquals("0.1.0", back.extensionVersion());
        assertEquals("2026-11-01T10:15:30+01:00", back.timestamp());
        assertEquals("/data/out", back.folder());
        assertTrue(back.compress());
        assertFalse(back.prettyPrint());
        assertTrue(back.excludeMeasurements());
        assertEquals(2, back.files().size());
        assertEquals("liver.svs", back.files().get(0).imageName());
        assertEquals(1531, back.files().get(0).objectCount());
        assertEquals(ExportManifest.SOURCE_LIVE, back.files().get(1).source());
    }

    @Test
    void maskedManifestCarriesNoImageName() {
        ExportManifest m = sample(true);
        String json = m.toJson();
        assertFalse(json.contains("\"imageName\""), json);
        assertFalse(json.contains("kidney.svs"), json);
        assertTrue(json.contains("\"imageNamesMasked\": true"));
        ExportManifest back = ExportManifest.parse(json);
        assertNull(back.files().get(0).imageName());
        assertEquals("a1", back.files().get(0).entryId());
        assertEquals("b2.geojson.gz", back.files().get(1).fileName());
    }

    @Test
    void writesIntoTheFolderAndReplacesAnEarlierOne() throws Exception {
        Files.writeString(dir.resolve(ExportManifest.FILE_NAME), "old");
        Path written = sample(false).write(dir);
        assertEquals(dir.resolve(ExportManifest.FILE_NAME), written);
        String text = Files.readString(written, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("{"));
        assertTrue(text.contains("\"entryId\": \"a1\""));
        assertFalse(Files.exists(dir.resolve(ExportManifest.FILE_NAME + ".tmp")));
    }

    @Test
    void versionsAndTimestampAreAvailableOutsideAJar() {
        assertNotNull(ExportManifest.currentExtensionVersion());
        assertFalse(ExportManifest.currentExtensionVersion().isBlank());
        assertTrue(ExportManifest.now().contains("T"));
    }
}
