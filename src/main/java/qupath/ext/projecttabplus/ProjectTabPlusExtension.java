package qupath.ext.projecttabplus;

import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Entry point: adds the Project+ tab beside QuPath's own Project tab.
 */
public class ProjectTabPlusExtension implements QuPathExtension {

    @Override
    public void installExtension(QuPathGUI qupath) {
        // Filled in by the ui commit.
    }

    @Override
    public String getName() {
        return "Project Tab Plus";
    }

    @Override
    public String getDescription() {
        return "Project tab with multi-level metadata grouping, display profiles, jump to the current image, saved-data indicators and checkbox-mode bulk actions.";
    }

    @Override
    public Version getQuPathVersion() {
        return Version.parse("0.7.0");
    }
}
