package com.mine.geometry_node.core.node.nodes.actions.display_entity;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.engine.system.entity.InteractionEntityFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Interaction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public class SpawnInteractionEntity extends BaseNode {

    public static final String TYPE_ID = "spawn_interaction_entity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.spawn_interaction_entity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.XYZ.toInput(), UIHint.VECTOR)
                .addPassthroughInput(StandardPorts.WIDTH.toInput(1.0f), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.HEIGHT.toInput(1.0f), UIHint.INPUT)
                .addPassthroughInput(StandardPorts.RESPONSIVE.toInput(false), UIHint.CHECKBOX)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.ENTITY.getId(), null);
        var level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return next(StandardPorts.FLOW_OUT.getId());

        Vec3 pos = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);
        if (pos == null) pos = Vec3.ZERO;

        Float width = getInput(context, StandardPorts.WIDTH.getId(), Float.class);
        Float height = getInput(context, StandardPorts.HEIGHT.getId(), Float.class);
        Boolean responsive = getInput(context, StandardPorts.RESPONSIVE.getId(), Boolean.class);

        Interaction interaction = InteractionEntityFactory.spawn(serverLevel, pos,
                width != null ? width : 1.0f, height != null ? height : 1.0f, responsive != null && responsive);
        context.setNodeResult(StandardPorts.ENTITY.getId(), interaction);

        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (StandardPorts.ENTITY.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.ENTITY.getId());
        }
        return null;
    }
}
