package com.mine.geometry_node.core.execution.event_handler.dispatcher;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.execution.GraphProcess;
import com.mine.geometry_node.core.execution.RuntimeGraphIndex;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.EntityImmunityAttachment;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

public class EntityDispatcher {

    public static void register() {
        // 实体加入世界加载
        EntityEvent.ADD.register((entity, level) -> {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                GraphEngine.registerEntityListeners(entity);
                GraphEngine.dispatchEvent(serverLevel, entity, OnEntitySpawn.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                    process.setEventData(StandardPorts.XYZ.getId(), entity.position());
                });
            }
            return EventResult.pass();
        });

        // 实体受伤与造成伤害
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (!entity.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) entity.level();
                Entity attacker = source.getEntity();
                Entity directSource = source.getDirectEntity();
                String damageTypeId = source.getMsgId();

                if (EntityImmunityAttachment.hasImmunity(entity, damageTypeId)) {
                    return EventResult.interruptFalse();
                }

                GraphEngine.dispatchEvent(serverLevel, entity, OnEntityHurt.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                    process.setEventData(StandardPorts.VALUE.getId(), amount);
                    process.setEventData(StandardPorts.DAMAGE_TYPE.getId(), damageTypeId);
                    if (attacker != null) process.setEventData(StandardPorts.ATTACK_SOURCE.getId(), attacker);
                    if (directSource != null) process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), directSource);
                });

                if (attacker != null) {
                    GraphEngine.dispatchEvent(serverLevel, attacker, OnEntityDealDamage.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), attacker);
                        process.setEventData(StandardPorts.ENTITY.getId(), entity);
                        process.setEventData(StandardPorts.VALUE.getId(), amount);
                        process.setEventData(StandardPorts.DAMAGE_TYPE.getId(), damageTypeId);
                        if (directSource != null) process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), directSource);
                    });
                }
            }
            return EventResult.pass();
        });

        // 实体死亡
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (!entity.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) entity.level();
                Entity attacker = source.getEntity();
                Entity directSource = source.getDirectEntity();
                String damageTypeId = source.getMsgId();

                GraphEngine.dispatchEvent(serverLevel, entity, OnEntityDeath.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                    process.setEventData(StandardPorts.DAMAGE_TYPE.getId(), damageTypeId);
                    if (attacker != null) process.setEventData(StandardPorts.ATTACK_SOURCE.getId(), attacker);
                    if (directSource != null) process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), directSource);
                });
            }
            return EventResult.pass();
        });

        var bus = NeoForge.EVENT_BUS;

        // 实体 Tick
        bus.addListener((EntityTickEvent.Post event) -> {
            Entity entity = event.getEntity();
            if (entity.level().isClientSide()) return;

            ServerLevel level = (ServerLevel) entity.level();
            EntityGraphAttachment attachment = entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;

            long currentTick = level.getGameTime();
            for (String graphId : attachment.getBoundGraphs()) {
                RuntimeGraphIndex index = GraphEngine.getGraphIndex(graphId);
                if (index == null) continue;

                List<Integer> tickNodes = index.findNodesByType(OnEntityTick.TYPE_ID);
                for (int nodeId : tickNodes) {
                    int interval = Math.max(1, index.getNodeStaticInput(nodeId, StandardPorts.INTERVAL.getId(), Integer.class, 1));
                    int offset = index.getNodeStaticInput(nodeId, StandardPorts.OFFSET.getId(), Integer.class, 0);

                    if (interval == 1 || currentTick % interval == offset) {
                        GraphProcess process = attachment.getProcess(graphId);
                        if (process == null) continue;

                        process.setEnvironment(level, entity);
                        process.executeEvent(nodeId, thread -> thread.setEventData(StandardPorts.ENTITY.getId(), entity));
                    }
                }
            }
        });

        bus.addListener((BabyEntitySpawnEvent event) -> {
            if (event.getParentA() != null && !event.getParentA().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getParentA().level(), event.getParentA(), OnEntityBreed.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getParentA());
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getChild());
                    if (event.getCausedByPlayer() != null) process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getCausedByPlayer());
                });
            }
        });

        bus.addListener((EntityTravelToDimensionEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityChangeDimension.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.DIMENSION.getId(), event.getDimension().location().toString());
                });
            }
        });

        bus.addListener((LivingDropsEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                for (var drop : event.getDrops()) {
                    GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityDropItem.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                        process.setEventData(StandardPorts.ITEM.getId(), drop.getItem());
                    });
                }
            }
        });

        bus.addListener((EntityMobGriefingEvent event) -> {
            if (event.getEntity() != null && !event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityGriefBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((LivingHealEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityHeal.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.VALUE.getId(), event.getAmount());
                });
            }
        });

        bus.addListener((LivingEvent.LivingJumpEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityJump.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((EntityMountEvent event) -> {
            if (!event.getLevel().isClientSide() && event.isMounting()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), event.getEntityMounting(), OnEntityMount.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntityMounting());
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getEntityBeingMounted());
                });
            }
        });

        bus.addListener((AnimalTameEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityTame.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getTamer());
                });
            }
        });

        bus.addListener((EntityTeleportEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityTeleport.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.START_POS.getId(), event.getPrev());
                    process.setEventData(StandardPorts.END_POS.getId(), event.getTarget());
                });
            }
        });

        bus.addListener((LivingChangeTargetEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getNewAboutToBeSetTarget() != null) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnTargetChange.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getNewAboutToBeSetTarget());
                });
            }
        });

        bus.addListener((LivingConversionEvent.Post event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ZombieVillager && event.getOutcome() instanceof Villager) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getOutcome(), OnVillagerCure.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((TradeWithVillagerEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getAbstractVillager(), OnVillagerTrade.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getAbstractVillager());
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile projectile && projectile.getOwner() != null) {
                GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), projectile.getOwner(), OnProjectileShoot.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), projectile.getOwner());
                    process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), projectile);
                });
            }
        });

        bus.addListener((MobEffectEvent.Added event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectApply.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), effectId);
                    process.setEventData(StandardPorts.VALUE.getId(), event.getEffectInstance().getAmplifier());
                });
            }
        });

        bus.addListener((MobEffectEvent.Expired event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectExpire.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), effectId);
                });
            }
        });

        bus.addListener((MobEffectEvent.Remove event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey().map(key -> key.location().toString()).orElse("unknown");
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectRemove.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), effectId);
                });
            }
        });
    }
}