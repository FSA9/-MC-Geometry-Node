package com.mine.geometry_node.client.ui.editor.game.gizmo;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera.Axis;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;

/** Canvas-only overlay: no world pass, textures, or independent input coordinate system. */
public final class ViewportOrientationOverlay {
    private final ViewportOrientation geometry = new ViewportOrientation();
    private Axis hovered;
    private final Paint paint = new Paint();

    public ViewportOrientationOverlay() { paint.setAntiAlias(true); }

    public void hide() { geometry.visible = false; }

    public void reset() { hide(); hovered = null; }

    public boolean contains(float x, float y) { return geometry.contains(x, y); }

    public Axis hit(float x, float y) { return geometry.hit(x, y); }

    /** Returns whether the hover visual needs repainting. */
    public boolean setHovered(Axis axis) {
        if (hovered == axis) return false;
        hovered = axis;
        return true;
    }

    public void layout(GameViewportLayout bounds, float density, float yaw, float pitch) {
        geometry.layout(bounds, density, yaw, pitch);
    }

    public void draw(Canvas canvas) {
        if (!geometry.visible) return;
        float u = geometry.unit;
        paint.setStyle(Paint.FILL);
        for (var e : geometry.endpoints) {
            if (!e.axis.positive()) continue;
            paint.setColor(color(e.axis));
            paint.setStrokeWidth(1.5f * u);
            canvas.drawLine(geometry.centerX, geometry.centerY, e.x, e.y, paint);
        }
        for (var e : geometry.endpoints) {
            float r = geometry.ballRadius;
            paint.setStyle(Paint.FILL);
            paint.setColor(e.axis.positive() ? color(e.axis) : 0xEE252A30);
            canvas.drawCircle(e.x, e.y, r, paint);
            paint.setStyle(Paint.STROKE);
            paint.setStrokeWidth((e.axis == hovered ? 2f : 1.3f) * u);
            paint.setColor(e.axis == hovered ? 0xFFFFFFFF : color(e.axis));
            canvas.drawCircle(e.x, e.y, r, paint);
            if (e.axis.positive()) drawLetter(canvas, e.axis, e.x, e.y, u);
        }
        paint.setStyle(Paint.FILL);
    }

    private static int color(Axis axis) {
        return axis.x != 0 ? 0xFFEB5967 : axis.y != 0 ? 0xFFA4CE50 : 0xFF4698EE;
    }

    // Three tiny vector glyphs remain sharp under parent transforms without font shaping per frame.
    private void drawLetter(Canvas canvas, Axis axis, float x, float y, float u) {
        paint.setColor(0xFF182029);
        paint.setStrokeWidth(1.1f * u);
        float w = 2.4f * u, h = 3.2f * u;
        if (axis.x != 0) {
            canvas.drawLine(x - w, y - h, x + w, y + h, paint);
            canvas.drawLine(x + w, y - h, x - w, y + h, paint);
        } else if (axis.y != 0) {
            canvas.drawLine(x - w, y - h, x, y, paint);
            canvas.drawLine(x + w, y - h, x, y, paint);
            canvas.drawLine(x, y, x, y + h, paint);
        } else {
            canvas.drawLine(x - w, y - h, x + w, y - h, paint);
            canvas.drawLine(x + w, y - h, x - w, y + h, paint);
            canvas.drawLine(x - w, y + h, x + w, y + h, paint);
        }
    }
}
