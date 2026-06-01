package com.mine.geometry_node.core.execution.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * [全局存储] 负责持久化全局绑定的蓝图 ID。
 * 该数据保存在存档的 {@code data/geometry_node_global.dat} 文件中。
 * 这里的图 ID 通常是绑定到世界本身（或作为全局事件监听器）运行的，而非特定实体。
 */
public class GlobalGraphStorage extends SavedData {
    // Constants & Fields
    private static final String DATA_NAME = "geometry_node_global";
    private static final String TAG_GRAPHS = "GlobalGraphs";

    // 全局绑定图ID集合
    private final Set<String> globalGraphs = new HashSet<>();

    private static final SavedData.Factory<GlobalGraphStorage> FACTORY = new SavedData.Factory<>(
            GlobalGraphStorage::new,
            GlobalGraphStorage::load,
            null
    );

    // Static Access

    /**
     * 获取当前存档的全局图存储实例。
     * @param level 任意服务端世界层级
     * @return 存储实例（如果不存在则自动创建）
     */
    public static GlobalGraphStorage get(ServerLevel level) {
        return level.getServer().overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, DATA_NAME);
    }

    // Constructor

    public GlobalGraphStorage() {}

    // Business Logic (API)

    /**
     * 获取所有全局绑定图 ID。
     * @return 不可修改的集合视图，防止外部直接操作导致未标记 Dirty。
     */
    public Set<String> getGraphs() {
        return Collections.unmodifiableSet(globalGraphs);
    }

    /**
     * 添加全局图绑定。
     * @param graphId 图的唯一标识符
     */
    public void addGraph(String graphId) {
        if (globalGraphs.add(graphId)) {
            setDirty();
        }
    }

    /**
     * 移除全局图绑定。
     * @param graphId 图的唯一标识符
     */
    public void removeGraph(String graphId) {
        if (globalGraphs.remove(graphId)) {
            setDirty();
        }
    }

    /**
     * 清空所有全局图绑定。
     */
    public void clearGraphs() {
        if (!globalGraphs.isEmpty()) {
            globalGraphs.clear();
            setDirty();
        }
    }

    // NBT Serialization

    /**
     * 从磁盘 NBT 数据恢复状态。
     */
    public static GlobalGraphStorage load(CompoundTag tag, HolderLookup.Provider provider) {
        GlobalGraphStorage storage = new GlobalGraphStorage();

        if (tag.contains(TAG_GRAPHS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_GRAPHS, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                storage.globalGraphs.add(list.getString(i));
            }
        }

        return storage;
    }

    /**
     * 将当前状态保存到磁盘 NBT。
     */
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        ListTag list = new ListTag();
        for (String graphId : globalGraphs) {
            list.add(StringTag.valueOf(graphId));
        }

        tag.put(TAG_GRAPHS, list);
        return tag;
    }
}