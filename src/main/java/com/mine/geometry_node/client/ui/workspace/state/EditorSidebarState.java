package com.mine.geometry_node.client.ui.workspace.state;

/** Immutable snapshot of an editor's weighted right sidebar. */
public record EditorSidebarState(boolean visible, float weight, String tab) {
    public static final String SCOPE = "global";
    public static final UiStateKey<EditorSidebarState> GRAPH = key("graph.sidebar");
    public static final UiStateKey<EditorSidebarState> ASSETS = key("assets.sidebar");

    public EditorSidebarState {
        weight = Float.isFinite(weight) ? Math.max(0.05f, Math.min(0.75f, weight)) : 0.2f;
        tab = tab == null || tab.isBlank() || tab.length() > 128 ? "properties" : tab;
    }

    private static UiStateKey<EditorSidebarState> key(String id) {
        return new UiStateKey<>(id, 1, UiStateLifetime.PERSISTENT, EditorSidebarState.class,
                () -> new EditorSidebarState(true, 0.2f, "properties"),
                value -> new EditorSidebarState(value.visible(), value.weight(), value.tab()));
    }
}
