package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.core.node.nodes.events.world.*;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

public class WorldDispatcher {

    public static void register() {
        var bus = NeoForge.EVENT_BUS;

        bus.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                GeometryNodeEvents.dispatch(serverLevel, null, OnChunkLoad.TYPE_ID, EventPayload.of(
                        StandardPorts.XYZ.getId(), event.getChunk().getPos().getMiddleBlockPosition(64),
                        StandardPorts.DIMENSION.getId(), serverLevel.dimension().identifier().toString()
                ));
            }
        });

        bus.addListener((ExplosionEvent.Detonate event) -> {
            if (!event.getLevel().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getLevel();
                GeometryNodeEvents.dispatch(serverLevel, event.getExplosion().getIndirectSourceEntity(), OnExplosion.TYPE_ID, EventPayload.of(
                        StandardPorts.XYZ.getId(), event.getExplosion().center(),
                        StandardPorts.FLOAT_VALUE.getId(), event.getExplosion().radius(),
                        StandardPorts.ENTITY.getId(), event.getExplosion().getIndirectSourceEntity()
                ));
            }
        });

        bus.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.LightningBolt lightning) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getLevel(), null, OnLightningStrike.TYPE_ID, EventPayload.of(
                        StandardPorts.XYZ.getId(), lightning.position()
                ));
            }
        });

        bus.addListener((BlockEvent.PortalSpawnEvent event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                GeometryNodeEvents.dispatch(serverLevel, null, OnPortalCreate.TYPE_ID, EventPayload.of(
                        StandardPorts.XYZ.getId(), event.getPos(),
                        StandardPorts.DIMENSION.getId(), serverLevel.dimension().identifier().toString()
                ));
            }
        });
    }
}
