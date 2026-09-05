package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

abstract class AbstractSetEntityIntegerProperty extends EntityPassthroughActionNode {

    @FunctionalInterface
    protected interface IntegerEntitySetter {
        void set(Entity entity, int value);
    }

    private final String typeId;
    private final int defaultValue;
    private final Integer minValue;
    private final IntegerEntitySetter setter;

    protected AbstractSetEntityIntegerProperty(String typeId, int defaultValue, Integer minValue, IntegerEntitySetter setter) {
        this.typeId = typeId;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.setter = setter;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(typeId, NodeType.ACTION, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.TICK.toInput(defaultValue), UIHint.INPUT, null, minValue != null ? Map.of(PortMetaKeys.NUMERIC_MIN, minValue) : null)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputs(context, StandardPorts.ENTITY.getId(), Entity.class);
        Integer value = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        if (value != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity == null) continue;
                setter.set(entity, value);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
