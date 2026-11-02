package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.projecttabplus.model.names.Suggestions.Kind;
import qupath.ext.projecttabplus.model.names.Suggestions.Suggestion;

class RealProjectSuggestionsTest {

    @Test
    void suggestionSetOnTheRealNames() {
        List<Suggestion> s = Suggestions.suggest(RealProjectNames.NAMES);
        assertEquals(List.of(Kind.PREFIX, Kind.TEXT, Kind.EXTENSION, Kind.LEFTOVER, Kind.LEFTOVER),
                s.stream().map(Suggestion::kind).toList());
        assertEquals(new Suggestion(Kind.PREFIX, new PrefixRule("MetroHealth_FNA_PDAC_", true), 50, 50,
                "MetroHealth_FNA_PDAC_", null), s.get(0));
        // _20x is in exactly 40 of 50 names; _biref (16 of 50) stays under the 50 percent bar.
        assertEquals(new Suggestion(Kind.TEXT, new HideTextRule("_20x", false, true), 40, 50, "_20x", null), s.get(1));
        assertEquals(new Suggestion(Kind.EXTENSION, new HideExtensionRule(true, true), 37, 50, ".svs, .ome.tif", null),
                s.get(2));
        assertEquals(new Suggestion(Kind.LEFTOVER, new HideTextRule(".ome.tif", false, true), 8, 50, ".ome.tif",
                ".ome.tif (1)"), s.get(3));
        assertEquals(new Suggestion(Kind.LEFTOVER, new HideTextRule(".svs", false, true), 5, 50, ".svs",
                ".svs (rotated 270) (Camera View)"), s.get(4));
    }

    @Test
    void everyShortNameOnTheRealNames() {
        List<NameRule> rules = Suggestions.suggest(RealProjectNames.NAMES).stream().map(Suggestion::rule).toList();
        NameRuleSet.Report r = new NameRuleSet(true, rules).applyAll(RealProjectNames.NAMES);
        assertEquals(RealProjectNames.SHORT, r.results().stream().map(ShortName::display).toList());
        assertEquals(50, r.changed());
        assertEquals(0, r.sharedNames());
        assertEquals(0, r.kept());
    }

    @Test
    void afterTheSuggestionsNothingElseStandsOut() {
        List<NameRule> rules = Suggestions.suggest(RealProjectNames.NAMES).stream().map(Suggestion::rule).toList();
        NameRuleSet set = new NameRuleSet(true, rules);
        List<String> shown = RealProjectNames.NAMES.stream().map(n -> set.apply(n).display()).toList();
        assertTrue(Suggestions.suggest(shown).isEmpty(), Suggestions.suggest(shown).toString());
    }
}
