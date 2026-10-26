package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.ui.workspace.state.*;
import java.util.*;

/** Small detached presentation snapshots; faction data remains owned by its repository. */
final class FactionViewState {
    private FactionViewState() {}
    record Page(String selected, String section, String query, Set<String> checked) {
        Page {
            selected = selected == null ? "" : selected;
            section = section != null && Set.of("info", "relations", "ranks", "members", "data").contains(section) ? section : "info";
            query = query == null ? "" : query.substring(0, Math.min(1024, query.length()));
            var cleanChecked = checked == null ? new LinkedHashSet<String>() : new LinkedHashSet<>(checked);
            cleanChecked.remove(null);
            checked = Collections.unmodifiableSet(cleanChecked);
        }
    }
    record Split(float extent) {
        Split {
            extent = Float.isFinite(extent) ? Math.max(180, Math.min(1000, extent)) : 260;
        }
    }
    static final UiStateKey<Page> PAGE = new UiStateKey<>("globaldata:factions.page", 1, UiStateLifetime.MEMORY,
            Page.class, () -> new Page("", "info", "", Set.of()), value -> value);
    static final UiStateKey<Split> SPLIT = new UiStateKey<>("globaldata:factions.split", 1, UiStateLifetime.PERSISTENT,
            Split.class, () -> new Split(260), value -> value);
}
