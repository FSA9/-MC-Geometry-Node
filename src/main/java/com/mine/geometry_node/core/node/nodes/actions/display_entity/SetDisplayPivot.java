package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.runtime.display.DisplayTransformController;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class SetDisplayPivot extends BaseNode {
    public static final String TYPE_ID = "set_display_pivot";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION,
                        Component.translatable("geometry_node.node.set_display_pivot"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .input(StandardPorts.DISPLAY_ENTITY, "display_entity")
                        .input(StandardPorts.PIVOT, "pivot")
                        .build())
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.DISPLAY_ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.PIVOT.toInput(Vec3.ZERO), UIHint.VECTOR)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.DISPLAY_ENTITY.getId(), Entity.class);
        Vec3 pivot = getInput(context, StandardPorts.PIVOT.getId(), Vec3.class);
        if (pivot == null) pivot = Vec3.ZERO;

        for (Entity entity : entities) {
            if (entity instanceof Display display) DisplayTransformController.setPivot(display, pivot);
        }
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (StandardPorts.DISPLAY_ENTITY.getId().equals(portName)) {
            return getRawInput(context, StandardPorts.DISPLAY_ENTITY.getId());
        }
        return null;
    }
}
