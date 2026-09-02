package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.GeometryNodePlugin;
import com.mine.geometry_node.api.MarkerRegistrationContext;
import com.mine.geometry_node.api.NodeRegistrationContext;
import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.node.nodes.actions.block.*;
import com.mine.geometry_node.core.node.nodes.actions.area.*;
import com.mine.geometry_node.core.node.nodes.behavior.action.*;
import com.mine.geometry_node.core.node.nodes.behavior.blackboard.*;
import com.mine.geometry_node.core.node.nodes.behavior.condition.*;
import com.mine.geometry_node.core.node.nodes.behavior.control.*;
import com.mine.geometry_node.core.node.nodes.behavior.decorator.*;
import com.mine.geometry_node.core.node.nodes.behavior.entity.*;
import com.mine.geometry_node.core.node.nodes.actions.display_entity.*;
import com.mine.geometry_node.core.node.nodes.actions.entity.*;
import com.mine.geometry_node.core.node.nodes.actions.inventory.*;
import com.mine.geometry_node.core.node.nodes.actions.item.*;
import com.mine.geometry_node.core.node.nodes.actions.marker.*;
import com.mine.geometry_node.core.node.nodes.actions.player.*;
import com.mine.geometry_node.core.node.nodes.actions.schematic.*;
import com.mine.geometry_node.core.node.nodes.actions.visual.*;
import com.mine.geometry_node.core.node.nodes.actions.world.*;
import com.mine.geometry_node.core.node.nodes.data.*;
import com.mine.geometry_node.core.node.nodes.data.container.*;
import com.mine.geometry_node.core.node.nodes.data.entity.*;
import com.mine.geometry_node.core.node.nodes.data.entity.attribution.*;
import com.mine.geometry_node.core.node.nodes.data.inventory.*;
import com.mine.geometry_node.core.node.nodes.data.item.attribution.*;
import com.mine.geometry_node.core.node.nodes.data.player.*;
import com.mine.geometry_node.core.node.nodes.data.type.*;
import com.mine.geometry_node.core.node.nodes.data.value.*;
import com.mine.geometry_node.core.node.nodes.data.world.*;
import com.mine.geometry_node.core.node.nodes.events.area.*;
import com.mine.geometry_node.core.node.nodes.events.block.*;
import com.mine.geometry_node.core.node.nodes.dialogue.*;
import com.mine.geometry_node.core.node.nodes.events.display_entity.*;
import com.mine.geometry_node.core.node.nodes.events.entity.*;
import com.mine.geometry_node.core.node.nodes.events.dialogue.*;
import com.mine.geometry_node.core.node.nodes.events.player.*;
import com.mine.geometry_node.core.node.nodes.events.inventory.*;
import com.mine.geometry_node.core.node.nodes.events.world.*;
import com.mine.geometry_node.core.node.nodes.functions.graph.*;
import com.mine.geometry_node.core.node.nodes.functions.color.*;
import com.mine.geometry_node.core.node.nodes.functions.time.*;
import com.mine.geometry_node.core.node.nodes.geometry.*;
import com.mine.geometry_node.core.node.nodes.maths.vector.*;
import com.mine.geometry_node.core.node.nodes.logics.*;
import com.mine.geometry_node.core.node.nodes.maths.*;
import com.mine.geometry_node.core.node.nodes.maths.operation.*;
import com.mine.geometry_node.core.node.nodes.quest.*;
import com.mine.geometry_node.core.node.nodes.special.RerouteNode;

public class BuiltinNodesPlugin implements GeometryNodePlugin {

    @Override
    public String addonId() {
        return "geometry_node";
    }

    @Override
    public void registerMarkerTypes(MarkerRegistrationContext registry) {
        registry.register(new MarkerType(MarkerTypeRegistry.DEFAULT_TYPE_ID,
                "geometry_node.marker.type.default", 0xFF4DA3FF, MarkerTypeRegistry.DEFAULT_RENDERER_ID));
        registry.register(new MarkerType("geometry_node:objective",
                "geometry_node.marker.type.objective", 0xFFFFA726, MarkerTypeRegistry.DEFAULT_RENDERER_ID));
        registry.register(new MarkerType("geometry_node:danger",
                "geometry_node.marker.type.danger", 0xFFE05A5A, MarkerTypeRegistry.DEFAULT_RENDERER_ID));
        registry.register(new MarkerType("geometry_node:destination",
                "geometry_node.marker.type.destination", 0xFF55B96B, MarkerTypeRegistry.DEFAULT_RENDERER_ID));
        registry.register(new MarkerType("geometry_node:entity",
                "geometry_node.marker.type.entity", 0xFFFF5C8A, MarkerTypeRegistry.DEFAULT_RENDERER_ID));
    }

    @Override
    public void registerNodes(NodeRegistrationContext registry) {
        System.out.println("[BuiltinNodesPlugin] Start to register Nodes...");

        registry.register("behavior/control", new BehaviorRootNode());
        registry.register("behavior/control", new BehaviorSequenceNode());
        registry.register("behavior/control", new BehaviorSelectorNode());
        registry.register("behavior/condition", new BehaviorConditionNode());
        registry.register("behavior/condition", new BehaviorHasValidTargetNode());
        registry.register("behavior/decorator", new BehaviorGuardNode());
        registry.register("behavior/decorator", new BehaviorInverterNode());
        registry.register("behavior/action", new BehaviorWaitNode());
        registry.register("behavior/action", new BehaviorIdleNode());
        registry.register("behavior/blackboard", new BehaviorGetBlackboardNode());
        registry.register("behavior/blackboard", new BehaviorHasBlackboardNode());
        registry.register("behavior/blackboard", new BehaviorSetBlackboardNode());
        registry.register("behavior/blackboard", new BehaviorClearBlackboardNode());
        registry.register("behavior/control", new BehaviorReactiveSequenceNode());
        registry.register("behavior/control", new BehaviorPrioritySelectorNode());
        registry.register("behavior/decorator", new BehaviorRepeatNode());
        registry.register("behavior/decorator", new BehaviorRetryNode());
        registry.register("behavior/decorator", new BehaviorTimeoutNode());
        registry.register("behavior/decorator", new BehaviorCooldownNode());
        registry.register("behavior/decorator", new BehaviorAlwaysSucceedNode());
        registry.register("behavior/decorator", new BehaviorAlwaysFailNode());
        registry.register("behavior/condition", new BehaviorBlackboardValueChangedNode());
        registry.register("behavior/condition", new BehaviorCanNavigateToNode());
        registry.register("behavior/entity", new BehaviorSelectTargetNode());
        registry.register("behavior/entity", new BehaviorClearTargetNode());
        registry.register("behavior/entity", new BehaviorMoveToNode());
        registry.register("behavior/entity", new BehaviorFollowNode());
        registry.register("behavior/entity", new BehaviorPatrolNode());
        registry.register("behavior/entity", new BehaviorStopMovingNode());
        registry.register("behavior/entity", new BehaviorWanderNode());
        registry.register("behavior/entity", new BehaviorLookAtNode());
        registry.register("behavior/entity", new BehaviorSetAttackTargetNode());

        registry.register("layout", new RerouteNode());

        // --- ACTIONS ---
//        registry.register("actions", new ExecuteCommand());
//        registry.register("actions", new LaunchProjectile());
//        registry.register("actions/visual", new Raycast());
        registry.register("actions/visual", new MultiRaycast());
        registry.register("actions/visual", new DrawDebugLine());
        registry.register("actions/visual", new DrawLaserBeam());
        registry.register("actions/visual", new DrawRayBeam());
        registry.register("actions/visual", new DrawItemVisual());
        registry.register("actions/visual", new DrawImageVisual());
        registry.register("actions/marker", new CreateMarker());
        registry.register("actions/marker", new RemoveMarker());
        registry.register("actions/area", new CreateArea());
        registry.register("actions/area", new RemoveArea());
        registry.register("actions/area", new CreateForceField());
        registry.register("actions/area", new RemoveForceField());
        registry.register("actions/entity", new SetEntityChunkLoading());

//        registry.register("actions", new SpawnHitbox());

        // Actions/AI
//        registry.register("actions/ai", new ClearEntityTarget());
//        registry.register("actions/ai", new MapsToPos());
//        registry.register("actions/ai", new SetEntityTarget());
//        registry.register("actions/ai", new StopNavigation());

        // Actions/Block
        registry.register("actions/block", new BreakBlock());
        registry.register("actions/block", new IgniteBlock());
//        registry.register("actions/block", new UpdateBlock());
        registry.register("actions/block", new FillBlock());
        registry.register("actions/block", new PlaceBlock());
        registry.register("actions/block", new SpawnFallingBlock());
        registry.register("actions/block", new SetBlockState());
        registry.register("actions/block", new SetBlocksOnGeometry());
        registry.register("actions/schematic", new CreateSchematicProjection());
        registry.register("actions/schematic", new RemoveSchematicProjection());
        registry.register("actions/schematic", new RevertSchematicPlacement());
        registry.register("actions/schematic", new RepairSchematicPlacement());
        // actions/block
        registry.register("actions/block", new SetBlockFacing());
        registry.register("actions/block", new SetBlockHorizontalFacing());
        registry.register("actions/block", new SetBlockAxis());
        registry.register("actions/block", new SetBlockHorizontalAxis());
        registry.register("actions/block", new SetBlockPowered());
        registry.register("actions/block", new SetBlockOpen());
        registry.register("actions/block", new SetBlockLit());
        registry.register("actions/block", new SetBlockEnabled());
        registry.register("actions/block", new SetBlockWaterlogged());
        registry.register("actions/block", new SetBlockAttached());
        registry.register("actions/block", new SetBlockTriggered());
        registry.register("actions/block", new SetBlockExtended());
        registry.register("actions/block", new SetBlockEye());
        registry.register("actions/block", new SetBlockInWall());
        registry.register("actions/block", new SetBlockLocked());
        registry.register("actions/block", new SetBlockPersistent());
        registry.register("actions/block", new SetBlockSnowy());
        registry.register("actions/block", new SetBlockDisarmed());
        registry.register("actions/block", new SetBlockOccupied());
        registry.register("actions/block", new SetBlockPower());
        registry.register("actions/block", new SetBlockLevel());
        registry.register("actions/block", new SetBlockAge());
        registry.register("actions/block", new SetBlockDistance());
        registry.register("actions/block", new SetBlockRotation());
        registry.register("actions/block", new SetBlockDelay());
        registry.register("actions/block", new SetBlockLayers());
        registry.register("actions/block", new SetBlockCharges());
        registry.register("actions/block", new SetBlockHalf());
        registry.register("actions/block", new SetBlockDoubleBlockHalf());
        registry.register("actions/block", new SetBlockSlabType());
        registry.register("actions/block", new SetBlockStairsShape());
        registry.register("actions/block", new SetBlockAttachFace());
        registry.register("actions/block", new SetBlockDoorHinge());
        registry.register("actions/block", new SetBlockComparatorMode());
        registry.register("actions/block", new SetBlockChestType());
        registry.register("actions/block", new SetBlockRailShape());

        // Actions/Entity
        registry.register("actions/entity", new AddForce());
        registry.register("actions/entity", new DamageEntity());
        registry.register("actions/entity", new HealEntity());
        registry.register("actions/entity", new AddEffect());
        registry.register("actions/entity", new ClearEffect());
        registry.register("actions/entity", new ClearAllEffects());
        registry.register("actions/entity", new IgniteEntity());
        registry.register("actions/entity", new ExtinguishEntity());
        registry.register("actions/entity", new ClearInvulnerableTicks());
//        registry.register("actions/entity", new _DismountEntity());
//        registry.register("actions/entity", new GetInvulnerableTicks());
        registry.register("actions/entity", new GrantDamageTypeImmunity());
        registry.register("actions/entity", new KillEntity());
        registry.register("actions/entity", new LeashEntity());
        registry.register("actions/entity", new MountEntity());
        registry.register("actions/entity", new RevokeDamageTypeImmunity());
        registry.register("actions/entity", new SetCustomName());
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
        registry.register("actions/entity", new SetEntityInvisible());
        registry.register("actions/entity", new SetEntityGlowing());
        registry.register("actions/entity", new SetEntitySilent());
        registry.register("actions/entity", new SetEntityMoveSpeed());
        registry.register("actions/entity", new SetEntityStepHeight());
        registry.register("actions/entity", new AddEntityTag());
        registry.register("actions/entity", new ClearEntityTags());
        registry.register("actions/entity", new RemoveEntityTag());
        registry.register("actions/entity", new SetEntityCanPickUpLoot());
        registry.register("actions/entity", new SetEntityInvulnerable());
        registry.register("actions/entity", new SetEntityNoAI());
        registry.register("actions/entity", new SetEntityNoGravity());
        registry.register("actions/entity", new SetEntityPersistence());
        registry.register("actions/entity", new ShootProjectile());

        // Action/DisplayEntity
        registry.register("actions/display_entity", new SpawnInteractionEntity());
        registry.register("actions/display_entity", new SpawnMarkerEntity());
        registry.register("actions/display_entity", new SpawnBlockDisplayEntity());
        registry.register("actions/display_entity", new SpawnItemDisplayEntity());
        registry.register("actions/display_entity", new SpawnTextDisplayEntity());
        registry.register("actions/display_entity", new SetBlockDisplayState());
        registry.register("actions/display_entity", new SetDisplayStyle());
        registry.register("actions/display_entity", new SetDisplayTransform());
        registry.register("actions/display_entity", new SetDisplayPosition());

        // Actions/Inventory
        registry.register("actions/inventory", new ClearSlots());
        registry.register("actions/inventory", new ClearSlot());
        registry.register("actions/inventory", new SetSlotItem());
        registry.register("actions/inventory", new DropSlotItem());
        registry.register("actions/inventory", new RemoveItemsFromInventory());
        registry.register("actions/inventory", new AddItemToInventory());

        // Actions/Item
        registry.register("actions/item", new DamageItemStack());
        registry.register("actions/item", new RepairItemStack());
        registry.register("actions/item", new SetDamage());
        registry.register("actions/item", new SetItemCount());
        registry.register("actions/item", new SetMaxDamage());
        registry.register("actions/item", new SetMaxStackSize());
        registry.register("actions/item", new SetRepairCost());
        registry.register("actions/item", new SetUnbreakable());
        registry.register("actions/item", new AddEnchantment());
        registry.register("actions/item", new RemoveEnchantment());
        registry.register("actions/item", new ClearAllEnchantments());
        registry.register("actions/item", new AddStoredEnchantment());
        registry.register("actions/item", new ClearAllStoredEnchantments());
        registry.register("actions/item", new AddAttributeModifier());
        registry.register("actions/item", new GiveItemStackToPlayer());
        registry.register("actions/item", new SetItemName());
        registry.register("actions/item", new SetEnchantmentGlintOverride());


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
        registry.register("data/world", new TargetSelector());
        registry.register("data", new GetScopedState());
        registry.register("data", new SetScopedState());
        registry.register("data", new HasScopedState());
        registry.register("data", new ClearScopedState());
        registry.register("data", new DataLibraryReference());
        registry.register("data/player", new IsKeyPressed());

        // Data/World
        registry.register("data/world", new GetGameTime());
        registry.register("data/world", new GetWorldTime());

        // Data/Entity
        registry.register("data/entity", new GetEntitiesByRadius());
        registry.register("data/entity", new GetEntitiesbyRotationBox());
        registry.register("data/entity", new PickEntityTemplate());

        // Data/Entity/Attribution
        registry.register("data/entity/attribution", new GetEntityDimension());
        registry.register("data/entity/attribution", new GetEntityEyePosition());
        registry.register("data/entity/attribution", new GetEntityFallDistance());
        registry.register("data/entity/attribution", new GetEntityHealth());
        registry.register("data/entity/attribution", new GetEntityMaxHealth());
        registry.register("data/entity/attribution", new GetEntityRotation());
        registry.register("data/entity/attribution", new GetEntityPosition());
        registry.register("data/entity/attribution", new GetEntityTags());
        registry.register("data/entity/attribution", new GetEntityUUID());
        registry.register("data/entity/attribution", new GetEntityVelocity());
        registry.register("data/entity/attribution", new GetEntityVisibleName());
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
        registry.register("data/type", new GetBlockState());
        registry.register("data/type", new GetBlockType());
        registry.register("data/type", new GetDamageType());
        registry.register("data/type", new GetDimension());
        registry.register("data/type", new GetEffect());
        registry.register("data/type", new GetEntityType());
        registry.register("data/type", new GetItemType());
        registry.register("data/type", new GetItemStack());
        registry.register("data/type", new GetSound());
        registry.register("data/type", new GetPortType());

        // Data/Inventory
        registry.register("data/inventory", new GetSlot());
        registry.register("data/inventory", new SlotFromIndex());
        registry.register("data/inventory", new PickItemStack());
        registry.register("data/inventory", new GetSlotItem());
        registry.register("data/inventory", new CountInventoryItem());
        registry.register("data/inventory", new FindInventorySlots());

        // Data/Item/Attribution
        registry.register("data/item/attribution", new GetItemId());
        registry.register("data/item/attribution", new GetItemCount());
        registry.register("data/item/attribution", new GetItemName());
        registry.register("data/item/attribution", new IsItemEmpty());
        registry.register("data/item/attribution", new IsItemDamageable());
        registry.register("data/item/attribution", new GetItemDamage());
        registry.register("data/item/attribution", new GetItemMaxDamage());
        registry.register("data/item/attribution", new GetItemDurability());
        registry.register("data/item/attribution", new GetItemMaxStackSize());
        registry.register("data/item/attribution", new HasItemEnchantments());
        registry.register("data/item/attribution", new HasItemCustomName());

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
        registry.register("data/value", new StringExpression());
        registry.register("data/value", new PathSelection());
        registry.register("data/value", new IntValue());
        registry.register("data/value", new FloatValue());
        registry.register("data/value", new BoolValue());

        // Geometry
        registry.register("geometry/mesh", new CreateMesh());
        registry.register("geometry/mesh", new CreateGeometryDebugMesh());
        registry.register("geometry/mesh", new DeleteGeometryDebugMesh());

        // --- DIALOGUE ---
        registry.register("dialogue", new BeginDialogue());
        registry.register("dialogue", new ShowDialoguePage());
        registry.register("dialogue", new ShowDialogueChoices());
        registry.register("dialogue", new CreateDialogueChoice());
        registry.register("dialogue", new FormatDialogueText());
        registry.register("dialogue", new CloseDialogue());
        registry.register("dialogue", new OpenShop());
        registry.register("dialogue", new AdjustShopTradeUses());

        // --- QUEST ---
        registry.register("quest", new AddQuestToList());
        registry.register("quest", new AcceptQuest());
        registry.register("quest", new CreateQuestCondition());
        registry.register("quest", new QuestVisibilityConditions());
        registry.register("quest", new QuestAcceptConditions());
        registry.register("quest", new QuestCompletionConditions());
        registry.register("quest", new SetQuestStatus());
        registry.register("quest", new GetQuestStatus());
        registry.register("quest", new GetRegisteredQuestStatus());
        registry.register("quest", new SetQuestCounter());
        registry.register("quest", new GetQuestCounter());
        registry.register("quest", new SubmitQuest());
        registry.register("quest", new OpenQuestScreen());
        OnQuestStatusChanged.registerEventPrecheck();
        registry.register("quest", new OnQuestStatusChanged());

        // --- EVENTS ---

        // Events/Area
        registry.register("events/area", new OnAreaEvent());

        // Events/Dialogue
        registry.register("events/dialogue", new OnShopTradeSuccess());

        // Events/Display Entity
        registry.register("events/display_entity", new OnInteraction());

        // Events/Block
        registry.register("events/block", new OnBlockBreak());
//        registry.register("events/block", new OnBlockIgnite());
        registry.register("events/block", new OnMultiblockBuilt());
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
        registry.register("events/entity", new OnEntityKill());
        registry.register("events/entity", new OnEntityDropItem());
        registry.register("events/entity", new OnEntityGriefBlock());
        registry.register("events/entity", new OnEntityHeal());
        registry.register("events/entity", new OnEntityHurt());
        registry.register("events/entity", new OnEntityGainItem());
        registry.register("events/entity", new OnEntityJump());
        registry.register("events/entity", new OnEntityMount());
        registry.register("events/entity", new OnEntityPickupItem());
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
        registry.register("events/entity", new OnEntityTick());

        // Events/Inventory
//        registry.register("events/inventory", new OnContainerClose());
        registry.register("events/inventory", new OnContainerOpen());
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
        registry.register("events/player", new OnPlayerWakeUp());
        registry.register("events/player", new OnPlayerKeyEvent());

        // Events/World
        registry.register("events/world", new OnChunkLoad());
        registry.register("events/world", new OnChunkUnload());
        registry.register("events/world", new OnExplosion());
        registry.register("events/world", new OnLightningStrike());
        registry.register("events/world", new OnPortalCreate());
        registry.register("events/world", new OnStructureGenerate());

        // --- FUNCTIONS ---
        registry.register("functions/graph", new FinishGraph());
        registry.register("functions/graph", new GraphOwner());
        registry.register("functions/graph", new ReceiveBlueprint());
        registry.register("functions/graph", new TriggerBlueprint());

        registry.register("functions/time", new Function_Delay_tick());
        registry.register("functions/color", new ColorRamp());
        registry.register("functions/color", new CombineColor());
        registry.register("functions/color", new SeparateColor());

        // --- LOGICS ---
        registry.register("logics", new Contain());
        registry.register("logics", new Equal());
        registry.register("logics", new HasTag());
        registry.register("logics", new GetTags());
        registry.register("logics", new ForEachLoop());
        registry.register("logics", new ForLoop());
        registry.register("logics", new WhileLoop());
        registry.register("logics", new IF());
        registry.register("logics", new Switch());
        registry.register("logics", new CombineFlow());

        // --- MATHS ---
        registry.register("maths", new SnapshotValue());
        registry.register("maths", new RandomValue());
        registry.register("maths/operation", new MathExpression());
        registry.register("maths/operation", new MathOperation());
        registry.register("maths/vector", new VectorOperation());
        registry.register("maths/vector", new ReflectVector());
        registry.register("maths/vector", new SeparateXYZ());
        registry.register("maths/vector", new CombineXYZ());

        System.out.println("[BuiltinNodesPlugin] Register Finished");
    }

}
