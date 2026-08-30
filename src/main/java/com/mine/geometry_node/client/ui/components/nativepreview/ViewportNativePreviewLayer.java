package com.mine.geometry_node.client.ui.components.nativepreview;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the single Minecraft raw-render surface used by all previews in one viewport.
 */
public final class ViewportNativePreviewLayer extends MinecraftSurfaceView {
    private final Object mPreviewLock = new Object();
    private final List<ViewportNativePreview> mPreviews = new ArrayList<>();
    private volatile List<ViewportNativePreview> mRenderOrder = List.of();

    public ViewportNativePreviewLayer(Context context) {
        super(context);
        setEnabled(false);
        setClickable(false);
        setFocusable(false);
        setVisibility(View.GONE);
        setRenderer(new Renderer() {
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
                for (ViewportNativePreview preview : mRenderOrder) {
                    try {
                        preview.renderNativePreview(graphics, deltaTick, guiScale, alpha);
                    } catch (RuntimeException ignored) {
                        // One addon nativepreview must not prevent the remaining previews from rendering.
                    }
                }
            }
        });
    }

    public NativePreviewHost.Registration registerPreview(ViewportNativePreview preview) {
        Objects.requireNonNull(preview, "preview");
        synchronized (mPreviewLock) {
            if (mPreviews.contains(preview)) {
                throw new IllegalStateException("Native preview is already registered");
            }
            mPreviews.add(preview);
            rebuildRenderOrderLocked();
        }
        setVisibility(View.VISIBLE);
        invalidate();
        return new PreviewRegistration(preview);
    }

    private void unregisterPreview(ViewportNativePreview preview) {
        boolean empty;
        synchronized (mPreviewLock) {
            if (!mPreviews.remove(preview)) return;
            rebuildRenderOrderLocked();
            empty = mPreviews.isEmpty();
        }
        if (empty) setVisibility(View.GONE);
        invalidate();
    }

    private void notifyPreviewOrderChanged() {
        synchronized (mPreviewLock) {
            rebuildRenderOrderLocked();
        }
        invalidate();
    }

    private void requestPreviewRender() {
        invalidate();
    }

    private void rebuildRenderOrderLocked() {
        List<ViewportNativePreview> ordered = new ArrayList<>(mPreviews);
        ordered.sort(Comparator.comparingLong(ViewportNativePreview::getNativePreviewOrder));
        mRenderOrder = List.copyOf(ordered);
    }

    private final class PreviewRegistration implements NativePreviewHost.Registration {
        private final ViewportNativePreview mPreview;
        private final AtomicBoolean mClosed = new AtomicBoolean();

        private PreviewRegistration(ViewportNativePreview preview) {
            mPreview = preview;
        }

        @Override
        public void requestRender() {
            if (!mClosed.get()) requestPreviewRender();
        }

        @Override
        public void notifyOrderChanged() {
            if (!mClosed.get()) notifyPreviewOrderChanged();
        }

        @Override
        public void close() {
            if (mClosed.compareAndSet(false, true)) unregisterPreview(mPreview);
        }
    }
}
