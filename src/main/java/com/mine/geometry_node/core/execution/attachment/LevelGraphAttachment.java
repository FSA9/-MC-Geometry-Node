package com.mine.geometry_node.core.execution.attachment;

import com.mine.geometry_node.core.execution.GraphProcess;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mine.geometry_node.core.execution.RuntimeGraphIndex;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * [世界级运行容器 - 重构版]
 * <p>
 * 绑定在 ServerLevel (特定维度) 上的“背包”，专门用于运行和持久化
 * 与特定实体无关的全局图进程 (Global Graph Processes)。
 * 现已全面接入“常驻虚拟机”架构。
 */
public class LevelGraphAttachment extends SavedData {

    private static final String DATA_NAME = "geometry_node_level_processes";
    private static final String TAG_PROCESSES = "ActiveProcesses";

    // 【重构点】活跃进程字典：GraphId -> 常驻进程实例
    private final Map<String, GraphProcess> processes = new HashMap<>();

    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * 工厂实例，用于 SavedData 的创建和加载机制。
     */
    private static final SavedData.Factory<LevelGraphAttachment> FACTORY = new SavedData.Factory<>(
            LevelGraphAttachment::new,
            LevelGraphAttachment::load,
            null
    );

    // --- Static Access ---

    public static LevelGraphAttachment get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    // --- Constructor ---

    public LevelGraphAttachment() {}

    // --- Attribute Management ---

    public void setAttribute(String key, Object value) {
        if (value == null) {
            this.attributes.remove(key);
        } else {
            this.attributes.put(key, value);
        }
        this.setDirty();
    }

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    // --- Process Management ---

    public void addProcess(GraphProcess process) {
        this.processes.put(process.getGraphId(), process);
        this.setDirty(); // 添加新进程时标记世界数据需要保存
    }

    public void removeProcess(String graphId) {
        if (this.processes.remove(graphId) != null) {
            this.setDirty();
        }
    }

    public Collection<GraphProcess> getProcesses() {
        return this.processes.values();
    }

    public GraphProcess getProcess(String graphId) {
        return this.processes.get(graphId);
    }

    /**
     * 由 GraphEventHandler 每 Tick 调用。
     */
    public void tick(ServerLevel level) {
        if (processes.isEmpty()) return;

        long currentTime = level.getGameTime();

        // 【重构点】遍历常驻进程，驱动协程等内部心跳，不再移除 isFinished 状态的进程
        for (GraphProcess process : processes.values()) {
            // 1. 注入环境（only world）
            process.setEnvironment(level, null);

            // 2. 驱动单次 Tick (比如唤醒挂起的 Wait 节点)
            process.tick(currentTime);
        }

        // 注意：进程内部的变量改变不会自动触发 setDirty()，这符合 MC 原生机制。
        // 若要严格保证每帧变量都能存盘，可由全局自动保存机制接管，此处无需每 tick setDirty() 以节省 IO。
    }

    // --- NBT Serialization ---

    public static LevelGraphAttachment load(CompoundTag tag, HolderLookup.Provider provider) {
        LevelGraphAttachment attachment = new LevelGraphAttachment();

        if (tag.contains(TAG_PROCESSES, Tag.TAG_LIST)) {
            ListTag processList = tag.getList(TAG_PROCESSES, Tag.TAG_COMPOUND);
            for (int i = 0; i < processList.size(); i++) {
                CompoundTag processTag = processList.getCompound(i);
                String graphId = processTag.getString("GraphId");

                RuntimeGraphIndex index = GraphResourceManager.getInstance().getIndex(graphId);
                if (index != null) {
                    GraphProcess process = new GraphProcess(processTag, index, provider);
                    attachment.processes.put(graphId, process);
                } else {
                    System.err.printf("[LevelGraphAttachment] Failed to restore global process '%s' - Graph not found.%n", graphId);
                }
            }
        }
        if (tag.contains("Attributes", Tag.TAG_COMPOUND)) {
            CompoundTag attrTag = tag.getCompound("Attributes");
            for (String key : attrTag.getAllKeys()) {
                Object obj = com.mine.geometry_node.core.execution.variables.VariableRegistry.fromTag(attrTag.get(key), provider);
                if (obj != null) attachment.attributes.put(key, obj);
            }
        }
        return attachment;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        if (!processes.isEmpty()) {
            ListTag processList = new ListTag();
            for (GraphProcess process : processes.values()) {
                CompoundTag processTag = new CompoundTag();
                process.save(processTag, provider);
                processList.add(processTag);
            }
            tag.put(TAG_PROCESSES, processList);
        }
        if (!attributes.isEmpty()) {
            CompoundTag attrTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                net.minecraft.nbt.Tag t = com.mine.geometry_node.core.execution.variables.VariableRegistry.toTag(entry.getValue(), provider);
                if (t != null) attrTag.put(entry.getKey(), t);
            }
            tag.put("Attributes", attrTag);
        }
        return tag;
    }
}