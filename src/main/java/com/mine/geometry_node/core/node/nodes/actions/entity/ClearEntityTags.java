package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClearEntityTags extends BaseNode {

    public static final String TYPE_ID = "clear_entity_tags";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.clear_entity_tags"))
                // 1. 执行流
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                // 2. 目标实体 (支持同时清空多个实体的标签)
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);

        if (!entities.isEmpty()) {
            for (Entity entity : entities) {
                Set<String> currentTags = entity.entityTags();
                if (!currentTags.isEmpty()) {
                    // 【关键防御】: 必须将其拷贝到一个新的列表中再进行遍历删除！
                    // 否则会触发 ConcurrentModificationException 并发修改异常
                    List<String> tagsToRemove = new ArrayList<>(currentTags);
                    for (String tag : tagsToRemove) {
                        entity.removeTag(tag);
                    }
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
