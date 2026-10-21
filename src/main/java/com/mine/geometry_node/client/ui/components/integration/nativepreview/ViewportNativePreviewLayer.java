package com.mine.geometry_node.client.ui.components.integration.nativepreview;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import net.minecraft.ReportedException;
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
    private final List<PreviewRegistration> mRegistrations = new ArrayList<>();
    private volatile List<PreviewRegistration> mRenderOrder = List.of();

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
                for (PreviewRegistration registration : mRenderOrder) {
                    // A frame may retain an older snapshot after close/re-register.
                    // Do not hold the list lock across rendering or interrupt an in-flight call.
                    if (registration.mClosed.get()) continue;
                    try {
                        registration.mPreview.renderNativePreview(graphics, deltaTick, guiScale, alpha);
                    } catch (RuntimeException failure) {
                        if (failure instanceof ReportedException reported && reported.getCause() instanceof Error) throw failure;
                        // One addon nativepreview must not prevent the remaining previews from rendering.
                    }
                }
            }
        });
    }

    public NativePreviewHost.Registration registerPreview(ViewportNativePreview preview) {
        Objects.requireNonNull(preview, "preview");
        PreviewRegistration registration = new PreviewRegistration(preview);
        synchronized (mPreviewLock) {
            if (mRegistrations.stream().anyMatch(existing -> preview.equals(existing.mPreview))) {
                throw new IllegalStateException("Native preview is already registered");
            }
            mRegistrations.add(registration);
            rebuildRenderOrderLocked();
        }
        setVisibility(View.VISIBLE);
        invalidate();
        return registration;
    }

    private void unregisterPreview(PreviewRegistration registration) {
        boolean empty;
        synchronized (mPreviewLock) {
            if (!mRegistrations.remove(registration)) return;
            rebuildRenderOrderLocked();
            empty = mRegistrations.isEmpty();
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
        List<PreviewRegistration> ordered = new ArrayList<>(mRegistrations);
        ordered.sort(Comparator.comparingLong(registration -> registration.mPreview.getNativePreviewOrder()));
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
            if (mClosed.compareAndSet(false, true)) unregisterPreview(this);
        }
    }
}
