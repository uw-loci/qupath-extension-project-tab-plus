package qupath.ext.projecttabplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.projecttabplus.core.BulkJobs.Failure;
import qupath.ext.projecttabplus.core.BulkJobs.Outcome;
import qupath.ext.projecttabplus.ui.Strings;

class BulkOutcomeTest {

    @Test
    void onlySkippedExistingNeedsNothingExportedAndNoFailures() {
        assertTrue(new Outcome(3, 0, 3, 3, List.of(), false).onlySkippedExisting());
        assertTrue(new Outcome(3, 0, 3, 2, List.of(), false).onlySkippedExisting());
        assertFalse(new Outcome(3, 1, 2, 2, List.of(), false).onlySkippedExisting());
        assertFalse(new Outcome(3, 0, 3, 0, List.of(), false).onlySkippedExisting());
        assertFalse(new Outcome(3, 0, 2, 2, List.of(new Failure("1", "x", "boom")), false).onlySkippedExisting());
        assertFalse(new Outcome(0, 0, 0, 0, List.of(), false).onlySkippedExisting());
    }

    @Test
    void skippedExistingStatusText() {
        Outcome outcome = new Outcome(3, 0, 3, 3, List.of(), false);
        assertEquals("Skipped 3 existing files; nothing exported",
                Strings.format("status.bulkExportSkippedExisting", outcome.skippedExisting()));
        assertEquals("Skipped 3 existing files; nothing exported",
                Strings.format("notify.exportSkippedExisting", outcome.skippedExisting()));
        assertEquals(3, outcome.attempted());
    }
}
