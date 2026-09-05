package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

public class IgniteEntity extends BaseNode {

    public static final String TYPE_ID = "ignite_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.ignite_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.TICK.toInput(), UIHint.INPUT, null, Map.of(PortMetaKeys.NUMERIC_MIN, 0))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputs(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer ticks = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (ticks != null && ticks > 0 && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity == null) continue;
                entity.igniteForTicks(ticks);
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
