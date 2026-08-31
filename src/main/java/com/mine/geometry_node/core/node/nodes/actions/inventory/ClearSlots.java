package com.mine.geometry_node.core.node.nodes.actions.inventory;

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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class ClearSlots extends BaseNode {
    public static final String TYPE_ID = "clear_slots";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_slots"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.REMOVED_COUNT, "removed_count")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .input(StandardPorts.ENTITY, "entity")
                        .input(StandardPorts.SCOPE, "scope")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.REMOVED_COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        StandardPorts.SCOPE.toInput(SlotAccessUtils.CLEAR_SCOPE_ALL).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, SlotAccessUtils.CLEAR_SCOPE_OPTIONS)
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        String scope = getInput(context, StandardPorts.SCOPE.getId(), String.class);
        int removed = 0;
        for (Entity entity : entities) {
            removed += SlotAccessUtils.clearSlots(entity, scope);
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
