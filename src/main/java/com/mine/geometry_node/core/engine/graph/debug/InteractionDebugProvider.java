package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugElement;
import com.mine.geometry_node.core.engine.graph.debug.geometry.GeometryDebugMeshFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class InteractionDebugProvider {
    List<GeometryDebugElement> collect(ServerLevel level, List<AABB> regions) {
        if (regions.isEmpty()) return List.of();
        Set<UUID> seen = new HashSet<>();
        List<GeometryDebugElement> meshes = new ArrayList<>();
        for (AABB region : regions) {
            for (Interaction interaction : level.getEntitiesOfClass(Interaction.class, region)) {
                if (interaction.isRemoved() || !seen.add(interaction.getUUID())) continue;
                AABB bounds = interaction.getBoundingBox();
                Vec3 center = bounds.getCenter();
                DebugRenderShape shape = new DebugRenderShape(
                        DebugRenderChannel.INTERACTION.id() + ":" + interaction.getStringUUID(),
                        "interaction", "box", center,
                        new Vec3(bounds.getXsize(), bounds.getYsize(), bounds.getZsize()),
                        Vec3.ZERO, DebugRenderChannel.INTERACTION.color()
                );
                meshes.add(GeometryDebugMeshFactory.buildShapeMesh(shape));
            }
        }
        return List.copyOf(meshes);
    }
}
