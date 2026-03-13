package com.mine.geometry_node;

import com.mine.geometry_node.core.command.GraphCommand;
import com.mine.geometry_node.core.execution.attachment.GraphDataAttachment;
import com.mine.geometry_node.core.execution.GraphEventHandler;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.node.NodeRegistry;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
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

    public static final Supplier<AttachmentType<GraphDataAttachment>> GRAPH_DATA_ATTACHMENT =
            ATTACHMENT_TYPES.register("graph_data", () -> AttachmentType.builder(() -> new GraphDataAttachment())
                    .serialize(new IAttachmentSerializer<CompoundTag, GraphDataAttachment>() {
                        @Override
                        public CompoundTag write(GraphDataAttachment attachment, HolderLookup.Provider provider) {
                            return attachment.save(new CompoundTag(), provider);
                        }

                        @Override
                        public GraphDataAttachment read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                            GraphDataAttachment newAttachment = new GraphDataAttachment();
                            newAttachment.load(tag, provider);
                            return newAttachment;
                        }
                    }).build());

    public GeometryNode(IEventBus modEventBus, ModContainer modContainer) {
        // 注册到 NeoForge 事件总线
        NeoForge.EVENT_BUS.register(this);

        // 初始化网络包
        NetworkHandler.init();

        // 初始化节点注册表
        NodeRegistry.INSTANCE.init();

        // 初始化蓝图系统事件引擎！
        GraphEventHandler.init();

        // [新增] 注册蓝图资源管理器 (监听 data/*/graphs/ 目录下的 JSON)
        ReloadListenerRegistry.register(PackType.SERVER_DATA, GraphResourceManager.getInstance());

        ATTACHMENT_TYPES.register(modEventBus);

        // 注册测试指令 (基于 Architectury API)
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            GraphCommand.register(dispatcher);
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[GeometryNode] Server starting, ready to process graphs!");
    }
}