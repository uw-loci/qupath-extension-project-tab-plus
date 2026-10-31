package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SummaryReaderTest {

    /** The shape QuPath 0.7 writes, with the real key names. */
    static final String SUMMARY = """
            {
              "timestamp": 1761900000000,
              "imageType": "BRIGHTFIELD_H_E",
              "server": {"width": 1000, "height": 800, "sizeC": 3, "sizeZ": 1, "sizeT": 1},
              "hierarchy": {
                "nObjects": 4423,
                "objectTypeCounts": {"Root objects": 1, "Annotations": 12, "Detections": 4,
                                     "Cells": 4310, "Tiles": 0, "TMA cores": 96},
                "annotationClassificationCounts": {"Tumor": 12},
                "detectionClassificationCounts": {"Unclassified": 4314}
              }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void parsesTheRealKeyNames() {
        ObjectPresence p = SummaryReader.parse(SUMMARY);
        assertEquals(ObjectPresence.State.PRESENT, p.state());
        assertEquals(12, p.annotations());
        assertEquals(4, p.detections());
        assertEquals(4310, p.cells());
        assertEquals(0, p.tiles());
        assertEquals(96, p.tmaCores());
        assertEquals(4314, p.detectionsLike());
        assertTrue(p.hasAnnotations());
        assertTrue(p.hasDetections());
        assertTrue(p.hasTmaCores());
        assertEquals("Brightfield (H&E)", p.imageType());
        assertFalse(p.unsaved());
        assertFalse(p.unreadable());
    }

    @Test
    void emptyHierarchyIsNoneNotUnknown() {
        ObjectPresence p = SummaryReader.parse(
                "{\"imageType\":\"FLUORESCENCE\",\"hierarchy\":{\"nObjects\":1,\"objectTypeCounts\":{\"Root objects\":1}}}");
        assertEquals(ObjectPresence.State.NONE, p.state());
        assertTrue(p.isKnown());
        assertFalse(p.hasAnnotations());
        assertEquals("Fluorescence", p.imageType());
    }

    @Test
    void unknownImageTypeIsKeptVerbatim() {
        ObjectPresence p = SummaryReader.parse("{\"imageType\":\"SOMETHING_NEW\"}");
        assertEquals("SOMETHING_NEW", p.imageType());
        assertNull(SummaryReader.displayImageType(""));
    }

    @Test
    void missingFileIsUnknown() {
        ObjectPresence p = SummaryReader.read(dir.resolve("summary.json"));
        assertEquals(ObjectPresence.UNKNOWN, p);
        assertFalse(p.unreadable());
        assertEquals(ObjectPresence.UNKNOWN, SummaryReader.read(null));
    }

    @Test
    void malformedFileIsUnknownAndUnreadableNotAnException() throws Exception {
        Path f = dir.resolve("summary.json");
        Files.writeString(f, "{ not json at all");
        ObjectPresence p = SummaryReader.read(f);
        assertEquals(ObjectPresence.State.UNKNOWN, p.state());
        assertTrue(p.unreadable());

        Files.writeString(f, "[1, 2, 3]");
        p = SummaryReader.read(f);
        assertTrue(p.unreadable());
    }

    @Test
    void cacheFollowsTheFileModificationTime() throws Exception {
        Path entryDir = dir.resolve("data").resolve("1");
        Files.createDirectories(entryDir);
        Path f = entryDir.resolve("summary.json");
        Files.writeString(f, SUMMARY);
        qupath.ext.projecttabplus.model.FakeEntry entry =
                new qupath.ext.projecttabplus.model.FakeEntry("1", "x").entryPath(entryDir);
        SummaryReader reader = new SummaryReader();
        assertEquals(12, reader.get(entry).annotations());
        assertEquals(12, reader.peek("1").annotations());

        Files.writeString(f, "{\"hierarchy\":{\"objectTypeCounts\":{\"Annotations\":1}}}");
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(f).toMillis() + 5000));
        assertEquals(1, reader.get(entry).annotations());

        reader.clear();
        assertNull(reader.peek("1"));
        assertEquals(ObjectPresence.UNKNOWN, reader.get(new qupath.ext.projecttabplus.model.FakeEntry("2", "y")));
    }
}
