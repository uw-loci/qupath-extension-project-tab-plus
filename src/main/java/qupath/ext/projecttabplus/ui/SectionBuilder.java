package qupath.ext.projecttabplus.ui;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;

/** Consistently styled collapsible sections. Lifted from QuIET. */
final class SectionBuilder {

    private SectionBuilder() {
    }

    static TitledPane createSection(String title, boolean expanded, Node content) {
        TitledPane tp = new TitledPane(title, content);
        tp.setExpanded(expanded);
        tp.setAnimated(false);
        tp.setCollapsible(true);
        tp.setMaxWidth(Double.MAX_VALUE);
        return tp;
    }
}
