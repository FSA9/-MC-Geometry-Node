package com.mine.geometry_node.core.engine.blueprint.attachment;

import com.mine.geometry_node.GeometryNode;
import com.mojang.serialization.Codec;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Set;

/**
 * [全局存储] 负责持久化全局绑定的蓝图 ID。
 * 该数据保存在存档的 {@code data/geometry_node/global.dat} 文件中。
 * 这里的图 ID 通常是绑定到世界本身（或作为全局事件监听器）运行的，而非特定实体。
 */
public class GlobalGraphStorage extends SavedData {
    // Constants & Fields
    private static final String TAG_GRAPHS = "GlobalGraphs";
    private static final Codec<GlobalGraphStorage> CODEC = CompoundTag.CODEC.xmap(
            GlobalGraphStorage::load,
            storage -> storage.saveToTag(new CompoundTag())
    );

    public static final SavedDataType<GlobalGraphStorage> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GeometryNode.MODID, "global"),
            GlobalGraphStorage::new,
            CODEC
    );

    // 全局绑定图ID集合
    private final GraphBindingSet globalGraphs = new GraphBindingSet();

    // Static Access

    /**
     * 获取当前存档的全局图存储实例。
     * @param level 任意服务端世界层级
     * @return 存储实例（如果不存在则自动创建）
     */
    public static GlobalGraphStorage get(ServerLevel level) {
        return level.getServer().getDataStorage().computeIfAbsent(TYPE);
    }

    // Constructor

    public GlobalGraphStorage() {}

    // Business Logic (API)

    /**
     * 获取所有全局绑定图 ID。
     * @return 不可修改的集合视图，防止外部直接操作导致未标记 Dirty。
     */
    public Set<String> getGraphs() {
        return globalGraphs.graphIds(GraphKind.BLUEPRINT);
    }

    public Set<GraphBindingKey> getBindings() {
        return globalGraphs.all();
    }

    /**
     * 添加全局图绑定。
     * @param graphId 图的唯一标识符
     */
    public void addGraph(String graphId) {
        addGraph(GraphBindingKey.blueprint(graphId));
    }

    public void addGraph(GraphBindingKey binding) {
        if (globalGraphs.add(binding)) {
            setDirty();
        }
    }

    /**
     * 移除全局图绑定。
     * @param graphId 图的唯一标识符
     */
    public void removeGraph(String graphId) {
        removeGraph(GraphBindingKey.blueprint(graphId));
    }

    public void removeGraph(GraphBindingKey binding) {
        if (globalGraphs.remove(binding)) {
            setDirty();
        }
    }

    /**
     * 清空所有全局图绑定。
     */
    public void clearGraphs() {
        if (globalGraphs.clear(GraphKind.BLUEPRINT)) {
            setDirty();
        }
    }

    // NBT Serialization

    /**
     * 从磁盘 NBT 数据恢复状态。
     */
    public static GlobalGraphStorage load(CompoundTag tag) {
        GlobalGraphStorage storage = new GlobalGraphStorage();

        ListTag list = tag.getListOrEmpty(TAG_GRAPHS);
        for (int i = 0; i < list.size(); i++) {
            String graphId = list.getStringOr(i, "");
            if (!graphId.isEmpty()) {
                storage.globalGraphs.add(GraphBindingKey.blueprint(graphId));
            }
        }

        return storage;
    }

    /**
     * 将当前状态保存到磁盘 NBT。
     */
    CompoundTag saveToTag(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String graphId : getGraphs()) {
            list.add(StringTag.valueOf(graphId));
        }

        tag.put(TAG_GRAPHS, list);
        return tag;
    }
}
