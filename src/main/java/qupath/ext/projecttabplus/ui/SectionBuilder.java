package qupath.ext.projecttabplus.ui;

import javafx.scene.Node;
import javafx.scene.control.TitledPane;

// Adapted from qupath-extension-image-export-toolkit (QuIET, Apache-2.0,
// github.com/uw-loci/qupath-extension-image-export-toolkit), modified 2026-10/11.

/** Consistently styled collapsible sections. */
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
