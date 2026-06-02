package com.mine.geometry_node.core.engine.blueprint.execution.event_handler.dispatcher;

import com.mine.geometry_node.core.engine.blueprint.execution.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.execution.state.PlayerInputStateManager;
import com.mine.geometry_node.core.node.nodes.events.display_entity.OnInteraction;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.nodes.events.player.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.InteractionEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
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
                GraphEngine.dispatchEvent(serverLevel, player, EntityInteractBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), serverLevel.getBlockState(pos));
                });
            }
            return EventResult.pass();
        });

        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                GraphEngine.dispatchEvent(serverLevel, player, OnPlayerLeftClickBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), player);
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), serverLevel.getBlockState(pos));
                    process.setEventData(StandardPorts.DIMENSION.getId(), serverLevel.dimension().location().toString());
                });
            }
            return EventResult.pass();
        });

        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!player.level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) player.level(), player, EntityInteractEntity.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                });
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (!player.level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) player.level(), player, EntityUseItem.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.ITEM.getId(), player.getItemInHand(hand));
                });
            }
            return CompoundEventResult.pass();
        });

        var bus = NeoForge.EVENT_BUS;

        bus.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> {
            if (!event.getEntity().level().isClientSide() && event.getTarget() instanceof Interaction interaction) {
                Vec3 hitPos = interaction.position().add(event.getLocalPos());
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnInteraction.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.ENTITY.getId(), interaction);
                    process.setEventData(StandardPorts.TYPE.getId(), "interact");
                    process.setEventData(StandardPorts.XYZ.getId(), hitPos);
                });
            }
        });

        bus.addListener((AttackEntityEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getTarget() instanceof Interaction interaction) {
                Vec3 hitPos = interaction.position().add(0, interaction.getBbHeight() / 2.0, 0);
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnInteraction.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.ENTITY.getId(), interaction);
                    process.setEventData(StandardPorts.TYPE.getId(), "attack");
                    process.setEventData(StandardPorts.XYZ.getId(), hitPos);
                });
            }
        });

        bus.addListener((ItemTossEvent event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnEntityDropItem.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM.getId(), event.getEntity().getItem());
                });
            }
        });

        bus.addListener((ItemEntityPickupEvent.Pre event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnPlayerPickupItemPre.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM_STACK.getId(), event.getItemEntity().getItem());
                });
            }
        });

        bus.addListener((ItemEntityPickupEvent.Post event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnPlayerPickupItemPost.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM_STACK.getId(), event.getItemEntity().getItem());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerJoin.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                PlayerInputStateManager.clearPlayer(event.getEntity().getUUID());
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerQuit.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((ServerChatEvent event) -> {
            if (event.getPlayer() != null && !event.getPlayer().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnPlayerChat.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.MESSAGE.getId(), event.getMessage().getString());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerChangeGameModeEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerChangeGameMode.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), event.getNewGameMode().getName());
                });
            }
        });

        bus.addListener((AdvancementEvent.AdvancementEarnEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerEarnAdvancement.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.NAME.getId(), event.getAdvancement().id().toString());
                });
            }
        });

        bus.addListener((CommandEvent event) -> {
            Entity sourceEntity = event.getParseResults().getContext().getSource().getEntity();
            if (sourceEntity instanceof Player player && !player.level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) player.level(), player, OnPlayerExecuteCommand.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), player);
                    process.setEventData(StandardPorts.MESSAGE.getId(), event.getParseResults().getReader().getString());
                });
            }
        });

        bus.addListener((PlayerXpEvent.LevelChange event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerLevelChange.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.VALUE.getId(), event.getLevels());
                });
            }
        });

        bus.addListener((PlayerXpEvent.PickupXp event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerPickupXp.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.VALUE.getId(), (float)event.getOrb().getValue());
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getOrb());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerRespawn.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((CanPlayerSleepEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerSleep.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.XYZ.getId(), event.getPos());
                });
            }
        });

        bus.addListener((PlayerWakeUpEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnPlayerWakeUp.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });
    }
}