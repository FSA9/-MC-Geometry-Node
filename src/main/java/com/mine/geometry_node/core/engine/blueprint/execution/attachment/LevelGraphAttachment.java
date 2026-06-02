package com.mine.geometry_node.core.engine.blueprint.execution.attachment;

import com.mine.geometry_node.core.engine.blueprint.execution.GraphProcess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * [世界级运行容器 - 组合版]
 * 继承 SavedData 实现持久化，内部委托 GraphContainer 执行具体逻辑。
 */
public class LevelGraphAttachment extends SavedData {

    private static final String DATA_NAME = "geometry_node_level_processes";

    private final GraphContainer container = new GraphContainer(this::setDirty);

    private static final SavedData.Factory<LevelGraphAttachment> FACTORY = new SavedData.Factory<>(
            LevelGraphAttachment::new,
            LevelGraphAttachment::load,
            null
    );

    public static LevelGraphAttachment get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
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

    public static LevelGraphAttachment load(CompoundTag tag, HolderLookup.Provider provider) {
        LevelGraphAttachment attachment = new LevelGraphAttachment();
        attachment.container.load(tag, provider);
        return attachment;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        return container.save(tag, provider);
    }
}