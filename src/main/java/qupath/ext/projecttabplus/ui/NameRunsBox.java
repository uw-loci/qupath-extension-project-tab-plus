package qupath.ext.projecttabplus.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import qupath.ext.projecttabplus.model.names.ShortName;

/**
 * A name drawn as a row of runs, hidden runs struck through and tinted ({@code .ptp-removed}).
 * Labels are reused between calls, so a table cell keeps a handful of nodes while it scrolls.
 */
final class NameRunsBox extends HBox {

    static final String STYLE_REMOVED = "ptp-removed";

    private final List<Label> pool = new ArrayList<>();
    private final List<ShortName.Run> shown = new ArrayList<>();

    NameRunsBox() {
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(0);
        setFillHeight(false);
    }

    /**
     * @param text the text the runs index into
     * @param runs consecutive runs covering the text
     */
    void show(String text, List<ShortName.Run> runs) {
        shown.clear();
        shown.addAll(runs);
        while (pool.size() < runs.size()) {
            Label l = new Label();
            l.setMnemonicParsing(false);
            l.setMinWidth(Region.USE_PREF_SIZE);
            l.setTextOverrun(OverrunStyle.CLIP);
            pool.add(l);
        }
        List<Label> children = new ArrayList<>(runs.size());
        for (int i = 0; i < runs.size(); i++) {
            ShortName.Run r = runs.get(i);
            Label l = pool.get(i);
            l.setText(text.substring(r.start(), r.end()));
            l.getStyleClass().remove(STYLE_REMOVED);
            if (r.hidden()) {
                l.getStyleClass().add(STYLE_REMOVED);
            }
            children.add(l);
        }
        getChildren().setAll(children);
    }

    /** @return the runs last shown */
    List<ShortName.Run> runs() {
        return shown;
    }

    /** @return the labels last shown, one per run */
    List<Label> labels() {
        return pool.subList(0, Math.min(pool.size(), shown.size()));
    }

    /**
     * @param text a text
     * @param hidden one flag per character
     * @return the text as runs
     */
    static List<ShortName.Run> runsOf(String text, boolean[] hidden) {
        List<ShortName.Run> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            boolean h = hidden[i];
            int j = i + 1;
            while (j < text.length() && hidden[j] == h) {
                j++;
            }
            out.add(new ShortName.Run(i, j, h ? 0 : qupath.ext.projecttabplus.model.names.Mapped.KEPT));
            i = j;
        }
        return out;
    }
}
