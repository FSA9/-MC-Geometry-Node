package com.mine.geometry_node.client.ui.viewport.preview;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.view.View;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
                        // One addon preview must not prevent the remaining previews from rendering.
                    }
                }
            }
        });
    }

    public void registerPreview(ViewportNativePreview preview) {
        if (preview == null) return;
        synchronized (mPreviewLock) {
            if (mPreviews.contains(preview)) return;
            mPreviews.add(preview);
            rebuildRenderOrderLocked();
        }
        setVisibility(View.VISIBLE);
        invalidate();
    }

    public void unregisterPreview(ViewportNativePreview preview) {
        if (preview == null) return;
        boolean empty;
        synchronized (mPreviewLock) {
            if (!mPreviews.remove(preview)) return;
            rebuildRenderOrderLocked();
            empty = mPreviews.isEmpty();
        }
        if (empty) setVisibility(View.GONE);
        invalidate();
    }

    public void notifyPreviewOrderChanged() {
        synchronized (mPreviewLock) {
            rebuildRenderOrderLocked();
        }
        invalidate();
    }

    public void requestPreviewRender() {
        invalidate();
    }

    private void rebuildRenderOrderLocked() {
        List<ViewportNativePreview> ordered = new ArrayList<>(mPreviews);
        ordered.sort(Comparator.comparingLong(ViewportNativePreview::getNativePreviewOrder));
        mRenderOrder = List.copyOf(ordered);
    }
}
