package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.GeometryNode;
import com.mojang.serialization.Codec;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphProcess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * [世界级运行容器 - 组合版]
 * 继承 SavedData 实现持久化，内部委托 GraphContainer 执行具体逻辑。
 */
public class LevelGraphAttachment extends SavedData {

    public static final SavedDataType<LevelGraphAttachment> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "level_processes"),
            ignored -> new LevelGraphAttachment(),
            LevelGraphAttachment::codec
    );

    private final GraphContainer container = new GraphContainer(this::setDirty);

    public static LevelGraphAttachment get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public LevelGraphAttachment() {}

    /**
     * [心跳驱动] 全局图纸不绑实体，故 target 传 null
     */
    public void tick(ServerLevel level) {
        container.tick(level, null);
    }

    // --- API 委托层 ---

    public void addProcess(GraphProcess process) { container.addProcess(process); }
    public void removeProcess(String graphId) { container.removeProcess(graphId); }
    public Collection<GraphProcess> getProcesses() { return container.getProcesses(); }
    public GraphProcess getProcess(String graphId) { return container.getProcess(graphId); }
    public void setAttribute(String key, Object value) { container.setAttribute(key, value); }
    public Object getAttribute(String key) { return container.getAttribute(key); }

    // --- 序列化层 ---

    private static Codec<LevelGraphAttachment> codec(ServerLevel level) {
        HolderLookup.Provider provider = level != null
                ? level.registryAccess()
                : HolderLookup.Provider.create(Stream.<HolderLookup.RegistryLookup<?>>empty());
        return CompoundTag.CODEC.xmap(
                tag -> load(tag, provider),
                attachment -> attachment.saveToTag(new CompoundTag(), provider)
        );
    }

    public static LevelGraphAttachment load(CompoundTag tag, HolderLookup.Provider provider) {
        LevelGraphAttachment attachment = new LevelGraphAttachment();
        attachment.container.load(tag, provider);
        return attachment;
    }

    private CompoundTag saveToTag(CompoundTag tag, HolderLookup.Provider provider) {
        return container.save(tag, provider);
    }
}
