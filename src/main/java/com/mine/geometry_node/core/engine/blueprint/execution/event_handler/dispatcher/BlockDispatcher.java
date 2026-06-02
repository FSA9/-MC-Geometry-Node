package com.mine.geometry_node.core.engine.blueprint.execution.event_handler.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.execution.GraphEngine;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import net.minecraft.server.level.ServerLevel;

public class BlockDispatcher {

    public static void register() {
        // 方块破坏
        BlockEvent.BREAK.register((level, pos, state, player, xp) -> {
            if (!level.isClientSide()) {
                String dimensionId = level.dimension().location().toString();

                GraphEngine.dispatchEvent((ServerLevel) level, player, OnBlockBreak.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), state);
                    process.setEventData(StandardPorts.DIMENSION.getId(), dimensionId);
                    process.setEventData(StandardPorts.ENTITY.getId(), player);
                });
            }
            return EventResult.pass();
        });

        // 方块放置
        BlockEvent.PLACE.register((level, pos, state, entity) -> {
            if (!level.isClientSide() && entity != null) {
                String dimensionId = level.dimension().location().toString();

                GraphEngine.dispatchEvent((ServerLevel) level, entity, OnBlockPlace.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), state);
                    process.setEventData(StandardPorts.DIMENSION.getId(), dimensionId);
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                });
            }
            return EventResult.pass();
        });
    }
}