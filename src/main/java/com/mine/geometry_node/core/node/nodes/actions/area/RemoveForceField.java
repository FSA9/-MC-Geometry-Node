package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

public final class RemoveForceField extends BaseNode {
    public static final String TYPE_ID = "remove_force_field";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.FORCE_FIELD_ID, "force_field_id")
                        .input(StandardPorts.DIMENSION, "dimension")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.FORCE_FIELD_ID.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION),
                        null, UIHint.SELECT, null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID)))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        ServerLevel hostLevel = context.getLevel();
        ServerLevel fieldLevel = hostLevel != null
                ? RegistryDataManager.resolveDimension(hostLevel.getServer(),
                        getInput(context, StandardPorts.DIMENSION.getId(), String.class))
                : null;
        String fieldId = getInput(context, StandardPorts.FORCE_FIELD_ID.getId(), String.class);
        if (hostLevel != null && fieldLevel != null && fieldId != null && !fieldId.isBlank()) {
            ForceFieldAddress address = ForceFieldAddress.tryCreate(fieldLevel.dimension(), fieldId);
            if (address != null) {
                ForceFieldResourceStore.INSTANCE.remove(hostLevel.getServer(), address);
                success = true;
            }
        }
        context.setTempData(tempKey(context), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getTempData(tempKey(context)) : null;
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + StandardPorts.BOOL.getId();
    }
}
