package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaRef;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class RemoveForceField extends BaseNode {
    public static final String TYPE_ID = "remove_force_field";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .input(StandardPorts.FORCE_FIELD_ID, "force_field_id")
                        .input(StandardPorts.AREA, "area")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.FORCE_FIELD_ID.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.AREA.toInput(), UIHint.DEFAULT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerLevel hostLevel = context.getLevel();
        String fieldId = getInput(context, StandardPorts.FORCE_FIELD_ID.getId(), String.class);
        AreaRef area = getInput(context, StandardPorts.AREA.getId(), AreaRef.class);
        if (hostLevel != null && area != null && fieldId != null && !fieldId.isBlank()) {
            ForceFieldAddress address = ForceFieldAddress.tryCreate(area.address().dimension(), fieldId);
            if (address != null) {
                ForceFieldResourceStore.INSTANCE.remove(hostLevel.getServer(), address);
            }
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
