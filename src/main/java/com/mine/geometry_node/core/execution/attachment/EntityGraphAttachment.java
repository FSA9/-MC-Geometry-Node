package com.mine.geometry_node.core.execution.attachment;

import com.mine.geometry_node.core.execution.GraphProcess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * [实体附加层 - 组合版]
 * 作为实体的背包，持有绑定关系，并委托 GraphContainer 执行具体逻辑。
 */
public class EntityGraphAttachment {

    private final Set<String> boundGraphs = new HashSet<>();

    private final GraphContainer container = new GraphContainer(() -> {});

    public EntityGraphAttachment() {}

    /**
     * [心跳驱动] 委托给底座
     */
    public void tick(Entity entity) {
        if (entity.level() instanceof ServerLevel serverLevel) {
            container.tick(serverLevel, entity);
        }
    }

    // --- 实体独占绑定逻辑 ---

    public void bindGraph(String graphId) {
        this.boundGraphs.add(graphId);
    }

    public void unbindGraph(String graphId) {
        this.boundGraphs.remove(graphId);
        this.container.removeProcess(graphId); // 同步卸载进程
    }

    public Set<String> getBoundGraphs() {
        return Collections.unmodifiableSet(boundGraphs);
    }

    public void clearGraphs() {
        this.boundGraphs.clear();
        this.container.clear();
    }

    // --- API 委托层 ---

    public void addProcess(GraphProcess process) { container.addProcess(process); }
    public Collection<GraphProcess> getProcesses() { return container.getProcesses(); }
    public GraphProcess getProcess(String graphId) { return container.getProcess(graphId); }
    public void setAttribute(String key, Object value) { container.setAttribute(key, value); }
    public Object getAttribute(String key) { return container.getAttribute(key); }

    // --- 序列化层 ---

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (!boundGraphs.isEmpty()) {
            ListTag boundList = new ListTag();
            for (String graphId : boundGraphs) boundList.add(StringTag.valueOf(graphId));
            tag.put("BoundGraphs", boundList);
        }
        return container.save(tag, provider);
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        this.boundGraphs.clear();
        if (tag.contains("BoundGraphs", Tag.TAG_LIST)) {
            ListTag list = tag.getList("BoundGraphs", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) this.boundGraphs.add(list.getString(i));
        }
        container.load(tag, provider);
    }
}