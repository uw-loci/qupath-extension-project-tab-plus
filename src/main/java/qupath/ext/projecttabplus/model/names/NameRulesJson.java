package qupath.ext.projecttabplus.model.names;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the rule set stored under the project metadata key, and moved between
 * projects through the clipboard. Tolerant in the same way as the profiles file: unknown
 * fields are ignored, unknown or unreadable rules are skipped and named, and text that is not
 * a rule set gives an empty, switched-off set and a warning, never an exception.
 */
public final class NameRulesJson {

    private static final Logger logger = LoggerFactory.getLogger(NameRulesJson.class);

    /** The version this build writes. */
    public static final int VERSION = 1;

    /**
     * What a read produced.
     *
     * @param rules the rules that could be read
     * @param found how many rules the text held
     * @param skipped the kind of every rule left out, in order
     * @param valid false when the text was not a rule set at all
     */
    public record Parsed(NameRuleSet rules, int found, List<String> skipped, boolean valid) {

        public Parsed {
            skipped = Collections.unmodifiableList(new ArrayList<>(skipped));
        }
    }

    private NameRulesJson() {
    }

    /**
     * @param set the rules
     * @return compact JSON
     */
    public static String write(NameRuleSet set) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.addProperty("enabled", set.enabled());
        JsonArray arr = new JsonArray();
        for (NameRule r : set.rules()) {
            arr.add(toJson(r));
        }
        root.add("rules", arr);
        return new Gson().toJson(root);
    }

    private static JsonObject toJson(NameRule r) {
        JsonObject o = new JsonObject();
        o.addProperty("kind", r.kind());
        if (!r.on()) {
            o.addProperty("on", false);
        }
        switch (r) {
            case PrefixRule p -> o.addProperty("text", p.prefix());
            case HideTextRule t -> {
                o.addProperty("text", t.text());
                o.addProperty("ignoreCase", t.ignoreCase());
            }
            case HideExtensionRule e -> o.addProperty("double", e.doubleExtension());
            case KeepPiecesRule k -> {
                o.addProperty("separator", k.separator());
                JsonArray keep = new JsonArray();
                for (int i : k.kept()) {
                    keep.add(i);
                }
                o.add("keep", keep);
                o.addProperty("pieces", k.pieces());
                o.addProperty("last", k.keepLast());
                o.addProperty("later", k.keepLater());
            }
        }
        return o;
    }

    /**
     * @param json the stored or pasted text; null or blank reads as no rules
     * @return the rules and what was left out
     */
    public static Parsed parse(String json) {
        if (json == null || json.isBlank()) {
            return new Parsed(NameRuleSet.EMPTY, 0, List.of(), false);
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject() || !el.getAsJsonObject().has("rules")
                    || !el.getAsJsonObject().get("rules").isJsonArray()) {
                logger.warn("Name rules: the text is not a rule set; using no rules");
                return new Parsed(NameRuleSet.EMPTY, 0, List.of(), false);
            }
            root = el.getAsJsonObject();
        } catch (RuntimeException e) {
            logger.warn("Name rules could not be read ({}); using no rules", e.getMessage());
            return new Parsed(NameRuleSet.EMPTY, 0, List.of(), false);
        }
        int version = intOr(root, "version", VERSION);
        if (version > VERSION) {
            logger.warn("Name rules are version {}, this build reads version {}; unknown rules are skipped",
                    version, VERSION);
        }
        boolean enabled = boolOr(root, "enabled", false);
        List<NameRule> rules = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int found = 0;
        for (JsonElement el : root.getAsJsonArray("rules")) {
            found++;
            if (!el.isJsonObject()) {
                skipped.add("?");
                continue;
            }
            JsonObject o = el.getAsJsonObject();
            String kind = stringOr(o, "kind", "?");
            NameRule r = null;
            try {
                r = fromJson(kind, o);
            } catch (RuntimeException e) {
                logger.debug("Name rule of kind {} unreadable: {}", kind, e.getMessage());
            }
            if (r == null) {
                skipped.add(kind);
            } else {
                rules.add(r);
            }
        }
        if (!skipped.isEmpty()) {
            logger.warn("Name rules: skipped {} of {} rules ({})", skipped.size(), found, String.join(", ", skipped));
        }
        return new Parsed(new NameRuleSet(enabled, rules), found, skipped, true);
    }

    private static NameRule fromJson(String kind, JsonObject o) {
        boolean on = boolOr(o, "on", true);
        return switch (kind) {
            case PrefixRule.KIND -> new PrefixRule(stringOr(o, "text", ""), on);
            case HideTextRule.KIND -> new HideTextRule(stringOr(o, "text", ""), boolOr(o, "ignoreCase", false), on);
            case HideExtensionRule.KIND -> new HideExtensionRule(boolOr(o, "double", true), on);
            case KeepPiecesRule.KIND -> {
                TreeSet<Integer> keep = new TreeSet<>();
                JsonElement k = o.get("keep");
                if (k != null && k.isJsonArray()) {
                    for (JsonElement e : k.getAsJsonArray()) {
                        int i = e.getAsInt();
                        if (i >= 0) {
                            keep.add(i);
                        }
                    }
                }
                yield new KeepPiecesRule(stringOr(o, "separator", "_"), keep, intOr(o, "pieces", 0),
                        boolOr(o, "last", false), boolOr(o, "later", false), on);
            }
            default -> null;
        };
    }

    private static String stringOr(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean boolOr(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
