package com.mine.geometry_node.core.node.nodes.special;

import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import net.minecraft.network.chat.Component;

public class RerouteNode extends BaseNode {
    public static final String TYPE_ID = "reroute";

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
