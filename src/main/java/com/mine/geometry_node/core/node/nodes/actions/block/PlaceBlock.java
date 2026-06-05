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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 模拟玩家放置方块。
 * 除了设置方块外，还会播放方块的放置音效并触发相应的游戏事件（如振动）。
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

            // 1. 设置方块
            boolean success = level.setBlock(pos, state, 3);

            if (success) {
                // 2. 模拟放置效果：获取并播放方块自带的放置声音
                SoundType soundType = state.getSoundType();
                level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);

                // 3. 触发游戏事件（例如让幽匿感应体感知到“放置方块”）
                level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(null, state));
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}