package qupath.ext.projecttabplus.ui;

import java.net.URL;
import java.util.Objects;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Window;
import qupath.lib.gui.prefs.QuPathStyleManager;

/**
 * Optional styling that makes every toggle button's state obvious (QuPath issue #2242): ON is a
 * tinted fill with a solid bar along the bottom, and a mouse click no longer leaves the cyan focus
 * ring that reads as ON on a button just switched off. Keyboard focus still draws the ring.
 *
 * <p>Added to each window's scene rather than installed as a QuPath theme, so it sits on top of
 * whichever theme is chosen (a CSS file in QuPath's css folder replaces the theme instead), and
 * its colors follow the theme's light or dark scheme.
 */
public final class ClearerToggleButtons {

    static final String BASE = "clearer-toggle-buttons.css";
    static final String LIGHT = "clearer-toggle-buttons-light.css";
    static final String DARK = "clearer-toggle-buttons-dark.css";

    private static BooleanProperty enabled;

    private static final ListChangeListener<Window> NEW_WINDOWS = change -> {
        while (change.next()) {
            for (Window w : change.getAddedSubList()) {
                style(w.getScene());
            }
        }
    };

    private ClearerToggleButtons() {}

    /**
     * Follow a preference: style every window while it is true, and restyle when the theme
     * changes. Call once, on any thread.
     */
    public static synchronized void bind(BooleanProperty preference) {
        if (enabled != null) {
            return;
        }
        enabled = preference;
        preference.addListener((obs, o, n) -> Platform.runLater(ClearerToggleButtons::refresh));
        QuPathStyleManager.selectedStyleProperty()
                .addListener((obs, o, n) -> Platform.runLater(ClearerToggleButtons::refresh));
        Platform.runLater(ClearerToggleButtons::refresh);
    }

    private static void refresh() {
        Window.getWindows().removeListener(NEW_WINDOWS);
        if (enabled.get()) {
            Window.getWindows().addListener(NEW_WINDOWS);
        }
        for (Window w : Window.getWindows()) {
            style(w.getScene());
        }
    }

    /** Remove any earlier copy from the scene, then add the sheets the preference and theme call for. */
    private static void style(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.getStylesheets().removeIf(s -> s.endsWith(BASE) || s.endsWith(LIGHT) || s.endsWith(DARK));
        if (enabled.get()) {
            boolean dark = QuPathStyleManager.getStyleColorScheme() == ColorScheme.DARK;
            scene.getStylesheets().add(url(BASE));
            scene.getStylesheets().add(url(dark ? DARK : LIGHT));
        }
    }

    static String url(String name) {
        URL url = Objects.requireNonNull(ClearerToggleButtons.class.getResource(name), name);
        return url.toExternalForm();
    }
}
