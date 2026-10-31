package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.projecttabplus.core.DeleteEligibility.Reason;

class DeleteEligibilityTest {

    @TempDir
    Path dir;

    private Path touch(String name) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, "x");
        return p;
    }

    @Test
    void singleLocalSvsIsEligible() throws Exception {
        Path f = touch("slide.svs");
        DeleteEligibility e = DeleteEligibility.check(List.of(f.toUri()), 0);
        assertTrue(e.isEligible());
        assertEquals(f, e.path());
        assertEquals(".svs", e.extension());
    }

    @Test
    void everyRefusalReason() throws Exception {
        assertEquals(Reason.NO_URI, DeleteEligibility.check(List.of(), 0).reason());
        assertEquals(Reason.NO_URI, DeleteEligibility.check(null, 0).reason());

        Path a = touch("a.tif");
        Path b = touch("b.tif");
        assertEquals(Reason.MULTIPLE_URIS, DeleteEligibility.check(List.of(a.toUri(), b.toUri()), 0).reason());

        assertEquals(Reason.NOT_LOCAL, DeleteEligibility.check(
                List.of(new URI("https://example.org/slide.svs")), 0).reason());

        assertEquals(Reason.NOT_FOUND, DeleteEligibility.check(
                List.of(dir.resolve("missing.svs").toUri()), 0).reason());

        Path vsi = touch("scan.vsi");
        DeleteEligibility e = DeleteEligibility.check(List.of(vsi.toUri()), 0);
        assertEquals(Reason.COMPANION_FORMAT, e.reason());
        assertEquals(".vsi", e.extension());

        Path mrxs = touch("scan.mrxs");
        assertEquals(Reason.COMPANION_FORMAT, DeleteEligibility.check(List.of(mrxs.toUri()), 0).reason());

        Path shared = touch("shared.svs");
        e = DeleteEligibility.check(List.of(shared.toUri()), 3);
        assertEquals(Reason.SHARED_URI, e.reason());
        assertEquals(3, e.sharedCount());
    }

    @Test
    void sharedCountComesFromTheOtherEntriesUris() throws Exception {
        Path shared = touch("shared.svs");
        Path other = touch("other.svs");
        DeleteEligibility e = DeleteEligibility.check(List.of(shared.toUri()),
                List.of(other.toUri(), shared.toUri(), shared.toUri()));
        assertEquals(Reason.SHARED_URI, e.reason());
        assertEquals(2, e.sharedCount());
        assertTrue(DeleteEligibility.check(List.of(shared.toUri()), List.of(other.toUri())).isEligible());
    }

    @Test
    void tifWithCompanionOmeSiblingIsRefused() throws Exception {
        Path tif = touch("image.tif");
        assertTrue(DeleteEligibility.check(List.of(tif.toUri()), 0).isEligible());
        touch("image.companion.ome");
        assertEquals(Reason.COMPANION_FORMAT, DeleteEligibility.check(List.of(tif.toUri()), 0).reason());
    }

    @Test
    void omeTifSetIsRefusedButALoneOmeTifIsNot() throws Exception {
        Path one = touch("series.ome.tif");
        assertTrue(DeleteEligibility.check(List.of(one.toUri()), 0).isEligible());
        touch("series_z1.ome.tiff");
        DeleteEligibility e = DeleteEligibility.check(List.of(one.toUri()), 0);
        assertEquals(Reason.COMPANION_FORMAT, e.reason());
        assertFalse(e.isEligible());
    }

    @Test
    void plainTifNextToAnOmeTifIsStillEligible() throws Exception {
        Path plain = touch("plain.tif");
        touch("other.ome.tif");
        assertTrue(DeleteEligibility.check(List.of(plain.toUri()), 0).isEligible());
    }

    @Test
    void extensionIsCaseInsensitive() throws Exception {
        Path f = touch("SLIDE.SVS");
        DeleteEligibility e = DeleteEligibility.check(List.of(f.toUri()), 0);
        assertTrue(e.isEligible());
        assertEquals(".svs", e.extension());
    }
}
