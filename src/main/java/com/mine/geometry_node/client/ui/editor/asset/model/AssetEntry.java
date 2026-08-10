package com.mine.geometry_node.client.ui.editor.asset.model;

import java.io.File;

public final class AssetEntry {
    private final AssetSourceKind mSourceKind;
    private final String mKey;
    private final String mName;
    private final String mPath;
    private final boolean mDirectory;
    private final long mSize;
    private final long mLastModified;
    private final File mLocalFile;
    private final AssetType mType;

    private AssetEntry(AssetSourceKind sourceKind, String key, String name, String path,
                       boolean directory, long size, long lastModified, File localFile, AssetType type) {
        mSourceKind = sourceKind;
        mKey = key;
        mName = name;
        mPath = path;
        mDirectory = directory;
        mSize = size;
        mLastModified = Math.max(0L, lastModified);
        mLocalFile = localFile;
        mType = type != null ? type : AssetTypeRegistry.INSTANCE.resolve(sourceKind, name, directory);
    }

    public static AssetEntry local(File file, String key, String displayPath) {
        String name = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        AssetType type = AssetTypeRegistry.INSTANCE.resolve(AssetSourceKind.LOCAL, name, file.isDirectory());
        return new AssetEntry(AssetSourceKind.LOCAL, key, name, displayPath,
                file.isDirectory(), file.length(), file.lastModified(), file, type);
    }

    public static AssetEntry remote(String path, String name, boolean directory, long size, long lastModified) {
        String normalizedPath = path == null ? "" : path.replace('\\', '/');
        String key = "remote:" + normalizedPath;
        AssetType type = AssetTypeRegistry.INSTANCE.resolve(AssetSourceKind.REMOTE, name, directory);
        return new AssetEntry(AssetSourceKind.REMOTE, key, name, normalizedPath,
                directory, size, lastModified, null, type);
    }

    public AssetSourceKind sourceKind() {
        return mSourceKind;
    }

    public String key() {
        return mKey;
    }

    public String name() {
        return mName;
    }

    public String path() {
        return mPath;
    }

    public boolean isDirectory() {
        return mDirectory;
    }

    public long size() {
        return mSize;
    }

    public long lastModified() {
        return mLastModified;
    }

    public File localFile() {
        return mLocalFile;
    }

    public AssetType type() {
        return mType;
    }

    public boolean supports(AssetTypeAction action) {
        return mType != null && mType.supports(action);
    }
}
