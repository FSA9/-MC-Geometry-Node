package com.mine.geometry_node.client.ui.editor.entity.graph;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.scroll.UiScrollView;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Bound graph page: stages bindings only; graph content actions use the separate graph sidecar. */
public final class EntityGraphsView extends FrameLayout {
    private final UiTheme theme;
    private final EntityGraphBindings bindings;
    private final Consumer<List<Map<String, Object>>> changed;
    private final Set<GraphBindingKey> selected = new LinkedHashSet<>();
    private final Map<GraphBindingKey, UiCheckBox> checks = new LinkedHashMap<>();
    private final Map<GraphBindingKey, LinearLayout> rows = new LinkedHashMap<>();
    private final UiCheckBox selectAll;
    private final UiDisplayText summary;
    private final UiActionButton add;
    private final UiActionButton remove;
    private final LinearLayout list;
    private UiContextMenu menu;

    public EntityGraphsView(Context context, UiTheme theme, List<Map<String, Object>> bound,
                            List<Map<String, Object>> available,
                            Consumer<List<Map<String, Object>>> changed) {
        super(context);
        this.theme = Objects.requireNonNull(theme, "theme");
        this.changed = Objects.requireNonNull(changed, "changed");
        bindings = new EntityGraphBindings(bound, available);
        LinearLayout content = column();
        content.setPadding(px(20), px(16), px(20), px(16));
        LinearLayout toolbar = row();
        selectAll = UiCheckBox.createThemed(context, theme);
        selectAll.setTooltipText(tr("select_all"));
        selectAll.setOnCheckedChangeListener((box, checked) -> {
            selected.clear();
            if (checked) selected.addAll(bindings.keys());
            updateSelection();
        });
        toolbar.addView(selectAll, new LinearLayout.LayoutParams(px(16), px(16)));
        summary = text("", theme.text().muted(), 11);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, -1, 1);
        summaryParams.leftMargin = px(10);
        toolbar.addView(summary, summaryParams);
        remove = button("remove_selected", UiActionButton.Role.SECONDARY, this::removeSelected);
        remove.setTooltipText(tr("unbind_help"));
        toolbar.addView(remove, new LinearLayout.LayoutParams(-2, px(32)));
        add = button("add_binding", UiActionButton.Role.PRIMARY, this::showAddMenu);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-2, px(32));
        addParams.leftMargin = px(8);
        toolbar.addView(add, addParams);
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, px(40)));
        UiDisplayText help = text(tr("bindings_help"), theme.text().muted(), 11);
        content.addView(help, new LinearLayout.LayoutParams(-1, px(40)));
        UiScrollView scroll = new UiScrollView(context, theme.scroll());
        list = column();
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addView(content, new FrameLayout.LayoutParams(-1, -1));
        render();
    }

    /** Detached protocol payload containing only canonical id and GraphKind.name(). */
    public List<Map<String, Object>> graphs() { return bindings.graphs(); }

    /** Binding changes only; checkbox selection and graph-content edits are separate state. */
    public boolean hasPendingChanges() { return bindings.dirty(); }

    private void render() {
        list.removeAllViews();
        checks.clear();
        rows.clear();
        selected.retainAll(bindings.keys());
        if (bindings.keys().isEmpty()) {
            list.addView(text(tr("empty_bindings"), theme.text().muted(), 12),
                    new LinearLayout.LayoutParams(-1, px(72)));
        }
        for (GraphBindingKey key : bindings.keys()) {
            LinearLayout entry = row();
            entry.setPadding(px(12), px(8), px(12), px(8));
            UiCheckBox check = UiCheckBox.createThemed(getContext(), theme);
            check.setTooltipText(tr("select_binding", key.graphId()));
            check.setOnCheckedChangeListener((box, checked) -> {
                if (checked) selected.add(key);
                else selected.remove(key);
                updateSelection();
            });
            entry.addView(check, new LinearLayout.LayoutParams(px(16), px(16)));
            LinearLayout labels = column();
            UiDisplayText name = text(bindings.name(key), theme.text().normal(), 12);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTooltipText(bindings.name(key));
            labels.addView(name, new LinearLayout.LayoutParams(-1, px(25)));
            UiDisplayText id = text(key.graphId(), theme.text().muted(), 10);
            id.setSingleLine(true);
            id.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            id.setTooltipText(key.graphId());
            labels.addView(id, new LinearLayout.LayoutParams(-1, px(22)));
            labels.addView(text(tr("kind." + key.kind().id()), theme.text().muted(), 10),
                    new LinearLayout.LayoutParams(-1, px(20)));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
            labelParams.leftMargin = px(12);
            labelParams.rightMargin = px(10);
            entry.addView(labels, labelParams);
            LinearLayout actions = column();
            actions.addView(button("view", UiActionButton.Role.SECONDARY,
                    () -> EntityGraphActions.open(this, key.graphId(), false, null)),
                    new LinearLayout.LayoutParams(px(64), px(28)));
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(px(64), px(28));
            editParams.topMargin = px(5);
            actions.addView(button("edit", UiActionButton.Role.SECONDARY,
                    () -> EntityGraphActions.open(this, key.graphId(), true, null)), editParams);
            entry.addView(actions, new LinearLayout.LayoutParams(-2, -2));
            entry.setOnClickListener(ignored -> check.performClick());
            LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(-1, -2);
            entryParams.bottomMargin = px(8);
            list.addView(entry, entryParams);
            checks.put(key, check);
            rows.put(key, entry);
        }
        updateSelection();
    }

    private void updateSelection() {
        int count = bindings.keys().size();
        selectAll.setEnabled(count > 0);
        selectAll.setChecked(count > 0 && selected.size() == count);
        selectAll.setIndeterminate(!selected.isEmpty() && selected.size() < count);
        summary.setText(tr("selection_count", selected.size(), count));
        remove.setEnabled(!selected.isEmpty());
        add.setEnabled(!bindings.candidates().isEmpty());
        add.setTooltipText(tr(bindings.candidates().isEmpty() ? "no_available" : "add_binding"));
        checks.forEach((key, check) -> check.setChecked(selected.contains(key)));
        rows.forEach((key, row) -> {
            ShapeDrawable background = new ShapeDrawable();
            background.setColor(selected.contains(key) ? theme.selection().subtle() : theme.surface().panel());
            background.setCornerRadius(px(4));
            background.setStroke(px(1), selected.contains(key) ? theme.selection().outline() : theme.border().subtle());
            row.setBackground(background);
        });
    }

    private void removeSelected() {
        dismissMenus();
        if (!bindings.remove(selected)) return;
        selected.clear();
        render();
        changed.accept(graphs());
    }

    private void showAddMenu() {
        dismissMenus();
        var candidates = bindings.candidates();
        if (candidates.isEmpty()) return;
        menu = new UiContextMenu(getContext(), theme);
        for (GraphKind kind : GraphKind.values()) {
            var group = candidates.stream().filter(key -> key.kind() == kind).toList();
            if (group.isEmpty()) continue;
            menu.addSubMenuItem(tr("kind." + kind.id()), child -> {
                for (var key : group) {
                    String label = bindings.name(key) + " · " + key.graphId();
                    child.addMenuItem(label, () -> {
                        if (!bindings.add(key)) return;
                        render();
                        changed.accept(graphs());
                    });
                }
            });
        }
        // Keep the popup inside the current modal's content, above its page. Root-shell hosts
        // would place the popup behind the modal scrim and make it impossible to interact with.
        menu.showBelow(add, menuHost());
    }

    ViewGroup menuHost() {
        View current = this;
        while (current != null) {
            if (current instanceof ModalWindowView modal) return modal.contentHost();
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return this;
    }

    /** Parent calls this before switching pages or replacing its entity draft. */
    public void dismissMenus() {
        if (menu != null) { menu.dismiss(); menu = null; }
    }

    /** Parent forwards Escape here before running its own close/discard guard. */
    public boolean onEscapePressed() {
        if (menu == null || !menu.isOpen()) return false;
        dismissMenus();
        return true;
    }

    private void hideMenusDeferred() {
        if (menu != null) {
            UiContextMenu closing = menu;
            menu = null;
            closing.setVisibility(GONE);
            // The menu is a sibling in the modal content host. Defer removal until the
            // ancestor's visibility/detach traversal has finished walking that child array.
            new Handler(Looper.myLooper()).post(closing::dismiss);
        }
    }

    @Override public void setVisibility(int visibility) {
        // Detached pages do not receive onVisibilityChanged. Hide their sibling popup
        // immediately too; removal remains deferred in case a host traversal is active.
        if (visibility != VISIBLE) hideMenusDeferred();
        super.setVisibility(visibility);
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // View construction can dispatch this before our fields are initialized. The nullable
        // menu is the only state inspected here, so construction and ancestor GONE are safe.
        if (visibility != VISIBLE) hideMenusDeferred();
    }

    @Override protected void onDetachedFromWindow() {
        hideMenusDeferred();
        super.onDetachedFromWindow();
    }

    private LinearLayout column() {
        LinearLayout result = new LinearLayout(getContext());
        result.setOrientation(LinearLayout.VERTICAL);
        return result;
    }
    private LinearLayout row() {
        LinearLayout result = new LinearLayout(getContext());
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }
    private UiDisplayText text(String value, int color, float size) {
        UiDisplayText result = new UiDisplayText(getContext(), value, size, color);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }
    private UiActionButton button(String key, UiActionButton.Role role, Runnable action) {
        UiActionButton result = new UiActionButton(getContext(), theme, tr(key), role,
                UiActionButton.Density.NORMAL, UiActionButton.Style.forTheme(theme));
        result.setOnClickListener(ignored -> action.run());
        return result;
    }
    private static int px(float dp) { return UIUtils.dp2pxInt(dp); }
    private static String tr(String key, Object... args) { return EntityGraphActions.tr(key, args); }
}
