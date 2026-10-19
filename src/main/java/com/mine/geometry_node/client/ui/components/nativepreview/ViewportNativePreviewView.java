package com.mine.geometry_node.client.ui.components.nativepreview;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Rect;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.FrameLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nonnull;

/**
 * View-side bridge for a nativepreview whose pixels are emitted by the viewport's shared renderer.
 */
public abstract class ViewportNativePreviewView extends FrameLayout implements ViewportNativePreview {
    private final Object mTransformLock = new Object();

    private NativePreviewHost.Registration mPreviewRegistration;
    private volatile MinecraftSurfaceView mFallbackSurface;
    private float mViewportScale = 1.0f;
    private float mWindowLeftPx;
    private float mWindowTopPx;
    private int mContentWidthPx;
    private int mContentHeightPx;
    private volatile long mPreviewOrder;
    private volatile boolean mHasViewportTransform;
    private volatile boolean mAggregatedVisible = true;

    // Shared-host previews sample clipping separately. Fallback surfaces synchronize it with
    // their geometry through MUI's raw-surface alpha property instead.
    private record VisibilityFrame(boolean visible, int left, int top, int width, int height) {}
    private volatile VisibilityFrame mVisibilityFrame;
    private volatile boolean mRequiresFullVisibility;
    private View mFullVisibilityAnchor;
    private ViewTreeObserver mVisibilityObserver;
    private final Rect mVisibleBounds = new Rect();
    private final RectF mFullBounds = new RectF();
    private final Matrix mVisibilityMatrix = new Matrix();
    private final int[] mLocation = new int[2];
    private final ViewTreeObserver.OnPreDrawListener mTrackVisibility = () -> {
        updateFullVisibility();
        return true;
    };
    private final ViewTreeObserver.OnScrollChangedListener mTrackScroll = this::updateFullVisibility;

    /**
     * Opt-in row policy: require the anchor's full vertical extent and this preview's entire
     * rectangle to be visible through ancestor clipping. Row width may exceed a horizontal viewport.
     * Configure on the UI thread. Null restores ordinary preview rendering.
     */
    public final void setFullVisibilityAnchor(View anchor) {
        if (mFullVisibilityAnchor == anchor) return;
        mVisibilityFrame = null;
        mFullVisibilityAnchor = anchor;
        mRequiresFullVisibility = anchor != null;
        stopVisibilityTracking();
        if (isAttachedToWindow() && mPreviewRegistration != null) startVisibilityTracking();
        requestNativePreviewRender();
    }

    private void startVisibilityTracking() {
        if (mFullVisibilityAnchor == null) return;
        mVisibilityObserver = getViewTreeObserver();
        mVisibilityObserver.addOnPreDrawListener(mTrackVisibility);
        mVisibilityObserver.addOnScrollChangedListener(mTrackScroll);
        updateFullVisibility();
    }

    private void stopVisibilityTracking() {
        if (mVisibilityObserver != null) {
            ViewTreeObserver observer = mVisibilityObserver.isAlive()
                    ? mVisibilityObserver : getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(mTrackVisibility);
                observer.removeOnScrollChangedListener(mTrackScroll);
            }
            mVisibilityObserver = null;
        }
        mVisibilityFrame = null;
    }

    private void updateFullVisibility() {
        if (mFullVisibilityAnchor == null) return;
        boolean visible = isFullyVisible();
        getLocationInWindow(mLocation);
        VisibilityFrame previous = mVisibilityFrame;
        if (previous == null || previous.visible() != visible || previous.left() != mLocation[0]
                || previous.top() != mLocation[1] || previous.width() != getWidth()
                || previous.height() != getHeight()) {
            mVisibilityFrame = new VisibilityFrame(visible, mLocation[0], mLocation[1], getWidth(), getHeight());
            requestNativePreviewRender();
        }
    }

    /** UI-thread policy, evaluated while MUI stages the surface's other properties. */
    private boolean isFullyVisible() {
        View anchor = mFullVisibilityAnchor;
        return anchor == null || (isShown() && anchor.isShown() && isAncestor(anchor)
                && fullyVisible(anchor, false) && fullyVisible(this, true));
    }

    private boolean isAncestor(View anchor) {
        for (View current = this; current != null;
             current = current.getParent() instanceof View parent ? parent : null) {
            if (current == anchor) return true;
        }
        return false;
    }

    private boolean fullyVisible(View view, boolean horizontal) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0 || !view.getGlobalVisibleRect(mVisibleBounds))
            return false;
        mVisibilityMatrix.setIdentity();
        view.transformMatrixToGlobal(mVisibilityMatrix);
        mFullBounds.set(0, 0, view.getWidth(), view.getHeight());
        mVisibilityMatrix.mapRect(mFullBounds);
        // Match MUI's outward rounding of visible bounds, including ancestor scaling.
        return mVisibleBounds.top <= (int) Math.floor(mFullBounds.top)
                && mVisibleBounds.bottom >= (int) Math.ceil(mFullBounds.bottom)
                && (!horizontal || (mVisibleBounds.left <= (int) Math.floor(mFullBounds.left)
                && mVisibleBounds.right >= (int) Math.ceil(mFullBounds.right)));
    }

    private boolean visibilityAllows(float left, float top, float width, float height, float scale) {
        if (!mRequiresFullVisibility) return true;
        VisibilityFrame frame = mVisibilityFrame;
        // The shared host supplies its own transform; require its clipping sample to match.
        return frame != null && frame.visible()
                && Math.abs(frame.left() - left) < 1f && Math.abs(frame.top() - top) < 1f
                && Math.abs(frame.width() * scale - width) < 1f && Math.abs(frame.height() * scale - height) < 1f;
    }

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
        NativePreviewHost.Registration registration = mPreviewRegistration;
        if (orderChanged && registration != null) {
            registration.notifyOrderChanged();
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
        if (!visibilityAllows(leftPx, topPx, widthPx, heightPx, scale)) return;
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
        if (!mAggregatedVisible || alpha <= 0.0f || widthPx <= 0.0f || heightPx <= 0.0f) return;

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
        NativePreviewHost.Registration registration = mPreviewRegistration;
        if (registration != null) registration.requestRender();
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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mVisibilityObserver != null) updateFullVisibility();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        mAggregatedVisible = visible;
        if (mVisibilityObserver != null) updateFullVisibility();
        requestNativePreviewRender();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAggregatedVisible = isShown();
        releaseNativePreviewRegistration();
        NativePreviewHost host = findNativePreviewHost();
        if (host != null) {
            removeFallbackSurface();
            mPreviewRegistration = host.registerNativePreview(this);
            startVisibilityTracking();
        } else {
            ensureFallbackSurface();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mAggregatedVisible = false;
        stopVisibilityTracking();
        releaseNativePreviewRegistration();
        super.onDetachedFromWindow();
    }

    private NativePreviewHost findNativePreviewHost() {
        View current = this;
        while (current.getParent() instanceof View parent) {
            if (parent instanceof NativePreviewHost host) return host;
            current = parent;
        }
        return null;
    }

    private void releaseNativePreviewRegistration() {
        NativePreviewHost.Registration registration = mPreviewRegistration;
        mPreviewRegistration = null;
        if (registration != null) registration.close();
    }

    private void ensureFallbackSurface() {
        if (mFallbackSurface != null) return;
        MinecraftSurfaceView surface = new MinecraftSurfaceView(getContext()) {
            @Override
            public float getAlpha() {
                // updateSurface() reads alpha, origin and size together on the UI thread.
                // Encode clipping in that same packet: comparing its synchronized pose with a
                // newer volatile UI position would drop otherwise visible frames during scrolling.
                return isFullyVisible() ? super.getAlpha() : 0.0f;
            }
        };
        mFallbackSurface = surface;
        surface.setEnabled(false);
        surface.setClickable(false);
        surface.setFocusable(false);
        surface.setRenderer(new MinecraftSurfaceView.Renderer() {
            private int renderWidth;
            private int renderHeight;

            @Override
            public void onSurfaceChanged(int width, int height) {
                renderWidth = width;
                renderHeight = height;
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
                if (surface != mFallbackSurface || alpha <= 0.0f) return;
                // MUI supplies the synchronized surface origin in the current graphics pose.
                // Never read mutable View geometry from the Minecraft render thread.
                float safeGuiScale = guiScale > 0 ? (float) guiScale : 1f;
                renderAt(
                        graphics,
                        deltaTick,
                        guiScale,
                        alpha,
                        graphics.pose().m20() * safeGuiScale,
                        graphics.pose().m21() * safeGuiScale,
                        renderWidth,
                        renderHeight,
                        1.0f
                );
            }
        });
        addView(surface, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void removeFallbackSurface() {
        MinecraftSurfaceView surface = mFallbackSurface;
        if (surface == null) return;
        mFallbackSurface = null;
        removeView(surface);
    }

    private static float sanitizeScale(float scale) {
        return scale > 0.0f ? scale : 1.0f;
    }
}
