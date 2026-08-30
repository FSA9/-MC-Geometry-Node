package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** One live, binding-owned Area stored under a public dimension + id address. */
public record AreaResource(AreaAddress address,
                           GraphResourceId owner,
                           long generation,
                           AreaShape shape,
                           Vec3 center,
                           Vec3 size,
                           Vec3 rotation,
                           @Nullable UUID anchorEntityId) {
    public AreaResource {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(center, "center");
        size = AreaEntityQuery.sanitizeSize(Objects.requireNonNull(size, "size"));
        Objects.requireNonNull(rotation, "rotation");
    }

    @Nullable
    public Resolved resolve(ServerLevel level) {
        if (level == null || !level.dimension().equals(address.dimension())) return null;
        Vec3 resolvedCenter = center;
        if (anchorEntityId != null) {
            Entity anchor = level.getEntity(anchorEntityId);
            if (anchor == null || anchor.isRemoved()) return null;
            resolvedCenter = anchor.position().add(center);
        }
        return new Resolved(shape, resolvedCenter, size, rotation);
    }

    public record Resolved(AreaShape shape, Vec3 center, Vec3 size, Vec3 rotation) {
    }
}
