package com.mine.geometry_node.core.node.nodes.actions.block;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;

abstract class AbstractSetBlockEnumProperty extends BaseNode {

    private final String typeId;
    private final String portId;
    private final String propertyName;
    private final String defaultValue;
    private final String[] options;

    protected AbstractSetBlockEnumProperty(String typeId, String portId, String propertyName, String defaultValue, String[] options) {
        this.typeId = typeId;
        this.portId = portId;
        this.propertyName = propertyName;
        this.defaultValue = defaultValue;
        this.options = options;
    }

    protected <T extends Comparable<T>> AbstractSetBlockEnumProperty(String typeId, String portId, Property<T> property, String defaultValue) {
        this(typeId, portId, property.getName(), defaultValue, optionNames(property));
    }

    private static <T extends Comparable<T>> String[] optionNames(Property<T> property) {
        return property.getPossibleValues().stream()
                .map(value -> property.getName(value))
                .toArray(String[]::new);
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(typeId, NodeType.ACTION, Component.translatable("geometry_node.node." + typeId))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BLOCK_STATE.toInput(), StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(portId, "geometry_node.port." + portId, PortType.STRING, defaultValue).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, options)
                ))
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
        String value = getInput(context, portId, String.class);
        if (state == null || value == null) {
            return state;
        }

        return BlockPropertySetter.setFromString(state, propertyName, value);
    }
}
