package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;


public class GetEntityTags extends BaseNode {

    public static final String TYPE_ID = "get_entity_tags";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_tags"))
                .addRow(new PortRow(null, PortDef.create("tags", "geometry_node.port.tags", PortType.LIST), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!"tags".equals(portName)) return null;

        Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
        if (entity == null) return null;

        // 原版的 getTags() 返回的是 Set<String>，我们转成 ArrayList 方便底层 LIST 类型处理
        return new java.util.ArrayList<>(entity.entityTags());
    }
}
