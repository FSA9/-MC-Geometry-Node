package com.mine.geometry_node.client.ui.workspace.surface;

import com.mine.geometry_node.client.history.HistoryTarget;

/** UI-thread capability of a registered surface. Documents own histories; surfaces only resolve them. */
public interface HistorySurface {
    /** Null when this surface has no editable document or is currently preview-only. */
    HistoryTarget historyTarget();
}
