package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SetEntityVelocity extends EntityPassthroughActionNode {

    public static final String TYPE_ID = "set_entity_velocity";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_entity_velocity"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.VECTOR.toInput(), UIHint.VECTOR) // 使用 VECTOR 端口
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        List<Entity> entities = getInputList(context, StandardPorts.ENTITY.getId(), Entity.class);
        Vec3 velocity = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);

        if (velocity != null && !entities.isEmpty()) {
            for (Entity entity : entities) {
                // 覆盖速度（区别于 AddForce 的 add）
                entity.setDeltaMovement(velocity);

                entity.hurtMarked = true;

                ClientboundSetEntityMotionPacket packet = new ClientboundSetEntityMotionPacket(entity);

                // 发送给玩家自己
                if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    player.connection.send(packet);
                }

                // 发送给周围玩家
                if (context.getLevel() != null) {
                    context.getLevel().getChunkSource().sendToTrackingPlayers(entity, packet);
                }
            }
        }

        return next(StandardPorts.FLOW_OUT.getId());
    }
}
