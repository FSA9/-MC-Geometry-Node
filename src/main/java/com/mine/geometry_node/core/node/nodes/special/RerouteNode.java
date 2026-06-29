package com.mine.geometry_node.core.node.nodes.special;

import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import net.minecraft.network.chat.Component;

public class RerouteNode extends BaseNode {
    public static final String TYPE_ID = "geometry_node:reroute";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(PortType.ANY);
    }

    @Override
    public NodeDef getDefinition(NodeData instanceData) {
        return definition(RerouteNodeSupport.resolveLockedType(instanceData));
    }

    private NodeDef definition(PortType type) {
        PortType safeType = type != null ? type : PortType.ANY;
        return NodeDef.builder(TYPE_ID, NodeType.CUSTOM, Component.translatable("geometry_node.node.reroute"))
                .uiWidth(64)
                .addRow(new PortRow(
                        PortDef.create(RerouteNodeSupport.INPUT_PORT, "geometry_node.port.reroute_in", safeType),
                        PortDef.create(RerouteNodeSupport.OUTPUT_PORT, "geometry_node.port.reroute_out", safeType),
                        UIHint.DEFAULT,
                        null,
                        null
                ))
                .build();
    }
}
