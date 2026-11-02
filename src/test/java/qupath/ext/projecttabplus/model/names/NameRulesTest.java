package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static qupath.ext.projecttabplus.model.names.UserNames.FOUR;
import static qupath.ext.projecttabplus.model.names.UserNames.SVS;
import static qupath.ext.projecttabplus.model.names.UserNames.SVS_ROTATED;
import static qupath.ext.projecttabplus.model.names.UserNames.TIF_A;
import static qupath.ext.projecttabplus.model.names.UserNames.TIF_B;
import static qupath.ext.projecttabplus.model.names.UserNames.TIF_C;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class NameRulesTest {

    private static String shortName(List<NameRule> rules, String full) {
        return new NameRuleSet(true, rules).apply(full).display();
    }

    @Test
    void fourSuggestedRulesShortenTheUsersNames() {
        assertEquals("1_38323_35919_-7.0_002", shortName(FOUR, TIF_A));
        assertEquals("1_38323_35919_7.0_biref_002", shortName(FOUR, TIF_B));
        assertEquals("1", shortName(FOUR, SVS));
        assertEquals("1 (rotated 270)", shortName(FOUR, SVS_ROTATED));
        assertEquals("2_11304_34962_90.0_004", shortName(FOUR, TIF_C));
    }

    @Test
    void hidingPiecesTwoAndThreeAfterTheFourRules() {
        List<NameRule> rules = new ArrayList<>(FOUR);
        rules.add(UserNames.hidePiecesTwoAndThree());
        assertEquals("1_-7.0_002", shortName(rules, TIF_A));
        assertEquals("1_7.0_biref_002", shortName(rules, TIF_B));
        assertEquals("1", shortName(rules, SVS));
        assertEquals("1 (rotated 270)", shortName(rules, SVS_ROTATED));
        assertEquals("2_90.0_004", shortName(rules, TIF_C));
    }

    @Test
    void lastChipKeepsTheCounterOnBothNameShapes() {
        List<NameRule> rules = new ArrayList<>(FOUR);
        rules.add(new KeepPiecesRule("_", new TreeSet<>(Set.of(0)), 6, true, false, true));
        assertEquals("1_002", shortName(rules, TIF_A));
        assertEquals("1_002", shortName(rules, TIF_B));
        assertEquals("2_004", shortName(rules, TIF_C));
        assertEquals("1", shortName(rules, SVS));
    }

    @Test
    void reorderingChangesWhatPieceNumbersMean() {
        // Pieces count in the name as the rules above leave it: moved to the top, the same
        // rule sees the full name, where piece 1 is "MetroHealth".
        KeepPiecesRule keep = new KeepPiecesRule("_", new TreeSet<>(Set.of(0)), 6, true, false, true);
        List<NameRule> first = new ArrayList<>();
        first.add(keep);
        first.addAll(FOUR);
        assertEquals("MetroHealth_002", shortName(first, TIF_A));
        List<NameRule> last = new ArrayList<>(FOUR);
        last.add(keep);
        assertEquals("1_002", shortName(last, TIF_A));
    }

    @Test
    void hiddenRunsMapBackToTheFullNameAcrossRules() {
        ShortName s = new NameRuleSet(true, FOUR).apply(TIF_A);
        assertEquals(List.of("MetroHealth_FNA_PDAC_", "_20x", ".ome.tif"), s.hiddenParts());
        List<ShortName.Run> runs = s.runs();
        assertEquals(new ShortName.Run(0, 21, 0), runs.get(0));
        assertEquals(new ShortName.Run(21, 22, Mapped.KEPT), runs.get(1));
        assertEquals(new ShortName.Run(22, 26, 1), runs.get(2));
        assertEquals(2, runs.get(runs.size() - 1).ruleIndex());
        assertTrue(s.hiddenBy(0));
        assertFalse(s.hiddenBy(3));
    }

    @Test
    void prefixIsCaseSensitiveAndOnlyAtTheStart() {
        PrefixRule p = new PrefixRule("Metro", true);
        assertEquals("Health_1", shortName(List.of(p), "MetroHealth_1"));
        assertEquals("metroHealth_1", shortName(List.of(p), "metroHealth_1"));
        assertEquals("x_MetroHealth", shortName(List.of(p), "x_MetroHealth"));
    }

    @Test
    void hideTextIsLiteralEveryOccurrenceAndOptionallyIgnoresCase() {
        assertEquals("a_b_c", shortName(List.of(new HideTextRule("_20x", false, true)), "a_20x_b_20x_c"));
        assertEquals("a_20X_b", shortName(List.of(new HideTextRule("_20x", false, true)), "a_20X_b"));
        assertEquals("a_b", shortName(List.of(new HideTextRule("_20x", true, true)), "a_20X_b"));
        assertEquals("ab", shortName(List.of(new HideTextRule(".*", false, true)), "a.*b"));
        assertEquals("a", shortName(List.of(new HideTextRule("aa", false, true)), "aaa"));
    }

    @Test
    void extensionCases() {
        HideExtensionRule dbl = new HideExtensionRule(true, true);
        HideExtensionRule single = new HideExtensionRule(false, true);
        assertEquals("slide", shortName(List.of(dbl), "slide.qptiff"));
        assertEquals("slide_7.0", shortName(List.of(dbl), "slide_7.0"));
        assertEquals("scan.5x", shortName(List.of(dbl), "scan.5x.tif"));
        assertEquals("scan.v2", shortName(List.of(dbl), "scan.v2.svs"));
        assertEquals("name.svs (rotated 270)", shortName(List.of(dbl), "name.svs (rotated 270)"));
        assertEquals("name", shortName(List.of(dbl), "name.ome.tif"));
        assertEquals("name.ome", shortName(List.of(single), "name.ome.tif"));
        assertEquals("name", shortName(List.of(dbl), "name.OME.TIFF"));
        assertEquals("archive", shortName(List.of(dbl), "archive.tar.gz"));
        assertEquals("noext", shortName(List.of(dbl), "noext"));
        assertEquals("trailing.", shortName(List.of(dbl), "trailing."));
        assertEquals(".hidden", shortName(List.of(dbl), ".hidden"));
        assertEquals("a.toolongext", shortName(List.of(dbl), "a.toolongext"));
        assertEquals("a b.svs x", shortName(List.of(dbl), "a b.svs x"));
    }

    @Test
    void leftoverExtensionIsFoundBeforeQuPathNotes() {
        assertEquals(".svs", HideExtensionRule.leftoverExtension(SVS_ROTATED, true));
        assertEquals(".svs", HideExtensionRule.leftoverExtension("a.svs (rotated 270) (Camera View)", true));
        assertEquals(".ome.tif", HideExtensionRule.leftoverExtension("a_002.ome.tif (1)", true));
        assertEquals(".tif", HideExtensionRule.leftoverExtension("a_002.ome.tif (1)", false));
        assertEquals(".qptiff", HideExtensionRule.leftoverExtension("core.qptiff (v1.2)", true));
        assertEquals(14, HideExtensionRule.leftoverStart("MetroHealth_x1.svs (rotated 270)", true));
        assertNull(HideExtensionRule.leftoverExtension(SVS, true));
        assertNull(HideExtensionRule.leftoverExtension("slide_7.0 (1)", true));
        assertNull(HideExtensionRule.leftoverExtension("a.svs copy", true));
        assertNull(HideExtensionRule.leftoverExtension("(1)", true));
        assertNull(HideExtensionRule.leftoverExtension("no period here", true));
    }

    @Test
    void keepPiecesWithDifferentPieceCounts() {
        KeepPiecesRule keep = new KeepPiecesRule("_", new TreeSet<>(Set.of(0, 2, 8)), 9, false, false, true);
        assertEquals("a_c", shortName(List.of(keep), "a_b_c"));
        assertEquals("a_c", shortName(List.of(keep), "a_b_c_d_e_f"));
        assertEquals("a_c_i", shortName(List.of(keep), "a_b_c_d_e_f_g_h_i"));
        KeepPiecesRule later = new KeepPiecesRule("_", new TreeSet<>(Set.of(0, 2)), 3, false, true, true);
        assertEquals("a_c_d_e", shortName(List.of(later), "a_b_c_d_e"));
    }

    @Test
    void keepPiecesWithNoChosenPieceLeavesTheNameAndFlagsIt() {
        KeepPiecesRule keep = new KeepPiecesRule("_", new TreeSet<>(Set.of(4)), 6, false, false, true);
        ShortName s = new NameRuleSet(true, List.of(keep)).apply("a_b_c");
        assertEquals("a_b_c", s.display());
        assertTrue(s.noPiece());
        assertTrue(s.kept());
    }

    @Test
    void keepPiecesWithTwoCharacterSeparatorAndEmptyPieces() {
        KeepPiecesRule keep = new KeepPiecesRule("--", new TreeSet<>(Set.of(0, 2)), 3, false, false, true);
        assertEquals("a--c", shortName(List.of(keep), "a--b--c"));
        KeepPiecesRule underscore = new KeepPiecesRule("_", new TreeSet<>(Set.of(0, 2)), 3, false, false, true);
        assertEquals("a_c", shortName(List.of(underscore), "a__c"));
    }

    @Test
    void ruleThatEmptiesTheNameFallsBackToTheFullName() {
        ShortName s = new NameRuleSet(true, List.of(new PrefixRule("abc", true))).apply("abc");
        assertEquals("abc", s.display());
        assertTrue(s.emptyFallback());
        assertFalse(s.changed());
        assertTrue(s.kept());
        assertTrue(s.hiddenBy(0));
        assertEquals(List.of(), s.hiddenParts());
    }

    @Test
    void offAndUnfinishedRulesAreSkipped() {
        List<NameRule> rules = List.of(new PrefixRule("Metro", false), new HideTextRule("", false, true),
                new KeepPiecesRule("_", new TreeSet<>(), 3, false, false, true));
        NameRuleSet set = new NameRuleSet(true, rules);
        assertEquals("MetroHealth_1", set.apply("MetroHealth_1").display());
        assertFalse(set.anyOn());
        assertTrue(set.hasUnfinished());
        assertFalse(set.active());
    }

    @Test
    void applyBeforeShowsTheNameAsARuleReceivesIt() {
        NameRuleSet set = new NameRuleSet(true, FOUR);
        assertEquals(TIF_A, set.applyBefore(TIF_A, 0).text());
        assertEquals("1_20x_38323_35919_-7.0_002.ome.tif", set.applyBefore(TIF_A, 1).text());
        assertEquals("1_38323_35919_-7.0_002", set.applyBefore(TIF_A, 4).text());
    }

    @Test
    void ruleSetsCompareByContent() {
        NameRuleSet a = new NameRuleSet(true, List.of(UserNames.hidePiecesTwoAndThree()));
        NameRuleSet b = new NameRuleSet(true, List.of(UserNames.hidePiecesTwoAndThree()));
        assertEquals(a, b);
        assertFalse(a.equals(b.withEnabled(false)));
    }
}
