package com.mine.geometry_node.client.ui.shell.layer;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

/** Full-screen host whose children are exclusively owned by MainUiLayerManager. */
public final class MainUiLayerHost extends FrameLayout {
    public MainUiLayerHost(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setVisibility(View.GONE);
    }
}
