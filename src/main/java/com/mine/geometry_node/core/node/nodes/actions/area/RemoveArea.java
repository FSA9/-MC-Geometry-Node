package com.mine.geometry_node.core.node.nodes.actions.area;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;

public final class RemoveArea extends BaseNode {
    public static final String TYPE_ID = "remove_area";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node." + TYPE_ID))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.AREA_ID, "area_id")
                        .input(StandardPorts.DIMENSION, "dimension")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.AREA_ID.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.DIMENSION.toInput(RegistryDataManager.DEFAULT_DIMENSION), UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID, RegistryDataManager.DIMENSION_REGISTRY_ID))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        ServerLevel hostLevel = context.getLevel();
        String areaId = getInput(context, StandardPorts.AREA_ID.getId(), String.class);
        ServerLevel areaLevel = hostLevel != null
                ? RegistryDataManager.resolveDimension(hostLevel.getServer(),
                        getInput(context, StandardPorts.DIMENSION.getId(), String.class))
                : null;
        if (areaLevel != null && areaId != null && !areaId.isBlank()) {
            AreaAddress address = AreaAddress.tryCreate(areaLevel.dimension(), areaId);
            if (address != null) {
                AreaResourceStore.INSTANCE.remove(hostLevel.getServer(), address);
                success = true;
            }
        }
        context.setNodeResult(StandardPorts.BOOL.getId(), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getNodeResult(portName) : null;
    }

}
