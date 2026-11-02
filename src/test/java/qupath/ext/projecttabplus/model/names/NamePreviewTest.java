package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NamePreviewTest {

    @Test
    void keepPiecesSeesTheNamesAsTheRulesAboveLeaveThem() {
        List<NameRule> rules = new ArrayList<>(UserNames.FOUR);
        rules.add(new KeepPiecesRule("_", null, 0, true, true, true));
        NamePreview p = NamePreview.compute(new NameRuleSet(true, rules), UserNames.FIVE, 4);
        assertEquals("1_38323_35919_-7.0_002", p.inputs().get(0));
        assertEquals(1, p.minPieces());
        assertEquals(6, p.maxPieces());
        assertEquals(1, p.sampleIndex(), "the biref name has the most pieces");
        assertEquals(4, p.fewerThan(6));
        assertTrue(p.separatorPresent());
        // Piece 5 is biref in one name and the counter in two others.
        assertTrue(p.varies(4));
        assertFalse(p.varies(1));
        assertEquals("002", p.lastTokens().get(0).getKey());
    }

    @Test
    void extensionRuleReportsTheLeftover() {
        NamePreview p = NamePreview.compute(new NameRuleSet(true, List.of(UserNames.PREFIX, UserNames.EXTENSION)),
                UserNames.FIVE, 1);
        assertEquals(".svs", p.leftover());
        assertEquals(1, p.leftoverCount());
        assertEquals(UserNames.SVS_ROTATED, p.leftoverExample());
    }

    @Test
    void noSelectionGivesOnlyTheReport() {
        NamePreview p = NamePreview.compute(new NameRuleSet(true, UserNames.FOUR), UserNames.FIVE, -1);
        assertEquals(-1, p.selected());
        assertTrue(p.inputs().isEmpty());
        assertNull(p.leftover());
        assertEquals(5, p.report().changed());
    }
}
