package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A project's ordered display-name rules and its "Show truncated names" flag. Immutable; pure
 * Java, safe on any thread.
 */
public final class NameRuleSet {

    /** No rules, short names off. */
    public static final NameRuleSet EMPTY = new NameRuleSet(false, List.of());

    private final boolean enabled;
    private final List<NameRule> rules;
    private final int version;

    /**
     * @param enabled whether short names are shown
     * @param rules the rules, applied in order
     */
    public NameRuleSet(boolean enabled, List<NameRule> rules) {
        this(enabled, rules, 0);
    }

    /**
     * @param enabled whether short names are shown
     * @param rules the rules, applied in order
     * @param version the format version the rules were read from, 0 for none; a save never
     *     writes a lower version
     */
    public NameRuleSet(boolean enabled, List<NameRule> rules, int version) {
        this.enabled = enabled;
        this.rules = List.copyOf(rules);
        this.version = Math.max(0, version);
    }

    /** @return the format version the rules were read from, 0 when made here */
    public int version() {
        return version;
    }

    /**
     * @param version a version the set must not be written below
     * @return a copy that keeps the higher of this set's version and that one
     */
    public NameRuleSet withVersionAtLeast(int version) {
        return version <= this.version ? this : new NameRuleSet(enabled, rules, version);
    }

    /** @return true when a rule is kept from a newer Project+ or could not be read */
    public boolean hasKept() {
        return rules.stream().anyMatch(r -> r instanceof KeptRule);
    }

    /** @return whether short names are switched on */
    public boolean enabled() {
        return enabled;
    }

    /** @return the rules, in order */
    public List<NameRule> rules() {
        return rules;
    }

    /** @return true when there are no rules */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * @param enabled the new flag
     * @return a copy with that flag
     */
    public NameRuleSet withEnabled(boolean enabled) {
        return enabled == this.enabled ? this : new NameRuleSet(enabled, rules, version);
    }

    /**
     * @param rules the new rules
     * @return a copy with those rules
     */
    public NameRuleSet withRules(List<NameRule> rules) {
        return new NameRuleSet(enabled, rules, version);
    }

    /** @return true when at least one finished rule is on */
    public boolean anyOn() {
        for (NameRule r : rules) {
            if (r.on() && r.finished()) {
                return true;
            }
        }
        return false;
    }

    /** @return true when any rule is unfinished */
    public boolean hasUnfinished() {
        for (NameRule r : rules) {
            if (!r.finished()) {
                return true;
            }
        }
        return false;
    }

    /** @return true when the tab should show short names: switched on and a rule is on */
    public boolean active() {
        return enabled && anyOn();
    }

    /**
     * Run the rules before one position, as the editor of that rule sees the name.
     *
     * @param full a full name
     * @param end run rules {@code [0, end)}
     * @return the name part-way through
     */
    public Mapped applyBefore(String full, int end) {
        Mapped m = Mapped.of(full);
        int stop = Math.min(end, rules.size());
        for (int i = 0; i < stop; i++) {
            NameRule r = rules.get(i);
            if (r.on() && r.finished()) {
                m = r.apply(m, i);
            }
        }
        return m;
    }

    /**
     * @param full a full name
     * @return its short name under every rule that is on, whatever {@link #enabled()} says
     */
    public ShortName apply(String full) {
        return new ShortName(applyBefore(full, rules.size()));
    }

    /**
     * @param fullNames every name, in project order
     * @return each short name with the counts the dialog and the status line report
     */
    public Report applyAll(List<String> fullNames) {
        List<ShortName> results = new ArrayList<>(fullNames.size());
        for (String n : fullNames) {
            results.add(apply(n));
        }
        return new Report(results, rules.size());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof NameRuleSet other && enabled == other.enabled && version == other.version
                && rules.equals(other.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, rules, version);
    }

    @Override
    public String toString() {
        return "NameRuleSet[enabled=" + enabled + ", version=" + version + ", rules=" + rules + "]";
    }

    /**
     * The short names of a whole project and what the rules did to them.
     */
    public static final class Report {

        private final List<ShortName> results;
        private final int changed;
        private final int kept;
        private final int[] perRule;
        private final Map<String, Integer> shared;
        private final int sharedRows;
        private final int longestBefore;
        private final int longestAfter;

        Report(List<ShortName> results, int ruleCount) {
            this.results = Collections.unmodifiableList(results);
            int c = 0;
            int k = 0;
            int before = 0;
            int after = 0;
            int[] per = new int[ruleCount];
            Map<String, Integer> counts = new HashMap<>();
            for (ShortName s : results) {
                if (s.changed()) {
                    c++;
                }
                if (s.kept()) {
                    k++;
                }
                before = Math.max(before, s.full().length());
                after = Math.max(after, s.display().length());
                for (int i = 0; i < ruleCount; i++) {
                    if (s.hiddenBy(i)) {
                        per[i]++;
                    }
                }
                counts.merge(s.display(), 1, Integer::sum);
            }
            Map<String, Integer> sh = new LinkedHashMap<>();
            int rows = 0;
            for (ShortName s : results) {
                int n = counts.get(s.display());
                if (n > 1 && !sh.containsKey(s.display())) {
                    sh.put(s.display(), n);
                    rows += n;
                }
            }
            this.changed = c;
            this.kept = k;
            this.perRule = per;
            this.shared = Collections.unmodifiableMap(sh);
            this.sharedRows = rows;
            this.longestBefore = before;
            this.longestAfter = after;
        }

        /** @return one short name per input, in input order */
        public List<ShortName> results() {
            return results;
        }

        /** @return how many names the rules changed */
        public int changed() {
            return changed;
        }

        /** @return how many names there are */
        public int total() {
            return results.size();
        }

        /** @return how many names keep their full name because the rules emptied them or no piece was found */
        public int kept() {
            return kept;
        }

        /**
         * @param ruleIndex a rule's position
         * @return how many names that rule changed
         */
        public int changedBy(int ruleIndex) {
            return ruleIndex >= 0 && ruleIndex < perRule.length ? perRule[ruleIndex] : 0;
        }

        /** @return every short name shown by two or more images, with how many */
        public Map<String, Integer> shared() {
            return shared;
        }

        /**
         * @param display a short name
         * @return how many images show it
         */
        public int sharedCount(String display) {
            return shared.getOrDefault(display, 1);
        }

        /** @return how many distinct short names are shared */
        public int sharedNames() {
            return shared.size();
        }

        /** @return how many images show a shared short name */
        public int sharedRows() {
            return sharedRows;
        }

        /** @return the longest full name */
        public int longestBefore() {
            return longestBefore;
        }

        /** @return the longest shown name */
        public int longestAfter() {
            return longestAfter;
        }
    }
}
