package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.projecttabplus.model.names.Suggestions.Kind;
import qupath.ext.projecttabplus.model.names.Suggestions.Suggestion;

class SuggestionsTest {

    /** Eight .ome.tif names, a .svs and its rotated copy: _20x is in 8 of 10. */
    private static List<String> tenNames(int withTwentyX) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String mag = i < withTwentyX ? "_20x" : "_40y";
            names.add("MetroHealth_FNA_PDAC_" + (i + 1) + mag + "_38323_35919_-7.0_00" + i + ".ome.tif");
        }
        names.add(UserNames.SVS);
        names.add(UserNames.SVS_ROTATED);
        return names;
    }

    @Test
    void usersFiveNames() {
        List<Suggestion> s = Suggestions.suggest(UserNames.FIVE);
        assertEquals(Kind.PREFIX, s.get(0).kind());
        assertEquals(UserNames.PREFIX, s.get(0).rule());
        assertEquals(5, s.get(0).reach());
        Suggestion ext = s.stream().filter(x -> x.kind() == Kind.EXTENSION).findFirst().orElseThrow();
        assertEquals(new HideExtensionRule(true, true), ext.rule());
        assertEquals(4, ext.reach());
        assertEquals(".ome.tif, .svs", ext.detail());
        Suggestion left = s.stream().filter(x -> x.kind() == Kind.LEFTOVER).findFirst().orElseThrow();
        assertEquals(UserNames.SVS_TEXT, left.rule());
        assertEquals(1, left.reach());
        assertEquals(".svs (rotated 270)", left.example());
        // _20x is in 3 of 5 names, under the 80 percent bar.
        assertTrue(s.stream().noneMatch(x -> x.kind() == Kind.TEXT));
    }

    @Test
    void repeatedTextAtEightyPercent() {
        List<Suggestion> s = Suggestions.suggest(tenNames(8));
        List<Suggestion> text = s.stream().filter(x -> x.kind() == Kind.TEXT).toList();
        assertEquals(1, text.size());
        assertEquals(UserNames.TWENTY_X, text.get(0).rule());
        assertEquals(8, text.get(0).reach());
        assertTrue(Suggestions.suggest(tenNames(7)).stream().noneMatch(x -> x.kind() == Kind.TEXT));
    }

    @Test
    void suggestionOrderIsPrefixTextExtensionLeftover() {
        List<Kind> kinds = Suggestions.suggest(tenNames(8)).stream().map(Suggestion::kind).toList();
        assertEquals(List.of(Kind.PREFIX, Kind.TEXT, Kind.EXTENSION, Kind.LEFTOVER), kinds);
    }

    @Test
    void suggestedRulesShortenTheTenNames() {
        List<NameRule> rules = Suggestions.suggest(tenNames(8)).stream().map(Suggestion::rule).toList();
        NameRuleSet set = new NameRuleSet(true, rules);
        assertEquals("1_38323_35919_-7.0_000", set.apply(tenNames(8).get(0)).display());
        assertEquals("1 (rotated 270)", set.apply(UserNames.SVS_ROTATED).display());
    }

    @Test
    void prefixTrimsToABoundarySoOneAndTenAgree() {
        Suggestions.Prefix p = Suggestions.prefix(List.of("S_PDAC_1_a", "S_PDAC_10_b", "S_PDAC_1_c"));
        assertEquals("S_PDAC_", p.prefix());
        assertTrue(p.all());
    }

    @Test
    void prefixSharedByMostNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 372; i++) {
            names.add("Metro_FNA_" + i + "_x");
        }
        for (int i = 0; i < 28; i++) {
            names.add("Other_" + i);
        }
        Suggestions.Prefix p = Suggestions.prefix(names);
        assertEquals("Metro_FNA_", p.prefix());
        assertEquals(372, p.count());
        assertEquals(400, p.total());
    }

    @Test
    void noPrefixUnderEightyPercentOrWithoutAShare() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            names.add("Metro_FNA_" + i);
        }
        for (int i = 0; i < 100; i++) {
            names.add("Other_" + i);
        }
        assertNull(Suggestions.prefix(names));
        assertNull(Suggestions.prefix(List.of("alpha_1", "beta_2")));
        assertNull(Suggestions.prefix(List.of("Only_one_name.tif")));
        assertNull(Suggestions.prefix(List.of()));
        assertTrue(Suggestions.suggest(List.of()).isEmpty());
    }

    @Test
    void nothingStandsOut() {
        assertTrue(Suggestions.suggest(List.of("alpha", "beta", "gamma")).isEmpty());
    }
}
