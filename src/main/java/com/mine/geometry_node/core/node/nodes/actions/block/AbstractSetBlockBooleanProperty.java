package com.mine.geometry_node.core.node.nodes.actions.block;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;


abstract class AbstractSetBlockBooleanProperty extends BaseNode {

    private final String typeId;
    private final String portId;
    private final BooleanProperty property;
    private final boolean defaultValue;

    protected AbstractSetBlockBooleanProperty(String typeId, String portId, BooleanProperty property, boolean defaultValue) {
        this.typeId = typeId;
        this.portId = portId;
        this.property = property;
        this.defaultValue = defaultValue;
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(typeId, NodeType.ACTION, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BLOCK_STATE.toInput(), StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(PortDef.create(portId, "geometry_node.port." + portId, PortType.BOOLEAN, defaultValue), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BLOCK_STATE.getId().equals(portName)) {
            return null;
        }

        BlockState state = getInput(context, StandardPorts.BLOCK_STATE.getId(), BlockState.class);
        Boolean value = getInput(context, portId, Boolean.class);
        if (state == null || value == null) {
            return state;
        }

        return BlockPropertySetter.set(state, property, value);
    }
}
