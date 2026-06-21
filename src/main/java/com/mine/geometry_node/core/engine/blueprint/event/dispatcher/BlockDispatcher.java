package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

public class BlockDispatcher {

    public static void register() {
        // 方块破坏
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            if (!level.isClientSide()) {
                String dimensionId = level.dimension().identifier().toString();

                GraphEngine.dispatchEvent((ServerLevel) level, player, OnBlockBreak.TYPE_ID, GraphEventData.of(
                        StandardPorts.XYZ.getId(), pos,
                        StandardPorts.BLOCK_STATE.getId(), state,
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
                    GraphEngine.dispatchEvent(serverLevel, entity, OnBlockPlace.TYPE_ID, GraphEventData.of(
                            StandardPorts.XYZ.getId(), pos,
                            StandardPorts.BLOCK_STATE.getId(), state,
                            StandardPorts.DIMENSION.getId(), dimensionId,
                            StandardPorts.ENTITY.getId(), entity
                    ));
                }

                Set<String> interestedIds = GraphEngine.getInterestedMultiblockStructureIds(serverLevel, entity);
                if (!interestedIds.isEmpty()) {
                    for (MultiblockStructureManager.Match match : MultiblockStructureManager.getInstance().findMatches(serverLevel, pos, state, interestedIds)) {
                        GraphEngine.dispatchMultiblockBuilt(serverLevel, entity, match.structureId(), GraphEventData.of(
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
}
