package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetPreviewKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.schematic.SchematicThumbnailView;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import java.io.File;

public final class AssetPreviewView extends FrameLayout {
    private final AssetEntry mEntry;
    private final AssetPreviewProvider mProvider;
    private final View mFallbackView;
    private AssetPreviewProvider.Subscription mSubscription;
    private View mResolvedView;
    private long mGeneration;

    public AssetPreviewView(Context context, AssetEntry entry, View fallbackView) {
        super(context);
        mEntry = entry;
        mProvider = AssetPreviewProviders.resolve(entry);
        mFallbackView = fallbackView;
        addView(fallbackView, matchParent());
    }

    public void preload() {
        ensureSubscription();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureSubscription();
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseSubscription();
        clearResolvedView();
        super.onDetachedFromWindow();
    }

    private void ensureSubscription() {
        if (mProvider == null || mSubscription != null) return;
        long generation = ++mGeneration;
        mSubscription = mProvider.subscribe(mEntry, new AssetPreviewProvider.Listener() {
            @Override
            public void available(File localSource) {
                if (generation == mGeneration) showResolved(localSource);
            }

            @Override
            public void unavailable() {
                if (generation == mGeneration) clearResolvedView();
            }
        });
    }

    private void showResolved(File localSource) {
        clearResolvedView();
        if (localSource == null || !localSource.isFile()) return;
        if (mEntry.sourceKind() == AssetSourceKind.LOCAL
                && mEntry.type().previewKind() == AssetPreviewKind.SCHEMATIC) {
            mResolvedView = new SchematicThumbnailView(getContext(), localSource);
        } else {
            mResolvedView = new ImageThumbnailView(getContext(), localSource);
        }
        mFallbackView.setVisibility(View.GONE);
        addView(mResolvedView, matchParent());
    }

    private void clearResolvedView() {
        if (mResolvedView == null) return;
        removeView(mResolvedView);
        mResolvedView = null;
        mFallbackView.setVisibility(View.VISIBLE);
    }

    private void releaseSubscription() {
        mGeneration++;
        if (mSubscription != null) {
            mSubscription.close();
            mSubscription = null;
        }
    }

    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
