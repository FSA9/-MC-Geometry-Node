package com.mine.geometry_node.client.ui.viewport.action;

import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;

public final class ViewportAction extends ScopedAction<ViewportActionId> {
    ViewportAction(KeyScope scope,
                   ViewportActionId id,
                   String label,
                   ShortcutReader shortcutReader,
                   EnabledReader<ViewportActionState> enabledReader) {
        super(scope, id, label, shortcutReader, enabledReader);
    }
}
