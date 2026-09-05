package com.mine.geometry_node.core.node.nodes.data.entity.attribution;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;


public class GetEntityVelocity extends BaseNode {

    public static final String TYPE_ID = "get_entity_velocity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_entity_velocity"))
                .addRow(new PortRow(null, PortDef.create("velocity", "geometry_node.port.velocity", PortType.XYZ), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!"velocity".equals(portName)) return null;

        Entity entity = getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class);
        if (entity == null) return null;

        Entity target = entity;
        // ServerPlayer#getKnownMovement is pre-friction displacement, not post-physics velocity.
        Vec3 velocity = getEventClientVelocity(context, target);
        return bindDynamicVector(velocity != null ? velocity : target.getKnownMovement(), target, "velocity");
    }

    private static Vec3 getEventClientVelocity(GraphDataContext context, Entity target) {
        if (!(target instanceof ServerPlayer)) return null;

        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        Object eventVelocity = context.getEventData(GraphEventFields.CLIENT_VELOCITY);
        Object eventGameTime = context.getEventData(GraphEventFields.CLIENT_VELOCITY_GAME_TIME);
        if (eventEntity instanceof Entity source
                && source.getUUID().equals(target.getUUID())
                && eventVelocity instanceof Vec3 velocity
                && eventGameTime instanceof Number gameTime
                && gameTime.longValue() == context.getLevel().getGameTime()
                && velocity.isFinite()) {
            return velocity;
        }
        return null;
    }
}
