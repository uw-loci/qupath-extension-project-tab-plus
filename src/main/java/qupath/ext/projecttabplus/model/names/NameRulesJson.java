package qupath.ext.projecttabplus.model.names;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads and writes the rule set stored under the project metadata key, and moved between
 * projects through the clipboard. Tolerant in the same way as the profiles file: text that is
 * not a rule set gives an empty, switched-off set and a warning, never an exception. A rule
 * this build cannot read (a newer kind, or fields that do not parse) is kept as a
 * {@link KeptRule} and written back unchanged in its place, and a set read from a newer
 * version is never written with a lower one.
 */
public final class NameRulesJson {

    private static final Logger logger = LoggerFactory.getLogger(NameRulesJson.class);

    /** The version this build writes. */
    public static final int VERSION = 1;

    /** Most kind names a log line lists. */
    private static final int MAX_LOGGED = 3;

    private static final Map<String, Set<String>> KNOWN_FIELDS = Map.of(
            PrefixRule.KIND, Set.of("kind", "on", "text"),
            HideTextRule.KIND, Set.of("kind", "on", "text", "ignoreCase"),
            HideExtensionRule.KIND, Set.of("kind", "on", "double"),
            KeepPiecesRule.KIND, Set.of("kind", "on", "separator", "keep", "pieces", "last", "later"));

    /**
     * What a read produced.
     *
     * @param rules every rule in order, the ones this build cannot read kept as {@link KeptRule}
     * @param found how many rules the text held
     * @param newer how many are of a kind this build does not know, or carry fields from a newer version
     * @param unreadable how many could not be read at all (not an object, no kind, bad fields)
     * @param valid false when the text was not a rule set at all
     */
    public record Parsed(NameRuleSet rules, int found, int newer, int unreadable, boolean valid) {

        /** @return how many rules are kept without being read */
        public int kept() {
            return newer + unreadable;
        }
    }

    private NameRulesJson() {
    }

    /**
     * @param set the rules
     * @return compact JSON, at the higher of this build's version and the set's own
     */
    public static String write(NameRuleSet set) {
        JsonObject root = new JsonObject();
        root.addProperty("version", Math.max(VERSION, set.version()));
        root.addProperty("enabled", set.enabled());
        JsonArray arr = new JsonArray();
        for (NameRule r : set.rules()) {
            arr.add(toJson(r));
        }
        root.add("rules", arr);
        return new Gson().toJson(root);
    }

    private static JsonElement toJson(NameRule r) {
        if (r instanceof KeptRule k) {
            try {
                return JsonParser.parseString(k.json());
            } catch (RuntimeException e) {
                return new com.google.gson.JsonPrimitive(k.json());
            }
        }
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
            case KeptRule k -> throw new IllegalStateException("handled above");
        }
        return o;
    }

    /**
     * @param json the stored or pasted text; null or blank reads as no rules
     * @return the rules and what could not be read
     */
    public static Parsed parse(String json) {
        if (json == null || json.isBlank()) {
            return new Parsed(NameRuleSet.EMPTY, 0, 0, 0, false);
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject() || !el.getAsJsonObject().has("rules")
                    || !el.getAsJsonObject().get("rules").isJsonArray()) {
                logger.warn("Name rules: the text is not a rule set; using no rules");
                return new Parsed(NameRuleSet.EMPTY, 0, 0, 0, false);
            }
            root = el.getAsJsonObject();
        } catch (RuntimeException e) {
            logger.warn("Name rules could not be read ({}); using no rules", e.getClass().getSimpleName());
            return new Parsed(NameRuleSet.EMPTY, 0, 0, 0, false);
        }
        int version = intOr(root, "version", VERSION);
        boolean fromNewer = version > VERSION;
        if (fromNewer) {
            logger.warn("Name rules are version {}, this build reads version {}; rules it cannot read are kept as they are",
                    version, VERSION);
        }
        boolean enabled = boolOr(root, "enabled", false);
        List<NameRule> rules = new ArrayList<>();
        List<String> keptKinds = new ArrayList<>();
        int found = 0;
        int newer = 0;
        int unreadable = 0;
        for (JsonElement el : root.getAsJsonArray("rules")) {
            found++;
            NameRule r = null;
            String kind = null;
            boolean newKind = false;
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                kind = stringOr(o, "kind", null);
                Set<String> fields = kind == null ? null : KNOWN_FIELDS.get(kind);
                if (fields == null) {
                    newKind = kind != null;
                } else if (fromNewer && !fields.containsAll(o.keySet())) {
                    newKind = true;
                } else {
                    try {
                        r = fromJson(kind, o);
                    } catch (RuntimeException e) {
                        logger.debug("Name rule of kind {} unreadable: {}", kind, e.getClass().getSimpleName());
                    }
                }
            }
            if (r == null) {
                KeptRule k = new KeptRule(el.toString(), kind, newKind);
                keptKinds.add(k.label());
                if (newKind) {
                    newer++;
                } else {
                    unreadable++;
                }
                r = k;
            }
            rules.add(r);
        }
        if (!keptKinds.isEmpty()) {
            String listed = String.join(", ", keptKinds.subList(0, Math.min(MAX_LOGGED, keptKinds.size())))
                    + (keptKinds.size() > MAX_LOGGED ? ", ..." : "");
            logger.warn("Name rules: kept {} of {} rules unread ({})", keptKinds.size(), found, listed);
        }
        return new Parsed(new NameRuleSet(enabled, rules, fromNewer ? version : 0), found, newer, unreadable, true);
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
