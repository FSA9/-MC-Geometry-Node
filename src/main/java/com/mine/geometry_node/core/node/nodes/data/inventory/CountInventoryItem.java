package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import com.mine.geometry_node.core.node.util.ValueMatchUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class CountInventoryItem extends BaseNode {
    public static final String TYPE_ID = "count_inventory_item";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.count_inventory_item"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.COUNT, "count")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.TAG, "tag")
                        .input(StandardPorts.MATCH_MODE, "match_mode")
                        .build())
                .addRow(new PortRow(null, StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT, null, null)
                .addPassthroughInput(StandardPorts.TAG.toInput(""), UIHint.INPUT, null, null)
                .addPassthroughInput(StandardPorts.MATCH_MODE.toInput(ValueMatchUtils.MODE_COMPONENTS).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.MODE_OPTIONS))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack template = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        String matchMode = getInput(context, StandardPorts.MATCH_MODE.getId(), String.class);
        int count = SlotAccessUtils.countMatching(entity, template, tag, matchMode, context);

        if (StandardPorts.COUNT.getId().equals(portName)) {
            return count;
        }
        if (StandardPorts.BOOL.getId().equals(portName)) {
            return count > 0;
        }
        return null;
    }
}
