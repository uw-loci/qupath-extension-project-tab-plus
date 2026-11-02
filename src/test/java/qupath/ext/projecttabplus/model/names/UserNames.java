package qupath.ext.projecttabplus.model.names;

import java.util.List;
import java.util.Set;

/** The user's five example names and the rules the dialog suggests for them. */
final class UserNames {

    static final String TIF_A = "MetroHealth_FNA_PDAC_1_20x_38323_35919_-7.0_002.ome.tif";
    static final String TIF_B = "MetroHealth_FNA_PDAC_1_20x_38323_35919_7.0_biref_002.ome.tif";
    static final String SVS = "MetroHealth_FNA_PDAC_1.svs";
    static final String SVS_ROTATED = "MetroHealth_FNA_PDAC_1.svs (rotated 270)";
    static final String TIF_C = "MetroHealth_FNA_PDAC_2_20x_11304_34962_90.0_004.ome.tif";

    static final List<String> FIVE = List.of(TIF_A, TIF_B, SVS, SVS_ROTATED, TIF_C);

    static final PrefixRule PREFIX = new PrefixRule("MetroHealth_FNA_PDAC_", true);
    static final HideTextRule TWENTY_X = new HideTextRule("_20x", false, true);
    static final HideExtensionRule EXTENSION = new HideExtensionRule(true, true);
    static final HideTextRule SVS_TEXT = new HideTextRule(".svs", false, true);

    static final List<NameRule> FOUR = List.of(PREFIX, TWENTY_X, EXTENSION, SVS_TEXT);

    private UserNames() {
    }

    /** Pieces 1, 4, 5 and 6 of the 6-piece sample, Last and later pieces kept: hides pieces 2 and 3. */
    static KeepPiecesRule hidePiecesTwoAndThree() {
        return new KeepPiecesRule("_", new java.util.TreeSet<>(Set.of(0, 3, 4, 5)), 6, true, true, true);
    }
}
