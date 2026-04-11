package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.GeometryNodePlugin;
// 导入由于数量极其庞大，建议使用通配符或由IDE自动补全，以下展示核心注册逻辑
import com.mine.geometry_node.core.node.nodes.actions.*;
import com.mine.geometry_node.core.node.nodes.actions.entity.*;
import com.mine.geometry_node.core.node.nodes.actions.inventory.*;
import com.mine.geometry_node.core.node.nodes.actions.player.*;
import com.mine.geometry_node.core.node.nodes.actions.visual.*;
import com.mine.geometry_node.core.node.nodes.data.*;
import com.mine.geometry_node.core.node.nodes.data.entity.*;
import com.mine.geometry_node.core.node.nodes.data.entity.attribution.*;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.nodes.events.player.*;
import com.mine.geometry_node.core.node.nodes.events.world.*;
import com.mine.geometry_node.core.node.nodes.functions.graph.*;
import com.mine.geometry_node.core.node.nodes.functions.time.*;
import com.mine.geometry_node.core.node.nodes.functions.vector.*;
import com.mine.geometry_node.core.node.nodes.logics.*;
import com.mine.geometry_node.core.node.nodes.maths.*;
import com.mine.geometry_node.core.node.nodes.maths.operation.*;

public class BuiltinNodesPlugin implements GeometryNodePlugin {

    @Override
    public void registerNodes(NodeRegistry registry) {
        System.out.println("[GeometryNode] 正在注册全量内置节点...");

        // --- ACTIONS ---
        registry.register("actions", new BoxOverlapWithRotation());
//        registry.register("actions", new CreateForceField());
//        registry.register("actions", new ExecuteCommand());
//        registry.register("actions", new LaunchProjectile());
        registry.register("actions", new Raycast());
//        registry.register("actions", new SpawnHitbox());

        // Actions/AI
//        registry.register("actions/ai", new ClearEntityTarget());
//        registry.register("actions/ai", new MapsToPos());
//        registry.register("actions/ai", new SetEntityTarget());
//        registry.register("actions/ai", new StopNavigation());

        // Actions/Block
//        registry.register("actions/block", new BreakBlockAndDrop());
//        registry.register("actions/block", new IgniteBlock());
//        registry.register("actions/block", new SetBlockProperty());
//        registry.register("actions/block", new UpdateBlock());

        // Actions/Entity
        registry.register("actions/entity", new AddForce());
//        registry.register("actions/entity", new _AddPotionEffect());
        registry.register("actions/entity", new ClearAllPotionEffects());
        registry.register("actions/entity", new ClearInvulnerableTicks());
//        registry.register("actions/entity", new _ClearPotionEffects());
        registry.register("actions/entity", new DamageEntity());
//        registry.register("actions/entity", new _DismountEntity());
        registry.register("actions/entity", new ExtinguishEntity());
//        registry.register("actions/entity", new GetInvulnerableTicks());
        registry.register("actions/entity", new GrantDamageTypeImmunity());
        registry.register("actions/entity", new HealEntity());
        registry.register("actions/entity", new IgniteEntity());
        registry.register("actions/entity", new KillEntity());
        registry.register("actions/entity", new LeashEntity());
        registry.register("actions/entity", new MountEntity());
        registry.register("actions/entity", new RevokeDamageTypeImmunity());
        registry.register("actions/entity", new SetCustomName());
//        registry.register("actions/entity", new SetEntityOnFire());
        registry.register("actions/entity", new SetEntityRotation());
//        registry.register("actions/entity", new SetEntitySize());
        registry.register("actions/entity", new SetEntityVelocity());
//        registry.register("actions/entity", new SetEquipment());
//        registry.register("actions/entity", new SetInvulnerable());
        registry.register("actions/entity", new SetInvulnerableTicks());
//        registry.register("actions/entity", new _SpawnEntity());
        registry.register("actions/entity", new TeleportEntityToPos());
//        registry.register("actions/entity", new _UnleashEntity());
//        registry.register("actions/entity", new _UseItem());

        // Actions/Inventory
        registry.register("actions/inventory", new ClearInventory());
        registry.register("actions/inventory", new ClearItem());
        registry.register("actions/inventory", new ClearSlot());
        registry.register("actions/inventory", new DropInventorySlot());
//        registry.register("actions/inventory", new _DropItem());
//        registry.register("actions/inventory", new _EquipItem());
//        registry.register("actions/inventory", new _GiveItem());
//        registry.register("actions/inventory", new _MoveItemToSlot());
//        registry.register("actions/inventory", new _TakeItem());

        // Actions/Item
//        registry.register("actions/item", new DamageItem());
//        registry.register("actions/item", new _EnchantItem());
//        registry.register("actions/item", new _RemoveEnchantment());
//        registry.register("actions/item", new RepairItem());

        // Actions/Player
        registry.register("actions/player", new AddExperience());
        registry.register("actions/player", new ExecuteCommand());
//        registry.register("actions/player", new PlayScreenShake());
//        registry.register("actions/player", new PlaySoundForPlayer());
        registry.register("actions/player", new SendMessage());
//        registry.register("actions/player", new SetCameraTarget());
//        registry.register("actions/player", new SetFlySpeed());
        registry.register("actions/player", new SetGameMode());
        registry.register("actions/player", new SetWalkSpeed());
//        registry.register("actions/player", new ShowTitle());

        // Actions/Visual
        registry.register("actions/visual", new DrawDebugLine());
        registry.register("actions/visual", new DrawLaserBeam());

        // Actions/World
//        registry.register("actions/world", new BreakBlock());
//        registry.register("actions/world", new CreateExplosion());
//        registry.register("actions/world", new FillBlock());
//        registry.register("actions/world", new PlaceBlock());
//        registry.register("actions/world", new PlaySound());
//        registry.register("actions/world", new SetBlockState());
//        registry.register("actions/world", new SpawnFallingBlock());
//        registry.register("actions/world", new StrikeLightning());

        // --- DATA ---
        registry.register("data", new GetEntityAttribute());
//        registry.register("data", new GetListLen());
        registry.register("data", new GetScopeAttribute());
        registry.register("data", new SetEntityAttribute());
        registry.register("data", new SetScopeAttribute());

        // Data/Entity
        registry.register("data/entity", new GetEntitiesByAABB());
        registry.register("data/entity", new GetEntitiesByRadius());

        // Data/Entity/Attribution
        registry.register("data/entity/attribution", new GetEntityDimension());
        registry.register("data/entity/attribution", new GetEntityEyePosition());
        registry.register("data/entity/attribution", new GetEntityFallDistance());
        registry.register("data/entity/attribution", new GetEntityHealth());
        registry.register("data/entity/attribution", new GetEntityPitch());
        registry.register("data/entity/attribution", new GetEntityPosition());
        registry.register("data/entity/attribution", new GetEntityTags());
        registry.register("data/entity/attribution", new GetEntityUUID());
        registry.register("data/entity/attribution", new GetEntityVelocity());
        registry.register("data/entity/attribution", new GetEntityVisibleName());
        registry.register("data/entity/attribution", new GetEntityYaw());
        registry.register("data/entity/attribution", new GetExperienceLevel());
        registry.register("data/entity/attribution", new GetFoodLevel());
        registry.register("data/entity/attribution", new GetGameMode());
        registry.register("data/entity/attribution", new GetSaturation());
        registry.register("data/entity/attribution", new GetTotalExperience());
        registry.register("data/entity/attribution", new IsInvisible());
        registry.register("data/entity/attribution", new IsOnFire());
        registry.register("data/entity/attribution", new IsOnGround());
        registry.register("data/entity/attribution", new IsSneaking());
        registry.register("data/entity/attribution", new IsSprinting());
        registry.register("data/entity/attribution", new IsSwimming());

        // Data/Type
//        registry.register("data/type", new GetBlockType());
//        registry.register("data/type", new GetDamageType());
//        registry.register("data/type", new GetDimension());
//        registry.register("data/type", new GetEffect());
//        registry.register("data/type", new GetEntityType());
//        registry.register("data/type", new GetItemType());
//        registry.register("data/type", new GetSound());

        // --- EVENTS ---
//        registry.register("events", new OnProjectileHit());

        // Events/Block
        registry.register("events/block", new OnBlockBreak());
//        registry.register("events/block", new OnBlockIgnite());
        registry.register("events/block", new OnBlockPlace());

        // Events/Entity
        registry.register("events/entity", new EntityInteractBlock());
        registry.register("events/entity", new EntityInteractEntity());
        registry.register("events/entity", new EntityUseItem());
        registry.register("events/entity", new OnBabyGrowUp());
        registry.register("events/entity", new OnEntityBreed());
        registry.register("events/entity", new OnEntityChangeDimension());
        registry.register("events/entity", new OnEntityDealDamage());
        registry.register("events/entity", new OnEntityDeath());
        registry.register("events/entity", new OnEntityDropItem());
        registry.register("events/entity", new OnEntityGriefBlock());
        registry.register("events/entity", new OnEntityHeal());
        registry.register("events/entity", new OnEntityHurt());
        registry.register("events/entity", new OnEntityJump());
        registry.register("events/entity", new OnEntityMount());
        registry.register("events/entity", new OnPlayerPickupItemPre());
        registry.register("events/entity", new OnEntityPotionEffectApply());
        registry.register("events/entity", new OnEntityPotionEffectExpire());
        registry.register("events/entity", new OnEntityPotionEffectRemove());
        registry.register("events/entity", new OnEntitySpawn());
        registry.register("events/entity", new OnEntityTame());
        registry.register("events/entity", new OnEntityTeleport());
        registry.register("events/entity", new OnProjectileShoot());
        registry.register("events/entity", new OnTargetChange());
        registry.register("events/entity", new OnVillagerCure());
        registry.register("events/entity", new OnVillagerTrade());

        // Events/Inventory
//        registry.register("events/inventory", new OnContainerClose());
//        registry.register("events/inventory", new OnContainerOpen());
//        registry.register("events/inventory", new OnItemCrafted());
//        registry.register("events/inventory", new OnItemSmelted());
//        registry.register("events/inventory", new OnSlotClick());

        // Events/Item
//        registry.register("events/item", new OnArrowLoose());
//        registry.register("events/item", new OnEquipmentChange());
//        registry.register("events/item", new OnItemConsume());
//        registry.register("events/item", new OnItemEnchant());
//        registry.register("events/item", new OnToolBreak());

        // Events/Player
        registry.register("events/player", new OnPlayerChangeGameMode());
        registry.register("events/player", new OnPlayerChat());
        registry.register("events/player", new OnPlayerEarnAdvancement());
        registry.register("events/player", new OnPlayerExecuteCommand());
        registry.register("events/player", new OnPlayerJoin());
        registry.register("events/player", new OnPlayerLeftClickBlock());
        registry.register("events/player", new OnPlayerLevelChange());
        registry.register("events/player", new OnPlayerPickupXp());
        registry.register("events/player", new OnPlayerQuit());
        registry.register("events/player", new OnPlayerRespawn());
        registry.register("events/player", new OnPlayerSleep());
        registry.register("events/player", new OnPlayerTick());
        registry.register("events/player", new OnPlayerWakeUp());

        // Events/Server
//        registry.register("events/server", new OnServerTick());
//        registry.register("events/server", new OnWorldTick());

        // Events/World
        registry.register("events/world", new OnChunkLoad());
        registry.register("events/world", new OnChunkUnload());
        registry.register("events/world", new OnExplosion());
        registry.register("events/world", new OnLightningStrike());
        registry.register("events/world", new OnPortalCreate());
        registry.register("events/world", new OnStructureGenerate());

        // --- FUNCTIONS ---
        registry.register("functions/graph", new FinishGraph());
        registry.register("functions/graph", new ReceiveBlueprint());
        registry.register("functions/graph", new TriggerBlueprint());

        registry.register("functions/time", new Function_Delay_s());
        registry.register("functions/time", new Function_Delay_tick());

        registry.register("functions/vector", new VectorAdd());

        // --- LOGICS ---
//        registry.register("logics", new Contain());
//        registry.register("logics", new Equal());
        registry.register("logics", new ForEach());
        registry.register("logics", new IF());
        registry.register("logics", new Switch());

        // --- MATHS ---
        registry.register("maths", new RandomValue());
        registry.register("maths/operation", new MathExpression());
        registry.register("maths/operation", new MathOperation());

        System.out.println("[GeometryNode] 内置节点注册完成！");
    }
}