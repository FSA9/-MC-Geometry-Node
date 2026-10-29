package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;


import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintUtils;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.InlineActionButton;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.client.integration.picker.VanillaInventoryPicker;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.value.SlotRef;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;

public class SlotRefHintRenderer implements UIHintRenderer {
    @Override
    public float getRequiredExtraRows(PortRow row) {
        return 1.0f;
    }

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String portId = row.leftPort().id();
        Object value = UIHintValueBinder.getValue(nodeData, row.leftPort());
        SlotRef slotRef = SlotRef.from(value);
        InlineActionButton button = new InlineActionButton(context,
                (slotRef != null ? slotRef : SlotRef.DEFAULT).displayName());
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        button.setOnClickListener(v -> VanillaInventoryPicker.openSlotRef(selected -> {
            button.setText(selected.displayName());
            UIHintValueBinder.commit(editorContext, nodeData, portId, selected.serialize());
        }, button::requestFocus));
        return button;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        UIHintUtils.layoutExtraRowInput(view, row, currentY, nodeWidth);
    }
}
