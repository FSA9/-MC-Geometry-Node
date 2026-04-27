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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * [世界级运行容器]
 * <p>
 * 绑定在 ServerLevel (特定维度) 上的“背包”，专门用于运行和持久化
 * 与特定实体无关的全局图进程 (Global Graph Processes)。
 */
public class LevelGraphAttachment extends SavedData {

    private static final String DATA_NAME = "geometry_node_level_processes";
    private static final String TAG_PROCESSES = "ActiveProcesses";

    // 活跃进程列表
    private final List<GraphProcess> processes = new ArrayList<>();

    private final java.util.Map<String, Object> attributes = new java.util.HashMap<>();

    /**
     * 工厂实例，用于 SavedData 的创建和加载机制。
     */
    private static final SavedData.Factory<LevelGraphAttachment> FACTORY = new SavedData.Factory<>(
            LevelGraphAttachment::new,
            LevelGraphAttachment::load,
            null
    );

    // --- Static Access ---

    /**
     * 获取指定维度的世界图容器。
     * 注意：每个维度 (主世界、下界、末地) 都有自己独立的容器！
     * 这确保了在下界触发的图，能正确操作下界的方块。
     */
    public static LevelGraphAttachment get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

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

    // --- Constructor ---

    public LevelGraphAttachment() {}

    // --- Process Management ---

    public void addProcess(GraphProcess process) {
        this.processes.add(process);
        this.setDirty();
    }

    /**
     * 由 GraphEventHandler 每 Tick 调用。
     */
    public void tick(ServerLevel level) {
        if (processes.isEmpty()) return;

        boolean needsSave = false;
        Iterator<GraphProcess> iterator = processes.iterator();

        while (iterator.hasNext()) {
            GraphProcess process = iterator.next();

            // 1. 注入环境（only world）
            process.setEnvironment(level, null);

            // 2. 驱动单次 Tick
            process.tick(level.getGameTime());

            // 3. 清理已结束流程
            if (process.isFinished()) {
                iterator.remove();
                needsSave = true;
            }
        }
        if (needsSave) {
            this.setDirty();
        }
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
                    attachment.processes.add(process);
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
            for (GraphProcess process : processes) {
                CompoundTag processTag = new CompoundTag();
                process.save(processTag, provider);
                processList.add(processTag);
            }
            tag.put(TAG_PROCESSES, processList);
        }
        if (!attributes.isEmpty()) {
            CompoundTag attrTag = new CompoundTag();
            for (java.util.Map.Entry<String, Object> entry : attributes.entrySet()) {
                net.minecraft.nbt.Tag t = com.mine.geometry_node.core.execution.variables.VariableRegistry.toTag(entry.getValue(), provider);
                if (t != null) attrTag.put(entry.getKey(), t);
            }
            tag.put("Attributes", attrTag);
        }
        return tag;
    }

    public List<GraphProcess> getProcesses() {
        return this.processes;
    }
}