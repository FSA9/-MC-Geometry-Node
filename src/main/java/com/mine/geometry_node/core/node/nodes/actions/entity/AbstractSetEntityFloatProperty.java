package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

abstract class AbstractSetEntityFloatProperty extends EntityPassthroughActionNode {

    @FunctionalInterface
    protected interface FloatEntitySetter {
        void set(Entity entity, float value);
    }

    private final String typeId;
    private final Float defaultValue;
    private final Float minValue;
    private final FloatEntitySetter setter;

    protected AbstractSetEntityFloatProperty(String typeId, FloatEntitySetter setter) {
        this(typeId, null, null, setter);
    }

    protected AbstractSetEntityFloatProperty(String typeId, float defaultValue, FloatEntitySetter setter) {
        this(typeId, defaultValue, null, setter);
    }

    protected AbstractSetEntityFloatProperty(String typeId, float defaultValue, float minValue, FloatEntitySetter setter) {
        this(typeId, Float.valueOf(defaultValue), Float.valueOf(minValue), setter);
    }

    private AbstractSetEntityFloatProperty(String typeId, Float defaultValue, Float minValue, FloatEntitySetter setter) {
        this.typeId = typeId;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.setter = setter;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        PortDef valuePort = defaultValue != null
                ? StandardPorts.FLOAT_VALUE.toInput(defaultValue)
                : StandardPorts.FLOAT_VALUE.toInput();
        Map<com.mine.geometry_node.core.node.meta.MetaKey<?>, Object> params = minValue != null
                ? Map.of(PortMetaKeys.NUMERIC_MIN, minValue)
                : null;

        return NodeDef.builder(typeId, NodeType.ACTION, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(valuePort, UIHint.INPUT, null, params)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputs(context, StandardPorts.ENTITY.getId(), Entity.class);
        Float value = getInput(context, StandardPorts.FLOAT_VALUE.getId(), Float.class);

        if (value != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                if (entity == null) continue;
                setter.set(entity, value);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
