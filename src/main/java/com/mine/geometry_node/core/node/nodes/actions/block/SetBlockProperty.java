package com.mine.geometry_node.core.node.nodes.actions.block;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

public class SetBlockProperty extends BaseNode {

    public static final String TYPE_ID = "set_block_property";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_block_property"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.STRING.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 posVec = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        String propertyName = getInput(context, StandardPorts.KEY.getId(), String.class);
        String rawValue = getInput(context, StandardPorts.STRING.getId(), String.class);

        if (posVec != null && propertyName != null && rawValue != null && context.getLevel() instanceof ServerLevel level) {
            BlockPos pos = BlockPos.containing(posVec);
            BlockState state = level.getBlockState(pos);
            Property<?> property = findProperty(state, propertyName);
            if (property != null) {
                BlockState newState = setProperty(state, property, rawValue);
                if (newState != state) {
                    level.setBlock(pos, newState, 3);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }

    private static Property<?> findProperty(BlockState state, String propertyName) {
        String normalizedName = propertyName.trim();
        if (normalizedName.isEmpty()) return null;

        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(normalizedName)) {
                return property;
            }
        }
        return null;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String rawValue) {
        String normalizedValue = rawValue.trim();
        return property.getValue(normalizedValue)
                .map(value -> state.setValue(property, value))
                .orElse(state);
    }
}
