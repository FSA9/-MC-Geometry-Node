package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
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
    public Object compute(GraphDataContext context, String portName) {
        return OWNER_PORT.equals(portName) ? context.getGraphOwnerEntity() : null;
    }
}
