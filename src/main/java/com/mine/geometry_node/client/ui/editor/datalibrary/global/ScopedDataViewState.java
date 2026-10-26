package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.ui.workspace.state.*;

/** Presentation only; dictionary forms stay in each retained embedded editor. */
final class ScopedDataViewState {
    private ScopedDataViewState() {}

    record Page(String target, String query, int scroll) {
        Page {
            target = target == null || target.isBlank() ? "GLOBAL" : target;
            query = query == null ? "" : query.substring(0, Math.min(1024, query.length()));
            scroll = Math.max(0, scroll);
        }
    }
    record Split(float extent) {
        Split { extent = Float.isFinite(extent) ? Math.max(180, Math.min(380, extent)) : 246; }
    }
    static final UiStateKey<Page> PAGE = new UiStateKey<>("globaldata:data.page", 1, UiStateLifetime.MEMORY,
            Page.class, () -> new Page("GLOBAL", "", 0), value -> new Page(value.target(), value.query(), value.scroll()));
    static final UiStateKey<Split> SPLIT = new UiStateKey<>("globaldata:data.split", 1, UiStateLifetime.PERSISTENT,
            Split.class, () -> new Split(246), value -> new Split(value.extent()));
}
