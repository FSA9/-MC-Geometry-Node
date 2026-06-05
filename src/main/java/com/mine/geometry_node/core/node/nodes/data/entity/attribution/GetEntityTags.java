package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class GetEntityTags extends BaseNode {

    public static final String TYPE_ID = "get_entity_tags";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_tags"))
                .addRow(new PortRow(
                        StandardPorts.ENTITY.toInput(),
                        PortDef.create("tags", "geometry_node.port.tags", PortType.LIST),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!"tags".equals(portName)) return null;

        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        if (entities.isEmpty()) return null;

        // 原版的 getTags() 返回的是 Set<String>，我们转成 ArrayList 方便底层 LIST 类型处理
        return new java.util.ArrayList<>(entities.getFirst().getTags());
    }
}