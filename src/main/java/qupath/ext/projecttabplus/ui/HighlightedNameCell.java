package qupath.ext.projecttabplus.ui;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import qupath.ext.projecttabplus.model.names.HideExtensionRule;
import qupath.ext.projecttabplus.model.names.HideTextRule;
import qupath.ext.projecttabplus.model.names.KeepPiecesRule;
import qupath.ext.projecttabplus.model.names.Mapped;
import qupath.ext.projecttabplus.model.names.NameRule;
import qupath.ext.projecttabplus.model.names.NameRuleSet;
import qupath.ext.projecttabplus.model.names.PrefixRule;
import qupath.ext.projecttabplus.model.names.ShortName;

/**
 * The Full name cell: the name as runs, hidden runs struck through and tinted. A click on a
 * piece opens a menu that turns it into a rule; a click on a hidden run says which rule hid it.
 */
final class HighlightedNameCell extends TableCell<NameRulesDialog.PreviewRow, NameRulesDialog.PreviewRow> {

    private static final String PIECE_SEPARATORS = "_ ";

    private final NameRulesDialog dialog;
    private final NameRunsBox runs = new NameRunsBox();
    private ContextMenu menu;

    HighlightedNameCell(NameRulesDialog dialog) {
        this.dialog = dialog;
        setMnemonicParsing(false);
        setCursor(Cursor.HAND);
        addEventHandler(MouseEvent.MOUSE_CLICKED, this::onClick);
    }

    @Override
    protected void updateItem(NameRulesDialog.PreviewRow row, boolean empty) {
        super.updateItem(row, empty);
        setText(null);
        if (empty || row == null) {
            setGraphic(null);
            setTooltip(null);
            setAccessibleText(null);
            return;
        }
        runs.show(row.full, row.shortName.runs());
        setGraphic(runs);
        setTooltip(Tooltips.of(Strings.format(dialog.piecesEditorShowing()
                ? "names.cell.sample.tooltip" : "names.cell.full.tooltip", row.full)));
        List<String> hidden = row.shortName.hiddenParts();
        setAccessibleText(hidden.isEmpty()
                ? Strings.format("names.a11y.nothingHidden", row.full)
                : Strings.format("names.a11y.full", row.full, String.join(", ", hidden)));
    }

    private void onClick(MouseEvent e) {
        NameRulesDialog.PreviewRow row = getItem();
        if (row == null || isEmpty()) {
            return;
        }
        // While the Keep pieces editor shows, a left click only picks the sample row.
        if (e.getButton() == MouseButton.PRIMARY && dialog.piecesEditorShowing()) {
            return;
        }
        if (e.getButton() != MouseButton.PRIMARY && e.getButton() != MouseButton.SECONDARY) {
            return;
        }
        int index = charIndexAt(e.getSceneX(), e.getSceneY());
        if (index < 0) {
            return;
        }
        if (menu != null) {
            menu.hide();
        }
        menu = new ContextMenu();
        menu.getItems().setAll(itemsFor(dialog, row, index));
        menu.show(this, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    /** @return the full-name character under a scene point, or -1 */
    private int charIndexAt(double sceneX, double sceneY) {
        List<ShortName.Run> rs = runs.runs();
        List<Label> labels = runs.labels();
        for (int i = 0; i < labels.size() && i < rs.size(); i++) {
            Label l = labels.get(i);
            Point2D p = l.sceneToLocal(sceneX, sceneY);
            if (p == null || !l.getLayoutBounds().contains(p.getX(), Math.max(0, Math.min(p.getY(), l.getHeight() - 1)))) {
                continue;
            }
            Node t = l.lookup(".text");
            int offset = 0;
            if (t instanceof Text text) {
                Point2D tp = text.sceneToLocal(sceneX, sceneY);
                offset = Math.max(0, Math.min(text.getText().length() - 1, text.hitTest(tp).getCharIndex()));
            }
            return rs.get(i).start() + offset;
        }
        return -1;
    }

    /**
     * The pieces of a full name the menu offers: split at {@code _} and space, with the
     * extension its own piece.
     *
     * @param full a full name
     * @return each piece as {@code {start, end}}
     */
    static List<int[]> pieces(String full) {
        List<int[]> out = new ArrayList<>();
        int ext = HideExtensionRule.extensionStart(full, true);
        int end = ext >= 0 ? ext : full.length();
        int start = 0;
        for (int i = 0; i <= end; i++) {
            if (i == end || PIECE_SEPARATORS.indexOf(full.charAt(i)) >= 0) {
                if (i > start) {
                    out.add(new int[] {start, i});
                }
                start = i + 1;
            }
        }
        if (ext >= 0) {
            out.add(new int[] {ext, full.length()});
        }
        return out;
    }

    /** @return the menu items for a click at one character of a row's full name */
    static List<MenuItem> itemsFor(NameRulesDialog dialog, NameRulesDialog.PreviewRow row, int index) {
        List<MenuItem> items = new ArrayList<>();
        String full = row.full;
        int rule = row.shortName.removedBy(index);
        if (rule != Mapped.KEPT) {
            NameRule r = dialog.rule(rule);
            items.add(title(Strings.format("names.piece.hiddenBy", rule + 1, NameRulesDialog.sentence(r))));
            items.add(new SeparatorMenuItem());
            MenuItem go = item(Strings.format("names.piece.goToRule", rule + 1));
            go.setOnAction(e -> dialog.selectRule(rule));
            MenuItem off = item(Strings.format("names.piece.turnOff", rule + 1));
            off.setOnAction(e -> dialog.turnOff(rule));
            items.add(go);
            items.add(off);
            return items;
        }
        int[] piece = null;
        for (int[] p : pieces(full)) {
            if (index >= p[0] && index < p[1]) {
                piece = p;
                break;
            }
        }
        if (piece == null) {
            return items;
        }
        String text = full.substring(piece[0], piece[1]);
        items.add(title(Strings.format("names.piece.title", text, full)));
        items.add(new SeparatorMenuItem());
        List<String> names = new ArrayList<>();
        for (NameRulesDialog.PreviewRow r : dialog.rows()) {
            names.add(r.full);
        }
        int ext = HideExtensionRule.extensionStart(full, true);
        if (piece[0] == ext) {
            MenuItem hideExt = item(Strings.get("names.piece.hideExt"));
            hideExt.setOnAction(e -> dialog.insertRule(dialog.workingRules().size(), new HideExtensionRule(true, true)));
            items.add(hideExt);
            return items;
        }
        // Hide this text anywhere, with the separator in front (or behind, for the first piece)
        // so no doubled separator is left.
        String hideText;
        if (piece[0] > 0) {
            hideText = full.substring(piece[0] - 1, piece[1]);
        } else if (piece[1] < full.length() && PIECE_SEPARATORS.indexOf(full.charAt(piece[1])) >= 0) {
            hideText = full.substring(0, piece[1] + 1);
        } else {
            hideText = text;
        }
        int textCount = (int) names.stream().filter(n -> n.contains(hideText)).count();
        MenuItem hideAnywhere = item(Strings.plural("names.piece.hideText.one", "names.piece.hideText.many", textCount, hideText));
        hideAnywhere.setOnAction(e -> dialog.insertRule(dialog.workingRules().size(), new HideTextRule(hideText, false, true)));
        items.add(hideAnywhere);

        MenuItem hideNumber = hidePieceItem(dialog, full, index);
        if (hideNumber != null) {
            items.add(hideNumber);
        }

        int startEnd = piece[1] < full.length() && PIECE_SEPARATORS.indexOf(full.charAt(piece[1])) >= 0
                ? piece[1] + 1 : piece[1];
        String prefix = full.substring(0, startEnd);
        int startCount = (int) names.stream().filter(n -> n.startsWith(prefix)).count();
        MenuItem hideStart = item(Strings.plural("names.piece.hideStart.one", "names.piece.hideStart.many", startCount));
        hideStart.setOnAction(e -> {
            List<NameRule> rules = dialog.workingRules();
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i) instanceof PrefixRule p) {
                    dialog.replaceRule(i, new PrefixRule(prefix, p.on()));
                    dialog.selectRule(i);
                    return;
                }
            }
            dialog.insertRule(0, new PrefixRule(prefix, true));
        });
        items.add(hideStart);
        return items;
    }

    /** "Hide piece N in every name", counted as the Keep pieces rule sees the name; null when it cannot. */
    private static MenuItem hidePieceItem(NameRulesDialog dialog, String full, int fullIndex) {
        List<NameRule> rules = dialog.workingRules();
        NameRuleSet set = dialog.workingSet();
        int at = rules.size();
        String sep = "_";
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i) instanceof KeepPiecesRule k && !k.separator().isEmpty()) {
                at = i;
                sep = k.separator();
                break;
            }
        }
        Mapped in = set.applyBefore(full, at);
        int k = -1;
        for (int j = 0; j < in.text().length(); j++) {
            if (in.origin(j) == fullIndex) {
                k = j;
                break;
            }
        }
        if (k < 0) {
            return null;
        }
        List<int[]> ranges = KeepPiecesRule.split(in.text(), sep);
        int piece = -1;
        for (int p = 0; p < ranges.size(); p++) {
            if (k >= ranges.get(p)[0] && k < ranges.get(p)[1]) {
                piece = p;
                break;
            }
        }
        if (piece < 0 || ranges.size() < 2) {
            return null;
        }
        int number = piece;
        int ruleAt = at;
        int n = ranges.size();
        String separator = sep;
        MenuItem item = item(Strings.format("names.piece.hideNumber", number + 1));
        item.setOnAction(e -> {
            if (ruleAt < dialog.workingRules().size()) {
                dialog.hidePieceOf(ruleAt, number);
            } else {
                java.util.SortedSet<Integer> kept = new java.util.TreeSet<>();
                for (int i = 0; i < n; i++) {
                    if (i != number) {
                        kept.add(i);
                    }
                }
                boolean last = number != n - 1;
                dialog.insertRule(ruleAt, new KeepPiecesRule(separator, kept, n, last, last, true));
            }
        });
        return item;
    }

    private static MenuItem title(String text) {
        MenuItem t = item(text);
        t.setDisable(true);
        return t;
    }

    private static MenuItem item(String text) {
        MenuItem m = new MenuItem(text);
        m.setMnemonicParsing(false);
        return m;
    }

    /** Shift+F10 or the Menu key on a row: one submenu per piece of its name. */
    static void showKeyboardMenu(NameRulesDialog dialog, NameRulesDialog.PreviewRow row, Node anchor) {
        ContextMenu menu = new ContextMenu();
        for (int[] p : pieces(row.full)) {
            Menu sub = new Menu(Strings.format("names.piece.submenu", row.full.substring(p[0], p[1])));
            sub.setMnemonicParsing(false);
            sub.getItems().setAll(itemsFor(dialog, row, p[0]));
            menu.getItems().add(sub);
        }
        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        if (b != null) {
            menu.show(anchor, b.getMinX() + 40, b.getMinY() + 40);
        }
    }
}
