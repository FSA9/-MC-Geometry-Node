package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

abstract class AbstractSetEntityBooleanProperty extends EntityPassthroughActionNode {

    @FunctionalInterface
    protected interface BooleanEntitySetter {
        void set(Entity entity, boolean value);
    }

    private final String typeId;
    private final boolean defaultValue;
    private final BooleanEntitySetter setter;

    protected AbstractSetEntityBooleanProperty(String typeId, boolean defaultValue, BooleanEntitySetter setter) {
        this.typeId = typeId;
        this.defaultValue = defaultValue;
        this.setter = setter;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(typeId, NodeType.ACTION, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BOOL.toInput(defaultValue), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Boolean value = getInput(context, StandardPorts.BOOL.getId(), Boolean.class);

        if (value != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                setter.set(entity, value);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
