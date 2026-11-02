package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A short name with nothing that tells images apart falls back to the full name. */
class ShortNameFallbackTest {

    @Test
    void namesWithoutALetterOrDigitFallBack() {
        NameRuleSet prefixAnd5 = new NameRuleSet(true,
                List.of(new PrefixRule("MetroHealth_FNA_PDAC_", true), new HideTextRule("5", false, true)));
        ShortName s = prefixAnd5.apply("MetroHealth_FNA_PDAC_5.svs");
        assertEquals("MetroHealth_FNA_PDAC_5.svs", s.display(), "\".svs\" says nothing");
        assertTrue(s.emptyFallback());
        assertTrue(s.kept());
        NameRuleSet abc = new NameRuleSet(true, List.of(new HideTextRule("abc", false, true)));
        assertEquals("abc_", abc.apply("abc_").display());
        assertEquals("abc ", abc.apply("abc ").display());
        assertEquals("abc (1)", abc.apply("abc (1)").display(), "a QuPath note alone says nothing");
        NameRuleSet tme = new NameRuleSet(true,
                List.of(new HideTextRule("tme_00", false, true), new HideExtensionRule(true, true)));
        assertEquals("tme_00.tif", tme.apply("tme_00.tif").display());
        assertEquals("tme_00.ome.tif", tme.apply("tme_00.ome.tif").display());
    }

    @Test
    void anythingWithALetterOrDigitStays() {
        assertTrue(ShortName.identifies("5"));
        assertTrue(ShortName.identifies("_a_"));
        assertTrue(ShortName.identifies("7.0"));
        assertTrue(ShortName.identifies("5 (1)"));
        assertTrue(ShortName.identifies("x.svs"));
        assertFalse(ShortName.identifies(""));
        assertFalse(ShortName.identifies("_"));
        assertFalse(ShortName.identifies(" "));
        assertFalse(ShortName.identifies(".svs"));
        assertFalse(ShortName.identifies(".ome.tif (1)"));
        assertFalse(ShortName.identifies("_-_"));
    }

    @Test
    void theReportCountsFallbacksAsKept() {
        NameRuleSet set = new NameRuleSet(true, List.of(new HideTextRule("abc", false, true)));
        NameRuleSet.Report r = set.applyAll(List.of("abc_", "abc_1"));
        assertEquals(1, r.kept());
        assertEquals(List.of("abc_", "_1"), r.results().stream().map(ShortName::display).toList());
    }
}
