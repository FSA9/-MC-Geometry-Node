package com.mine.geometry_node.client.ui.editor.datalibrary.global;

import com.mine.geometry_node.client.ui.editor.datalibrary.objects.WorldObjectsView;
import com.mine.geometry_node.client.ui.workspace.state.UiStateContext;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import java.util.UUID;

/** Global entities and faction members share the same table and its toolbar commands. */
final class EntityCatalogView extends FrameLayout implements GlobalDataSection {
    private final WorldObjectsView table;
    private boolean shown, disposed;

    EntityCatalogView(Context context, UiStateContext state) { this(context, state, null); }
    EntityCatalogView(Context context, UiStateContext state, UUID faction) {
        super(context);
        table = faction == null ? new WorldObjectsView(context, state, WorldObjectsView.Mode.ENTITIES)
                : new WorldObjectsView(context, state, faction);
        addView(table, new LayoutParams(-1, -1));
    }
    @Override public View view() { return this; }
    @Override public void onShow() {
        if (disposed || shown) return;
        shown = true; table.onShow();
    }
    @Override public void onHide() {
        if (disposed) return;
        shown = false; table.onHide();
    }
    @Override public void onDispose() {
        if (disposed) return;
        onHide(); disposed = true; table.onDispose();
    }
}
