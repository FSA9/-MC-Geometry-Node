package com.mine.geometry_node.client.model.render.backend.host.light.occlusion;

import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Objects;

/** Immutable world/model coordinate adapter around one asset-shared occluder. */
public final class HostModelOccluderInstance {
    private final HostTriangleBvh bvh;
    private final HostConservativeVoxelGrid voxelGrid;
    private final double originX, originY, originZ;
    private final Matrix4f modelFromInstance;
    private final double minimumWorldVoxelSize;

    public HostModelOccluderInstance(HostTriangleBvh bvh, HostConservativeVoxelGrid voxelGrid,
                                     ModelInstancePlacement placement) {
        this.bvh = Objects.requireNonNull(bvh, "bvh");
        this.voxelGrid = Objects.requireNonNull(voxelGrid, "voxelGrid");
        Objects.requireNonNull(placement, "placement");
        this.originX = placement.position().x;
        this.originY = placement.position().y;
        this.originZ = placement.position().z;
        this.modelFromInstance = new Matrix4f().rotate(placement.rotation()).scale(placement.scale()).invert();
        this.minimumWorldVoxelSize = voxelGrid.cellSize() * Math.min(Math.abs(placement.scale().x),
                Math.min(Math.abs(placement.scale().y), Math.abs(placement.scale().z)));
    }

    public boolean blocksOpenSegment(double fromX, double fromY, double fromZ,
                                     double toX, double toY, double toZ) {
        Vector3f from = modelPosition(fromX, fromY, fromZ);
        Vector3f to = modelPosition(toX, toY, toZ);
        return bvh.blocked(from.x, from.y, from.z, to.x, to.y, to.z);
    }

    public boolean occupiedAtWorld(double x, double y, double z) {
        Vector3f model = modelPosition(x, y, z);
        return voxelGrid.occupiedAt(model.x, model.y, model.z);
    }

    public boolean blocksVoxelSegment(double fromX, double fromY, double fromZ,
                                      double toX, double toY, double toZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.clamp((int) Math.ceil(length / Math.max(minimumWorldVoxelSize * 0.5, 1.0e-4)),
                1, 64);
        for (int step = 1; step < steps; step++) {
            double t = step / (double) steps;
            if (occupiedAtWorld(fromX + dx * t, fromY + dy * t, fromZ + dz * t)) return true;
        }
        return false;
    }

    public float modelVoxelSize() { return voxelGrid.cellSize(); }

    private Vector3f modelPosition(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("world coordinates must be finite");
        }
        return modelFromInstance.transformPosition(new Vector3f(
                (float) (x - originX), (float) (y - originY), (float) (z - originZ)));
    }
}
