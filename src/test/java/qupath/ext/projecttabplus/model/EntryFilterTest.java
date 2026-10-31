package qupath.ext.projecttabplus.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import qupath.ext.projecttabplus.core.ObjectPresence;

class EntryFilterTest {

    private static final FakeEntry HE = new FakeEntry("1", "liver_12_HE_0.svs").meta("Stain", "HE").meta("Patient", "12").data(true);
    private static final FakeEntry PPM = new FakeEntry("2", "liver_12_PPM.tif").meta("Stain", "PPM").data(false);
    private static final FakeEntry PLAIN = new FakeEntry("3", "other.svs").data(true);

    private static final ObjectPresence ANNOT = ObjectPresence.of(3, 0, 0, 0, 0, null, false);
    private static final ObjectPresence CELLS = ObjectPresence.of(0, 0, 400, 0, 0, null, false);
    private static final ObjectPresence TMA = ObjectPresence.of(0, 0, 0, 0, 12, null, false);

    private static boolean test(EntryFilter f, FakeEntry e, ObjectPresence p) {
        return f.test(new EntryFilter.Candidate(e, e.info(), p));
    }

    @Test
    void emptyFilterAcceptsEverything() {
        EntryFilter f = EntryFilter.of("", null, null);
        assertTrue(f.isEmpty());
        assertTrue(test(f, HE, ObjectPresence.UNKNOWN));
        assertTrue(EntryFilter.none().isEmpty());
        assertTrue(EntryFilter.of("  ", "", null).isEmpty());
    }

    @Test
    void plainTextDelegatesToQuPathNameSearch() {
        EntryFilter f = EntryFilter.of("HE_0", null, null);
        assertFalse(f.isEmpty());
        assertTrue(test(f, HE, null));
        assertFalse(test(f, PPM, null));
        assertTrue(test(EntryFilter.of("he_0", null, null), HE, null));
    }

    @Test
    void barTokensMatchMetadataAndHasData() {
        assertTrue(test(EntryFilter.of("stain=he|liver", null, null), HE, null));
        assertFalse(test(EntryFilter.of("stain=he|liver", null, null), PPM, null));
        assertTrue(test(EntryFilter.of("has_data=false|", null, null), PPM, null));
        assertFalse(test(EntryFilter.of("has_data=false|", null, null), HE, null));
    }

    @Test
    void hasKeywordsAreStrippedBeforeDelegating() {
        Set<EntryFilter.Has> into = EnumSet.noneOf(EntryFilter.Has.class);
        assertEquals("", EntryFilter.stripHasTokens("has=annotations", into));
        assertEquals(EnumSet.of(EntryFilter.Has.ANNOTATIONS), into);

        into.clear();
        assertEquals("stain=HE|", EntryFilter.stripHasTokens("stain=HE|has=tma", into));
        assertEquals(EnumSet.of(EntryFilter.Has.TMA), into);

        into.clear();
        assertEquals("a|b", EntryFilter.stripHasTokens("a|has=cells|b", into));
        assertEquals(EnumSet.of(EntryFilter.Has.DETECTIONS), into);

        into.clear();
        assertEquals("has=nothing", EntryFilter.stripHasTokens("has=nothing", into));
        assertTrue(into.isEmpty());

        into.clear();
        assertEquals("liver", EntryFilter.stripHasTokens("liver", into));
    }

    @Test
    void hasKeywordsNeedPresence() {
        EntryFilter ann = EntryFilter.of("has=annotations", null, null);
        assertTrue(test(ann, HE, ANNOT));
        assertFalse(test(ann, HE, CELLS));
        assertFalse(test(ann, HE, ObjectPresence.UNKNOWN));
        assertFalse(test(ann, HE, null));

        EntryFilter det = EntryFilter.of("has=detections", null, null);
        assertTrue(test(det, HE, CELLS));
        assertFalse(test(det, HE, ANNOT));

        EntryFilter tma = EntryFilter.of("has=tma", null, null);
        assertTrue(test(tma, HE, TMA));
        assertFalse(test(tma, HE, ANNOT));
    }

    @Test
    void hasKeywordCombinesWithMetadataToken() {
        EntryFilter f = EntryFilter.of("stain=HE|has=annotations", null, null);
        assertTrue(test(f, HE, ANNOT));
        assertFalse(test(f, HE, CELLS));
        assertFalse(test(f, PPM, ANNOT));
    }

    @Test
    void keyValueCombosAndWithText() {
        assertTrue(test(EntryFilter.of("", "Stain", null), HE, null));
        assertFalse(test(EntryFilter.of("", "Stain", null), PLAIN, null));
        assertTrue(test(EntryFilter.of("", "Stain", "HE"), HE, null));
        assertFalse(test(EntryFilter.of("", "Stain", "HE"), PPM, null));
        assertTrue(test(EntryFilter.of("", "Stain", GroupingKey.UNASSIGNED), PLAIN, null));
        assertFalse(test(EntryFilter.of("", "Stain", GroupingKey.UNASSIGNED), HE, null));
        assertTrue(test(EntryFilter.of("liver", "Stain", "HE"), HE, null));
        assertFalse(test(EntryFilter.of("other", "Stain", "HE"), HE, null));
        assertFalse(test(EntryFilter.of("liver", "Patient", "12"), PPM, null));
    }
}
