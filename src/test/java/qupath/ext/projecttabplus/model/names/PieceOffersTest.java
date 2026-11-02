package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The piece menu's offers on the user's 50 real names with the five suggested rules on. */
class PieceOffersTest {

    private static final List<String> NAMES = RealProjectNames.NAMES;
    private static final NameRuleSet SUGGESTED = new NameRuleSet(true,
            Suggestions.suggest(NAMES).stream().map(Suggestions.Suggestion::rule).toList());
    private static final String PATIENT_1 = "MetroHealth_FNA_PDAC_1_20x_38323_35919_90.0_002.ome.tif";
    private static final String PATIENT_2 = "MetroHealth_FNA_PDAC_2_20x_11304_34962_90.0_004.ome.tif";

    private static int at(String full, String text) {
        int i = full.indexOf(text);
        assertTrue(i >= 0, text);
        return i;
    }

    @Test
    void hideTextOnThePatientNumberIsBuiltFromTheShortName() {
        // The patient number sits right after the hidden start, so the short name begins "1_".
        PieceOffers.Offer o = PieceOffers.hideText(SUGGESTED, NAMES, PATIENT_1, at(PATIENT_1, "_1_") + 1);
        assertEquals("1_", o.text());
        assertTrue(o.insert());
        assertEquals(SUGGESTED.rules().size(), o.at());
        // The old full-name offer was "_1", which turned patient 2's "2_11304" into "21304".
        NameRuleSet after = withRule(o);
        assertEquals("2_11304_34962_90.0_004", after.apply(PATIENT_2).display());
        // The count is of the short names, as the editor will count them.
        int editorCount = 0;
        for (String n : NAMES) {
            if (SUGGESTED.apply(n).display().contains("1_")) {
                editorCount++;
            }
        }
        assertEquals(editorCount, o.count());
        // "1_" also ends patient 5's 27471, which the offer reports.
        assertTrue(o.cutsInto() > 0, "cuts into 27471_ in patient 5's names");
        assertEquals("5_32474_274790.0_025", after.apply("MetroHealth_FNA_PDAC_5_20x_32474_27471_90.0_025.ome.tif").display());
    }

    @Test
    void anOfferThatMakesNamesSharedSaysHowMany() {
        // Hiding "biref" makes each biref image read like the plain 7.0 image of its field.
        String biref = "MetroHealth_FNA_PDAC_5_20x_32474_27471_7.0_biref_025.ome.tif";
        PieceOffers.Offer o = PieceOffers.hideText(SUGGESTED, NAMES, biref, at(biref, "biref"));
        assertEquals("_biref", o.text());
        assertEquals(16, o.count());
        assertEquals(0, o.cutsInto());
        // 8 biref images and the 8 plain 7.0 images they now match; the (1) duplicates stay apart.
        assertEquals(16, o.newlyShared());
        String full = "MetroHealth_FNA_PDAC_4_20x_33775_25384_90.0_014.ome.tif";
        PieceOffers.Offer field = PieceOffers.hideText(SUGGESTED, NAMES, full, at(full, "33775"));
        assertEquals("_33775", field.text());
        assertEquals(0, field.newlyShared(), "4_25384_... is still unique");
    }

    @Test
    void newlySharedCountsOnlyImagesThatHadTheirOwnName() {
        NameRuleSet before = new NameRuleSet(true, List.of(new HideTextRule("_a", false, true)));
        List<String> names = List.of("x_a", "x_b", "x", "y");
        // Before: x, x_b, x, y -> two already share "x". Hiding "_b" adds x_b to them.
        NameRuleSet after = before.withRules(List.of(new HideTextRule("_a", false, true), new HideTextRule("_b", false, true)));
        assertEquals(1, PieceOffers.newlyShared(before, after, names));
    }

    @Test
    void hidePieceCountsPiecesAsTheRulesAboveLeaveThem() {
        PieceOffers.Offer o = PieceOffers.hidePiece(SUGGESTED, NAMES, PATIENT_1, at(PATIENT_1, "38323"));
        assertEquals(1, o.piece(), "piece 2 of 1_38323_35919_90.0_002");
        assertTrue(o.insert());
        KeepPiecesRule k = (KeepPiecesRule) o.rule();
        assertFalse(k.kept().contains(1));
        assertEquals("1_35919_90.0_002", withRule(o).apply(PATIENT_1).display());
        assertTrue(o.count() > 0);
    }

    @Test
    void hidePieceOnAnExistingRuleUnticksItsChip() {
        List<NameRule> rules = new java.util.ArrayList<>(SUGGESTED.rules());
        rules.add(new KeepPiecesRule("_", new java.util.TreeSet<>(List.of(0, 1, 2, 3, 4)), 5, true, true, true));
        NameRuleSet set = new NameRuleSet(true, rules);
        PieceOffers.Offer o = PieceOffers.hidePiece(set, NAMES, PATIENT_1, at(PATIENT_1, "35919"));
        assertFalse(o.insert());
        assertEquals(rules.size() - 1, o.at());
        assertEquals(((KeepPiecesRule) rules.get(rules.size() - 1)).withPieceHidden(2), o.rule());
    }

    @Test
    void hideStartIsBuiltAtTheStartRulesPosition() {
        PieceOffers.Offer o = PieceOffers.hideStart(SUGGESTED, NAMES, PATIENT_1, at(PATIENT_1, "_1_") + 1);
        assertFalse(o.insert(), "replaces the suggested start rule");
        assertEquals(0, o.at());
        assertEquals("MetroHealth_FNA_PDAC_1_", o.text());
        assertEquals(new PrefixRule("MetroHealth_FNA_PDAC_1_", true), o.rule());
        assertEquals(5, o.count(), "patient 1: the 4 angles and the (1) duplicate; the .svs names have no _ after 1");
    }

    @Test
    void aHiddenCharacterOffersNothing() {
        assertNull(PieceOffers.hideText(SUGGESTED, NAMES, PATIENT_1, at(PATIENT_1, "Metro")));
        // The start rule runs first, so at its position the start is still there to pick.
        assertEquals("MetroHealth_", PieceOffers.hideStart(SUGGESTED, NAMES, PATIENT_1, at(PATIENT_1, "Metro")).text());
        assertNull(PieceOffers.hidePiece(SUGGESTED, NAMES, PATIENT_1, at(PATIENT_1, "Metro")));
    }

    @Test
    void aNoteKeepsItsSpaceAndNoOtherOffer() {
        String dup = "MetroHealth_FNA_PDAC_2_20x_6022_7751_7.0_biref_004.ome.tif (1)";
        PieceOffers.Offer o = PieceOffers.hideText(SUGGESTED, NAMES, dup, at(dup, "(1)"));
        assertEquals(" (1)", o.text());
        assertEquals(8, o.count());
        assertEquals(0, o.cutsInto());
    }

    @Test
    void cutsIntoPieceFindsMatchesThatSplitAWord() {
        assertTrue(PieceOffers.cutsIntoPiece("2_11304_34962", "_1"));
        assertTrue(PieceOffers.cutsIntoPiece("5_32474_27471_90.0", "1_"));
        assertFalse(PieceOffers.cutsIntoPiece("1_38323_35919", "1_"));
        assertFalse(PieceOffers.cutsIntoPiece("2_7.0_biref_004", "_biref"));
        assertFalse(PieceOffers.cutsIntoPiece("4_002.ome.tif (1)", ".ome.tif"));
        assertFalse(PieceOffers.cutsIntoPiece("a b (1)", " (1)"));
    }

    private static NameRuleSet withRule(PieceOffers.Offer o) {
        List<NameRule> rules = new java.util.ArrayList<>(SUGGESTED.rules());
        if (o.insert()) {
            rules.add(o.at(), o.rule());
        } else {
            rules.set(o.at(), o.rule());
        }
        return SUGGESTED.withRules(rules);
    }
}
