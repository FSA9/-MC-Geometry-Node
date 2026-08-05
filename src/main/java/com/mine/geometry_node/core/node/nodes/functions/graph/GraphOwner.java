package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Exposes the entity bound to the current graph process, if the graph has one. */
public final class GraphOwner extends BaseNode {
    public static final String TYPE_ID = "graph_owner";
    public static final String OWNER_PORT = "graph_owner";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.graph_owner"))
                .addRow(new PortRow(
                        null,
                        PortDef.create(OWNER_PORT, "geometry_node.port.graph_owner", PortType.ENTITY),
                        UIHint.DEFAULT,
                        null,
                        null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return OWNER_PORT.equals(portName) ? context.getGraphOwnerEntity() : null;
    }
}
