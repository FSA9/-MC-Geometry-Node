package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.network.chat.Component;

public class GetSlot extends BaseNode {
    public static final String TYPE_ID = "get_slot";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_slot"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.SLOT, "slot_out")
                        .input(StandardPorts.SLOT, "slot_in")
                        .build())
                .addPassthroughInput(StandardPorts.SLOT.toInput(SlotRef.DEFAULT.serialize()).hiddenPin(),
                        UIHint.SLOT_REF, null, null)
                .build();
    }
}
