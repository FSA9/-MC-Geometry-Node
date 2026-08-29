package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintEngine;
import com.mine.geometry_node.core.engine.blueprint.event.PlayerInputStateManager;
import com.mine.geometry_node.core.node.nodes.events.display_entity.OnInteraction;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.nodes.events.inventory.OnContainerOpen;
import com.mine.geometry_node.core.node.nodes.events.player.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.*;

public class PlayerDispatcher {

    public static void register() {
        // 实体交互方块
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                BlueprintEngine.dispatchEvent(serverLevel, player, EntityInteractBlock.TYPE_ID, GraphEventData.of(
                        StandardPorts.TRIGGER_ENTITY.getId(), player,
                        StandardPorts.XYZ.getId(), pos,
                        StandardPorts.BLOCK_STATE.getId(), serverLevel.getBlockState(pos)
                ));
            }
            return EventResult.pass().asMinecraft();
        });

        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                BlueprintEngine.dispatchEvent(serverLevel, player, OnPlayerLeftClickBlock.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), player,
                        StandardPorts.XYZ.getId(), pos,
                        StandardPorts.BLOCK_STATE.getId(), serverLevel.getBlockState(pos),
                        StandardPorts.DIMENSION.getId(), serverLevel.dimension().identifier().toString()
                ));
            }
            return EventResult.pass().asMinecraft();
        });

        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!player.level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) player.level(), player, EntityInteractEntity.TYPE_ID, GraphEventData.of(
                        StandardPorts.TRIGGER_ENTITY.getId(), player,
                        StandardPorts.ENTITY.getId(), entity
                ));
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (!player.level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) player.level(), player, EntityUseItem.TYPE_ID, GraphEventData.of(
                        StandardPorts.TRIGGER_ENTITY.getId(), player,
                        StandardPorts.ITEM.getId(), player.getItemInHand(hand)
                ));
            }
            return EventResult.pass().asMinecraft();
        });

        dev.architectury.event.events.common.PlayerEvent.OPEN_MENU.register((player, menu) -> {
            if (!player.level().isClientSide()) {
                String containerType = menuTypeId(menu);
                BlueprintEngine.dispatchEvent((ServerLevel) player.level(), player, OnContainerOpen.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), player,
                        StandardPorts.PLAYER.getId(), player,
                        StandardPorts.TYPE.getId(), containerType,
                        GraphEventFields.CONTAINER_TYPE, containerType,
                        StandardPorts.COUNT.getId(), slotCount(menu)
                ));
            }
        });

        var bus = NeoForge.EVENT_BUS;

        bus.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> {
            if (!event.getEntity().level().isClientSide() && event.getTarget() instanceof Interaction interaction) {
                Vec3 hitPos = interaction.position().add(event.getLocalPos());
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnInteraction.TYPE_ID, GraphEventData.of(
                        StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity(),
                        StandardPorts.ENTITY.getId(), interaction,
                        StandardPorts.TYPE.getId(), "interact",
                        StandardPorts.XYZ.getId(), hitPos
                ));
            }
        });

        bus.addListener((AttackEntityEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getTarget() instanceof Interaction interaction) {
                Vec3 hitPos = interaction.position().add(0, interaction.getBbHeight() / 2.0, 0);
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnInteraction.TYPE_ID, GraphEventData.of(
                        StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity(),
                        StandardPorts.ENTITY.getId(), interaction,
                        StandardPorts.TYPE.getId(), "attack",
                        StandardPorts.XYZ.getId(), hitPos
                ));
            }
        });

        bus.addListener((ItemTossEvent event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnEntityDropItem.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getPlayer(),
                        StandardPorts.ITEM.getId(), event.getEntity().getItem()
                ));
            }
        });

        dev.architectury.event.events.common.PlayerEvent.PICKUP_ITEM_POST.register((player, itemEntity, pickedStack) -> {
            if (player.level().isClientSide()) {
                return;
            }
            BlueprintEngine.dispatchEvent((ServerLevel) player.level(), player, OnEntityPickupItem.TYPE_ID, GraphEventData.of(
                    StandardPorts.ENTITY.getId(), player,
                    StandardPorts.SOURCE_ENTITY.getId(), itemEntity,
                    StandardPorts.ITEM_STACK.getId(), pickedStack.copy()
            ));
        });

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerJoin.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                PlayerInputStateManager.clearPlayer(event.getEntity().getUUID());
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerQuit.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((ServerChatEvent event) -> {
            if (event.getPlayer() != null && !event.getPlayer().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnPlayerChat.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getPlayer(),
                        StandardPorts.MESSAGE.getId(), event.getMessage().getString()
                ));
            }
        });

        bus.addListener((PlayerEvent.PlayerChangeGameModeEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerChangeGameMode.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.TYPE.getId(), event.getNewGameMode().getName()
                ));
            }
        });

        bus.addListener((AdvancementEvent.AdvancementEarnEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerEarnAdvancement.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.NAME.getId(), event.getAdvancement().id().toString()
                ));
            }
        });

        bus.addListener((CommandEvent event) -> {
            Entity sourceEntity = event.getParseResults().getContext().getSource().getEntity();
            if (sourceEntity instanceof Player player && !player.level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) player.level(), player, OnPlayerExecuteCommand.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), player,
                        StandardPorts.MESSAGE.getId(), event.getParseResults().getReader().getString()
                ));
            }
        });

        bus.addListener((PlayerXpEvent.LevelChange event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerLevelChange.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.INT_VALUE.getId(), event.getLevels()
                ));
            }
        });

        bus.addListener((PlayerXpEvent.PickupXp event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerPickupXp.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.INT_VALUE.getId(), event.getOrb().getValue(),
                        StandardPorts.SOURCE_ENTITY.getId(), event.getOrb()
                ));
            }
        });

        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerRespawn.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });

        bus.addListener((CanPlayerSleepEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerSleep.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity(),
                        StandardPorts.XYZ.getId(), event.getPos()
                ));
            }
        });

        bus.addListener((PlayerWakeUpEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                BlueprintEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerWakeUp.TYPE_ID, GraphEventData.of(
                        StandardPorts.ENTITY.getId(), event.getEntity()
                ));
            }
        });
    }

    private static String menuTypeId(AbstractContainerMenu menu) {
        if (menu == null || menu.getType() == null) {
            return "";
        }
        Object id = BuiltInRegistries.MENU.getKey(menu.getType());
        return id != null ? id.toString() : "";
    }

    private static int slotCount(AbstractContainerMenu menu) {
        return menu != null ? menu.slots.size() : 0;
    }
}
