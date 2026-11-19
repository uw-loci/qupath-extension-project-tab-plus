package qupath.ext.projecttabplus.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.List;
import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.junit.jupiter.api.Test;

/** The three stylesheets ship, parse without errors, and define every color the base sheet uses. */
class ClearerToggleButtonsTest {

    private static Stylesheet parse(String name) throws Exception {
        URL url = ClearerToggleButtons.class.getResource(name);
        CssParser.errorsProperty().clear();
        Stylesheet sheet = new CssParser().parse(url);
        assertEquals(List.of(), List.copyOf(CssParser.errorsProperty()), name + " parses cleanly");
        return sheet;
    }

    @Test
    void theStylesheetsParseAndDefineTheirColors() throws Exception {
        Stylesheet base = parse(ClearerToggleButtons.BASE);
        assertFalse(base.getRules().isEmpty());
        for (String theme : List.of(ClearerToggleButtons.LIGHT, ClearerToggleButtons.DARK)) {
            Stylesheet colors = parse(theme);
            List<String> defined = colors.getRules().stream()
                    .flatMap(r -> r.getDeclarations().stream())
                    .map(d -> d.getProperty())
                    .toList();
            for (String color : List.of("-ptp-toggle-on-bar", "-ptp-toggle-on-fill", "-ptp-toggle-on-hover")) {
                assertTrue(defined.contains(color), theme + " defines " + color);
            }
        }
    }
}
