package com.mine.geometry_node.core.node.nodes.actions.block;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 强制设置方块状态。
 * 对应底层的 setBlock 操作，只同步客户端，不包含音效、游戏事件和邻居更新副作用。
 */
public class SetBlockState extends BaseNode {

    public static final String TYPE_ID = "set_block_state";
    private static final int DIRECT_SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_block_state"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.BLOCK_STATE.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 posVec = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        BlockState state = getInput(context, StandardPorts.BLOCK_STATE.getId(), BlockState.class);

        if (posVec != null && state != null && context.getLevel() instanceof ServerLevel level) {
            BlockPos pos = BlockPos.containing(posVec);
            level.setBlock(pos, state, DIRECT_SET_FLAGS);
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
