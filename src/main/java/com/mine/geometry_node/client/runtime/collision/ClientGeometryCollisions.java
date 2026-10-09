package com.mine.geometry_node.client.runtime.collision;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryCollision;
import net.minecraft.client.Minecraft;

/** Mirrors server colliders into the current client level for movement prediction. */
public final class ClientGeometryCollisions {
    private ClientGeometryCollisions() {}

    public static void handle(PacketGeometryCollision packet) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) return;
        try {
            GraphResourceId id = GraphResourceIdCodec.decode(packet.resourceId());
            if (!id.type().equals(GraphResourceTypeRegistry.GEOMETRY_COLLISION)
                    || !id.scope().dimension().equals(level.dimension())) return;
            if (packet.operation() == PacketGeometryCollision.REMOVE) {
                ModelCollisionScene.remove(level, id);
                return;
            }
            ModelCollisionScene.Prepared geometry;
            java.util.UUID excludedEntity = packet.excludedEntity();
            if (packet.operation() == PacketGeometryCollision.UPSERT) {
                geometry = ModelCollisionScene.prepare(packet.mesh());
            } else {
                var entry = ModelCollisionScene.lookup(level, id);
                geometry = entry == null ? null : entry.geometry();
                if (geometry == null) return;
                excludedEntity = entry.excludedEntity();
            }
            var position = packet.position();
            ModelCollisionScene.publish(level, id, geometry, position.x, position.y, position.z, excludedEntity);
        } catch (Exception exception) {
            GeometryNode.LOGGER.warn("Unable to synchronize geometry collision: {}", exception.getMessage());
        }
    }
}
