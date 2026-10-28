package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared by command and editor snapshots. Bounds come from the same live Area used by physics. */
final class ForceFieldDebugProvider {
    private ForceFieldDebugProvider() {}

    static List<GeometryDebugElement> collect(ServerLevel level, Vec3 origin, double radius) {
        if (level == null) return List.of();
        List<GeometryDebugElement> meshes = new ArrayList<>();
        double radiusSquared = radius * radius;
        for (var field : ForceFieldResourceStore.INSTANCE.snapshot(level)) {
            if (field.isAuthored()) continue; // Scene presentation owns its helper; it has no graph owner.
            var resource = AreaResourceStore.INSTANCE.get(level.getServer(), field.area());
            var area = resource == null ? null : resource.resolveActive(level);
            if (area == null || area.center().distanceToSqr(origin) > radiusSquared) continue;
            var source = DebugSourceId.graph(DebugRenderChannel.FORCE_FIELD, field.owner());
            meshes.add(GeometryDebugMeshFactory.buildShapeMesh(new DebugRenderShape(
                    DebugSourceIdCodec.element(source, field.address().id()), field.owner().binding().graphId(),
                    area.shape().id(), area.center(), area.size(), area.rotation(),
                    DebugRenderChannel.FORCE_FIELD.color())));
        }
        return meshes;
    }
}
