package qupath.ext.projecttabplus.model.names;

import java.util.ArrayList;
import java.util.List;

/**
 * The pieces a click in the Full name column offers: words split at {@code _} and space, the
 * file extension as one piece, and each trailing QuPath note in parentheses (such as
 * {@code (1)} or {@code (rotated 270)}) as one piece. So
 * {@code ..._002.ome.tif (1)} gives {@code 002}, {@code .ome.tif} and {@code (1)}.
 */
public final class NamePieces {

    /** Characters that end a word piece. */
    public static final String SEPARATORS = "_ ";

    /** What a piece is. */
    public enum Kind {
        /** Text between separators. */
        WORD,
        /** The file extension with its period, at the end of the name or before notes. */
        EXTENSION,
        /** A trailing note in parentheses, such as {@code (1)}. */
        NOTE
    }

    /**
     * @param start first character, inclusive
     * @param end last character, exclusive
     * @param kind what the piece is
     */
    public record Piece(int start, int end, Kind kind) {

        /**
         * @param full the name the piece came from
         * @return the piece's text
         */
        public String text(String full) {
            return full.substring(start, end);
        }
    }

    private NamePieces() {}

    /**
     * @param full a full name
     * @return its pieces, left to right, none empty
     */
    public static List<Piece> of(String full) {
        List<Piece> notes = new ArrayList<>();
        int stemEnd = full.length();
        int open = noteStart(full, stemEnd);
        while (open > 0) {
            notes.add(0, new Piece(open, stemEnd, Kind.NOTE));
            stemEnd = open - 1;
            open = noteStart(full, stemEnd);
        }
        String stem = full.substring(0, stemEnd);
        int ext = HideExtensionRule.extensionStart(stem, true);
        int wordsEnd = ext >= 0 ? ext : stemEnd;
        List<Piece> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= wordsEnd; i++) {
            if (i == wordsEnd || SEPARATORS.indexOf(full.charAt(i)) >= 0) {
                if (i > start) {
                    out.add(new Piece(start, i, Kind.WORD));
                }
                start = i + 1;
            }
        }
        if (ext >= 0) {
            out.add(new Piece(ext, stemEnd, Kind.EXTENSION));
        }
        out.addAll(notes);
        return out;
    }

    /**
     * @param s a name
     * @return where its trailing {@code " (...)"} notes start (the space before the first), or
     *     its length when it has none
     */
    public static int notesStart(String s) {
        int end = s.length();
        int open = noteStart(s, end);
        while (open > 0) {
            end = open - 1;
            open = noteStart(s, end);
        }
        return end;
    }

    /**
     * @param s a name
     * @param end where the text to look at ends
     * @return the index of the {@code (} of a {@code " (...)"} note ending at {@code end}, or -1
     */
    private static int noteStart(String s, int end) {
        if (end < 4 || s.charAt(end - 1) != ')') {
            return -1;
        }
        int depth = 0;
        for (int i = end - 1; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
                if (depth == 0) {
                    return i >= 2 && s.charAt(i - 1) == ' ' ? i : -1;
                }
            }
        }
        return -1;
    }
}
