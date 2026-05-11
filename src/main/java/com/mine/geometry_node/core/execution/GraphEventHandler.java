package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.*;
import com.mine.geometry_node.core.execution.state.PlayerInputStateManager;
import com.mine.geometry_node.core.node.nodes.events.entity.OnInteraction;
import com.mine.geometry_node.core.node.nodes.events.world.*;
import com.mine.geometry_node.core.node.nodes.events.player.*;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.port.StandardPorts;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.*;
import dev.architectury.event.events.common.EntityEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.*;


public class GraphEventHandler {

    // Fields

    /**
     * 活跃实体清单。
     * 使用 WeakHashMap 包装的 Set，防止因 Entity 被移除但此处仍引用导致的内存泄漏。
     */
    private static final Set<Entity> ACTIVE_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());

    // Lifecycle & Initialization

    public static void init() {
        // 监听服务端 Tick
        TickEvent.SERVER_LEVEL_POST.register(GraphEventHandler::onLevelTick);

        // 实体加载：监听实体加入世界
        EntityEvent.ADD.register((entity, level) -> EventResult.pass());

        // 物理事件监听&路由分发
        registerPhysicalEvents();
    }

    /**
     * 标记一个实体为“活跃状态”，让引擎在接下来的 Tick 中驱动它。
     */
    public static void markActive(Entity entity) {
        if (entity != null && !entity.level().isClientSide) {
            ACTIVE_ENTITIES.add(entity);
        }
    }

    // Tick Logic

    private static void onLevelTick(ServerLevel level) {
        // 驱动全局蓝图
        LevelGraphAttachment levelAttachment = LevelGraphAttachment.get(level);
        levelAttachment.tick(level);

        // 驱动局部蓝图
        if (ACTIVE_ENTITIES.isEmpty()) return;

        Iterator<Entity> iterator = ACTIVE_ENTITIES.iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();

            // 实体有效性检查
            if (entity == null) {
                iterator.remove();
                continue;
            }

            // 维度检查：确保只在实体所在的维度更新它
            if (!entity.isRemoved() && entity.level() != level) {
                continue;
            }

            // 获取挂载层并驱动
            EntityGraphAttachment attachment = getAttachmentFromEntity(entity);

            // 即使 entity.isRemoved() 为 true，只要进程列表不为空，继续驱动遗愿图！
            if (attachment != null && !attachment.getProcesses().isEmpty()) {
                attachment.tick(entity);
            } else {
                iterator.remove();
            }
        }
    }

    // Physical Event Listeners

    private static void registerPhysicalEvents() {
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

        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (!entity.level().isClientSide()) {
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) entity.level();
                net.minecraft.world.entity.Entity attacker = source.getEntity();
                net.minecraft.world.entity.Entity directSource = source.getDirectEntity();
                String damageTypeId = source.getMsgId();

                if (EntityImmunityAttachment.hasImmunity(entity, damageTypeId)) {
                    return EventResult.interruptFalse();
                }

                // 1. 实体受伤
                GraphEngine.dispatchEvent(serverLevel, entity, OnEntityHurt.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                    process.setEventData(StandardPorts.VALUE.getId(), amount);
                    process.setEventData(StandardPorts.DAMAGE_TYPE.getId(), damageTypeId);

                    if (attacker != null) {
                        process.setEventData(StandardPorts.ATTACK_SOURCE.getId(), attacker);
                    }
                    if (directSource != null) {
                        process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), directSource);
                    }
                });

                // 2. 实体造成伤害
                if (attacker != null) {
                    GraphEngine.dispatchEvent(serverLevel, attacker, OnEntityDealDamage.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), attacker);
                        process.setEventData(StandardPorts.ENTITY.getId(), entity);
                        process.setEventData(StandardPorts.VALUE.getId(), amount);
                        process.setEventData(StandardPorts.DAMAGE_TYPE.getId(), damageTypeId);

                        if (directSource != null) {
                            process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), directSource);
                        }
                    });
                }
            }
            return EventResult.pass();
        });

        // 实体死亡事件
        EntityEvent.LIVING_DEATH.register((entity, source) -> {
            if (!entity.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) entity.level();

                Entity attacker = source.getEntity();
                net.minecraft.world.entity.Entity directSource = source.getDirectEntity();
                String damageTypeId = source.getMsgId();

                GraphEngine.dispatchEvent(serverLevel, entity, OnEntityDeath.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                    process.setEventData(StandardPorts.DAMAGE_TYPE.getId(), damageTypeId);

                    if (attacker != null) {
                        process.setEventData(StandardPorts.ATTACK_SOURCE.getId(), attacker);
                    }
                    if (directSource != null) {
                        process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), directSource);
                    }
                });
            }
            return EventResult.pass();
        });

        // 实体交互方块
        InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);

                GraphEngine.dispatchEvent(serverLevel, player, EntityInteractBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), state);
                });
            }
            return EventResult.pass();
        });

        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();

                GraphEngine.dispatchEvent(serverLevel, player, EntityInteractEntity.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                });
            }
            return EventResult.pass();
        });

        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                ItemStack itemStack = player.getItemInHand(hand);

                GraphEngine.dispatchEvent(serverLevel, player, EntityUseItem.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.ITEM.getId(), itemStack);
                });
            }
            return CompoundEventResult.pass();
        });

        // 以下事件使用 NeoForge 原生事件总线 (1.21.1)
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        
        bus.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> {
            if (!event.getEntity().level().isClientSide() && event.getTarget() instanceof Interaction interaction) {
                ServerLevel serverLevel = (ServerLevel) event.getEntity().level();

                Vec3 localPos = event.getLocalPos();
                Vec3 hitPos = interaction.position().add(localPos);

                GraphEngine.dispatchEvent(serverLevel, event.getEntity(), OnInteraction.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.ENTITY.getId(), interaction);
                    process.setEventData(StandardPorts.TYPE.getId(), "interact"); // 右键
                    process.setEventData(StandardPorts.XYZ.getId(), hitPos);
                });
            }
        });

        bus.addListener((AttackEntityEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getTarget() instanceof Interaction interaction) {
                ServerLevel serverLevel = (ServerLevel) event.getEntity().level();

                net.minecraft.world.phys.Vec3 hitPos = interaction.position().add(0, interaction.getBbHeight() / 2.0, 0);

                GraphEngine.dispatchEvent(serverLevel, event.getEntity(), OnInteraction.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.ENTITY.getId(), interaction);
                    process.setEventData(StandardPorts.TYPE.getId(), "attack"); // 左键
                    process.setEventData(StandardPorts.XYZ.getId(), hitPos);
                });
            }
        });

        bus.addListener((BabyEntitySpawnEvent event) -> {
            if (event.getParentA() != null && !event.getParentA().level().isClientSide()) {
                ServerLevel level = (ServerLevel) event.getParentA().level();
                GraphEngine.dispatchEvent(level, event.getParentA(), OnEntityBreed.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getParentA());
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getChild());
                    if (event.getCausedByPlayer() != null) {
                        process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getCausedByPlayer());
                    }
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

        bus.addListener((ItemTossEvent event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnEntityDropItem.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM.getId(), event.getEntity().getItem());
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
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntityMounting()); // 乘客
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getEntityBeingMounted()); // 载具
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
            if (!event.getEntity().level().isClientSide()) {
                if (event.getEntity() instanceof ZombieVillager &&
                        event.getOutcome() instanceof Villager) {

                    GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getOutcome(), OnVillagerCure.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    });
                }
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
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile projectile) {
                if (projectile.getOwner() != null) {
                    GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), projectile.getOwner(), OnProjectileShoot.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.ENTITY.getId(), projectile.getOwner());
                        process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), projectile);
                    });
                }
            }
        });

        InteractionEvent.LEFT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                BlockState state = serverLevel.getBlockState(pos);
                String dimensionId = serverLevel.dimension().location().toString();

                GraphEngine.dispatchEvent(serverLevel, player, OnPlayerLeftClickBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), player);
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), state);
                    process.setEventData(StandardPorts.DIMENSION.getId(), dimensionId);
                });
            }
            return EventResult.pass();
        });

        bus.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(serverLevel, event.getEntity(), OnPlayerJoin.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.ServerChatEvent event) -> {
            if (event.getPlayer() != null && !event.getPlayer().level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getPlayer().level();
                GraphEngine.dispatchEvent(serverLevel, event.getPlayer(), OnPlayerChat.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.MESSAGE.getId(), event.getMessage().getString());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerChangeGameModeEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(serverLevel, event.getEntity(), OnPlayerChangeGameMode.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), event.getNewGameMode().getName());
                });
            }
        });

        bus.addListener((AdvancementEvent.AdvancementEarnEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(serverLevel, event.getEntity(), OnPlayerEarnAdvancement.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.NAME.getId(), event.getAdvancement().id().toString());
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.CommandEvent event) -> {
            Entity sourceEntity = event.getParseResults().getContext().getSource().getEntity();
            if (sourceEntity instanceof Player player && !player.level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                GraphEngine.dispatchEvent(serverLevel, player, OnPlayerExecuteCommand.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), player);
                    process.setEventData(StandardPorts.MESSAGE.getId(), event.getParseResults().getReader().getString());
                });
            }
        });

        bus.addListener((PlayerXpEvent.LevelChange event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel level = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(level, event.getEntity(), OnPlayerLevelChange.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.VALUE.getId(), event.getLevels());
                });
            }
        });

        bus.addListener((PlayerXpEvent.PickupXp event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel level = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(level, event.getEntity(), OnPlayerPickupXp.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.VALUE.getId(), (float)event.getOrb().getValue());
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getOrb());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                PlayerInputStateManager.clearPlayer(event.getEntity().getUUID());

                ServerLevel level = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(level, event.getEntity(), OnPlayerQuit.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel level = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(level, event.getEntity(), OnPlayerRespawn.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((CanPlayerSleepEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel level = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(level, event.getEntity(), OnPlayerSleep.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.XYZ.getId(), event.getPos());
                });
            }
        });

        bus.addListener((PlayerWakeUpEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                ServerLevel level = (ServerLevel) event.getEntity().level();
                GraphEngine.dispatchEvent(level, event.getEntity(), OnPlayerWakeUp.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        bus.addListener((EntityTickEvent.Post event) -> {
            Entity entity = event.getEntity();
            if (entity.level().isClientSide()) return;

            ServerLevel level = (ServerLevel) entity.level();

            // 1. O(1) 获取实体挂载的蓝图容器
            EntityGraphAttachment attachment = getAttachmentFromEntity(entity);
            if (attachment == null || attachment.getBoundGraphs().isEmpty()) return;

            long currentTick = level.getGameTime();

            for (String graphId : attachment.getBoundGraphs()) {
                RuntimeGraphIndex index = GraphEngine.getGraphIndex(graphId);
                if (index == null) continue;

                List<Integer> tickNodes = index.findNodesByType(OnEntityTick.TYPE_ID);

                for (int nodeId : tickNodes) {
                    // 【属性预检】从 Node 的 Properties 中极速读取配置 (因为没有连线端口了)
                    Object rawInterval = index.getNodeProperty(nodeId, "interval");
                    Object rawOffset = index.getNodeProperty(nodeId, "offset");

                    // 提取 interval (兼容数字和字符串)
                    int interval = 1;
                    if (rawInterval instanceof Number n) {
                        interval = Math.max(1, n.intValue());
                    } else if (rawInterval instanceof String s) {
                        try { interval = Math.max(1, Integer.parseInt(s)); } catch (Exception ignored) {}
                    }

                    // 提取 offset (兼容数字和字符串)
                    int offset = 0;
                    if (rawOffset instanceof Number n) {
                        offset = Math.max(0, n.intValue());
                    } else if (rawOffset instanceof String s) {
                        try { offset = Math.max(0, Integer.parseInt(s)); } catch (Exception ignored) {}
                    }

                    // 【海关拦截】如果时间没到，直接跳过，0 虚拟机开销
                    if (interval == 1 || currentTick % interval == offset) {

                        // 获取复用的常驻进程
                        GraphProcess process = attachment.getProcess(graphId);
                        if (process == null) {
                            process = new GraphProcess(graphId, index);
                            attachment.addProcess(process);
                        }

                        // 注入环境，分配轻量级线程执行
                        process.setEnvironment(level, entity);
                        process.executeEvent(nodeId, thread -> {
                            thread.setEventData(StandardPorts.ENTITY.getId(), entity);
                        });
                    }
                }
            }
        });

        bus.addListener((net.neoforged.neoforge.event.entity.living.MobEffectEvent.Added event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("unknown");
                float amplifier = event.getEffectInstance().getAmplifier();

                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectApply.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), effectId);
                    process.setEventData(StandardPorts.VALUE.getId(), amplifier);
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.entity.living.MobEffectEvent.Expired event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("unknown");

                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectExpire.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), effectId);
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove event) -> {
            if (!event.getEntity().level().isClientSide() && event.getEffectInstance() != null) {
                String effectId = event.getEffectInstance().getEffect().unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("unknown");

                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityPotionEffectRemove.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TYPE.getId(), effectId);
                });
            }
        });

        bus.addListener((ItemEntityPickupEvent.Pre event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getPlayer().level();

                GraphEngine.dispatchEvent(serverLevel, event.getPlayer(), OnPlayerPickupItemPre.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM_STACK.getId(), event.getItemEntity().getItem());
                });
            }
        });

        bus.addListener((ItemEntityPickupEvent.Post event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getPlayer().level();

                GraphEngine.dispatchEvent(serverLevel, event.getPlayer(), OnPlayerPickupItemPost.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM_STACK.getId(), event.getItemEntity().getItem());
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                GraphEngine.dispatchEvent(serverLevel, null, OnChunkLoad.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), event.getChunk().getPos().getMiddleBlockPosition(64));
                    process.setEventData(StandardPorts.DIMENSION.getId(), serverLevel.dimension().location().toString());
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) -> {
            if (!event.getLevel().isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) event.getLevel();
                GraphEngine.dispatchEvent(serverLevel, event.getExplosion().getIndirectSourceEntity(), OnExplosion.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), event.getExplosion().center());
                    process.setEventData(StandardPorts.VALUE.getId(), event.getExplosion().radius());
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getExplosion().getIndirectSourceEntity());
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.LightningBolt lightning) {
                GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), null, OnLightningStrike.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), lightning.position());
                });
            }
        });

        bus.addListener((net.neoforged.neoforge.event.level.BlockEvent.PortalSpawnEvent event) -> {
            if (event.getLevel() instanceof ServerLevel serverLevel) {
                GraphEngine.dispatchEvent(serverLevel, null, OnPortalCreate.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.XYZ.getId(), event.getPos());
                    process.setEventData(StandardPorts.DIMENSION.getId(), serverLevel.dimension().location().toString());
                });
            }
        });

    }

    // Helpers

    private static EntityGraphAttachment getAttachmentFromEntity(Entity entity) {
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }
}