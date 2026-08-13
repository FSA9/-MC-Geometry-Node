package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.TextView;

/** MODEL-specific availability view. World rendering remains an explicit M5 preview action. */
final class ModelAssetPreviewView extends TextView {
    ModelAssetPreviewView(Context context) {
        super(context);
        setText("GLB");
        setTextSize(10.0F);
        setTextColor(0xFFF4C28A);
        setGravity(Gravity.CENTER);
        setPadding(UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(1), UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(1));
    }
}
