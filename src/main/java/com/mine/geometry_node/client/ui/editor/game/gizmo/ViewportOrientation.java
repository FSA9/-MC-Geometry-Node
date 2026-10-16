package com.mine.geometry_node.client.ui.editor.game.gizmo;

import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera.Axis;
import java.util.Arrays;
import java.util.Comparator;

/** Reusable local-space geometry shared by painting and hit testing. Minecraft is Y-up. */
final class ViewportOrientation {
    private static final float DISPLAY_SCALE = 0.8f;
    static final class Endpoint {
        final Axis axis;
        float x, y, depth;
        Endpoint(Axis axis) { this.axis = axis; }
    }

    final Endpoint[] endpoints = Arrays.stream(Axis.values()).map(Endpoint::new).toArray(Endpoint[]::new);
    private static final Comparator<Endpoint> DEPTH = Comparator.comparingDouble(e -> e.depth);
    float centerX, centerY, radius, ballRadius, unit;
    boolean visible;

    void layout(GameViewportLayout bounds, float density, float yaw, float pitch) {
        density *= DISPLAY_SCALE;
        float available = Math.min(bounds.right() - bounds.left(), bounds.bottom() - bounds.top());
        visible = Float.isFinite(available) && density > 0 && available >= 64 * density;
        if (!visible) return;
        unit = Math.min(density, available / 100f);
        radius = 44 * unit;
        ballRadius = 8 * unit;
        centerX = bounds.right() - 6 * unit - radius;
        centerY = bounds.top() + 6 * unit + radius;
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double sy = Math.sin(y), cy = Math.cos(y), sp = Math.sin(p), cp = Math.cos(p);
        for (Endpoint e : endpoints) {
            Axis a = e.axis;
            e.x = centerX + (float) (-cy * a.x - sy * a.z) * 30 * unit;
            e.y = centerY - (float) (-sy * sp * a.x + cp * a.y + cy * sp * a.z) * 30 * unit;
            e.depth = (float) (sy * cp * a.x + sp * a.y - cy * cp * a.z);
        }
        // Paint far to near, hit test near to far, including coincident axis ends.
        Arrays.sort(endpoints, DEPTH);
    }

    boolean contains(float x, float y) {
        return visible && squaredDistance(x, y, centerX, centerY) <= radius * radius;
    }

    Axis hit(float x, float y) {
        if (!contains(x, y)) return null;
        for (int i = endpoints.length - 1; i >= 0; i--) {
            Endpoint e = endpoints[i];
            if (squaredDistance(x, y, e.x, e.y) <= ballRadius * ballRadius) return e.axis;
        }
        return null;
    }

    private static float squaredDistance(float x, float y, float cx, float cy) {
        return (x - cx) * (x - cx) + (y - cy) * (y - cy);
    }
}
