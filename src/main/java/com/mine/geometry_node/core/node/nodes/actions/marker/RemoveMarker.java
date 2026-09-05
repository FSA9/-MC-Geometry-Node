package com.mine.geometry_node.core.node.nodes.actions.marker;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.marker.MarkerService;
import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class RemoveMarker extends BaseNode {
    public static final String TYPE_ID = "remove_marker";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.remove_marker"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.FLOW_IN, "flow_in")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.BOOL, "bool")
                        .input(StandardPorts.KEY, "key")
                        .input(StandardPorts.ONLY_SELF_VISIBLE, "only_self_visible")
                        .input(StandardPorts.PLAYER, "player")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.KEY.toInput(""), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.ONLY_SELF_VISIBLE.toInput(true), UIHint.CHECKBOX)
                .addPassthroughInput(StandardPorts.PLAYER.toInput(), UIHint.DEFAULT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        boolean success = false;
        String key = getInput(context, StandardPorts.KEY.getId(), String.class);
        if (context.getLevel() != null && key != null && !key.isBlank()) {
            try {
                Boolean onlySelfInput = getInput(context, StandardPorts.ONLY_SELF_VISIBLE.getId(), Boolean.class);
                boolean onlySelf = onlySelfInput == null || onlySelfInput;
                MarkerAddress address = resolveAddress(context, key.trim(), onlySelf);
                if (address != null) {
                    success = MarkerService.INSTANCE.remove(context.getLevel().getServer(), address);
                }
            } catch (IllegalArgumentException ignored) {
                success = false;
            }
        }
        context.setNodeResult(StandardPorts.BOOL.getId(), success);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return StandardPorts.BOOL.getId().equals(portName) ? context.getNodeResult(portName) : null;
    }

    private MarkerAddress resolveAddress(ExecutionContext context, String key, boolean onlySelf) {
        if (!onlySelf) return MarkerAddress.all(key);
        Entity viewer = getInputFromList(context, StandardPorts.PLAYER.getId(), 0, Entity.class);
        if (viewer == null) viewer = context.getGraphOwnerEntity();
        if (viewer == null) viewer = context.getEntity();
        return viewer instanceof ServerPlayer player ? MarkerAddress.self(player.getUUID(), key) : null;
    }

}
