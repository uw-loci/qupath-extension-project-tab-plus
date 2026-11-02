package qupath.ext.projecttabplus.model.names;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class NameRuleSetReportTest {

    @Test
    void countsChangedPerRuleAndLengths() {
        NameRuleSet.Report r = new NameRuleSet(true, UserNames.FOUR).applyAll(UserNames.FIVE);
        assertEquals(5, r.total());
        assertEquals(5, r.changed());
        assertEquals(5, r.changedBy(0));
        assertEquals(3, r.changedBy(1));
        assertEquals(4, r.changedBy(2));
        assertEquals(1, r.changedBy(3));
        assertEquals(0, r.changedBy(9));
        assertEquals(UserNames.TIF_B.length(), r.longestBefore());
        assertEquals("1_38323_35919_7.0_biref_002".length(), r.longestAfter());
        assertEquals(0, r.sharedNames());
        assertEquals(0, r.kept());
    }

    @Test
    void collisionsAcrossChangedNames() {
        KeepPiecesRule keep = new KeepPiecesRule("_", new TreeSet<>(Set.of(0, 1)), 3, false, false, true);
        NameRuleSet.Report r = new NameRuleSet(true, List.of(keep)).applyAll(List.of("A_1_x", "A_1_y", "A_2_x"));
        assertEquals(1, r.sharedNames());
        assertEquals(2, r.sharedRows());
        assertEquals(2, r.sharedCount("A_1"));
        assertEquals(1, r.sharedCount("A_2"));
    }

    @Test
    void collisionWithAnUnchangedName() {
        NameRuleSet.Report r = new NameRuleSet(true, List.of(new HideTextRule("_bar", false, true)))
                .applyAll(List.of("foo_bar", "foo", "baz"));
        assertEquals(1, r.changed());
        assertEquals(1, r.sharedNames());
        assertEquals(2, r.sharedRows());
    }

    @Test
    void lastChipMakesTwoAnglesShareAName() {
        List<NameRule> rules = new ArrayList<>(UserNames.FOUR);
        rules.add(new KeepPiecesRule("_", new TreeSet<>(Set.of(0)), 6, true, false, true));
        NameRuleSet.Report r = new NameRuleSet(true, rules).applyAll(UserNames.FIVE);
        // 1_002 twice (two angles), 1 once, 1 (rotated 270) once, 2_004 once.
        assertEquals(1, r.sharedNames());
        assertEquals(2, r.sharedRows());
    }

    @Test
    void emptiedAndNoPieceNamesCountAsKept() {
        List<NameRule> rules = List.of(new PrefixRule("abc", true),
                new KeepPiecesRule("_", new TreeSet<>(Set.of(3)), 4, false, false, true));
        NameRuleSet.Report r = new NameRuleSet(true, rules).applyAll(List.of("abc", "x_y", "q_r_s_t"));
        assertEquals(2, r.kept());
        assertEquals(1, r.changed());
    }

    @Test
    void emptyProject() {
        NameRuleSet.Report r = new NameRuleSet(true, UserNames.FOUR).applyAll(List.of());
        assertEquals(0, r.total());
        assertEquals(0, r.changed());
        assertEquals(0, r.longestBefore());
    }
}
