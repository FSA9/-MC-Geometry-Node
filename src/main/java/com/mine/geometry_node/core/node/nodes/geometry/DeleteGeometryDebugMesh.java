package com.mine.geometry_node.core.node.nodes.geometry;

import com.mine.geometry_node.core.engine.blueprint.debug.AreaDebugSessionManager;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class DeleteGeometryDebugMesh extends BaseNode {
    public static final String TYPE_ID = "delete_geometry_debug_mesh";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.delete_geometry_debug_mesh"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        if (key == null || key.trim().isEmpty()) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        AreaDebugSessionManager.removeSourceGeometry(level, key.trim());
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
