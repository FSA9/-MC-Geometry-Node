package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
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

import java.util.List;
import java.util.Map;

public class RemoveItemsFromInventory extends BaseNode {
    public static final String TYPE_ID = "remove_items_from_inventory";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.remove_items_from_inventory"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.REMOVED_COUNT, "removed_count")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.COUNT, "count")
                        .input(StandardPorts.TAG, "tag")
                        .input(StandardPorts.MATCH_MODE, "match_mode")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.REMOVED_COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.COUNT.toInput(1), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.TAG.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.MATCH_MODE.toInput(ValueMatchUtils.MODE_COMPONENTS).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.MODE_OPTIONS))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack template = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        Integer count = getInput(context, StandardPorts.COUNT.getId(), Integer.class);
        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        String matchMode = getInput(context, StandardPorts.MATCH_MODE.getId(), String.class);
        int removed = 0;
        for (Entity entity : entities) {
            removed += SlotAccessUtils.removeMatching(entity, template, tag, count != null ? count : 1, matchMode, context);
        }
        context.setNodeResult(StandardPorts.REMOVED_COUNT.getId(), removed);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.REMOVED_COUNT.getId().equals(portName)) {
            Object value = context.getNodeResult(StandardPorts.REMOVED_COUNT.getId());
            return value instanceof Number number ? number.intValue() : 0;
        }
        return null;
    }
}
