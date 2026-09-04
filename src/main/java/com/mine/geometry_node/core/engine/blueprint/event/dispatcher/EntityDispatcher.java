package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.attachment.EntityImmunityAttachment;
import com.mine.geometry_node.core.node.nodes.events.area.OnAreaEvent;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.nodes.events.projectile.OnProjectileShoot;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class EntityDispatcher {

    public static void register() {
        // 实体加入世界加载
        EntityEvent.ADD.register((entity, level) -> {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                BlueprintRuntime.INSTANCE.registerEntityListeners(entity);
                GeometryNodeEvents.dispatch(serverLevel, entity, OnEntitySpawn.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), entity,
                        StandardPorts.XYZ.getId(), entity.position()
                ));
            }
            return EventResult.pass();
        });

        // 实体受伤与造成伤害
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (!entity.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) entity.level();
                Entity attacker = source.getEntity();
                Entity directSource = source.getDirectEntity();
                String damageTypeId = EntityImmunityAttachment.damageTypeId(source);

                if (EntityImmunityAttachment.hasImmunity(entity, damageTypeId)) {
                    return EventResult.interruptFalse();
                }

                GeometryNodeEvents.dispatch(serverLevel, entity, OnEntityHurt.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), entity,
                        StandardPorts.FLOAT_VALUE.getId(), amount,
                        StandardPorts.DAMAGE_TYPE.getId(), damageTypeId,
                        StandardPorts.ATTACK_SOURCE.getId(), attacker,
                        StandardPorts.DIRECT_SOURCE.getId(), directSource
                ));

                if (attacker != null) {
                    GeometryNodeEvents.dispatch(serverLevel, attacker, OnEntityDealDamage.TYPE_ID, EventPayload.of(
                            StandardPorts.TRIGGER_ENTITY.getId(), attacker,
                            StandardPorts.ENTITY.getId(), entity,
                            StandardPorts.FLOAT_VALUE.getId(), amount,
                            StandardPorts.DAMAGE_TYPE.getId(), damageTypeId,
                            StandardPorts.DIRECT_SOURCE.getId(), directSource
                    ));
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
                String damageTypeId = EntityImmunityAttachment.damageTypeId(source);

                GeometryNodeEvents.dispatch(serverLevel, entity, OnEntityDeath.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), entity,
                        StandardPorts.DAMAGE_TYPE.getId(), damageTypeId,
                        StandardPorts.ATTACK_SOURCE.getId(), attacker,
                        StandardPorts.DIRECT_SOURCE.getId(), directSource
                ));

                if (attacker != null) {
                    GeometryNodeEvents.dispatch(serverLevel, attacker, OnEntityKill.TYPE_ID, EventPayload.of(
                            StandardPorts.ENTITY.getId(), entity,
                            StandardPorts.DAMAGE_TYPE.getId(), damageTypeId,
                            StandardPorts.ATTACK_SOURCE.getId(), attacker,
                            StandardPorts.DIRECT_SOURCE.getId(), directSource
                    ));
                }
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
            if (attachment == null || attachment.getBoundGraphs().isEmpty()) {
                return;
            }
            long currentTick = level.getGameTime();
            if (BlueprintRuntime.INSTANCE.hasEntityEventSubscription(entity, OnAreaEvent.TYPE_ID)) {
                BlueprintRuntime.INSTANCE.tickEntityAreas(level, entity, attachment, currentTick);
            }
            if (BlueprintRuntime.INSTANCE.hasEntityEventSubscription(entity, OnEntityTick.TYPE_ID)) {
                BlueprintRuntime.INSTANCE.dispatchBoundEntityEvent(level, entity, OnEntityTick.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), entity
                ).values());
            }
            if (BlueprintRuntime.INSTANCE.hasEntityEventSubscription(entity, OnEntityGainItem.TYPE_ID)) {
                BlueprintRuntime.INSTANCE.tickEntityInventory(level, entity, true);
            }
        });

        bus.addListener((BabyEntitySpawnEvent event) -> {
            if (event.getParentA() != null && !event.getParentA().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getParentA().level(), event.getParentA(), OnEntityBreed.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getParentA(),
                        StandardPorts.SOURCE_ENTITY.getId(), event.getChild(),
                        StandardPorts.TRIGGER_ENTITY.getId(), event.getCausedByPlayer()
                ));
            }
        });

        bus.addListener((EntityTravelToDimensionEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityChangeDimension.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.DIMENSION.getId(), event.getDimension().identifier().toString()
                ));
            }
        });

        bus.addListener((LivingDropsEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                for (var drop : event.getDrops()) {
                    GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityDropItem.TYPE_ID, EventPayload.of(
                            StandardPorts.ENTITY.getId(), event.getEntity(),
                            StandardPorts.ITEM.getId(), drop.getItem()
                    ));
                }
            }
        });

        bus.addListener((EntityMobGriefingEvent event) -> {
            if (event.getEntity() != null && !event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityGriefBlock.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((LivingHealEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityHeal.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.FLOAT_VALUE.getId(), event.getAmount()
                ));
            }
        });

        bus.addListener((LivingEvent.LivingJumpEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityJump.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((EntityMountEvent event) -> {
            if (!event.getLevel().isClientSide() && event.isMounting()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getLevel(), event.getEntityMounting(), OnEntityMount.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntityMounting(),
                        StandardPorts.SOURCE_ENTITY.getId(), event.getEntityBeingMounted()
                ));
            }
        });

        bus.addListener((AnimalTameEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityTame.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.TRIGGER_ENTITY.getId(), event.getTamer()
                ));
            }
        });

        bus.addListener((EntityTeleportEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityTeleport.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.START_POS.getId(), event.getPrev(),
                        StandardPorts.END_POS.getId(), event.getTarget()
                ));
            }
        });

        bus.addListener((LivingChangeTargetEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getNewAboutToBeSetTarget() != null) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnTargetChange.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.TRIGGER_ENTITY.getId(), event.getNewAboutToBeSetTarget()
                ));
            }
        });

        bus.addListener((LivingConversionEvent.Post event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ZombieVillager && event.getOutcome() instanceof Villager) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getOutcome(), OnVillagerCure.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((TradeWithVillagerEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getAbstractVillager(), OnVillagerTrade.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getAbstractVillager(),
                        StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile projectile) {
                Entity owner = projectile.getOwner();
                Entity dispatchTarget = owner != null ? owner : projectile;
                ItemStack weapon = projectile instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                        ? arrow.getWeaponItem().copy()
                        : ItemStack.EMPTY;
                GeometryNodeEvents.dispatch((ServerLevel) event.getLevel(), dispatchTarget, OnProjectileShoot.TYPE_ID, EventPayload.of(
                        StandardPorts.PROJECTILE.getId(), projectile,
                        StandardPorts.SOURCE_ENTITY.getId(), owner,
                        StandardPorts.ITEM_STACK.getId(), weapon,
                        StandardPorts.XYZ.getId(), projectile.position(),
                        StandardPorts.VECTOR.getId(), projectile.getDeltaMovement()
                ));
            }
        });

        bus.addListener((MobEffectEvent.Added event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectApply.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.TYPE.getId(), effectId,
                        StandardPorts.INT_VALUE.getId(), event.getEffectInstance().getAmplifier()
                ));
            }
        });

        bus.addListener((MobEffectEvent.Expired event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectExpire.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.TYPE.getId(), effectId
                ));
            }
        });

        bus.addListener((MobEffectEvent.Remove event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
                GeometryNodeEvents.dispatch((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectRemove.TYPE_ID, EventPayload.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.TYPE.getId(), effectId
                ));
            }
        });
    }
}
