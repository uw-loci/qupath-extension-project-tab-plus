package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class NamePiecesTest {

    /** Each piece as its text, with E: for an extension and N: for a note. */
    private static List<String> texts(String full) {
        List<String> out = new ArrayList<>();
        for (NamePieces.Piece p : NamePieces.of(full)) {
            String tag = switch (p.kind()) {
                case WORD -> "";
                case EXTENSION -> "E:";
                case NOTE -> "N:";
            };
            out.add(tag + p.text(full));
        }
        return out;
    }

    @Test
    void extensionAtTheEndIsItsOwnPiece() {
        assertEquals(List.of("MetroHealth", "FNA", "PDAC", "1", "20x", "38323", "35919", "-7.0", "002", "E:.ome.tif"),
                texts("MetroHealth_FNA_PDAC_1_20x_38323_35919_-7.0_002.ome.tif"));
        assertEquals(List.of("MetroHealth", "FNA", "PDAC", "5", "E:.svs"), texts("MetroHealth_FNA_PDAC_5.svs"));
    }

    @Test
    void duplicateNoteDoesNotGlueTheExtensionToTheLastWord() {
        assertEquals(List.of("5", "32474", "27471", "7.0", "biref", "025", "E:.ome.tif", "N:(1)"),
                texts("5_32474_27471_7.0_biref_025.ome.tif (1)"));
    }

    @Test
    void notesWithSpacesStayWhole() {
        assertEquals(List.of("MetroHealth", "FNA", "PDAC", "1", "E:.svs", "N:(rotated 270)", "N:(Camera View)"),
                texts("MetroHealth_FNA_PDAC_1.svs (rotated 270) (Camera View)"));
    }

    @Test
    void noteWithoutExtensionAndNestedParentheses() {
        assertEquals(List.of("slide", "1", "N:(copy)"), texts("slide_1 (copy)"));
        assertEquals(List.of("a", "N:(b (c))"), texts("a (b (c))"));
    }

    @Test
    void parenthesesThatAreNotATrailingNoteAreWords() {
        assertEquals(List.of("sample", "(A)", "001", "E:.tif"), texts("sample (A)_001.tif"));
        assertEquals(List.of("x(1)", "E:.tif"), texts("x(1).tif"));
        assertEquals(List.of("(1)"), texts("(1)"));
        assertEquals(List.of("slide", "7.0"), texts("slide_7.0"));
    }

    @Test
    void piecesCoverEveryNonSeparatorCharacterOfTheRealNames() {
        for (String full : RealProjectNames.NAMES) {
            boolean[] covered = new boolean[full.length()];
            int last = -1;
            for (NamePieces.Piece p : NamePieces.of(full)) {
                assertEquals(true, p.start() > last && p.end() > p.start(), full);
                for (int i = p.start(); i < p.end(); i++) {
                    covered[i] = true;
                }
                last = p.end() - 1;
            }
            for (int i = 0; i < full.length(); i++) {
                if (!covered[i]) {
                    assertEquals(true, NamePieces.SEPARATORS.indexOf(full.charAt(i)) >= 0, full + " at " + i);
                }
            }
        }
    }

    @Test
    void notesStartIsTheSpaceBeforeTheFirstTrailingNote() {
        assertEquals(26, NamePieces.notesStart("MetroHealth_FNA_PDAC_1.svs (rotated 270) (Camera View)"));
        assertEquals(5, NamePieces.notesStart("a_b.c (1)"));
        assertEquals(9, NamePieces.notesStart("x(1)_y.ab"));
        assertEquals(3, NamePieces.notesStart("(1)"));
    }

    @Test
    void keepPiecesLeavesQuPathNotesOutOfThePieces() {
        assertEquals(List.of("5", "32474", "025"),
                splitTexts("5_32474_025 (1)", "_"));
        assertEquals(List.of("1"), splitTexts("1 (rotated 270) (Camera View)", "_"));
        assertEquals(List.of("1"), splitTexts("1 (rotated 270) (Camera View)", " "));
    }

    @Test
    void hidingTheLastPieceKeepsTheDuplicateNote() {
        KeepPiecesRule keepFirst = new KeepPiecesRule("_", new TreeSet<>(Set.of(0, 1)), 3, false, false, true);
        NameRuleSet set = new NameRuleSet(true, List.of(keepFirst));
        assertEquals("5_32474 (1)", set.apply("5_32474_025 (1)").display());
        assertEquals("5_32474", set.apply("5_32474_025").display());
        assertEquals("1 (rotated 270) (Camera View)", set.apply("1 (rotated 270) (Camera View)").display());
    }

    @Test
    void notesDoNotMakeThePatientPieceLookMixed() {
        List<String> names = new ArrayList<>();
        names.add("1");
        names.add("1 (rotated 270) (Camera View)");
        names.add("1_38323_35919_7.0_002");
        names.add("1_38323_35919_7.0_biref_002 (1)");
        KeepPiecesRule keep = new KeepPiecesRule("_", new TreeSet<>(Set.of(0, 1, 2, 3, 4, 5)), 6, true, true, true);
        NamePreview p = NamePreview.compute(new NameRuleSet(true, List.of(keep)), names, 0);
        assertEquals(false, p.varies(0));
        assertEquals(true, p.varies(4));
    }

    private static List<String> splitTexts(String s, String sep) {
        List<String> out = new ArrayList<>();
        for (int[] r : KeepPiecesRule.split(s, sep)) {
            out.add(s.substring(r[0], r[1]));
        }
        return out;
    }
}
