package com.mine.geometry_node.client.ui.components.nativepreview;

import com.mine.geometry_node.client.ui.editor.graph.Viewport;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nonnull;

/**
 * View-side bridge for a nativepreview whose pixels are emitted by the viewport's shared renderer.
 */
public abstract class ViewportNativePreviewView extends FrameLayout implements ViewportNativePreview {
    private final Object mTransformLock = new Object();

    private ViewportNativePreviewLayer mPreviewLayer;
    private MinecraftSurfaceView mFallbackSurface;
    private float mViewportScale = 1.0f;
    private float mWindowLeftPx;
    private float mWindowTopPx;
    private int mContentWidthPx;
    private int mContentHeightPx;
    private volatile long mPreviewOrder;
    private volatile boolean mHasViewportTransform;
    private volatile boolean mAggregatedVisible = true;

    protected ViewportNativePreviewView(Context context) {
        super(context);
    }

    protected final void updateNativePreviewScale(float scale) {
        synchronized (mTransformLock) {
            mViewportScale = sanitizeScale(scale);
        }
        requestNativePreviewRender();
    }

    protected final void updateNativePreviewTransform(
            float scale,
            float windowLeftPx,
            float windowTopPx,
            long previewOrder
    ) {
        boolean orderChanged = mPreviewOrder != previewOrder;
        synchronized (mTransformLock) {
            mViewportScale = sanitizeScale(scale);
            mWindowLeftPx = windowLeftPx;
            mWindowTopPx = windowTopPx;
            mPreviewOrder = previewOrder;
            mHasViewportTransform = true;
        }
        ViewportNativePreviewLayer layer = mPreviewLayer;
        if (orderChanged && layer != null) {
            layer.notifyPreviewOrderChanged();
        } else {
            requestNativePreviewRender();
        }
    }

    @Override
    public final long getNativePreviewOrder() {
        return mPreviewOrder;
    }

    @Override
    public final void renderNativePreview(
            GuiGraphicsExtractor graphics,
            float deltaTick,
            double guiScale,
            float alpha
    ) {
        if (!mHasViewportTransform) return;

        float scale;
        float leftPx;
        float topPx;
        float widthPx;
        float heightPx;
        synchronized (mTransformLock) {
            scale = mViewportScale;
            leftPx = mWindowLeftPx;
            topPx = mWindowTopPx;
            widthPx = mContentWidthPx * scale;
            heightPx = mContentHeightPx * scale;
        }
        renderAt(graphics, deltaTick, guiScale, alpha, leftPx, topPx, widthPx, heightPx, scale);
    }

    private void renderAt(
            GuiGraphicsExtractor graphics,
            float deltaTick,
            double guiScale,
            float alpha,
            float leftPx,
            float topPx,
            float widthPx,
            float heightPx,
            float scale
    ) {
        if (!mAggregatedVisible || widthPx <= 0.0f || heightPx <= 0.0f) return;

        float safeGuiScale = guiScale > 0.0 ? (float) guiScale : 1.0f;
        float leftGui = leftPx / safeGuiScale;
        float topGui = topPx / safeGuiScale;
        float rightGui = (leftPx + widthPx) / safeGuiScale;
        float bottomGui = (topPx + heightPx) / safeGuiScale;
        if (rightGui <= 0.0f || bottomGui <= 0.0f
                || leftGui >= graphics.guiWidth() || topGui >= graphics.guiHeight()) {
            return;
        }

        float poseLeft = graphics.pose().m20();
        float poseTop = graphics.pose().m21();
        int clipLeft = (int) Math.floor(leftGui - poseLeft);
        int clipTop = (int) Math.floor(topGui - poseTop);
        int clipRight = (int) Math.ceil(rightGui - poseLeft);
        int clipBottom = (int) Math.ceil(bottomGui - poseTop);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        try {
            renderNativePreviewContent(
                    graphics,
                    deltaTick,
                    safeGuiScale,
                    alpha,
                    leftPx,
                    topPx,
                    widthPx,
                    heightPx,
                    scale
            );
        } finally {
            graphics.disableScissor();
        }
    }

    protected abstract void renderNativePreviewContent(
            GuiGraphicsExtractor graphics,
            float deltaTick,
            float guiScale,
            float alpha,
            float windowLeftPx,
            float windowTopPx,
            float widthPx,
            float heightPx,
            float viewportScale
    );

    protected final void requestNativePreviewRender() {
        ViewportNativePreviewLayer layer = mPreviewLayer;
        if (layer != null) layer.requestPreviewRender();
        if (mFallbackSurface != null) mFallbackSurface.invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        synchronized (mTransformLock) {
            mContentWidthPx = width;
            mContentHeightPx = height;
        }
        requestNativePreviewRender();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        mAggregatedVisible = visible;
        requestNativePreviewRender();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAggregatedVisible = isShown();
        Viewport viewport = findViewportAncestor();
        if (viewport != null) {
            removeFallbackSurface();
            mPreviewLayer = viewport.getNativePreviewLayer();
            mPreviewLayer.registerPreview(this);
        } else {
            ensureFallbackSurface();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        ViewportNativePreviewLayer layer = mPreviewLayer;
        mPreviewLayer = null;
        if (layer != null) layer.unregisterPreview(this);
        super.onDetachedFromWindow();
    }

    private Viewport findViewportAncestor() {
        View current = this;
        while (current.getParent() instanceof View parent) {
            if (parent instanceof Viewport viewport) return viewport;
            current = parent;
        }
        return null;
    }

    private void ensureFallbackSurface() {
        if (mFallbackSurface != null) return;
        mFallbackSurface = new MinecraftSurfaceView(getContext());
        mFallbackSurface.setEnabled(false);
        mFallbackSurface.setClickable(false);
        mFallbackSurface.setFocusable(false);
        mFallbackSurface.setRenderer(new MinecraftSurfaceView.Renderer() {
            private final int[] mLocationInWindow = new int[2];

            @Override
            public void onSurfaceChanged(int width, int height) {
            }

            @Override
            public void onDraw(
                    @Nonnull GuiGraphicsExtractor graphics,
                    int mouseX,
                    int mouseY,
                    float deltaTick,
                    double guiScale,
                    float alpha
            ) {
                mFallbackSurface.getLocationInWindow(mLocationInWindow);
                renderAt(
                        graphics,
                        deltaTick,
                        guiScale,
                        alpha,
                        mLocationInWindow[0],
                        mLocationInWindow[1],
                        mFallbackSurface.getWidth(),
                        mFallbackSurface.getHeight(),
                        1.0f
                );
            }
        });
        addView(mFallbackSurface, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void removeFallbackSurface() {
        if (mFallbackSurface == null) return;
        removeView(mFallbackSurface);
        mFallbackSurface = null;
    }

    private static float sanitizeScale(float scale) {
        return scale > 0.0f ? scale : 1.0f;
    }
}
