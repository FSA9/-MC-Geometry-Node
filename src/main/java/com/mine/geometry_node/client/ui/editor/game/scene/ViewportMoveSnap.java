package com.mine.geometry_node.client.ui.editor.game.scene;

import net.minecraft.world.phys.Vec3;

/** Align the shared selection pivot to the half-block world lattice, preserving relative spacing. */
final class ViewportMoveSnap {
    private ViewportMoveSnap() {}

    // Vertices, edge midpoints, face centers and cell centers together form this
    // lattice. Quantize the destination, never the displacement from the origin.
    private static double nearest(double coordinate) {
        return Math.floor(coordinate * 2 + .5) * .5;
    }
    static Vec3 delta(Vec3 origin, Vec3 movement, int axes) {
        if (movement.lengthSqr() < 1e-16) return Vec3.ZERO;
        int mask = axes == 0 ? 7 : axes;
        return new Vec3((mask & 1) == 0 ? 0 : nearest(origin.x + movement.x) - origin.x,
                (mask & 2) == 0 ? 0 : nearest(origin.y + movement.y) - origin.y,
                (mask & 4) == 0 ? 0 : nearest(origin.z + movement.z) - origin.z);
    }
}
