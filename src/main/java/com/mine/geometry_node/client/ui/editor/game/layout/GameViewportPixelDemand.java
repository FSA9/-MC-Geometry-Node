package com.mine.geometry_node.client.ui.editor.game.layout;

import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.RectF;

/** Pure UI measurement; it neither waits for a frame nor changes GPU resources. */
public final class GameViewportPixelDemand {
    private GameViewportPixelDemand() {}

    public static double scale(int width, int height, int framebufferWidth, int framebufferHeight,
                        int rootWidth, int rootHeight, Matrix localToRoot, Rect visibleInRoot) {
        if (width <= 0 || height <= 0 || framebufferWidth <= 0 || framebufferHeight <= 0
                || rootWidth <= 0 || rootHeight <= 0 || visibleInRoot.isEmpty()) return 0;
        var fit = GameViewportLayout.fit(width, height, framebufferWidth, framebufferHeight);
        RectF image = new RectF(fit.left(), fit.top(), fit.right(), fit.bottom());
        localToRoot.mapRect(image);
        if (!Float.isFinite(image.left) || !Float.isFinite(image.top)
                || !Float.isFinite(image.right) || !Float.isFinite(image.bottom)
                || image.right <= image.left || image.bottom <= image.top) return 0;
        if (image.right <= visibleInRoot.left || image.left >= visibleInRoot.right
                || image.bottom <= visibleInRoot.top || image.top >= visibleInRoot.bottom) return 0;
        // ModernUI lays out its root in framebuffer pixels. The root/framebuffer
        // ratio also handles a pending root resize without applying GUI scale twice.
        // Partial clipping changes visibility, never the density of the whole image.
        return Math.min(1, Math.max((double) image.width() / rootWidth,
                (double) image.height() / rootHeight));
    }
}
