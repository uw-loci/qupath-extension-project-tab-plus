package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExportNamingTest {

    @Test
    void plainNameGetsTheExtension() {
        Set<String> taken = new HashSet<>();
        assertEquals("liver_12_HE_0.svs.geojson", ExportNaming.fileName("liver_12_HE_0.svs", "1", false, taken));
        assertEquals("liver_12_HE_0.svs.geojson.gz", ExportNaming.fileName("liver_12_HE_0.svs", "1", true, taken));
    }

    @Test
    void collisionAppendsTheEntryId() {
        Set<String> taken = new HashSet<>();
        assertEquals("dup.geojson", ExportNaming.fileName("dup", "7", false, taken));
        assertEquals("dup (8).geojson", ExportNaming.fileName("dup", "8", false, taken));
        assertEquals("dup (8) 2.geojson", ExportNaming.fileName("dup", "8", false, taken));
        assertEquals(3, taken.size());
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
