package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ExportNamingTest {

    @Test
    void plainNameGetsTheExtension() {
        Set<String> taken = ExportNaming.newTakenSet();
        assertEquals("liver_12_HE_0.svs.geojson", ExportNaming.fileName("liver_12_HE_0.svs", "1", false, false, taken));
        assertEquals("liver_12_HE_0.svs.geojson.gz", ExportNaming.fileName("liver_12_HE_0.svs", "1", true, false, taken));
    }

    @Test
    void collisionAppendsTheEntryId() {
        Set<String> taken = ExportNaming.newTakenSet();
        assertEquals("dup.geojson", ExportNaming.fileName("dup", "7", false, false, taken));
        assertEquals("dup (8).geojson", ExportNaming.fileName("dup", "8", false, false, taken));
        assertEquals("dup (8) 2.geojson", ExportNaming.fileName("dup", "8", false, false, taken));
        assertEquals(3, taken.size());
    }

    @Test
    void collisionsAreCaseInsensitive() {
        // Liver.svs and liver.svs are one file on Windows and macOS.
        Set<String> taken = ExportNaming.newTakenSet();
        assertEquals("Liver.svs.geojson", ExportNaming.fileName("Liver.svs", "1", false, false, taken));
        assertEquals("liver.svs (2).geojson", ExportNaming.fileName("liver.svs", "2", false, false, taken));
        assertEquals("LIVER.SVS (3).geojson", ExportNaming.fileName("LIVER.SVS", "3", false, false, taken));
        assertTrue(taken.contains("LIVER.SVS.GEOJSON"));
    }

    @Test
    void maskedNamesUseTheEntryId() {
        Set<String> taken = ExportNaming.newTakenSet();
        assertEquals("42.geojson", ExportNaming.fileName("8e1d-random-session-name", "42", false, true, taken));
        assertEquals("43.geojson.gz", ExportNaming.fileName("another", "43", true, true, taken));
        // The same ID twice (not possible in a project, but the suffix rule still holds).
        assertEquals("42 (42).geojson", ExportNaming.fileName("x", "42", false, true, taken));
    }

    @Test
    void stemIsCappedBeforeTheCollisionSuffix() {
        String longName = "a".repeat(400);
        String stem = ExportNaming.safeStem(longName);
        assertEquals(ExportNaming.MAX_STEM_LENGTH, stem.length());
        Set<String> taken = ExportNaming.newTakenSet();
        String first = ExportNaming.fileName(longName, "1", false, false, taken);
        String second = ExportNaming.fileName(longName, "2", false, false, taken);
        assertEquals(ExportNaming.MAX_STEM_LENGTH + ".geojson".length(), first.length());
        assertEquals(stem + " (2).geojson", second);
        // A cap that lands on trailing dots or spaces is tidied like any other stem.
        assertEquals("b".repeat(149), ExportNaming.safeStem("b".repeat(149) + " ." + "c".repeat(300)));
    }

    @Test
    void invalidCharactersAreStripped() {
        assertEquals("ab", ExportNaming.safeStem("a/b"));
        assertEquals("slide 1", ExportNaming.safeStem(" slide: 1? "));
        assertEquals("x", ExportNaming.safeStem("x..."));
        assertEquals("image", ExportNaming.safeStem("\\/:*?\"<>|"));
        assertEquals("image", ExportNaming.safeStem(""));
        assertEquals("image", ExportNaming.safeStem(null));
    }

    @Test
    void windowsReservedNamesAreAvoided() {
        for (String r : new String[] {"CON", "con", "PRN", "AUX", "NUL", "COM1", "COM9", "LPT1", "LPT9", "Con.svs"}) {
            assertEquals("image " + r, ExportNaming.safeStem(r), r);
        }
        assertEquals("COM10", ExportNaming.safeStem("COM10"));
        assertEquals("console", ExportNaming.safeStem("console"));
    }
}
