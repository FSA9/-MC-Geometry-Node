package com.mine.geometry_node.core.node.nodes.actions.visual;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class DrawLaserBeam extends BaseNode {

    public static final String TYPE_ID = "draw_laser_beam";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.draw_laser_beam"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.START_POS.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.END_POS.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.COLOR.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.SIZE_1.toInput(), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.TICK.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Vec3 startPos = getInput(context, StandardPorts.START_POS.getId(), Vec3.class);
        Vec3 endPos = getInput(context, StandardPorts.END_POS.getId(), Vec3.class);
        Integer color = getInput(context, StandardPorts.COLOR.getId(), Integer.class);
        Float size = getInput(context, StandardPorts.SIZE_1.getId(), Float.class);
        Integer duration = getInput(context, StandardPorts.TICK.getId(), Integer.class);

        context.broadcastVisual("laser_beam", -1, startPos, -1, endPos, color, size, duration);

        return next(StandardPorts.FLOW_OUT.getId());
    }
}