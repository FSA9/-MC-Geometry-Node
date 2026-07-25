package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
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
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.REMOVED_COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.COUNT.toInput(1), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TAG.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.MATCH_MODE.toInput(ValueMatchUtils.MODE_COMPONENTS).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.MODE_OPTIONS)
                ))
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
        context.setTempData(StandardPorts.REMOVED_COUNT.getId(), removed);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.REMOVED_COUNT.getId().equals(portName)) {
            Object value = context.getTempData(StandardPorts.REMOVED_COUNT.getId());
            return value instanceof Number number ? number.intValue() : 0;
        }
        return null;
    }
}
