package com.mine.geometry_node.client.ui.editor.game.scene;

import net.minecraft.world.phys.Vec3;

/** Align the shared selection pivot to the one-block world grid, preserving relative spacing. */
final class ViewportMoveSnap {
    private ViewportMoveSnap() {}
    static Vec3 delta(Vec3 origin, Vec3 movement, int axes) {
        if (movement.lengthSqr() < 1e-16) return Vec3.ZERO;
        int mask = axes == 0 ? 7 : axes;
        return new Vec3((mask & 1) == 0 ? 0 : Math.floor(origin.x + movement.x + .5) - origin.x,
                (mask & 2) == 0 ? 0 : Math.floor(origin.y + movement.y + .5) - origin.y,
                (mask & 4) == 0 ? 0 : Math.floor(origin.z + movement.z + .5) - origin.z);
    }
}
