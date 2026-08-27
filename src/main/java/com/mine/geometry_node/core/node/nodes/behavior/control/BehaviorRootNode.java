package com.mine.geometry_node.core.node.nodes.behavior.control;

import com.mine.geometry_node.core.engine.behavior.document.BehaviorNodeTypes;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

/** Unique structural entry point of a behavior tree. */
public final class BehaviorRootNode extends BaseNode {
    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(BehaviorNodeTypes.ROOT, NodeType.FLOW_CONTROL,
                Component.translatable("geometry_node.node.behavior_root"))
                .addRow(new PortRow(null, structurePort(BehaviorNodeTypes.CHILDREN_PORT), UIHint.DEFAULT, null, null))
                .addRow(staticInteger(BehaviorNodeTypes.RECHECK_INTERVAL_PORT, 1))
                .addRow(staticInteger(BehaviorNodeTypes.SCHEDULE_OFFSET_PORT, -1))
                .build();
    }

    private static PortDef structurePort(String id) {
        return PortDef.create(id, "geometry_node.port." + id, PortType.BEHAVIOR_STRUCTURE);
    }

    private static PortRow staticInteger(String id, int defaultValue) {
        return new PortRow(PortDef.create(id, "geometry_node.port." + id,
                PortType.INTEGER, defaultValue).hiddenPin(), null, UIHint.INPUT, null, null);
    }
}
