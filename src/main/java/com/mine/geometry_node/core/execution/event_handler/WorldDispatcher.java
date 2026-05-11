package com.mine.geometry_node.core.execution.event_handler;

import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.node.nodes.events.world.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
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
                GraphEngine.dispatchEvent(serverLevel, null, OnChunkLoad.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), event.getChunk().getPos().getMiddleBlockPosition(64));
                    process.setEventData(StandardPorts.DIMENSION.getId(), serverLevel.dimension().location().toString());
                });
            }
        });

        bus.addListener((ExplosionEvent.Detonate event) -> {
            if (!event.getLevel().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getLevel();
                GraphEngine.dispatchEvent(serverLevel, event.getExplosion().getIndirectSourceEntity(), OnExplosion.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), event.getExplosion().center());
                    process.setEventData(StandardPorts.VALUE.getId(), event.getExplosion().radius());
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getExplosion().getIndirectSourceEntity());
                });
            }
        });

        bus.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.LightningBolt lightning) {
                GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), null, OnLightningStrike.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), lightning.position());
                });
            }
        });

        bus.addListener((BlockEvent.PortalSpawnEvent event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                GraphEngine.dispatchEvent(serverLevel, null, OnPortalCreate.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), event.getPos());
                    process.setEventData(StandardPorts.DIMENSION.getId(), serverLevel.dimension().location().toString());
                });
            }
        });
    }
}