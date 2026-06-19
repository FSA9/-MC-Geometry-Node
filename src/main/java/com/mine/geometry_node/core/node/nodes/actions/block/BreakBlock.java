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
import net.minecraft.world.phys.Vec3;

public class BreakBlock extends BaseNode {

    public static final String TYPE_ID = "break_block";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.break_block"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.BOOL.toInput(true), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 pos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        Boolean dropItems = getInput(context, StandardPorts.BOOL.getId(), Boolean.class);

        if (pos != null && context.getLevel() instanceof ServerLevel level) {
            level.destroyBlock(BlockPos.containing(pos), dropItems != null && dropItems);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
