package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.projecttabplus.model.FakeEntry;
import qupath.ext.projecttabplus.ui.Strings;

/**
 * The job phases that can be reached without an image: a data file that cannot be read is
 * counted as failed with the read reason and is never written back. The empty-ImageData
 * fallback of a real DefaultProject is covered by the harness scenario
 * {@code BulkClearUnreadableScenario}.
 */
class BulkJobsTest {

    @TempDir
    Path dir;

    private static final BulkJobs.Progress NO_PROGRESS = (done, total, name) -> { };

    @Test
    void clearCountsAnUnreadableDataFileAsFailedWithTheReadReason() {
        FakeEntry unreadable = new FakeEntry("1", "corrupt.svs").data(true);
        FakeEntry noData = new FakeEntry("2", "fresh.svs").data(false);
        BulkJobs.Outcome outcome = BulkJobs.clearData(List.of(unreadable, noData), NO_PROGRESS, () -> false);
        assertEquals(0, outcome.done());
        assertEquals(1, outcome.skipped());
        assertEquals(1, outcome.failures().size());
        BulkJobs.Failure f = outcome.failures().get(0);
        assertEquals("1", f.entryId());
        assertEquals("corrupt.svs", f.name());
        assertEquals("data file could not be read; not cleared (see log)", f.reason());
        assertEquals(Strings.get("reason.clearDataUnreadable"), f.reason());
    }

    @Test
    void flushSwingEventThreadReturnsAfterTheQueuedTasksRan() {
        AtomicBoolean ran = new AtomicBoolean();
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ran.set(true);
        });
        BulkJobs.flushSwingEventThread();
        assertTrue(ran.get());
    }

    @Test
    void dataFolderRemainsSeesAFolderRemovedByAQueuedSwingTask() throws Exception {
        // Windows QuPath moves the folder to the Recycle Bin on the Swing event thread, later.
        Path folder = Files.createDirectories(dir.resolve("entry-1"));
        FakeEntry entry = new FakeEntry("1", "a").entryPath(folder);
        SwingUtilities.invokeLater(() -> {
            try {
                Thread.sleep(150);
                Files.delete(folder);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertFalse(BulkJobs.dataFolderRemains(entry));
        assertFalse(BulkJobs.dataFolderRemains(new FakeEntry("2", "b")));
    }

    @Test
    void clearStopsAfterTheCurrentEntryWhenCanceled() {
        FakeEntry a = new FakeEntry("1", "a").data(true);
        FakeEntry b = new FakeEntry("2", "b").data(true);
        BulkJobs.Outcome outcome = BulkJobs.clearData(List.of(a, b), NO_PROGRESS, () -> true);
        assertTrue(outcome.canceled());
        assertEquals(0, outcome.attempted());
    }

    @Test
    void exportReportsTheReadReasonAndWritesNoManifestWhenNothingWasWritten() {
        FakeEntry unreadable = new FakeEntry("1", "corrupt.svs").data(true);
        BulkJobs.ExportOptions options = new BulkJobs.ExportOptions(dir, false, false, false,
                BulkJobs.OverwritePolicy.OVERWRITE, false);
        BulkJobs.Outcome outcome = BulkJobs.exportGeoJson(List.of(unreadable), Map.of(), options, NO_PROGRESS, () -> false);
        assertEquals(0, outcome.done());
        assertEquals(1, outcome.failures().size());
        assertEquals("data file could not be read (see log)", outcome.failures().get(0).reason());
        assertFalse(Files.exists(dir.resolve(ExportManifest.FILE_NAME)));
        assertFalse(Files.exists(dir.resolve("corrupt.svs.geojson")));
    }

    @Test
    void exportWritesLiveObjectsThroughATempFileAndAManifest() throws Exception {
        FakeEntry open = new FakeEntry("7", "open.svs").data(false);
        FakeEntry nothing = new FakeEntry("8", "empty.svs").data(false);
        BulkJobs.ExportOptions options = new BulkJobs.ExportOptions(dir, true, true, false,
                BulkJobs.OverwritePolicy.OVERWRITE, false);
        BulkJobs.Outcome outcome = BulkJobs.exportGeoJson(List.of(open, nothing),
                Map.of("7", List.of()), options, NO_PROGRESS, () -> false);
        assertEquals(1, outcome.done());
        assertEquals(1, outcome.skipped());
        assertTrue(outcome.isClean());
        Path file = dir.resolve("open.svs.geojson.gz");
        assertTrue(Files.exists(file));
        assertFalse(Files.exists(dir.resolve("open.svs.geojson.gz.tmp")));
        ExportManifest manifest = ExportManifest.parse(Files.readString(dir.resolve(ExportManifest.FILE_NAME)));
        assertEquals(1, manifest.files().size());
        assertEquals("7", manifest.files().get(0).entryId());
        assertEquals("open.svs", manifest.files().get(0).imageName());
        assertEquals(ExportManifest.SOURCE_LIVE, manifest.files().get(0).source());
        assertEquals(0, manifest.files().get(0).objectCount());
        assertTrue(manifest.compress());
        assertTrue(manifest.prettyPrint());
        assertEquals(dir.toString(), manifest.folder());
    }

    @Test
    void maskedExportNamesFilesByEntryId() {
        FakeEntry open = new FakeEntry("7", "random-session-uuid").data(false);
        Map<String, String> names = BulkJobs.plannedFileNames(List.of(open), false, true);
        assertEquals("7.geojson", names.get("7"));
        BulkJobs.ExportOptions options = new BulkJobs.ExportOptions(dir, false, false, false,
                BulkJobs.OverwritePolicy.OVERWRITE, true);
        BulkJobs.Outcome outcome = BulkJobs.exportGeoJson(List.of(open), Map.of("7", List.of()), options,
                NO_PROGRESS, () -> false);
        assertEquals(1, outcome.done());
        assertTrue(Files.exists(dir.resolve("7.geojson")));
    }

    @Test
    void skipExistingLeavesTheFileAndCountsIt() throws Exception {
        FakeEntry open = new FakeEntry("7", "open.svs").data(false);
        Files.writeString(dir.resolve("open.svs.geojson"), "keep me");
        BulkJobs.ExportOptions options = new BulkJobs.ExportOptions(dir, false, false, false,
                BulkJobs.OverwritePolicy.SKIP, false);
        BulkJobs.Outcome outcome = BulkJobs.exportGeoJson(List.of(open), Map.of("7", List.of()), options,
                NO_PROGRESS, () -> false);
        assertTrue(outcome.onlySkippedExisting());
        assertEquals("keep me", Files.readString(dir.resolve("open.svs.geojson")));
    }
}
