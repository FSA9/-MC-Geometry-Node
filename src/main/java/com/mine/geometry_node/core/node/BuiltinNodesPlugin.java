package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.GeometryNodePlugin;
import com.mine.geometry_node.core.node.nodes.actions.block.*;
import com.mine.geometry_node.core.node.nodes.actions.display_entity.*;
import com.mine.geometry_node.core.node.nodes.actions.entity.*;
import com.mine.geometry_node.core.node.nodes.actions.inventory.*;
import com.mine.geometry_node.core.node.nodes.actions.item.*;
import com.mine.geometry_node.core.node.nodes.actions.player.*;
import com.mine.geometry_node.core.node.nodes.actions.visual.*;
import com.mine.geometry_node.core.node.nodes.actions.world.*;
import com.mine.geometry_node.core.node.nodes.data.*;
import com.mine.geometry_node.core.node.nodes.data.MakeList;
import com.mine.geometry_node.core.node.nodes.data.container.*;
import com.mine.geometry_node.core.node.nodes.data.entity.*;
import com.mine.geometry_node.core.node.nodes.data.entity.attribution.*;
import com.mine.geometry_node.core.node.nodes.data.player.*;
import com.mine.geometry_node.core.node.nodes.data.type.*;
import com.mine.geometry_node.core.node.nodes.data.value.*;
import com.mine.geometry_node.core.node.nodes.data.world.*;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.nodes.events.player.*;
import com.mine.geometry_node.core.node.nodes.events.world.*;
import com.mine.geometry_node.core.node.nodes.functions.graph.*;
import com.mine.geometry_node.core.node.nodes.functions.time.*;
import com.mine.geometry_node.core.node.nodes.maths.vector.*;
import com.mine.geometry_node.core.node.nodes.logics.*;
import com.mine.geometry_node.core.node.nodes.maths.*;
import com.mine.geometry_node.core.node.nodes.maths.operation.*;

public class BuiltinNodesPlugin implements GeometryNodePlugin {

    @Override
    public void registerNodes(NodeRegistry registry) {
        System.out.println("[GeometryNode] 正在注册全量内置节点...");

        // --- ACTIONS ---
//        registry.register("actions", new CreateForceField());
//        registry.register("actions", new ExecuteCommand());
//        registry.register("actions", new LaunchProjectile());
        registry.register("actions/visual", new Raycast());
        registry.register("actions/visual", new MultiRaycast());
        registry.register("actions/visual", new DrawDebugLine());
        registry.register("actions/visual", new DrawLaserBeam());
        registry.register("actions/visual", new DrawRayBeam());
//        registry.register("actions", new SpawnHitbox());

        // Actions/AI
//        registry.register("actions/ai", new ClearEntityTarget());
//        registry.register("actions/ai", new MapsToPos());
//        registry.register("actions/ai", new SetEntityTarget());
//        registry.register("actions/ai", new StopNavigation());

        // Actions/Block
//        registry.register("actions/block", new BreakBlock());
        registry.register("actions/block", new BreakBlockAndDrop());
        registry.register("actions/block", new IgniteBlock());
//        registry.register("actions/block", new SetBlockProperty());
//        registry.register("actions/block", new UpdateBlock());
        registry.register("actions/block", new FillBlock());
        registry.register("actions/block", new PlaceBlock());
        registry.register("actions/block", new SpawnFallingBlock());
        registry.register("actions/block", new SetBlockState());

        // Actions/Entity
        registry.register("actions/entity", new AddForce());
        registry.register("actions/entity", new AddEffect());
        registry.register("actions/entity", new ClearEffect());
        registry.register("actions/entity", new ClearAllEffects());
        registry.register("actions/entity", new ClearInvulnerableTicks());
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
        registry.register("actions/entity", new SetEntitySize());
        registry.register("actions/entity", new SetEntityVelocity());
//        registry.register("actions/entity", new SetEquipment());
//        registry.register("actions/entity", new SetInvulnerable());
        registry.register("actions/entity", new SetInvulnerableTicks());
//        registry.register("actions/entity", new SpawnEntity());
        registry.register("actions/entity", new TeleportEntityToPos());
//        registry.register("actions/entity", new _UnleashEntity());
//        registry.register("actions/entity", new _UseItem());
        registry.register("actions/entity", new SetEntityGlowing());
        registry.register("actions/entity", new SetEntitySilent());
        registry.register("actions/entity", new SetEntityMoveSpeed());
        registry.register("actions/entity", new SetEntityStepHeight());
        registry.register("actions/entity", new SetEntityInvisible());
        registry.register("actions/entity", new AddEntityTag());
        registry.register("actions/entity", new ClearEntityTags());
        registry.register("actions/entity", new RemoveEntityTag());
        registry.register("actions/entity", new SetEntityCanPickUpLoot());
        registry.register("actions/entity", new SetEntityInvulnerable());
        registry.register("actions/entity", new SetEntityNoAI());
        registry.register("actions/entity", new SetEntityNoGravity());
        registry.register("actions/entity", new SetEntityPersistence());

        // Action/DisplayEntity
        registry.register("actions/display_entity", new SpawnBlockDisplayEntity());
        registry.register("actions/display_entity", new SpawnItemDisplayEntity());
        registry.register("actions/display_entity", new SpawnTextDisplayEntity());
        registry.register("actions/display_entity", new SetBlockDisplayState());
        registry.register("actions/display_entity", new SetDisplayStyle());
        registry.register("actions/display_entity", new SetDisplayTransform());
        registry.register("actions/display_entity", new SetDisplayPosition());

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
        registry.register("actions/item", new DamageItemStack());
        registry.register("actions/item", new RepairItemStack());
        registry.register("actions/item", new AddEnchantment());
        registry.register("actions/item", new RemoveEnchantment());
        registry.register("actions/item", new ClearAllEnchantments());
        registry.register("actions/item", new GiveItemStackToPlayer());
        registry.register("actions/item", new SetItemName());
        registry.register("actions/item", new AddAttributeModifier());
        registry.register("actions/item", new AddStoredEnchantment());
        registry.register("actions/item", new ClearAllStoredEnchantments());
        registry.register("actions/item", new SetDamage());
        registry.register("actions/item", new SetEnchantmentGlintOverride());
        registry.register("actions/item", new SetMaxDamage());
        registry.register("actions/item", new SetMaxStackSize());
        registry.register("actions/item", new SetRepairCost());
        registry.register("actions/item", new SetUnbreakable());


        // Actions/Player
        registry.register("actions/player", new AddExperience());
        registry.register("actions/player", new ExecuteCommand());
//        registry.register("actions/player", new PlayScreenShake());
//        registry.register("actions/player", new PlaySoundForPlayer());
        registry.register("actions/player", new SendMessage());
        registry.register("actions/player", new SetCameraTarget());
//        registry.register("actions/player", new SetFlySpeed());
        registry.register("actions/player", new SetGameMode());
        registry.register("actions/player", new SetWalkSpeed());
//        registry.register("actions/player", new ShowTitle());

//        // Actions/World

        registry.register("actions/world", new CreateExplosion());
        registry.register("actions/world", new PlaySound());
        registry.register("actions/world", new StrikeLightning());
        registry.register("actions/world", new SpawnParticle());

        // Client/Visual

        // --- DATA ---
        registry.register("data", new TargetSelector());
        registry.register("data", new GetEntityAttribute());
        registry.register("data", new GetScopeAttribute());
        registry.register("data", new SetEntityAttribute());
        registry.register("data", new SetScopeAttribute());
        registry.register("data", new IsKeyPressed());

        // Data/Entity
        registry.register("data/entity", new GetEntitiesByAABB());
        registry.register("data/entity", new GetEntitiesByRadius());
        registry.register("data/entity", new GetEntitiesbyRotationBox());

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
        registry.register("data/type", new GetBlockType());
        registry.register("data/type", new GetDamageType());
        registry.register("data/type", new GetDimension());
        registry.register("data/type", new GetEffect());
        registry.register("data/type", new GetEntityType());
        registry.register("data/type", new GetItemType());
        registry.register("data/type", new GetItemStack());
        registry.register("data/type", new GetSound());
        registry.register("data/type", new GetPortType());

        // Data/Container
        registry.register("data/container", new GetInputDataType());
        registry.register("data/container", new GetLength());
        registry.register("data/container", new MakeList());
        registry.register("data/container", new MakeDict());
        registry.register("data/container", new GetListValue());
        registry.register("data/container", new GetDictValue());
        registry.register("data/container", new ListHasValue());
        registry.register("data/container", new DictHasKey());
        registry.register("data/container", new DictHasValue());

        // Data/Value
        registry.register("data/value", new StringValue());
        registry.register("data/value", new IntValue());
        registry.register("data/value", new FloatValue());
        registry.register("data/value", new BoolValue());

        // --- EVENTS ---

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
        registry.register("events/entity", new OnProjectileHit());

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
        registry.register("events/player", new OnPlayerKeyEvent());

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

        // --- LOGICS ---
//        registry.register("logics", new Contain());
        registry.register("logics", new Equal());
        registry.register("logics", new ForEach());
        registry.register("logics", new IF());
        registry.register("logics", new Switch());

        // --- MATHS ---
        registry.register("maths", new SnapshotValue());
        registry.register("maths", new RandomValue());
        registry.register("maths/operation", new MathExpression());
        registry.register("maths/operation", new MathOperation());
        registry.register("maths/vector", new VectorAdd());
        registry.register("maths/vector", new SeparateXYZ());
        registry.register("maths/vector", new CombineXYZ());

        System.out.println("[GeometryNode] 内置节点注册完成！");
    }
}