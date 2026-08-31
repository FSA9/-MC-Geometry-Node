package com.mine.geometry_node.client.ui.editor.asset.model;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;

import java.util.EnumSet;
import java.util.Set;

public final class AssetType {
    private final String mId;
    private final int mDefaultColor;
    private final boolean mDirectory;
    private final boolean mDisplayInBrowser;
    private final AssetPreviewKind mPreviewKind;
    private final Set<AssetSourceKind> mSources;
    private final Set<AssetTypeAction> mActions;

    public AssetType(String id, int defaultColor, boolean directory, boolean displayInBrowser,
                     AssetPreviewKind previewKind, Set<AssetSourceKind> sources,
                     Set<AssetTypeAction> actions) {
        mId = normalizeId(id);
        if (mId.isEmpty()) throw new IllegalArgumentException("asset type id must not be empty");
        mDefaultColor = defaultColor;
        mDirectory = directory;
        mDisplayInBrowser = displayInBrowser;
        mPreviewKind = previewKind != null ? previewKind : AssetPreviewKind.NONE;
        mSources = immutableEnumSet(sources);
        mActions = immutableEnumSet(actions);
    }

    public String id() {
        return mId;
    }

    public int defaultColor() {
        return mDefaultColor;
    }

    public boolean isDirectory() {
        return mDirectory;
    }

    public boolean displayInBrowser() {
        return mDisplayInBrowser;
    }

    public AssetPreviewKind previewKind() {
        return mPreviewKind;
    }

    public boolean supportsSource(AssetSourceKind source) {
        return source != null && mSources.contains(source);
    }

    public boolean supports(AssetTypeAction action) {
        return action != null && mActions.contains(action);
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(Set<E> source) {
        if (source == null || source.isEmpty()) return Set.of();
        return Set.copyOf(EnumSet.copyOf(source));
    }
}
