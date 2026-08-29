package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.widget.FrameLayout;

final class ColorInputView extends FrameLayout {
    private final NodeData mNodeData;
    private final PortDef mPort;
    private final EditorContext mEditorContext;
    private final ColorSwatchView mSwatch;

    ColorInputView(Context context, NodeData nodeData, PortDef port, EditorContext editorContext) {
        super(context);
        mNodeData = nodeData;
        mPort = port;
        mEditorContext = editorContext;

        setWillNotDraw(true);
        setClipChildren(false);

        mSwatch = new ColorSwatchView(context, this::currentColor, next -> {
            UIHintValueBinder.commit(mEditorContext, mNodeData, mPort.id(), next);
        });
        addView(mSwatch, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private ColorValue currentColor() {
        ColorValue color = ColorValue.from(UIHintValueBinder.getValue(mNodeData, mPort));
        return color != null ? color : ColorValue.WHITE;
    }
}
