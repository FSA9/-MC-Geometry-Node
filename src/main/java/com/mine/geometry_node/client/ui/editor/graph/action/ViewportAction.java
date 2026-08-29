package com.mine.geometry_node.client.ui.editor.graph.action;

import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;

public final class ViewportAction extends ScopedAction<ViewportActionId> {
    ViewportAction(KeyScope scope,
                   ViewportActionId id,
                   String label,
                   ConfigEntry<String> shortcutEntry,
                   EnabledReader<ViewportActionState> enabledReader) {
        super(scope, id, label, shortcutEntry, enabledReader);
    }
}
