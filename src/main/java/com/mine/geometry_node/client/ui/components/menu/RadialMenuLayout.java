package com.mine.geometry_node.client.ui.components.menu;

/** Local-space layout; the view hierarchy handles parent transforms for paint and input. */
public final class RadialMenuLayout {
    public static final float WIDTH = 416, HEIGHT = 256;

    public enum Slot {
        TOP(208, 28), TOP_RIGHT(336, 64), RIGHT(352, 128), BOTTOM_RIGHT(336, 192),
        BOTTOM(208, 228), BOTTOM_LEFT(80, 192), LEFT(64, 128), TOP_LEFT(80, 64);
        public final float x, y;
        Slot(float x, float y) { this.x = x; this.y = y; }
    }

    public record Placement(float left, float top, float scale) {}

    /** Keep the whole menu within its host. Tiny hosts decline to open rather than clip controls. */
    public static Placement fit(float width, float height, float x, float y, float density) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || !Float.isFinite(x)
                || !Float.isFinite(y) || !Float.isFinite(density) || density <= 0) return null;
        float scale = Math.min(1, Math.min(width / (WIDTH * density), height / (HEIGHT * density)));
        if (scale < 0.55f) return null;
        float w = WIDTH * density * scale, h = HEIGHT * density * scale;
        return new Placement(Math.clamp(x - w / 2, 0, Math.max(0, width - w)),
                Math.clamp(y - h / 2, 0, Math.max(0, height - h)), scale);
    }

    private RadialMenuLayout() {}
}
