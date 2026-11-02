package qupath.ext.projecttabplus.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import qupath.ext.projecttabplus.model.names.HideExtensionRule;
import qupath.ext.projecttabplus.model.names.HideTextRule;
import qupath.ext.projecttabplus.model.names.KeepPiecesRule;
import qupath.ext.projecttabplus.model.names.KeptRule;
import qupath.ext.projecttabplus.model.names.Mapped;
import qupath.ext.projecttabplus.model.names.NamePreview;
import qupath.ext.projecttabplus.model.names.NameRule;
import qupath.ext.projecttabplus.model.names.NameRuleSet;
import qupath.ext.projecttabplus.model.names.PrefixRule;
import qupath.ext.projecttabplus.model.names.Suggestions;

/**
 * The right side of the name rules window: one editor per rule kind, and the suggestions
 * card. Editors write a new rule through {@link Host#replaceRule} on every change and redraw
 * their result lines from each preview; they never rebuild themselves, so focus stays put.
 */
final class NameRuleEditors {

    /** What an editor needs from the window. */
    interface Host {

        /** @return the working rule at a position */
        NameRule rule(int index);

        /** Replace a working rule; the window previews it after a short pause. */
        void replaceRule(int index, NameRule rule);

        /** Insert a working rule and select it. */
        void insertRule(int index, NameRule rule);

        /** Filter the table. */
        void showFilter(NameRulesDialog.Filter filter);

        /** @return the selected table row's position in the project list, or -1 */
        int selectedRow();

        /** @return the working rules, as the tab would apply them */
        NameRuleSet workingSet();

        /** Propose a start of the name for the rule at a position, off the FX thread. */
        void suggestPrefix(int index, Consumer<Suggestions.Prefix> onDone);
    }

    private NameRuleEditors() {
    }

    /**
     * @param host the window
     * @param index the rule's position
     * @return an editor for the rule there
     */
    static Editor create(Host host, int index) {
        return switch (host.rule(index)) {
            case PrefixRule p -> new PrefixEditor(host, index);
            case HideTextRule t -> new TextEditor(host, index);
            case HideExtensionRule e -> new ExtensionEditor(host, index);
            case KeepPiecesRule k -> new PiecesEditor(host, index);
            case KeptRule k -> new KeptEditor(host, index);
        };
    }

    /**
     * @param rule a rule
     * @param input a name as it reaches the rule
     * @return true when the rule would change it, whether or not the rule is on
     */
    static boolean changes(NameRule rule, String input) {
        return input != null && rule.finished() && !rule.apply(Mapped.of(input), 0).text().equals(input);
    }

    static Label wrapped(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMnemonicParsing(false);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    static void warn(Label l, boolean warning) {
        l.getStyleClass().remove("ptp-warn-note");
        if (warning) {
            l.getStyleClass().add("ptp-warn-note");
        }
    }

    private static Hyperlink link() {
        Hyperlink h = new Hyperlink();
        h.setMnemonicParsing(false);
        h.setTooltip(Tooltips.of(Strings.get("names.showSkipped.tooltip")));
        return h;
    }

    /** One rule's form. */
    abstract static class Editor {

        final Host host;
        final int index;
        final VBox box = new VBox(6);
        final ExampleLine example = new ExampleLine();
        /** Set while controls are filled from the rule, so their listeners do not write back. */
        boolean loading;

        Editor(Host host, int index, String titleKey) {
            this.host = host;
            this.index = index;
            Label title = new Label(Strings.get(titleKey));
            title.setStyle("-fx-font-weight: bold;");
            box.getChildren().add(title);
        }

        Node node() {
            return box;
        }

        NameRule rule() {
            return host.rule(index);
        }

        /** Redraw result lines and the example from a preview of the working rules. */
        abstract void update(NamePreview p);

        /** @return how many names reach this rule and would be changed by it */
        int countChanged(NamePreview p) {
            int n = 0;
            NameRule r = rule();
            for (String in : p.inputs()) {
                if (changes(r, in)) {
                    n++;
                }
            }
            return n;
        }

        void updateExample(NamePreview p) {
            example.update(host, index, p);
        }
    }

    /** Example: a real name before and after this rule, as it reaches the rule. */
    static final class ExampleLine extends VBox {

        private final NameRunsBox before = new NameRunsBox();
        private final Label after = new Label();
        private final Label none = wrapped(Strings.get("names.example.none"));
        private final HBox line1;
        private final HBox line2;

        ExampleLine() {
            super(2);
            Label ex = new Label(Strings.get("names.example"));
            ex.setMinWidth(Region.USE_PREF_SIZE);
            line1 = new HBox(6, ex, before);
            line1.setAlignment(Pos.CENTER_LEFT);
            Label arrow = new Label("->");
            arrow.setMinWidth(Region.USE_PREF_SIZE);
            arrow.setPadding(new javafx.geometry.Insets(0, 0, 0, 24));
            after.setMnemonicParsing(false);
            line2 = new HBox(6, arrow, after);
            line2.setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("ptp-example");
        }

        void update(Host host, int index, NamePreview p) {
            NameRule rule = host.rule(index);
            List<String> inputs = p.inputs();
            int row = -1;
            int sel = host.selectedRow();
            if (sel >= 0 && sel < inputs.size() && changes(rule, inputs.get(sel))) {
                row = sel;
            } else {
                for (int i = 0; i < inputs.size(); i++) {
                    if (changes(rule, inputs.get(i))) {
                        row = i;
                        break;
                    }
                }
            }
            if (row < 0 || p.selected() != index) {
                getChildren().setAll(none);
                return;
            }
            String full = p.report().results().get(row).full();
            NameRuleSet set = host.workingSet();
            Mapped in = set.applyBefore(full, index);
            Mapped out = rule.apply(in, index);
            String text = in.text();
            boolean[] hidden = new boolean[text.length()];
            for (int k = 0; k < text.length(); k++) {
                hidden[k] = out.removedBy(in.origin(k)) == index;
            }
            before.show(text, NameRunsBox.runsOf(text, hidden));
            after.setText(out.text().isEmpty() ? full : out.text());
            getChildren().setAll(line1, line2);
        }
    }

    // ------------------------------------------------------------------ (a) prefix

    static final class PrefixEditor extends Editor {

        private final TextField field = new TextField();
        private final Label result = wrapped("");
        private final Hyperlink show = link();
        private boolean noSuggestion;

        PrefixEditor(Host host, int index) {
            super(host, index, "names.kind.prefix");
            Label label = new Label(Strings.get("names.field.textToHide"));
            label.setLabelFor(field);
            label.setMinWidth(Region.USE_PREF_SIZE);
            field.setTooltip(Tooltips.of(Strings.get("names.prefix.field.tooltip")));
            field.setOnAction(e -> e.consume());
            HBox.setHgrow(field, Priority.ALWAYS);
            Button suggest = new Button(Strings.get("names.prefix.suggest"));
            suggest.setTooltip(Tooltips.of(Strings.get("names.prefix.suggest.tooltip")));
            suggest.setMinWidth(Region.USE_PREF_SIZE);
            suggest.setOnAction(e -> host.suggestPrefix(index, p -> {
                noSuggestion = p == null;
                if (p != null) {
                    field.setText(p.prefix());
                } else {
                    result.setText(Strings.get("names.prefix.noSuggestion"));
                    warn(result, false);
                    show.setVisible(false);
                    show.setManaged(false);
                }
            }));
            HBox row = new HBox(6, label, field, suggest);
            row.setAlignment(Pos.CENTER_LEFT);
            show.setOnAction(e -> host.showFilter(NameRulesDialog.Filter.SKIPPED));
            box.getChildren().addAll(row, new VBox(0, result, show), example);
            loading = true;
            field.setText(((PrefixRule) rule()).prefix());
            loading = false;
            field.textProperty().addListener((obs, o, n) -> {
                if (!loading) {
                    noSuggestion = false;
                    host.replaceRule(index, new PrefixRule(n, rule().on()));
                }
            });
        }

        TextField field() {
            return field;
        }

        @Override
        void update(NamePreview p) {
            String prefix = ((PrefixRule) rule()).prefix();
            int total = p.inputs().size();
            int n = countChanged(p);
            boolean link = false;
            if (prefix.isEmpty()) {
                result.setText(Strings.get(noSuggestion ? "names.prefix.noSuggestion" : "names.prefix.empty"));
                warn(result, !noSuggestion);
            } else if (n == 0) {
                result.setText(Strings.get("names.prefix.none"));
                warn(result, true);
            } else if (n == total) {
                result.setText(Strings.format("names.prefix.all", total));
                warn(result, false);
            } else {
                result.setText(Strings.plural("names.prefix.most.one", "names.prefix.most.many", total - n, n, total));
                warn(result, false);
                show.setText(Strings.plural("names.show.otherStart.one", "names.show.otherStart.many", total - n));
                link = true;
            }
            show.setVisible(link);
            show.setManaged(link);
            updateExample(p);
        }
    }

    // ------------------------------------------------------------------ (b) text

    static final class TextEditor extends Editor {

        private final TextField field = new TextField();
        private final CheckBox ignoreCase = new CheckBox(Strings.get("names.text.ignoreCase"));
        private final Label result = wrapped("");
        private final Hyperlink show = link();

        TextEditor(Host host, int index) {
            super(host, index, "names.kind.text");
            Label label = new Label(Strings.get("names.field.textToHide"));
            label.setLabelFor(field);
            label.setMinWidth(Region.USE_PREF_SIZE);
            field.setTooltip(Tooltips.of(Strings.get("names.text.field.tooltip")));
            field.setOnAction(e -> e.consume());
            HBox.setHgrow(field, Priority.ALWAYS);
            ignoreCase.setTooltip(Tooltips.of(Strings.get("names.text.ignoreCase.tooltip")));
            ignoreCase.setMinWidth(Region.USE_PREF_SIZE);
            HBox row = new HBox(6, label, field, ignoreCase);
            row.setAlignment(Pos.CENTER_LEFT);
            show.setOnAction(e -> host.showFilter(NameRulesDialog.Filter.SKIPPED));
            box.getChildren().addAll(row, wrapped(Strings.get("names.text.help")), new VBox(0, result, show), example);
            HideTextRule r = (HideTextRule) rule();
            loading = true;
            field.setText(r.text());
            ignoreCase.setSelected(r.ignoreCase());
            loading = false;
            field.textProperty().addListener((obs, o, n) -> write());
            ignoreCase.selectedProperty().addListener((obs, o, n) -> write());
        }

        private void write() {
            if (!loading) {
                host.replaceRule(index, new HideTextRule(field.getText(), ignoreCase.isSelected(), rule().on()));
            }
        }

        TextField field() {
            return field;
        }

        @Override
        void update(NamePreview p) {
            HideTextRule r = (HideTextRule) rule();
            int total = p.inputs().size();
            int n = countChanged(p);
            boolean link = false;
            if (r.text().isEmpty()) {
                result.setText(Strings.get("names.text.empty"));
                warn(result, true);
            } else if (n == 0) {
                result.setText(Strings.get("names.text.none"));
                warn(result, true);
            } else {
                result.setText(Strings.format("names.text.found", n, total));
                warn(result, false);
                if (n < total) {
                    show.setText(Strings.plural("names.show.withoutText.one", "names.show.withoutText.many", total - n));
                    link = true;
                }
            }
            show.setVisible(link);
            show.setManaged(link);
            updateExample(p);
        }
    }

    // ------------------------------------------------------------------ (c) extension

    static final class ExtensionEditor extends Editor {

        private final CheckBox dbl = new CheckBox(Strings.get("names.ext.double"));
        private final Label result = wrapped("");
        private final Hyperlink show = link();
        private final Label leftover = wrapped("");
        private final Button addLeftover = new Button();
        private final VBox leftoverBox = new VBox(4, leftover, addLeftover);

        ExtensionEditor(Host host, int index) {
            super(host, index, "names.kind.ext");
            dbl.setWrapText(true);
            dbl.setTooltip(Tooltips.of(Strings.get("names.ext.double.tooltip")));
            show.setOnAction(e -> host.showFilter(NameRulesDialog.Filter.SKIPPED));
            warn(leftover, true);
            addLeftover.setMnemonicParsing(false);
            addLeftover.setTooltip(Tooltips.of(Strings.get("names.ext.addLeftover.tooltip")));
            leftoverBox.setVisible(false);
            leftoverBox.setManaged(false);
            box.getChildren().addAll(dbl, new VBox(0, result, show), example, leftoverBox,
                    wrapped(Strings.get("names.ext.help")));
            loading = true;
            dbl.setSelected(((HideExtensionRule) rule()).doubleExtension());
            loading = false;
            dbl.selectedProperty().addListener((obs, o, n) -> {
                if (!loading) {
                    host.replaceRule(index, new HideExtensionRule(n, rule().on()));
                }
            });
        }

        CheckBox doubleBox() {
            return dbl;
        }

        @Override
        void update(NamePreview p) {
            int total = p.inputs().size();
            int n = countChanged(p);
            boolean link = false;
            if (n == 0) {
                result.setText(Strings.get("names.ext.none"));
                warn(result, true);
            } else {
                result.setText(Strings.format("names.ext.found", n, total));
                warn(result, false);
                if (n < total) {
                    show.setText(Strings.plural("names.show.withoutExt.one", "names.show.withoutExt.many", total - n));
                    link = true;
                }
            }
            show.setVisible(link);
            show.setManaged(link);
            String left = p.selected() == index ? p.leftover() : null;
            boolean hasLeft = left != null;
            if (hasLeft) {
                leftover.setText(Strings.plural("names.ext.leftover.one", "names.ext.leftover.many",
                        p.leftoverCount(), left, p.leftoverExample()));
                addLeftover.setText(Strings.format("names.ext.addLeftover", left));
                addLeftover.setOnAction(e -> host.insertRule(index + 1, new HideTextRule(left, false, true)));
            }
            leftoverBox.setVisible(hasLeft);
            leftoverBox.setManaged(hasLeft);
            updateExample(p);
        }
    }

    // ------------------------------------------------------------------ (d) pieces

    /** The separators offered in the combo, with their spoken names. */
    private static final List<String> SEPARATORS = List.of("_", "-", ".", " ");

    static String separatorLabel(String sep) {
        return switch (sep) {
            case "_" -> Strings.get("names.pieces.sep.underscore");
            case "-" -> Strings.get("names.pieces.sep.hyphen");
            case "." -> Strings.get("names.pieces.sep.period");
            case " " -> Strings.get("names.pieces.sep.space");
            default -> sep;
        };
    }

    static final class PiecesEditor extends Editor {

        private final ComboBox<String> separator = new ComboBox<>();
        private final Label sampleLabel = wrapped("");
        private final FlowPane chips = new FlowPane(4, 4);
        private final Label notAll = wrapped(Strings.get("names.pieces.notAll"));
        private final Label range = wrapped("");
        private final Label fewer = wrapped("");
        private final Hyperlink show = link();
        private final Label varies = wrapped("");
        private final Label problem = wrapped("");
        private final List<ToggleButton> chipButtons = new ArrayList<>();
        private final ToggleButton lastChip = new ToggleButton();
        private String sample;
        private String chipKey;
        private NamePreview lastPreview;

        PiecesEditor(Host host, int index) {
            super(host, index, "names.kind.pieces");
            Label sepLabel = new Label(Strings.get("names.pieces.separator"));
            sepLabel.setLabelFor(separator);
            sepLabel.setMinWidth(Region.USE_PREF_SIZE);
            separator.setEditable(true);
            separator.getItems().setAll(SEPARATORS);
            separator.setConverter(new StringConverter<>() {
                @Override
                public String toString(String sep) {
                    return sep == null ? "" : separatorLabel(sep);
                }

                @Override
                public String fromString(String text) {
                    if (text == null) {
                        return "";
                    }
                    for (String s : SEPARATORS) {
                        if (separatorLabel(s).equals(text)) {
                            return s;
                        }
                    }
                    return text;
                }
            });
            separator.setTooltip(Tooltips.of(Strings.get("names.pieces.separator.tooltip")));
            separator.setPrefWidth(150);
            separator.getEditor().setOnAction(e -> e.consume());
            // Enter keeps the typed separator and stays here, as in the other fields; it must not
            // reach the window's Apply button.
            separator.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    if (separator.isShowing()) {
                        separator.hide();
                    }
                    separator.commitValue();
                    e.consume();
                }
            });
            HBox sepRow = new HBox(6, sepLabel, separator);
            sepRow.setAlignment(Pos.CENTER_LEFT);
            Label sampleHint = new Label(Strings.get("names.pieces.sampleHint"));
            sampleHint.setMinWidth(Region.USE_PREF_SIZE);
            ThemeColors.textFill(sampleHint, ThemeColors.MUTED);
            sampleLabel.setWrapText(false);
            FlowPane sampleRow = new FlowPane(8, 2, sampleLabel, sampleHint);
            show.setOnAction(e -> host.showFilter(NameRulesDialog.Filter.FEWER));
            warn(varies, true);
            warn(problem, true);
            lastChip.setMnemonicParsing(false);
            lastChip.getStyleClass().add("ptp-piece-chip");
            lastChip.setOnAction(e -> {
                KeepPiecesRule k = (KeepPiecesRule) rule();
                host.replaceRule(index, new KeepPiecesRule(k.separator(), k.kept(), k.pieces(), lastChip.isSelected(),
                        k.keepLater(), k.on()));
            });
            // Warnings first, so they show without scrolling at the default window size.
            box.getChildren().addAll(problem, varies, sepRow, sampleRow, wrapped(Strings.get("names.pieces.pick")), chips,
                    notAll, new VBox(0, fewer, show), range, wrapped(Strings.get("names.pieces.order")), example);
            loading = true;
            separator.setValue(((KeepPiecesRule) rule()).separator());
            loading = false;
            separator.valueProperty().addListener((obs, o, n) -> {
                if (!loading) {
                    separatorChanged(n == null ? "" : n);
                }
            });
        }

        private void separatorChanged(String sep) {
            KeepPiecesRule k = (KeepPiecesRule) rule();
            int n = sample == null || sep.isEmpty() ? 0 : KeepPiecesRule.split(sample, sep).size();
            host.replaceRule(index, new KeepPiecesRule(sep, all(n), n, true, true, k.on()));
        }

        private static SortedSet<Integer> all(int n) {
            SortedSet<Integer> s = new TreeSet<>();
            for (int i = 0; i < n; i++) {
                s.add(i);
            }
            return s;
        }

        /** The user clicked a table row: use it as the sample, keeping the chips that still exist. */
        void setSample(String input) {
            if (input == null || input.equals(sample)) {
                return;
            }
            sample = input;
            KeepPiecesRule k = (KeepPiecesRule) rule();
            if (k.separator().isEmpty()) {
                updateChips();
                return;
            }
            int n = KeepPiecesRule.split(sample, k.separator()).size();
            SortedSet<Integer> kept = new TreeSet<>();
            for (int i = 0; i < n; i++) {
                if (k.kept().contains(i) || i >= k.pieces() && k.keepLater()) {
                    kept.add(i);
                }
            }
            host.replaceRule(index, new KeepPiecesRule(k.separator(), kept, n, k.keepLast(), kept.contains(n - 1), k.on()));
        }

        @Override
        void update(NamePreview p) {
            lastPreview = p;
            KeepPiecesRule k = (KeepPiecesRule) rule();
            if (sample == null && p.sampleIndex() >= 0) {
                sample = p.inputs().get(p.sampleIndex());
            }
            if (k.pieces() == 0 && sample != null && !k.separator().isEmpty()) {
                // A new rule: one chip per piece of the sample, all ticked.
                int n = KeepPiecesRule.split(sample, k.separator()).size();
                host.replaceRule(index, new KeepPiecesRule(k.separator(), all(n), n, true, true, k.on()));
                return;
            }
            updateChips();
            int total = p.inputs().size();
            if (k.separator().isEmpty()) {
                problem.setText(Strings.get("names.pieces.noSeparator"));
            } else if (!p.separatorPresent() && total > 0) {
                problem.setText(Strings.get("names.pieces.separatorAbsent"));
            } else if (!k.finished()) {
                problem.setText(Strings.get("names.pieces.noneChosen"));
            } else {
                problem.setText("");
            }
            problem.setVisible(!problem.getText().isEmpty());
            problem.setManaged(problem.isVisible());
            int min = p.minPieces();
            int max = p.maxPieces();
            range.setText(min == max ? Strings.format("names.pieces.same", max) : Strings.format("names.pieces.range", min, max));
            int nFewer = p.fewerThan(k.pieces());
            boolean anyFewer = nFewer > 0 && !k.separator().isEmpty();
            fewer.setText(anyFewer ? Strings.plural("names.pieces.fewer.one", "names.pieces.fewer.many", nFewer) : "");
            fewer.setVisible(anyFewer);
            fewer.setManaged(anyFewer);
            show.setText(Strings.plural("names.show.fewer.one", "names.show.fewer.many", nFewer));
            show.setVisible(anyFewer);
            show.setManaged(anyFewer);
            String v = null;
            for (int i = 0; i < k.pieces(); i++) {
                if (p.varies(i)) {
                    v = Strings.format("names.pieces.varies", i + 1, tokenList(p.tokensAt(i), 3));
                    break;
                }
            }
            varies.setText(v == null ? "" : v);
            varies.setVisible(v != null);
            varies.setManaged(v != null);
            updateExample(p);
        }

        private static String tokenList(List<Map.Entry<String, Integer>> tokens, int max) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < Math.min(max, tokens.size()); i++) {
                Map.Entry<String, Integer> e = tokens.get(i);
                parts.add(Strings.plural("names.pieces.tokenIn.one", "names.pieces.tokenIn.many", e.getValue(), e.getKey()));
            }
            String s = String.join(", ", parts);
            return tokens.size() > max ? s + ", ..." : s;
        }

        private void updateChips() {
            KeepPiecesRule k = (KeepPiecesRule) rule();
            sampleLabel.setText(Strings.format("names.pieces.sample", sample == null ? "" : sample));
            List<String> pieces = new ArrayList<>();
            if (sample != null && !k.separator().isEmpty()) {
                for (int[] r : KeepPiecesRule.split(sample, k.separator())) {
                    pieces.add(sample.substring(r[0], r[1]));
                }
            }
            String key = sample + "\u0000" + k.separator() + "\u0000" + k.pieces();
            int min = lastPreview == null ? 0 : lastPreview.minPieces();
            if (!Objects.equals(key, chipKey)) {
                chipKey = key;
                chipButtons.clear();
                List<Node> nodes = new ArrayList<>();
                for (int i = 0; i < k.pieces(); i++) {
                    ToggleButton b = new ToggleButton();
                    b.setMnemonicParsing(false);
                    b.getStyleClass().add("ptp-piece-chip");
                    int piece = i;
                    b.setOnAction(e -> chipToggled(piece, b.isSelected()));
                    chipButtons.add(b);
                    nodes.add(b);
                }
                nodes.add(lastChip);
                chips.getChildren().setAll(nodes);
            }
            boolean anyStar = false;
            for (int i = 0; i < chipButtons.size(); i++) {
                ToggleButton b = chipButtons.get(i);
                boolean star = i >= min;
                anyStar |= star;
                String text = i < pieces.size() ? pieces.get(i) : "";
                b.setText((i + 1) + (star ? "*" : "") + "  " + text);
                b.setSelected(k.kept().contains(i));
                b.setTooltip(Tooltips.of(chipTooltip(i, text)));
                b.setAccessibleText(Strings.format("names.a11y.chip", i + 1, text,
                        Strings.get(b.isSelected() ? "names.a11y.shownWord" : "names.a11y.hiddenWord")));
            }
            String lastText = pieces.isEmpty() ? "" : pieces.get(pieces.size() - 1);
            lastChip.setText(Strings.format("names.pieces.last", lastText));
            lastChip.setSelected(k.keepLast());
            lastChip.setTooltip(Tooltips.of(Strings.format("names.pieces.chipLast.tooltip", lastText)));
            lastChip.setAccessibleText(Strings.format("names.a11y.chipLast", lastText,
                    Strings.get(k.keepLast() ? "names.a11y.shownWord" : "names.a11y.hiddenWord")));
            notAll.setVisible(anyStar);
            notAll.setManaged(anyStar);
        }

        private String chipTooltip(int i, String text) {
            List<Map.Entry<String, Integer>> tokens = lastPreview == null ? List.of() : lastPreview.tokensAt(i);
            List<Map.Entry<String, Integer>> others = new ArrayList<>();
            for (Map.Entry<String, Integer> e : tokens) {
                if (!e.getKey().equals(text)) {
                    others.add(e);
                }
            }
            if (others.isEmpty()) {
                return Strings.format("names.pieces.chip.tooltip", i + 1, text);
            }
            List<String> parts = new ArrayList<>();
            for (int j = 0; j < Math.min(3, others.size()); j++) {
                parts.add(Strings.plural("names.pieces.tokenCount.one", "names.pieces.tokenCount.many",
                        others.get(j).getValue(), others.get(j).getKey()));
            }
            String list = String.join(", ", parts) + (others.size() > 3 ? ", ..." : "");
            return Strings.format("names.pieces.chipVaries.tooltip", i + 1, text, list);
        }

        private void chipToggled(int piece, boolean keep) {
            KeepPiecesRule k = (KeepPiecesRule) rule();
            SortedSet<Integer> kept = new TreeSet<>(k.kept());
            if (keep) {
                kept.add(piece);
            } else {
                kept.remove(piece);
            }
            host.replaceRule(index, new KeepPiecesRule(k.separator(), kept, k.pieces(), k.keepLast(),
                    kept.contains(k.pieces() - 1), k.on()));
        }

        ComboBox<String> separatorBox() {
            return separator;
        }

        /** Untick one chip, as the table's piece menu asks. */
        void hidePiece(int piece) {
            if (piece >= 0 && piece < chipButtons.size()) {
                chipToggled(piece, false);
            }
        }
    }

    // ------------------------------------------------------------------ kept rule

    /** A rule this build cannot read: what it is and what can be done with it, nothing to edit. */
    static final class KeptEditor extends Editor {

        KeptEditor(Host host, int index) {
            super(host, index, "names.kept.title");
            KeptRule k = (KeptRule) rule();
            box.getChildren().addAll(
                    wrapped(Strings.get(k.newer() ? "names.kept.newer" : "names.kept.unreadable")),
                    wrapped(Strings.format("names.kept.kind", k.label())),
                    wrapped(Strings.get("names.kept.help")));
        }

        @Override
        void update(NamePreview p) {
            // Nothing to redraw: the rule changes no name here.
        }
    }

    // ------------------------------------------------------------------ suggestions card

    /** Shown when the list is empty or Suggest... is pressed. Ticks drive the table preview. */
    static final class SuggestionCard {

        private final VBox box = new VBox(8);
        private final List<CheckBox> ticks = new ArrayList<>();
        private final List<Suggestions.Suggestion> suggestions;
        private final Button add = new Button();

        SuggestionCard(List<Suggestions.Suggestion> suggestions, int total, boolean showBack, Runnable onTick,
                       Consumer<List<NameRule>> onAdd, Runnable onBack) {
            this.suggestions = List.copyOf(suggestions);
            Label title = new Label(Strings.get("names.card.title"));
            title.setStyle("-fx-font-weight: bold;");
            box.getChildren().addAll(title, wrapped(Strings.get("names.card.intro")));
            if (suggestions.isEmpty()) {
                box.getChildren().add(wrapped(Strings.get(showBack ? "names.card.covered" : "names.card.none")));
            } else {
                box.getChildren().add(wrapped(Strings.format("names.card.suggestions", total)));
                VBox rows = new VBox(4);
                for (Suggestions.Suggestion s : suggestions) {
                    CheckBox cb = new CheckBox(text(s));
                    cb.setMnemonicParsing(false);
                    cb.setSelected(true);
                    cb.setTooltip(Tooltips.of(Strings.get(s.kind() == Suggestions.Kind.LEFTOVER
                            ? "names.suggestion.leftover.tooltip" : "names.suggestion.tooltip")));
                    cb.setMaxWidth(Double.MAX_VALUE);
                    cb.setMinWidth(0);
                    // A leftover's example can be long; wrap rather than cut it.
                    cb.setWrapText(true);
                    HBox.setHgrow(cb, Priority.ALWAYS);
                    cb.selectedProperty().addListener((obs, o, n) -> {
                        updateAdd();
                        onTick.run();
                    });
                    Label reach = new Label(s.reach() == s.total()
                            ? Strings.format("names.card.reachAll", s.total())
                            : Strings.format("names.card.reach", s.reach(), s.total()));
                    reach.setMinWidth(Region.USE_PREF_SIZE);
                    HBox row = new HBox(8, cb, reach);
                    row.setAlignment(Pos.BASELINE_LEFT);
                    ticks.add(cb);
                    rows.getChildren().add(row);
                }
                add.setTooltip(Tooltips.of(Strings.get("names.addSuggestions.tooltip")));
                add.setMnemonicParsing(false);
                add.setMinWidth(Region.USE_PREF_SIZE);
                add.setOnAction(e -> onAdd.accept(ticked()));
                Label hint = wrapped(Strings.get("names.card.hint"));
                HBox addRow = new HBox(10, add, hint);
                addRow.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(hint, Priority.ALWAYS);
                box.getChildren().addAll(rows, addRow);
                updateAdd();
            }
            if (showBack) {
                Button back = new Button(Strings.get("names.card.back"));
                back.setTooltip(Tooltips.of(Strings.get("names.backToRules.tooltip")));
                back.setOnAction(e -> onBack.run());
                box.getChildren().add(back);
            }
        }

        private static String text(Suggestions.Suggestion s) {
            return switch (s.kind()) {
                case PREFIX -> Strings.format("names.card.prefix", s.detail());
                case TEXT -> Strings.format("names.card.text", s.detail());
                case EXTENSION -> Strings.format("names.card.ext", s.detail());
                case LEFTOVER -> Strings.format("names.card.leftover", s.detail(), s.example());
            };
        }

        private void updateAdd() {
            int n = ticked().size();
            add.setText(Strings.plural("names.card.add.one", "names.card.add.many", n));
            add.setDisable(n == 0);
        }

        Node node() {
            return box;
        }

        /** @return the ticked rules, in suggestion order */
        List<NameRule> ticked() {
            List<NameRule> out = new ArrayList<>();
            for (int i = 0; i < ticks.size(); i++) {
                if (ticks.get(i).isSelected()) {
                    out.add(suggestions.get(i).rule());
                }
            }
            return out;
        }

        /** @return the first tick, to take focus */
        Node firstControl() {
            return ticks.isEmpty() ? null : ticks.get(0);
        }
    }
}
