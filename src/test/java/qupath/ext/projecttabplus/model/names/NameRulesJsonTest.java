package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NameRulesJsonTest {

    @Test
    void roundTripKeepsEveryRuleAndState() {
        List<NameRule> rules = new ArrayList<>(UserNames.FOUR);
        rules.add(UserNames.hidePiecesTwoAndThree());
        rules.add(new HideTextRule("_HE", true, false));
        NameRuleSet set = new NameRuleSet(true, rules);
        String json = NameRulesJson.write(set);
        NameRulesJson.Parsed back = NameRulesJson.parse(json);
        assertTrue(back.valid());
        assertEquals(set, back.rules());
        assertEquals(6, back.found());
        assertTrue(back.skipped().isEmpty());
        assertTrue(json.contains("\"on\":false"));
        assertEquals(1, json.split("\"on\"", -1).length - 1, "only the off rule writes on");
    }

    @Test
    void unknownKindsAreSkippedAndNamed() {
        String json = "{\"version\":2,\"enabled\":true,\"rules\":["
                + "{\"kind\":\"prefix\",\"text\":\"S_\",\"color\":\"red\"},"
                + "{\"kind\":\"regex\",\"pattern\":\"a+\"},"
                + "{\"kind\":\"hideExtension\"},"
                + "7]}";
        NameRulesJson.Parsed p = NameRulesJson.parse(json);
        assertTrue(p.valid());
        assertEquals(4, p.found());
        assertEquals(List.of("regex", "?"), p.skipped());
        assertEquals(List.of(new PrefixRule("S_", true), new HideExtensionRule(true, true)), p.rules().rules());
        assertTrue(p.rules().enabled());
    }

    @Test
    void unreadableFieldsSkipTheRule() {
        String json = "{\"rules\":[{\"kind\":\"keepPieces\",\"keep\":[\"x\"]}]}";
        NameRulesJson.Parsed p = NameRulesJson.parse(json);
        assertEquals(List.of("keepPieces"), p.skipped());
        assertFalse(p.rules().enabled());
    }

    @Test
    void textThatIsNotARuleSetGivesNoRules() {
        for (String bad : new String[] {null, "", "  ", "{not json", "\"hello\"", "[]", "{\"rules\":3}", "{}"}) {
            NameRulesJson.Parsed p = NameRulesJson.parse(bad);
            assertFalse(p.valid(), String.valueOf(bad));
            assertEquals(NameRuleSet.EMPTY, p.rules());
        }
    }
}
