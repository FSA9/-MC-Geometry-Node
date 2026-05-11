package com.mine.geometry_node;

import com.mine.geometry_node.core.command.ServerGraphCommand;
import com.mine.geometry_node.core.execution.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.execution.event_handler.GraphEventHandler;
import com.mine.geometry_node.core.execution.attachment.EntityImmunityAttachment;
import com.mine.geometry_node.core.execution.storage.DynamicGraphManager;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.node.NodeRegistry;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.packs.PackType;
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
                    .serialize(new IAttachmentSerializer<CompoundTag, EntityGraphAttachment>() {
                        @Override
                        public CompoundTag write(EntityGraphAttachment attachment, HolderLookup.Provider provider) {
                            return attachment.save(new CompoundTag(), provider);
                        }

                        @Override
                        public EntityGraphAttachment read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                            EntityGraphAttachment newAttachment = new EntityGraphAttachment();
                            newAttachment.load(tag, provider);
                            return newAttachment;
                        }
                    }).build());

    public static final Supplier<AttachmentType<EntityImmunityAttachment>> IMMUNITY_ATTACHMENT =
            ATTACHMENT_TYPES.register("immunities", () -> AttachmentType.builder(EntityImmunityAttachment::new)
                    .serialize(new IAttachmentSerializer<ListTag, EntityImmunityAttachment>() {
                        @Override
                        public ListTag write(EntityImmunityAttachment attachment, HolderLookup.Provider provider) {
                            return attachment.save(provider);
                        }

                        @Override
                        public EntityImmunityAttachment read(IAttachmentHolder holder, ListTag tag, HolderLookup.Provider provider) {
                            EntityImmunityAttachment attachment = new EntityImmunityAttachment();
                            attachment.load(tag, provider);
                            return attachment;
                        }
                    })
                    .copyOnDeath()
                    .build());

    public GeometryNode(IEventBus modEventBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);

        // 初始化网络包
        NetworkHandler.init();

        // 初始化节点注册表
        NodeRegistry.INSTANCE.init();

        // 初始化蓝图系统事件引擎！
        GraphEventHandler.init();

        // 注册蓝图资源管理器 (监听 data/*/graphs/ 目录下的 JSON)
        ReloadListenerRegistry.register(PackType.SERVER_DATA, GraphResourceManager.getInstance());

        ATTACHMENT_TYPES.register(modEventBus);

        // 注册测试指令 (基于 Architectury API)
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            ServerGraphCommand.register(dispatcher);
        });
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