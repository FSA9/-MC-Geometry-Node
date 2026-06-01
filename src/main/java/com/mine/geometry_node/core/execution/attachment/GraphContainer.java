package com.mine.geometry_node.core.execution.attachment;

import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.execution.GraphProcess;
import com.mine.geometry_node.core.execution.GraphProcessSerializer;
import com.mine.geometry_node.core.execution.RuntimeGraphIndex;
import com.mine.geometry_node.core.execution.variables.VariableRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [蓝图运行容器核心] (组合模式)
 * 抽离了 Entity 和 Level 共用的虚拟机调度、属性存储和存档逻辑。
 */
public class GraphContainer {

    private final Map<String, GraphProcess> processes = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();

    // 脏标记回调 (用于通知 Level 保存存档)
    private final Runnable dirtyMarker;

    public GraphContainer(Runnable dirtyMarker) {
        this.dirtyMarker = dirtyMarker != null ? dirtyMarker : () -> {};
    }

    /**
     * [心跳驱动] 驱动所有常驻进程处理延时任务。
     */
    public void tick(ServerLevel level, @Nullable Entity target) {
        if (processes.isEmpty()) return;
        long currentTime = level.getGameTime();
        for (GraphProcess process : List.copyOf(processes.values())) {
            process.setEnvironment(level, target);
            process.tick(currentTime);
        }
    }

    /**
     * [智能获取进程] 自带热更新比对机制。
     */
    public GraphProcess getProcess(String graphId) {
        GraphProcess process = this.processes.get(graphId);
        RuntimeGraphIndex latestIndex = GraphEngine.getGraphIndex(graphId);

        if (latestIndex != null) {
            // 如果内存没进程，或者图纸版本更新了，强行重建
            if (process == null || process.getIndex() != latestIndex) {
                process = new GraphProcess(graphId, latestIndex);
                addProcess(process);
            }
        }
        return process;
    }

    /**
     * [挂载进程]
     */
    public void addProcess(GraphProcess process) {
        this.processes.put(process.getGraphId(), process);
        this.dirtyMarker.run(); // 触发存盘
    }

    /**
     * [卸载进程]
     */
    public void removeProcess(String graphId) {
        if (this.processes.remove(graphId) != null) {
            this.dirtyMarker.run();
        }
    }

    public Collection<GraphProcess> getProcesses() {
        return this.processes.values();
    }

    /**
     * [清理全部]
     */
    public void clear() {
        this.processes.clear();
        this.attributes.clear();
        this.dirtyMarker.run();
    }

    /**
     * [属性写入]
     */
    public void setAttribute(String key, Object value) {
        if (value == null) this.attributes.remove(key);
        else this.attributes.put(key, value);
        this.dirtyMarker.run();
    }

    /**
     * [属性读取]
     */
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * [序列化] 将进程和属性保存到 NBT
     */
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return GraphProcessSerializer.saveContainer(this, tag, provider);
    }

    /**
     * [反序列化] 从 NBT 恢复进程和属性
     */
    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        GraphProcessSerializer.loadContainer(this, tag, provider);
    }

    public Map<String, GraphProcess> getProcessesMap() {
        return this.processes;
    }

    public Map<String, Object> getAttributesMap() {
        return this.attributes;
    }
}
