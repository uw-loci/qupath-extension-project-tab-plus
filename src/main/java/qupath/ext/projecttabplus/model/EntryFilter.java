package qupath.ext.projecttabplus.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import qupath.ext.projecttabplus.core.ObjectPresence;
import qupath.lib.gui.panes.ProjectEntryPredicate;
import qupath.lib.projects.ProjectImageEntry;

/**
 * The search and key/value filter. Text syntax is QuPath's own
 * ({@link ProjectEntryPredicate}: substring, {@code |}-separated tokens that must all match the
 * name or a {@code key=value} string, {@code has_data=true|false}); this class adds the
 * {@code has=annotations|detections|tma} keywords, which need the saved-data summary, and the
 * structured key and value combos.
 */
public final class EntryFilter implements Predicate<EntryFilter.Candidate> {

    /**
     * What one image offers the filter.
     *
     * @param entry the project entry
     * @param info the entry snapshot
     * @param presence the saved-data summary, or null when not read yet (treated as UNKNOWN, so
     *                 has= keywords reject the image)
     */
    public record Candidate(ProjectImageEntry<?> entry, EntryInfo info, ObjectPresence presence) {
    }

    /** The {@code has=} keywords. */
    public enum Has {
        ANNOTATIONS, DETECTIONS, TMA;

        static Has parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "annotation", "annotations" -> ANNOTATIONS;
                case "detection", "detections", "cell", "cells" -> DETECTIONS;
                case "tma", "tmacores", "tma cores" -> TMA;
                default -> null;
            };
        }
    }

    private static final String HAS_PREFIX = "has=";

    private final Predicate<ProjectImageEntry<?>> textPredicate;
    private final Set<Has> has;
    private final String key;
    private final String value;
    private final boolean empty;

    private EntryFilter(Predicate<ProjectImageEntry<?>> textPredicate, Set<Has> has, String key,
                        String value, boolean empty) {
        this.textPredicate = textPredicate;
        this.has = has;
        this.key = key;
        this.value = value;
        this.empty = empty;
    }

    /**
     * @param text the search text; null or blank matches everything
     * @param key the structured key, or null for any key
     * @param value the structured value, {@link GroupingKey#UNASSIGNED} for "key missing", or null
     *              for any value
     * @return the filter
     */
    public static EntryFilter of(String text, String key, String value) {
        String t = text == null ? "" : text.trim();
        Set<Has> hasSet = EnumSet.noneOf(Has.class);
        String delegated = stripHasTokens(t, hasSet);
        Predicate<ProjectImageEntry<?>> textPredicate = ProjectEntryPredicate.createIgnoreCase(delegated);
        String k = key == null || key.isBlank() ? null : key;
        String v = k == null || value == null ? null : value;
        boolean empty = t.isEmpty() && k == null;
        return new EntryFilter(textPredicate, hasSet, k, v, empty);
    }

    /** @return a filter that keeps every image */
    public static EntryFilter none() {
        return of("", null, null);
    }

    /**
     * Pull the {@code has=} tokens out of the text and hand back what QuPath's predicate should
     * see. QuPath parses {@code key=value} and {@code has_data=} only in token mode, which it
     * enters only when the text contains {@code |}; text without a bar is a name-only search. So
     * a lone {@code key=value} gets a trailing {@code |} (QuPath drops the empty token), and a
     * trailing bar is kept when the original text had more than one token.
     */
    static String stripHasTokens(String text, Set<Has> into) {
        if (!text.toLowerCase(Locale.ROOT).contains(HAS_PREFIX)) {
            return keepTokenMode(text);
        }
        boolean tokenMode = text.contains("|");
        List<String> rest = new ArrayList<>();
        for (String raw : text.split("\\|")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.toLowerCase(Locale.ROOT).startsWith(HAS_PREFIX)) {
                Has h = Has.parse(token.substring(HAS_PREFIX.length()).trim());
                if (h != null) {
                    into.add(h);
                    continue;
                }
            }
            rest.add(token);
        }
        if (rest.isEmpty()) {
            return "";
        }
        String joined = String.join("|", rest);
        return tokenMode && rest.size() == 1 ? joined + "|" : keepTokenMode(joined);
    }

    /**
     * @param text the text QuPath will see
     * @return the text with a trailing {@code |} when it holds {@code =} but no bar, so a plain
     *         {@code key=value} is read as a metadata token (and still matched against the name)
     */
    private static String keepTokenMode(String text) {
        return text.contains("=") && !text.contains("|") ? text + "|" : text;
    }

    /** @return true when nothing is filtered */
    public boolean isEmpty() {
        return empty;
    }

    /** @return the {@code has=} keywords in force */
    public Set<Has> hasKeywords() {
        return has;
    }

    @Override
    public boolean test(Candidate c) {
        if (empty) {
            return true;
        }
        if (!textPredicate.test(c.entry())) {
            return false;
        }
        if (!has.isEmpty()) {
            ObjectPresence p = c.presence() == null ? ObjectPresence.UNKNOWN : c.presence();
            for (Has h : has) {
                boolean ok = switch (h) {
                    case ANNOTATIONS -> p.hasAnnotations();
                    case DETECTIONS -> p.hasDetections();
                    case TMA -> p.hasTmaCores();
                };
                if (!ok) {
                    return false;
                }
            }
        }
        if (key != null) {
            String actual = c.info().metadata().get(key);
            if (value == null) {
                return actual != null;
            }
            if (GroupingKey.UNASSIGNED.equals(value)) {
                return actual == null || actual.isBlank();
            }
            return value.equals(actual);
        }
        return true;
    }
}
