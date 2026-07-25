package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import com.mine.geometry_node.core.node.util.ValueMatchUtils;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

public class FindInventorySlots extends BaseNode {
    public static final String TYPE_ID = "find_inventory_slots";
    private static final String INCLUDE_EMPTY = "include_empty";
    private static final SlotRef NO_SLOT = new SlotRef(SlotRef.PLAYER_INVENTORY, "inventory.-1");

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.find_inventory_slots"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.LIST, "list")
                        .output(StandardPorts.SLOT, "slot")
                        .output(StandardPorts.COUNT, "count")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.ITEM_STACK, "item_stack")
                        .input(StandardPorts.TAG, "tag")
                        .input(StandardPorts.SCOPE, "scope")
                        .input(StandardPorts.MATCH_MODE, "match_mode")
                        .input(StandardPorts.LIMIT, "limit")
                        .input(INCLUDE_EMPTY, "include_empty")
                        .build())
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.LIST.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.SLOT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TAG.toInput(""), StandardPorts.BOOL.toOutput(), UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.SCOPE.toInput(SlotAccessUtils.CLEAR_SCOPE_INVENTORY).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, SlotAccessUtils.CLEAR_SCOPE_OPTIONS)
                ))
                .addRow(new PortRow(
                        StandardPorts.MATCH_MODE.toInput(ValueMatchUtils.MODE_COMPONENTS).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.MODE_OPTIONS)
                ))
                .addRow(new PortRow(StandardPorts.LIMIT.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(PortDef.create(INCLUDE_EMPTY, "geometry_node.port.include_empty", PortType.BOOLEAN, false), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        List<SlotRef> slots = findSlots(context);
        if (StandardPorts.LIST.getId().equals(portName)) {
            return slots;
        }
        if (StandardPorts.SLOT.getId().equals(portName)) {
            return slots.isEmpty() ? NO_SLOT : slots.getFirst();
        }
        if (StandardPorts.COUNT.getId().equals(portName)) {
            return slots.size();
        }
        if (StandardPorts.BOOL.getId().equals(portName)) {
            return !slots.isEmpty();
        }
        return null;
    }

    private List<SlotRef> findSlots(ExecutionContext context) {
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack template = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        String scope = getInput(context, StandardPorts.SCOPE.getId(), String.class);
        String matchMode = getInput(context, StandardPorts.MATCH_MODE.getId(), String.class);
        Integer limit = getInput(context, StandardPorts.LIMIT.getId(), Integer.class);
        Boolean includeEmpty = getInput(context, INCLUDE_EMPTY, Boolean.class);

        return SlotAccessUtils.findMatchingSlots(
                entity,
                scope,
                template,
                tag,
                matchMode,
                includeEmpty != null && includeEmpty,
                limit != null ? limit : 0,
                context
        );
    }
}
