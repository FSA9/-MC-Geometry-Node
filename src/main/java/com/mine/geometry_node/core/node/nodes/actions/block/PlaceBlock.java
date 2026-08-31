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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Sets one block state and emits placement-style sound/game events.
 * This is not the full player item placement flow.
 */
public class PlaceBlock extends BaseNode {

    public static final String TYPE_ID = "place_block";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.place_block"))
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

            boolean success = level.setBlock(pos, state, Block.UPDATE_ALL);

            if (success) {
                SoundType soundType = state.getSoundType();
                level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

                level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(null, state));
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
