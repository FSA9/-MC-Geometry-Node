package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public class BlockDispatcher {

    public static void register() {
        // 方块破坏
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            if (!level.isClientSide()) {
                String dimensionId = level.dimension().identifier().toString();

                BlueprintRuntime.INSTANCE.dispatchEvent((ServerLevel) level, player, OnBlockBreak.TYPE_ID, GraphEventData.of(
                        StandardPorts.XYZ.getId(), pos,
                        StandardPorts.BLOCK_STATE.getId(), state,
                        GraphEventFields.BLOCK_TYPE, blockTypeId(state),
                        StandardPorts.DIMENSION.getId(), dimensionId,
                        StandardPorts.ENTITY.getId(), player
                ));
            }
            return EventResult.pass();
        });

        // 方块放置
        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            if (!level.isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) level;
                String dimensionId = level.dimension().identifier().toString();

                if (entity != null) {
                    BlueprintRuntime.INSTANCE.dispatchEvent(serverLevel, entity, OnBlockPlace.TYPE_ID, GraphEventData.of(
                            StandardPorts.XYZ.getId(), pos,
                            StandardPorts.BLOCK_STATE.getId(), state,
                            GraphEventFields.BLOCK_TYPE, blockTypeId(state),
                            StandardPorts.DIMENSION.getId(), dimensionId,
                            StandardPorts.ENTITY.getId(), entity
                    ));
                }

                Set<String> interestedIds = BlueprintRuntime.INSTANCE.getInterestedMultiblockStructureIds(serverLevel, entity);
                if (!interestedIds.isEmpty()) {
                    for (MultiblockStructureManager.Match match : MultiblockStructureManager.getInstance().findMatches(serverLevel, pos, state, interestedIds)) {
                        BlueprintRuntime.INSTANCE.dispatchMultiblockBuilt(serverLevel, entity, match.structureId(), GraphEventData.of(
                                StandardPorts.NAME.getId(), match.structureId(),
                                StandardPorts.XYZ.getId(), match.origin(),
                                StandardPorts.BLOCK_STATE.getId(), state,
                                StandardPorts.DIMENSION.getId(), dimensionId,
                                StandardPorts.ENTITY.getId(), entity
                        ));
                    }
                }
            }
            return EventResult.pass();
        });
    }

    private static String blockTypeId(BlockState state) {
        if (state == null) {
            return "";
        }
        Object id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null ? id.toString() : "";
    }
}
