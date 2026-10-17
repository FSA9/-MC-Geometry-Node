package com.mine.geometry_node.client.ai.history;

import com.mine.geometry_node.client.ai.command.CommandResult;
import com.mine.geometry_node.client.ai.command.HistoryCommandCatalog;
import com.mine.geometry_node.client.ui.workspace.surface.HistorySurface;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import icyllis.modernui.core.Core;

/** Resolves on the UI thread, captures an immutable state, and never falls back to a different area. */
public final class HistorySurfaceQueries {
    private HistorySurfaceQueries() {}

    public static CommandResult get(String surfaceRef) {
        Core.checkUiThread();
        final UiSurfaceId id;
        try {
            id = UiSurfaceId.parse(surfaceRef);
        } catch (IllegalArgumentException | NullPointerException failure) {
            return CommandResult.failure("SURFACE_REF_INVALID", "无效的界面引用");
        }
        var lease = UiSurfaceRegistry.INSTANCE.lease(id, HistorySurface.class).orElse(null);
        if (lease == null || !lease.visible())
            return CommandResult.failure("HISTORY_UNAVAILABLE", "指定界面不存在、不可见或不支持历史");
        var target = lease.owner().historyTarget();
        if (target == null)
            return CommandResult.failure("HISTORY_UNAVAILABLE", "指定界面没有活动编辑文档");
        return HistoryCommandCatalog.describe(id.ref(), target.historyState());
    }
}
