package com.mine.geometry_node.core.node.nodes.events.entity;

import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import net.minecraft.network.chat.Component;

public class OnBabyGrowUp extends BaseEventNode {
    public static final String TYPE_ID = "on_baby_grow_up";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.EVENT, Component.translatable("geometry_node.node.on_baby_grow_up")).build();
    }
}