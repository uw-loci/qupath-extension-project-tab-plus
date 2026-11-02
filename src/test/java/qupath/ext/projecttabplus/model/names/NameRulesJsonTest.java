package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        assertEquals(0, back.kept());
        assertTrue(json.contains("\"on\":false"));
        assertEquals(1, json.split("\"on\"", -1).length - 1, "only the off rule writes on");
    }

    @Test
    void unknownKindsAreKeptInPlace() {
        String json = "{\"version\":2,\"enabled\":true,\"rules\":["
                + "{\"kind\":\"prefix\",\"text\":\"S_\"},"
                + "{\"kind\":\"regex\",\"pattern\":\"a+\"},"
                + "{\"kind\":\"hideExtension\"},"
                + "7]}";
        NameRulesJson.Parsed p = NameRulesJson.parse(json);
        assertTrue(p.valid());
        assertEquals(4, p.found());
        assertEquals(1, p.newer());
        assertEquals(1, p.unreadable());
        List<NameRule> rules = p.rules().rules();
        assertEquals(4, rules.size());
        assertEquals(new PrefixRule("S_", true), rules.get(0));
        assertEquals(new KeptRule("{\"kind\":\"regex\",\"pattern\":\"a+\"}", "regex", true), rules.get(1));
        assertEquals(new HideExtensionRule(true, true), rules.get(2));
        assertEquals(new KeptRule("7", "?", false), rules.get(3));
        assertEquals(2, p.rules().version());
        assertTrue(p.rules().enabled());
    }

    @Test
    void keptRulesAndTheHigherVersionSurviveASave() {
        String regex = "{\"kind\":\"regexReplace\",\"pattern\":\"_\\\\d+\",\"with\":\"\"}";
        String json = "{\"version\":3,\"enabled\":true,\"rules\":[" + regex
                + ",{\"kind\":\"hideText\",\"text\":\"tme_\"},[1,2],null]}";
        NameRuleSet read = NameRulesJson.parse(json).rules();
        // What a toggle in this build does: same rules, flag changed, saved.
        String written = NameRulesJson.write(read.withEnabled(false));
        JsonObject root = JsonParser.parseString(written).getAsJsonObject();
        assertEquals(3, root.get("version").getAsInt());
        assertFalse(root.get("enabled").getAsBoolean());
        JsonArray rules = root.getAsJsonArray("rules");
        assertEquals(4, rules.size());
        assertEquals(JsonParser.parseString(regex), rules.get(0));
        assertEquals("hideText", rules.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals(JsonParser.parseString("[1,2]"), rules.get(2));
        assertTrue(rules.get(3).isJsonNull());
        // And read again, nothing is lost a second time round.
        assertEquals(read.withEnabled(false), NameRulesJson.parse(written).rules());
    }

    @Test
    void keptRulesChangeNoNameAndCannotBeTurnedOn() {
        KeptRule k = new KeptRule("{\"kind\":\"regex\"}", "regex", true);
        NameRuleSet set = new NameRuleSet(true, List.of(k, new HideTextRule("_x", false, true)));
        assertEquals("a_b", set.apply("a_x_b").display());
        assertEquals(k, k.withOn(true));
        assertFalse(k.on());
        assertTrue(k.finished());
        assertFalse(new NameRuleSet(true, List.of(k)).anyOn(), "a kept rule alone shows no short names");
        assertTrue(set.hasKept());
    }

    @Test
    void aKnownKindWithFieldsFromANewerVersionIsKept() {
        String json = "{\"version\":2,\"rules\":[{\"kind\":\"hideText\",\"text\":\"_x\",\"wholeWord\":true}]}";
        NameRulesJson.Parsed p = NameRulesJson.parse(json);
        assertEquals(1, p.newer());
        assertTrue(p.rules().rules().get(0) instanceof KeptRule);
        // At this build's own version an unknown field is ignored instead.
        NameRulesJson.Parsed same = NameRulesJson.parse(json.replace("\"version\":2", "\"version\":1"));
        assertEquals(List.of(new HideTextRule("_x", false, true)), same.rules().rules());
    }

    @Test
    void thisBuildsVersionIsTheFloor() {
        String json = NameRulesJson.write(new NameRuleSet(true, List.of(new PrefixRule("S_", true))));
        assertEquals(NameRulesJson.VERSION, JsonParser.parseString(json).getAsJsonObject().get("version").getAsInt());
    }

    @Test
    void kindNamesAreSanitizedForTheLogAndTheList() {
        String odd = "x".repeat(40) + "\u00e9\n";
        NameRulesJson.Parsed p = NameRulesJson.parse("{\"rules\":[{\"kind\":\"" + "y\\u00e9\\n" + "\"}]}");
        KeptRule k = (KeptRule) p.rules().rules().get(0);
        assertEquals("y??", k.label());
        String label = KeptRule.sanitize(odd);
        assertEquals(KeptRule.MAX_LABEL + 3, label.length());
        assertTrue(label.chars().allMatch(c -> c >= 0x20 && c < 0x7F), label);
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
