package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
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
}
