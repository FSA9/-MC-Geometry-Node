package com.mine.geometry_node.core.node.nodes.actions.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class ClearSlots extends BaseNode {
    public static final String TYPE_ID = "clear_slots";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                按范围清空目标实体或容器的槽位。
                inventory 清空主要物品存储；equipment 清空装备槽；all 同时清空两者。
                输出 removed_count 为实际清除的物品总数量。""";

        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_slots"))
                .comment(comment)
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
