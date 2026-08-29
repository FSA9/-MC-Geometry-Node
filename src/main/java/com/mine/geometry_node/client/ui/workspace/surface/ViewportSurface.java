package com.mine.geometry_node.client.ui.workspace.surface;

import com.mine.geometry_node.client.ui.session.GraphSession;

/** UI-side viewport context used by the MCP adapter without exposing it to runtime-neutral AI code. */
public interface ViewportSurface {
    GraphSession currentGraphSession();
}
