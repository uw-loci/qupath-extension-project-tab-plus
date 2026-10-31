package qupath.ext.projecttabplus.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.controlsfx.control.PropertySheet;
import qupath.ext.projecttabplus.ui.Strings;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Persistent preferences: hide the built-in tab, current-image highlight, select on startup, and
 * the last-used profile name. Profiles themselves live in {@code profiles.json}, not here, because
 * Java Preferences caps a value at 8 KB.
 */
public final class ProjectTabPlusPreferences {

    private static final String PREFIX = "projecttabplus.";

    /** Pale blue, the light-theme INFO_BG; used only when the custom highlight is on. */
    private static final int DEFAULT_HIGHLIGHT = 0xFFE8F0FE;

    private static final BooleanProperty hideBuiltInProjectTab =
            PathPrefs.createPersistentPreference(PREFIX + "hideBuiltInProjectTab", false);
    private static final BooleanProperty useCustomHighlightColor =
            PathPrefs.createPersistentPreference(PREFIX + "useCustomHighlightColor", false);
    private static final IntegerProperty currentImageHighlightColor =
            PathPrefs.createPersistentPreference(PREFIX + "currentImageHighlightColor", DEFAULT_HIGHLIGHT);
    private static final BooleanProperty selectTabOnStartup =
            PathPrefs.createPersistentPreference(PREFIX + "selectTabOnStartup", true);
    private static final StringProperty lastProfile =
            PathPrefs.createPersistentPreference(PREFIX + "lastProfile", "Default");

    private static boolean installed;

    private ProjectTabPlusPreferences() {
    }

    /**
     * Register the preference rows under the "Project+" category. Idempotent.
     *
     * @param qupath the QuPath instance
     */
    public static synchronized void installPreferences(QuPathGUI qupath) {
        if (installed || qupath == null) {
            return;
        }
        ObservableList<PropertySheet.Item> items = qupath.getPreferencePane().getPropertySheet().getItems();
        String category = Strings.get("prefs.category");
        items.add(new PropertyItemBuilder<>(hideBuiltInProjectTab, Boolean.class)
                .name(Strings.get("prefs.hideBuiltIn.name"))
                .category(category)
                .description(Strings.get("prefs.hideBuiltIn.description"))
                .build());
        items.add(new PropertyItemBuilder<>(useCustomHighlightColor, Boolean.class)
                .name(Strings.get("prefs.customHighlight.name"))
                .category(category)
                .description(Strings.get("prefs.customHighlight.description"))
                .build());
        items.add(new PropertyItemBuilder<>(currentImageHighlightColor, Integer.class)
                .name(Strings.get("prefs.highlightColor.name"))
                .category(category)
                .description(Strings.get("prefs.highlightColor.description"))
                .propertyType(PropertyItemBuilder.PropertyType.COLOR)
                .build());
        items.add(new PropertyItemBuilder<>(selectTabOnStartup, Boolean.class)
                .name(Strings.get("prefs.selectOnStartup.name"))
                .category(category)
                .description(Strings.get("prefs.selectOnStartup.description"))
                .build());
        installed = true;
    }

    /** @return whether QuPath's own Project tab is detached from the analysis pane */
    public static BooleanProperty hideBuiltInProjectTabProperty() {
        return hideBuiltInProjectTab;
    }

    /** @return whether the custom highlight color replaces the theme tint */
    public static BooleanProperty useCustomHighlightColorProperty() {
        return useCustomHighlightColor;
    }

    /** @return the custom highlight color, packed ARGB */
    public static IntegerProperty currentImageHighlightColorProperty() {
        return currentImageHighlightColor;
    }

    /** @return whether the Project+ tab is selected at startup when a project is open */
    public static BooleanProperty selectTabOnStartupProperty() {
        return selectTabOnStartup;
    }

    /** @return the name of the last profile used */
    public static StringProperty lastProfileProperty() {
        return lastProfile;
    }
}
