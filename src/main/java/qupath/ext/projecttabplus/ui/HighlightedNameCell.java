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
import qupath.ext.projecttabplus.model.names.Mapped;
import qupath.ext.projecttabplus.model.names.NamePieces;
import qupath.ext.projecttabplus.model.names.NameRule;
import qupath.ext.projecttabplus.model.names.NameRuleSet;
import qupath.ext.projecttabplus.model.names.PieceOffers;
import qupath.ext.projecttabplus.model.names.ShortName;

/**
 * The Full name cell: the name as runs, hidden runs struck through and tinted. A click on a
 * piece opens a menu that turns it into a rule; a click on a hidden run says which rule hid it.
 */
final class HighlightedNameCell extends TableCell<NameRulesDialog.PreviewRow, NameRulesDialog.PreviewRow> {

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
        // A click on a separator takes the piece after it (or before it, at the end).
        NamePieces.Piece piece = null;
        NamePieces.Piece before = null;
        for (NamePieces.Piece p : NamePieces.of(full)) {
            if (index >= p.start() && index < p.end() || p.start() > index) {
                piece = p;
                break;
            }
            before = p;
        }
        if (piece == null) {
            piece = before;
        }
        if (piece == null) {
            return items;
        }
        String text = piece.text(full);
        items.add(title(Strings.format("names.piece.title", text, full)));
        items.add(new SeparatorMenuItem());
        if (piece.kind() == NamePieces.Kind.EXTENSION && piece.end() == full.length()) {
            MenuItem hideExt = item(Strings.get("names.piece.hideExt"));
            hideExt.setOnAction(e -> dialog.insertRule(dialog.workingRules().size(), new HideExtensionRule(true, true)));
            items.add(hideExt);
            return items;
        }
        // Every offer is built from the name as the rules above it leave it, so the text and the
        // count are those of the short name, and a rule that would cut into other pieces or make
        // names shared says so.
        NameRuleSet set = dialog.workingSet();
        List<String> names = new ArrayList<>();
        for (NameRulesDialog.PreviewRow r : dialog.rows()) {
            names.add(r.full);
        }
        int position = piece.start() + Math.max(0, Math.min(index - piece.start(), piece.end() - piece.start() - 1));
        PieceOffers.Offer hide = PieceOffers.hideText(set, names, full, position);
        if (hide != null) {
            String label = Strings.plural("names.piece.hideText.one", "names.piece.hideText.many", hide.count(), hide.text());
            items.add(offerItem(dialog, hide, label));
        }
        if (piece.kind() == NamePieces.Kind.WORD) {
            // A note or an extension before one is not a Keep pieces piece, and hiding the
            // start through it would hide the whole name.
            PieceOffers.Offer number = PieceOffers.hidePiece(set, names, full, position);
            if (number != null) {
                items.add(offerItem(dialog, number, Strings.format("names.piece.hideNumber", number.piece() + 1)));
            }
            PieceOffers.Offer start = PieceOffers.hideStart(set, names, full, position);
            if (start != null) {
                items.add(offerItem(dialog, start,
                        Strings.plural("names.piece.hideStart.one", "names.piece.hideStart.many", start.count())));
            }
        }
        if (items.size() == 2) {
            items.add(title(Strings.get(row.shortName.emptyFallback() ? "names.piece.nothingLeft" : "names.piece.nothing")));
        }
        return items;
    }

    /** One offer as a menu item, its label carrying any warning. */
    private static MenuItem offerItem(NameRulesDialog dialog, PieceOffers.Offer offer, String label) {
        StringBuilder sb = new StringBuilder(label);
        if (offer.cutsInto() > 0) {
            sb.append(' ').append(Strings.plural("names.piece.cuts.one", "names.piece.cuts.many", offer.cutsInto()));
        }
        if (offer.newlyShared() > 0) {
            sb.append(' ').append(Strings.plural("names.piece.shared.one", "names.piece.shared.many", offer.newlyShared()));
        }
        MenuItem item = item(sb.toString());
        item.setOnAction(e -> {
            if (offer.insert()) {
                dialog.insertRule(offer.at(), offer.rule());
            } else {
                dialog.replaceRule(offer.at(), offer.rule());
                dialog.selectRule(offer.at());
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
        for (NamePieces.Piece p : NamePieces.of(row.full)) {
            Menu sub = new Menu(Strings.format("names.piece.submenu", p.text(row.full)));
            sub.setMnemonicParsing(false);
            sub.getItems().setAll(itemsFor(dialog, row, p.start()));
            menu.getItems().add(sub);
        }
        Bounds b = anchor.localToScreen(anchor.getBoundsInLocal());
        if (b != null) {
            menu.show(anchor, b.getMinX() + 40, b.getMinY() + 40);
        }
    }
}
