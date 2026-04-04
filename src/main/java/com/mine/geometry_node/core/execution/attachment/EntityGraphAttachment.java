package com.mine.geometry_node.core.execution.attachment;

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
 * [数据附加层] 基于 Architectury Attachment API 实现。
 * <p>
 * 此类作为实体的附加数据组件，职责分为两部分：
 * <ol>
 *     <li><b>持久化绑定 (Bound Graphs):</b> 静态记录该实体被绑定了哪些蓝图 ID（存盘，永久有效）。</li>
 *     <li><b>运行时进程 (Active Processes):</b> 动态记录当前正在运行的蓝图执行线程（由 Tick 驱动）。</li>
 * </ol>
 */
public class EntityGraphAttachment {

    // Fields

    // 静态绑定的图 ID 集合
    private final Set<String> boundGraphs = new HashSet<>();

    // 活跃进程列表
    private final List<GraphProcess> processes = new ArrayList<>();

    // 持久化属性存储 (Attribute 节点)
    private final java.util.Map<String, Object> attributes = new java.util.HashMap<>();

    // Constructor

    public EntityGraphAttachment() {}

    // Logic Loop (Tick)

    /**
     * [心跳驱动] 由 GraphEventHandler 每 Tick 调用，驱动所有挂载的虚拟机运行。
     * @param entity 宿主实体
     */
    public void tick(Entity entity) {
        if (processes.isEmpty()) return;

        // 仅在服务端运行
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        Iterator<GraphProcess> iterator = processes.iterator();
        while (iterator.hasNext()) {
            GraphProcess process = iterator.next();

            // 运行时注入环境上下文
            process.setEnvironment(serverLevel, entity);

            // 驱动单次 Tick 执行
            process.tick(serverLevel.getGameTime());

            // 清理已结束流程
            if (process.isFinished()) {
                iterator.remove();
            }
        }
    }

    // Graph Binding Management (Static API)

    public void bindGraph(String graphId) {
        this.boundGraphs.add(graphId);
    }

    public void unbindGraph(String graphId) {
        this.boundGraphs.remove(graphId);
    }

    public Set<String> getBoundGraphs() {
        return Collections.unmodifiableSet(boundGraphs);
    }

    public void clearGraphs() {
        this.boundGraphs.clear();
    }

    // Process Management (Runtime API)

    public void addProcess(GraphProcess process) {
        this.processes.add(process);
    }

    public List<GraphProcess> getProcesses() {
        return processes;
    }


    // Attribute

    public void setAttribute(String key, Object value) {
        if (value == null) this.attributes.remove(key);
        else this.attributes.put(key, value);
    }
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    // Serialization (NBT)

    /**
     * 序列化逻辑：保存绑定关系以及当前挂起的进程状态。
     */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        // 保存绑定关系
        if (!boundGraphs.isEmpty()) {
            ListTag boundList = new ListTag();
            for (String graphId : boundGraphs) {
                boundList.add(StringTag.valueOf(graphId));
            }
            tag.put("BoundGraphs", boundList);
        }

        // 保存活跃进程
        if (!processes.isEmpty()) {
            ListTag processList = new ListTag();
            for (GraphProcess process : processes) {
                CompoundTag processTag = new CompoundTag();
                process.save(processTag, provider);
                processList.add(processTag);
            }
            tag.put("ActiveProcesses", processList);
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

    /**
     * 反序列化逻辑：恢复绑定关系和进程状态。
     */
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        this.boundGraphs.clear();
        this.processes.clear();

        // 恢复绑定关系
        if (tag.contains("BoundGraphs", Tag.TAG_LIST)) {
            ListTag boundList = tag.getList("BoundGraphs", Tag.TAG_STRING);
            for (int i = 0; i < boundList.size(); i++) {
                this.boundGraphs.add(boundList.getString(i));
            }
        }

        // 恢复活跃进程
        if (tag.contains("ActiveProcesses", Tag.TAG_LIST)) {
            ListTag processList = tag.getList("ActiveProcesses", Tag.TAG_COMPOUND);
            for (int i = 0; i < processList.size(); i++) {
                CompoundTag processTag = processList.getCompound(i);
                String graphId = processTag.getString("GraphId");

                RuntimeGraphIndex index = GraphResourceManager.getInstance().getIndex(graphId);

                if (index != null) {
                    GraphProcess process = new GraphProcess(processTag, index, provider);
                    this.processes.add(process);
                } else {
                    System.err.printf("[EntityGraphAttachment] Failed to restore process '%s' - Graph Index not found.%n", graphId);
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