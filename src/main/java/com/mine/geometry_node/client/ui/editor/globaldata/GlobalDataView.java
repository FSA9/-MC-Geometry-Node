package com.mine.geometry_node.client.ui.editor.globaldata;

import com.mine.geometry_node.client.ui.components.base.layout.ThinResizableDivider;
import com.mine.geometry_node.client.ui.components.composite.layout.UiFixedSplitLayout;
import com.mine.geometry_node.client.ui.components.composite.list.UiNavigationRow;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.state.*;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.BiFunction;

/** Category host only: domains supply factories and their own list/detail UI. */
public final class GlobalDataView extends LinearLayout {
    public record Section(String id, String titleKey, BiFunction<Context, UiStateContext, GlobalDataSection> create) {
        public Section {
            if (id == null || !id.matches("[a-z][a-z0-9_.-]*")) throw new IllegalArgumentException("Invalid global-data section id");
            Objects.requireNonNull(titleKey); Objects.requireNonNull(create);
        }
    }
    public record Page(String section) {}
    public record Navigation(float extent) {
        public Navigation { extent = Float.isFinite(extent) ? Math.max(120, Math.min(600, extent)) : 160; }
    }
    private static final UiStateKey<Page> PAGE = new UiStateKey<>("globaldata:page", 1, UiStateLifetime.MEMORY,
            Page.class, () -> new Page("entities"), value -> new Page(value.section() == null ? "" : value.section()));
    private static final UiStateKey<Navigation> NAVIGATION = new UiStateKey<>("globaldata:navigation", 1, UiStateLifetime.PERSISTENT,
            Navigation.class, () -> new Navigation(160), value -> value);
    private final UiStateContext state;
    private final Map<String, Section> definitions = new LinkedHashMap<>();
    private final Map<String, GlobalDataSection> sections = new LinkedHashMap<>();
    private final Map<String, UiNavigationRow> buttons = new LinkedHashMap<>();
    private final FrameLayout content;
    private final UiFixedSplitLayout split;
    private final Runnable restoreTask = this::restoreScope;
    private final Runnable scopeChanged = () -> { removeCallbacks(restoreTask); post(restoreTask); };
    private String current, scope;
    private boolean shown, disposed;

    public GlobalDataView(Context context, UiStateContext state) {
        this(context, state, List.of(
                new Section("entities", "geometry_node.database.entities", EntityCatalogView::new),
                new Section("factions", "geometry_node.faction.factions", FactionCatalogView::new)));
    }
    public GlobalDataView(Context context, UiStateContext state, List<Section> definitions) {
        super(context); this.state = Objects.requireNonNull(state); setOrientation(VERTICAL);
        LinearLayout navigation = new LinearLayout(context); navigation.setOrientation(VERTICAL);
        navigation.setPadding(px(8), px(10), px(8), px(10));
        ShapeDrawable background = new ShapeDrawable(); background.setColor(UiThemes.EDITOR.surface().root()); navigation.setBackground(background);
        for (Section section : definitions) {
            if (this.definitions.putIfAbsent(section.id(), section) != null) throw new IllegalArgumentException("Duplicate global-data section");
            UiNavigationRow button = new UiNavigationRow(context, Component.translatable(section.titleKey()).getString(), UiThemes.EDITOR);
            button.button().setOnClickListener(view -> showSection(section.id()));
            navigation.addView(button, new LayoutParams(-1, px(33))); buttons.put(section.id(), button);
        }
        if (this.definitions.isEmpty()) throw new IllegalArgumentException("No global-data sections");
        content = new FrameLayout(context);
        split = new UiFixedSplitLayout(context, ThinResizableDivider.Orientation.HORIZONTAL,
                UiFixedSplitLayout.Edge.FIRST, 160, 120, 480); split.setPanes(navigation, content);
        split.restorePreferredExtentDp(state.read(NAVIGATION, UiStateScopes.GLOBAL).extent());
        split.addUserStateListener(this::saveNavigation); addView(split, new LayoutParams(-1, 0, 1));
        restoreScope();
    }
    public void showSection(String id) {
        if (disposed || !definitions.containsKey(id) || Objects.equals(current, id)) return;
        if (current != null && sections.containsKey(current)) sections.get(current).onHide();
        current = id;
        GlobalDataSection selected = sections.computeIfAbsent(id, key -> {
            GlobalDataSection section = definitions.get(key).create().apply(getContext(), state);
            content.addView(section.view(), new FrameLayout.LayoutParams(-1, -1)); return section;
        });
        sections.forEach((key, section) -> section.view().setVisibility(key.equals(id) ? VISIBLE : GONE));
        buttons.forEach((key, button) -> button.setPalette(UiTableStyle.rowPalette(key.equals(id), UiThemes.EDITOR.surface().root())));
        savePage(); if (shown) selected.onShow();
    }
    public void onShow() {
        if (shown || disposed) return;
        restoreScope(); shown = true; UiStateScopes.addChangeListener(scopeChanged);
        if (current != null) sections.get(current).onShow();
    }
    public void onHide() {
        if (!shown) return;
        shown = false; UiStateScopes.removeChangeListener(scopeChanged); removeCallbacks(restoreTask);
        sections.values().forEach(GlobalDataSection::onHide); savePage(); saveNavigation();
    }
    public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true; sections.values().forEach(GlobalDataSection::onDispose); sections.clear();
    }
    private void restoreScope() {
        if (disposed || Objects.equals(scope, UiStateScopes.current())) return;
        savePage(); sections.values().forEach(GlobalDataSection::onDispose); sections.clear(); content.removeAllViews();
        current = null; scope = UiStateScopes.current();
        String saved = state.read(PAGE, scope).section();
        showSection(definitions.containsKey(saved) ? saved : definitions.keySet().iterator().next());
    }
    private void savePage() {
        if (scope != null && current != null && !disposed) state.write(PAGE, scope, new Page(current));
    }
    private void saveNavigation() {
        if (!disposed) state.write(NAVIGATION, UiStateScopes.GLOBAL, new Navigation(split.preferredExtentDp()));
    }
    private static int px(float value) { return UIUtils.dp2pxInt(value); }
}
