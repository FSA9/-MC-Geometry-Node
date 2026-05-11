package com.mine.geometry_node.core.execution.attachment;

import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.execution.GraphProcess;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mine.geometry_node.core.execution.RuntimeGraphIndex;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * [数据附加层 - 重构版]
 * 职责：
 * 1. 记录持久化绑定关系 (BoundGraphs)
 * 2. 持有常驻虚拟机进程 (Processes Map)
 */
public class EntityGraphAttachment {

    // 静态绑定的图 ID 集合 (存盘)
    private final Set<String> boundGraphs = new HashSet<>();

    // 【重构点】常驻进程字典：GraphId -> 常驻进程实例
    // 进程不再频繁销毁，而是随实体同生共死
    private final Map<String, GraphProcess> processes = new HashMap<>();

    // 持久化属性存储
    private final Map<String, Object> attributes = new HashMap<>();

    public EntityGraphAttachment() {}

    /**
     * [心跳驱动]
     * 驱动所有常驻进程处理它们的内部逻辑（如唤醒延时线程）。
     */
    public void tick(Entity entity) {
        if (processes.isEmpty() || !(entity.level() instanceof ServerLevel serverLevel)) return;

        long currentTime = serverLevel.getGameTime();

        // 遍历所有常驻进程进行心跳
        for (GraphProcess process : processes.values()) {
            process.setEnvironment(serverLevel, entity);
            process.tick(currentTime);
        }

        // 注意：此处不再需要 iterator.remove() 逻辑，进程由 bind/unbind 显式管理
    }

    // --- 绑定管理 ---

    public void bindGraph(String graphId) {
        this.boundGraphs.add(graphId);
    }

    public void unbindGraph(String graphId) {
        this.boundGraphs.remove(graphId);
        this.processes.remove(graphId); // 显式移除进程
    }

    public Set<String> getBoundGraphs() {
        return Collections.unmodifiableSet(boundGraphs);
    }

    public void clearGraphs() {
        this.boundGraphs.clear();
        this.processes.clear();
    }

    // --- 进程管理 ---

    public void addProcess(GraphProcess process) {
        this.processes.put(process.getGraphId(), process);
    }

    public Collection<GraphProcess> getProcesses() {
        return processes.values();
    }

    public GraphProcess getProcess(String graphId) {
        GraphProcess process = processes.get(graphId);

        RuntimeGraphIndex latestIndex = GraphEngine.getGraphIndex(graphId);

        if (latestIndex != null) {
            if (process == null || process.getIndex() != latestIndex) {
                process = new GraphProcess(graphId, latestIndex);
                addProcess(process);
            }
        }
        return process;
    }

    // --- 属性管理 ---

    public void setAttribute(String key, Object value) {
        if (value == null) this.attributes.remove(key);
        else this.attributes.put(key, value);
    }

    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    // --- 序列化 (适配新 GraphProcess) ---

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // 1. 保存绑定关系
        if (!boundGraphs.isEmpty()) {
            ListTag boundList = new ListTag();
            for (String graphId : boundGraphs) boundList.add(StringTag.valueOf(graphId));
            tag.put("BoundGraphs", boundList);
        }

        // 2. 保存常驻进程状态 (变量、挂起的线程等)
        if (!processes.isEmpty()) {
            ListTag processList = new ListTag();
            for (GraphProcess process : processes.values()) {
                CompoundTag pTag = new CompoundTag();
                process.save(pTag, provider);
                processList.add(pTag);
            }
            tag.put("ActiveProcesses", processList);
        }

        // 3. 属性
        if (!attributes.isEmpty()) {
            CompoundTag attrTag = new CompoundTag();
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                Tag t = com.mine.geometry_node.core.execution.variables.VariableRegistry.toTag(entry.getValue(), provider);
                if (t != null) attrTag.put(entry.getKey(), t);
            }
            tag.put("Attributes", attrTag);
        }
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        this.boundGraphs.clear();
        this.processes.clear();

        if (tag.contains("BoundGraphs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BoundGraphs", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) this.boundGraphs.add(list.getString(i));
        }

        if (tag.contains("ActiveProcesses", Tag.TAG_LIST)) {
            ListTag list = tag.getList("ActiveProcesses", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag pTag = list.getCompound(i);
                String graphId = pTag.getString("GraphId");
                RuntimeGraphIndex index = GraphEngine.getGraphIndex(graphId);
                if (index != null) {
                    this.processes.put(graphId, new GraphProcess(pTag, index, provider));
                }
            }
        }

        this.attributes.clear();
        if (tag.contains("Attributes", Tag.TAG_COMPOUND)) {
            CompoundTag attrTag = tag.getCompound("Attributes");
            for (String key : attrTag.getAllKeys()) {
                Object obj = com.mine.geometry_node.core.execution.variables.VariableRegistry.fromTag(attrTag.get(key), provider);
                if (obj != null) this.attributes.put(key, obj);
            }
        }
    }
}