package com.mine.geometry_node;

import com.mojang.serialization.MapCodec;
import com.mine.geometry_node.core.command.registry.ModServerCommands;
import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.graph.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventHandler;
import com.mine.geometry_node.core.engine.blueprint.attachment.EntityImmunityAttachment;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeRegistry;
import com.mine.geometry_node.core.engine.graph.scoped.ServerScopedStateStore;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.engine.system.quest.QuestScreenService;
import com.mine.geometry_node.core.engine.system.marker.MarkerService;
import com.mine.geometry_node.core.engine.system.chunk_loading.EntityChunkLoadingService;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferServerConfig;
import com.mine.geometry_node.core.engine.system.quest.storage.EntityQuestAttachment;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.engine.system.schematic.SchematicPlacementDebugSync;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.util.function.Supplier;

@Mod(GeometryNode.MODID)
public class GeometryNode {

    public static final String MODID = "geometry_node";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GeometryNode.MODID);

    public static final Supplier<AttachmentType<EntityGraphAttachment>> GRAPH_DATA_ATTACHMENT =
            ATTACHMENT_TYPES.register("graph_data", () -> AttachmentType.builder(() -> new EntityGraphAttachment())
                    .serialize(new IAttachmentSerializer<EntityGraphAttachment>() {
                        @Override
                        public EntityGraphAttachment read(IAttachmentHolder holder, ValueInput input) {
                            EntityGraphAttachment newAttachment = new EntityGraphAttachment();
                            CompoundTag tag = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
                            newAttachment.load(tag, input.lookup());
                            return newAttachment;
                        }

                        @Override
                        public boolean write(EntityGraphAttachment attachment, ValueOutput output) {
                            CompoundTag tag = attachment.save(new CompoundTag(), HolderLookup.Provider.create(java.util.stream.Stream.empty()));
                            output.store(tag);
                            return !tag.isEmpty();
                        }
                    }).build());

    public static final Supplier<AttachmentType<EntityImmunityAttachment>> IMMUNITY_ATTACHMENT =
            ATTACHMENT_TYPES.register("immunities", () -> AttachmentType.builder(EntityImmunityAttachment::new)
                    .serialize(new IAttachmentSerializer<EntityImmunityAttachment>() {
                        @Override
                        public EntityImmunityAttachment read(IAttachmentHolder holder, ValueInput input) {
                            EntityImmunityAttachment attachment = new EntityImmunityAttachment();
                            CompoundTag tag = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
                            attachment.load(tag.getListOrEmpty("Immunities"), input.lookup());
                            return attachment;
                        }

                        @Override
                        public boolean write(EntityImmunityAttachment attachment, ValueOutput output) {
                            ListTag list = attachment.save(HolderLookup.Provider.create(java.util.stream.Stream.empty()));
                            if (list.isEmpty()) {
                                return false;
                            }
                            CompoundTag tag = new CompoundTag();
                            tag.put("Immunities", list);
                            output.store(tag);
                            return true;
                        }
                    })
                    .copyOnDeath()
                    .build());

    public static final Supplier<AttachmentType<EntityQuestAttachment>> QUEST_DATA_ATTACHMENT =
            ATTACHMENT_TYPES.register("quest_data", () -> AttachmentType.builder(EntityQuestAttachment::new)
                    .serialize(new IAttachmentSerializer<EntityQuestAttachment>() {
                        @Override
                        public EntityQuestAttachment read(IAttachmentHolder holder, ValueInput input) {
                            EntityQuestAttachment attachment = new EntityQuestAttachment();
                            CompoundTag tag = input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
                            attachment.load(tag);
                            return attachment;
                        }

                        @Override
                        public boolean write(EntityQuestAttachment attachment, ValueOutput output) {
                            CompoundTag tag = attachment.save();
                            output.store(tag);
                            return !tag.isEmpty();
                        }
                    })
                    .copyOnDeath()
                    .build());

    public GeometryNode(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(EntityChunkLoadingService::registerTicketController);
        modContainer.registerConfig(ModConfig.Type.SERVER, AssetTransferServerConfig.SPEC,
                "geometry_node-server.toml");

        // 初始化网络包
        NetworkHandler.init();

        // 初始化节点注册表
        NodeRegistry.INSTANCE.init();

        GraphEngineServices.INSTANCE.setScopedStateStore(new ServerScopedStateStore());

        // 初始化图运行时注册表
        GraphRuntimeRegistry.INSTANCE.register(BlueprintRuntime.INSTANCE);
        GraphRuntimeRegistry.INSTANCE.register(DialogueRuntime.INSTANCE);
        GraphRuntimeRegistry.INSTANCE.register(BehaviorTreeRuntime.INSTANCE);

        QuestService.INSTANCE.init();
        QuestScreenService.INSTANCE.init();
        MarkerService.INSTANCE.init();
        EntityChunkLoadingService.INSTANCE.init();

        // 初始化蓝图系统事件引擎
        GraphEventHandler.init();
        SchematicPlacementDebugSync.register();

        // 注册蓝图资源管理器
        ReloadListenerRegistry.register(
                PackType.SERVER_DATA,
                GraphResourceManager.getInstance(),
                Identifier.fromNamespaceAndPath(MODID, "graphs")
        );
        ReloadListenerRegistry.register(
                PackType.SERVER_DATA,
                MultiblockStructureManager.getInstance(),
                Identifier.fromNamespaceAndPath(MODID, "multiblocks")
        );

        ATTACHMENT_TYPES.register(modEventBus);

        // 注册测试指令
        ModServerCommands.register();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }

    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        LOGGER.info("[GeometryNode] Server about to start, loading dynamic graphs...");
        DynamicGraphManager.loadAllFromDisk(event.getServer());
    }
}
