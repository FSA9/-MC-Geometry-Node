package com.mine.geometry_node.client.ui.components.composite.table;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.layout.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagChip;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.query.ColumnFilters;
import com.mine.geometry_node.core.metadata.TagPolicy;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Shared column-filter editors. Drafts are local; only Apply/Clear submit immutable criteria. */
public final class UiColumnFilterPopover {
    private final Context context;
    private final View owner;
    private UiPopover popup;
    private long generation;

    public UiColumnFilterPopover(Context context, View host) {
        this.context = Objects.requireNonNull(context);
        owner = Objects.requireNonNull(host);
    }

    public void dismiss() {
        generation++;
        if (popup != null) { popup.dismiss(); popup = null; }
    }

    public void showText(View anchor, String title, String value, Consumer<String> apply) {
        LinearLayout content = content(title);
        UiTextInput input = input(value, tr("keyword"));
        content.addView(input, params(-1, 30));
        actions(content, () -> apply.accept(input.getText().toString().trim()), () -> apply.accept(""), input);
        show(anchor, content);
    }

    public void showChoices(View anchor, String title, Map<String, String> options,
                            ColumnFilters.Choice selected, Consumer<ColumnFilters.Choice> apply) {
        showChoices(anchor, title, options, selected, null, false, (choice, scope) -> apply.accept(choice));
    }

    /** Optional independent scope toggle, e.g. loaded chunks; it is committed with the choices. */
    public void showChoices(View anchor, String title, Map<String, String> options,
                            ColumnFilters.Choice selected, String scopeLabel, boolean scope,
                            BiConsumer<ColumnFilters.Choice, Boolean> apply) {
        LinearLayout content = content(title);
        LinkedHashMap<String, String> labels = new LinkedHashMap<>(options);
        selected.selected().forEach(id -> labels.putIfAbsent(id, id));
        LinkedHashSet<String> draft = new LinkedHashSet<>(selected.restricted() ? selected.selected() : labels.keySet());
        boolean[] unrestricted = { !selected.restricted() };
        UiCheckBox scopeCheck = scopeLabel == null ? null : checkRow(content, scopeLabel, scope);
        if (scopeCheck != null) space(content);
        UiSearchInput search = new UiSearchInput(context);
        search.setHint(tr("keyword"));
        content.addView(search, params(-1, 30));
        LinearLayout commands = row();
        UiActionButton all = button(tr("select_all")), invert = button(tr("invert"));
        commands.addView(all, weighted()); commands.addView(invert, weighted());
        content.addView(commands, params(-1, 32));
        LinearLayout list = column();
        LinkedHashMap<String, UiCheckBox> checks = new LinkedHashMap<>();
        LinkedHashMap<String, View> rows = new LinkedHashMap<>();
        labels.forEach((id, label) -> {
            int index = list.getChildCount();
            UiCheckBox check = checkRow(list, label, draft.contains(id));
            check.setOnCheckedChangeListener((view, checked) -> {
                unrestricted[0] = false;
                if (checked) draft.add(id); else draft.remove(id);
            });
            checks.put(id, check); rows.put(id, list.getChildAt(index));
        });
        all.setOnClickListener(view -> {
            unrestricted[0] = true;
            draft.clear(); draft.addAll(labels.keySet()); checks.values().forEach(check -> check.setChecked(true));
        });
        invert.setOnClickListener(view -> {
            unrestricted[0] = false;
            checks.forEach((id, check) -> {
                boolean next = !draft.contains(id);
                if (next) draft.add(id); else draft.remove(id);
                check.setChecked(next);
            });
        });
        search.setOnQueryChanged(text -> {
            String query = text.toLowerCase(Locale.ROOT).trim();
            rows.forEach((id, view) -> view.setVisibility(labels.get(id).toLowerCase(Locale.ROOT).contains(query)
                    || id.toLowerCase(Locale.ROOT).contains(query) ? View.VISIBLE : View.GONE));
        });
        UiScrollView scroll = new UiScrollView(context);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        content.addView(scroll, params(-1, Math.min(210, Math.max(32, labels.size() * 30))));
        actions(content, () -> {
            ColumnFilters.Choice next = (unrestricted[0] || !labels.isEmpty() && draft.size() == labels.size() && draft.containsAll(labels.keySet()))
                    ? ColumnFilters.Choice.all() : new ColumnFilters.Choice(true, draft);
            apply.accept(next, scopeCheck != null && scopeCheck.isChecked());
        }, () -> {
            apply.accept(ColumnFilters.Choice.all(), false);
        });
        show(anchor, content);
    }

    public void showNumber(View anchor, String title, ColumnFilters.NumberRange value,
                           Consumer<ColumnFilters.NumberRange> apply) {
        LinearLayout content = content(title);
        UiTextInput[] range = range(content, "", value);
        actions(content, () -> apply.accept(readRange(range)), () -> apply.accept(ColumnFilters.NumberRange.unbounded()), range);
        show(anchor, content);
    }

    public void showPosition(View anchor, ColumnFilters.NumberRange x, ColumnFilters.NumberRange y,
                             ColumnFilters.NumberRange z, Consumer<List<ColumnFilters.NumberRange>> apply) {
        LinearLayout content = content(tr("position"));
        UiTextInput[] xi = range(content, "X", x), yi = range(content, "Y", y), zi = range(content, "Z", z);
        actions(content, () -> apply.accept(List.of(readRange(xi), readRange(yi), readRange(zi))),
                () -> apply.accept(Collections.nCopies(3, ColumnFilters.NumberRange.unbounded())),
                xi[0], xi[1], yi[0], yi[1], zi[0], zi[1]);
        show(anchor, content);
    }

    public void showTags(View anchor, Set<String> included, Set<String> excluded,
                         BiConsumer<Set<String>, Set<String>> apply) {
        LinearLayout content = content(tr("tags"));
        TagDraft include = new TagDraft(content, tr("include_tags"), included);
        space(content);
        TagDraft exclude = new TagDraft(content, tr("exclude_tags"), excluded);
        actions(content, () -> apply.accept(include.values(), exclude.values()), () -> apply.accept(Set.of(), Set.of()));
        show(anchor, content);
    }

    /** Local chip drafts; Enter adds a tag, while Apply also includes any unfinished input. */
    private final class TagDraft {
        private final LinkedHashSet<String> tags;
        private final TagFlowLayout chips;
        private final UiTextInput input;
        private final long token = generation;

        TagDraft(LinearLayout content, String title, Set<String> values) {
            tags = new LinkedHashSet<>(values);
            content.addView(label(title), params(-1, 24));
            chips = new TagFlowLayout(context, 4, 4);
            chips.setMinimumHeight(px(UiTagChip.HEIGHT_DP));
            LinearLayout.LayoutParams chipParams = params(-1, -2);
            chipParams.bottomMargin = px(6);
            content.addView(chips, chipParams);
            input = input("", tr("add_tag"));
            input.setOnKeyListener((view, key, event) -> {
                if (key != KeyEvent.KEY_ENTER && key != KeyEvent.KEY_KP_ENTER) return false;
                if (active() && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    try {
                        Set<String> next = values();
                        tags.clear(); tags.addAll(next);
                        input.setText(""); input.setErrorMessage(null);
                        rebuildChips(); popup.refreshSize();
                    } catch (IllegalArgumentException invalid) { input.setErrorMessage(tr("invalid_filter")); }
                }
                return true;
            });
            content.addView(input, params(-1, 30));
            rebuildChips();
        }
        private boolean active() { return token == generation && popup != null && popup.isOpen(); }
        Set<String> values() {
            Set<String> values = new LinkedHashSet<>(tags);
            String pending = input.getText().toString().trim();
            if (!pending.isEmpty()) values.add(TagPolicy.EXACT.normalize(pending));
            new ColumnFilters.Choice(true, values); // Validate the draft without changing its display order.
            return Collections.unmodifiableSet(values);
        }
        private void rebuildChips() {
            chips.removeAllViews();
            for (String tag : tags) {
                UiTagChip chip = new UiTagChip(context, tag);
                chip.setOnRemove(tr("remove_tag"), () -> {
                    if (!active()) return;
                    tags.remove(tag); rebuildChips(); popup.refreshSize();
                });
                chips.addView(chip, new TagFlowLayout.LayoutParams(-2, px(UiTagChip.HEIGHT_DP)));
            }
        }
    }

    private UiTextInput[] range(LinearLayout content, String title, ColumnFilters.NumberRange value) {
        if (!title.isEmpty()) content.addView(label(title), params(-1, 24));
        LinearLayout row = row();
        UiTextInput min = input(number(value.minimum()), tr("minimum"));
        UiTextInput max = input(number(value.maximum()), tr("maximum"));
        row.addView(min, weighted());
        row.addView(label("–"), params(22, 30));
        row.addView(max, weighted());
        content.addView(row, params(-1, 32));
        return new UiTextInput[]{min, max};
    }

    private static String number(Double value) { return value == null ? "" : Double.toString(value); }
    private ColumnFilters.NumberRange readRange(UiTextInput[] inputs) {
        try {
            Double min = parse(inputs[0]), max = parse(inputs[1]);
            return new ColumnFilters.NumberRange(min, max);
        } catch (IllegalArgumentException invalid) {
            for (UiTextInput input : inputs) input.setErrorMessage(tr("invalid_range"));
            throw invalid;
        }
    }

    private static Double parse(UiTextInput input) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? null : Double.valueOf(value);
    }

    private LinearLayout content(String title) {
        dismiss();
        LinearLayout content = column();
        content.setPadding(px(8), px(4), px(8), px(4));
        content.addView(label(title), params(-1, 28));
        return content;
    }

    private void actions(LinearLayout content, Runnable apply, Runnable clear, UiTextInput... inputs) {
        space(content);
        UiDisplayText error = label(tr("invalid_filter"));
        error.setTextColor(UiTheme.Control.ERROR);
        error.setVisibility(View.GONE);
        content.addView(error, params(-1, 30));
        LinearLayout actions = row();
        UiActionButton reset = button(tr("clear_column")), submit = button(tr("apply"));
        actions.addView(reset, weighted()); actions.addView(submit, weighted());
        content.addView(actions, params(-1, 30));
        long token = generation;
        Consumer<Runnable> commit = command -> {
            if (token != generation || popup == null || !popup.isOpen()) return;
            try { command.run(); dismiss(); }
            catch (IllegalArgumentException invalid) {
                error.setVisibility(View.VISIBLE);
                if (popup != null) popup.refreshSize();
            }
        };
        reset.setOnClickListener(view -> commit.accept(clear));
        UiSearchSubmit.bind(submit, () -> commit.accept(apply), inputs);
    }

    private void show(View anchor, LinearLayout content) {
        FrameLayout host = owner instanceof FrameLayout frame ? frame : null;
        if (host == null) for (View view = owner; view.getParent() instanceof View parent;) {
            view = parent;
            if (view instanceof FrameLayout frame) { host = frame; break; }
        }
        if (host == null && owner.getRootView() instanceof FrameLayout frame) host = frame;
        if (host == null) return;
        popup = new UiPopover(context, content, 300, 460);
        popup.showBelow(anchor, host);
    }

    private UiCheckBox checkRow(LinearLayout content, String text, boolean checked) {
        LinearLayout row = row();
        UiCheckBox check = new UiCheckBox(context);
        check.setChecked(checked);
        check.setContentDescription(text);
        LinearLayout.LayoutParams cp = params(16, 16); cp.leftMargin = px(4); cp.rightMargin = px(6);
        row.addView(check, cp);
        row.addView(label(text), new LinearLayout.LayoutParams(0, px(30), 1));
        row.setOnClickListener(view -> check.performClick());
        content.addView(row, params(-1, 30));
        return check;
    }

    private UiTextInput input(String value, String hint) {
        UiTextInput input = new UiTextInput(context);
        input.setSingleLine(true); input.setHint(hint); input.setText(value);
        return input;
    }
    private UiDisplayText label(String text) {
        UiDisplayText label = UiTableStyle.cell(context, text, UiTheme.Text.NORMAL);
        label.setPadding(px(3), 0, px(3), 0);
        return label;
    }
    private UiActionButton button(String text) {
        return new UiActionButton(context, text, UiActionButton.Role.SECONDARY, UiActionButton.Density.COMPACT);
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(context); view.setBaselineAligned(false); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private void space(LinearLayout content) { content.addView(new View(context), params(-1, 6)); }
    private static int px(int value) { return UIUtils.dp2pxInt(value); }
    private static LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : px(width), height < 0 ? height : px(height));
    }
    private static LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, px(30), 1); }
    private static String tr(String key) { return Component.translatable("geometry_node.database.column_filter." + key).getString(); }
}
