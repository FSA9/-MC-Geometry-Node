package com.mine.geometry_node.core.execution;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.attachment.EntityImmunityAttachment;
import com.mine.geometry_node.core.execution.attachment.LevelGraphAttachment;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.events.block.OnBlockBreak;
import com.mine.geometry_node.core.node.nodes.events.block.OnBlockPlace;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.InteractionEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * [驱动马达] 负责将游戏引擎的 Tick 信号传递给蓝图系统。
 * <p>
 * 采用倒排索引优化：只对真正挂载了活跃蓝图的实体进行 Tick 遍历，
 * 避免全局实体遍历带来的性能开销。
 */
public class GraphEventHandler {

    // Fields

    /**
     * 活跃实体清单。
     * 使用 WeakHashMap 包装的 Set，防止因 Entity 被移除但此处仍引用导致的内存泄漏。
     */
    private static final Set<Entity> ACTIVE_ENTITIES = Collections.newSetFromMap(new WeakHashMap<>());

    // Lifecycle & Initialization

    /**
     * 在模组初始化阶段调用，注册所有事件监听器。
     */
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
        // 破坏方块事件
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

        // 放置方块事件
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

        // 实体受伤 / 造成伤害事件
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
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) entity.level();

                net.minecraft.world.entity.Entity attacker = source.getEntity();
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
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.level.block.state.BlockState state = serverLevel.getBlockState(pos);

                GraphEngine.dispatchEvent(serverLevel, player, EntityInteractBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.XYZ.getId(), pos);
                    process.setEventData(StandardPorts.BLOCK_STATE.getId(), state);
                });
            }
            return EventResult.pass();
        });

        // 实体交互实体
        InteractionEvent.INTERACT_ENTITY.register((player, entity, hand) -> {
            if (!player.level().isClientSide()) {
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) player.level();

                GraphEngine.dispatchEvent(serverLevel, player, EntityInteractEntity.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                });
            }
            return EventResult.pass();
        });

        // 实体使用物品
        InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (!player.level().isClientSide()) {
                net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) player.level();
                net.minecraft.world.item.ItemStack itemStack = player.getItemInHand(hand);

                GraphEngine.dispatchEvent(serverLevel, player, EntityUseItem.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), player);
                    process.setEventData(StandardPorts.ITEM.getId(), itemStack);
                });
            }
            return CompoundEventResult.pass();
        });

        EntityEvent.ADD.register((entity, level) -> {
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                GraphEngine.dispatchEvent(serverLevel, entity, OnEntitySpawn.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), entity);
                    process.setEventData(StandardPorts.XYZ.getId(), entity.position());
                });
            }
            return EventResult.pass();
        });

        // 以下事件使用 NeoForge 原生事件总线 (1.21.1)
        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;

        // 2. 实体繁殖 (OnEntityBreed)
        bus.addListener((net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event) -> {
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

        // 3. 实体切换维度 (OnEntityChangeDimension)
        bus.addListener((net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityChangeDimension.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.DIMENSION.getId(), event.getDimension().location().toString());
                });
            }
        });

        // 4. 玩家丢弃物品 (OnEntityDropItem - Player)
        bus.addListener((net.neoforged.neoforge.event.entity.item.ItemTossEvent event) -> {
            if (!event.getPlayer().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getPlayer().level(), event.getPlayer(), OnEntityDropItem.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getPlayer());
                    process.setEventData(StandardPorts.ITEM.getId(), event.getEntity().getItem());
                });
            }
        });

        // 5. 实体死亡掉落物品 (OnEntityDropItem - Mob)
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                for (var drop : event.getDrops()) {
                    GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityDropItem.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                        process.setEventData(StandardPorts.ITEM.getId(), drop.getItem());
                    });
                }
            }
        });

        // 6. 实体破坏方块 / 苦力怕爆炸防爆检查 (OnEntityGriefBlock)
        bus.addListener((net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event) -> {
            if (event.getEntity() != null && !event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityGriefBlock.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    // 注意：GriefingEvent 不自带坐标，通常需要结合节点内获取实体当前坐标来使用
                });
            }
        });

        // 7. 实体恢复生命 (OnEntityHeal)
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingHealEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityHeal.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.VALUE.getId(), event.getAmount());
                });
            }
        });

        // 8. 实体跳跃 (OnEntityJump)
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingEvent.LivingJumpEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityJump.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                });
            }
        });

        // 9. 实体骑乘 (OnEntityMount)
        bus.addListener((net.neoforged.neoforge.event.entity.EntityMountEvent event) -> {
            if (!event.getLevel().isClientSide() && event.isMounting()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), event.getEntityMounting(), OnEntityMount.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntityMounting()); // 乘客
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), event.getEntityBeingMounted()); // 载具
                });
            }
        });

        // 10. 实体被驯服 (OnEntityTame)
        bus.addListener((net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityTame.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getTamer());
                });
            }
        });

        // 11. 实体传送 (OnEntityTeleport)
        bus.addListener((net.neoforged.neoforge.event.entity.EntityTeleportEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnEntityTeleport.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.START_POS.getId(), event.getPrev());
                    process.setEventData(StandardPorts.END_POS.getId(), event.getTarget());
                });
            }
        });

        // 12. 仇恨/目标改变 (OnTargetChange)
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) -> {
            if (!event.getEntity().level().isClientSide() && event.getNewAboutToBeSetTarget() != null) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getEntity(), OnTargetChange.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getNewAboutToBeSetTarget());
                });
            }
        });

        // 13. 村民被治愈 (OnVillagerCure) -> 监听实体类型转换
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post event) -> {
            if (!event.getEntity().level().isClientSide()) {
                if (event.getEntity() instanceof net.minecraft.world.entity.monster.ZombieVillager &&
                        event.getOutcome() instanceof net.minecraft.world.entity.npc.Villager) {

                    GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getOutcome(), OnVillagerCure.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.ENTITY.getId(), event.getEntity());
                        // Trigger Entity 通常保存在 ZombieVillager 的 conversionPlayer 里，但事件未直接暴露，这里传转换后的村民实体作为主体
                    });
                }
            }
        });

        // 14. 村民交易 (OnVillagerTrade)
        bus.addListener((net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent event) -> {
            if (!event.getEntity().level().isClientSide()) {
                GraphEngine.dispatchEvent((ServerLevel) event.getEntity().level(), event.getAbstractVillager(), OnVillagerTrade.TYPE_ID, process -> {
                    process.setEventData(StandardPorts.ENTITY.getId(), event.getAbstractVillager());
                    process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), event.getEntity());
                    // 假设能拿到交易结果，因 Forge 事件不直接暴露 ItemStack，建议用作纯通知流
                });
            }
        });

        // 15. 发射弹射物 (OnProjectileShoot) -> 拦截弹射物加入世界
        bus.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) {
                if (projectile.getOwner() != null) {
                    GraphEngine.dispatchEvent((ServerLevel) event.getLevel(), projectile.getOwner(), OnProjectileShoot.TYPE_ID, process -> {
                        process.setEventData(StandardPorts.ENTITY.getId(), projectile.getOwner());
                        process.setEventData(StandardPorts.DIRECT_SOURCE.getId(), projectile);
                    });
                }
            }
        });
    }

    // Helpers

    private static EntityGraphAttachment getAttachmentFromEntity(Entity entity) {
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }
}