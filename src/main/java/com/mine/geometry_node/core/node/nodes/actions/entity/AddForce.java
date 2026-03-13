package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AddForce extends BaseNode {

    public static final String TYPE_ID = "add_force";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.add_force"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.XYZ.toInput(), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Vec3 force = getInput(context, StandardPorts.XYZ.getId(), Vec3.class);

        if (force != null && !entities.isEmpty() && !force.equals(Vec3.ZERO)) {
            for (Entity entity : entities) {
                Vec3 currentVelocity = entity.getDeltaMovement();
                entity.setDeltaMovement(currentVelocity.add(force));
                entity.hasImpulse = true;

                if (entity instanceof ServerPlayer player) {
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}