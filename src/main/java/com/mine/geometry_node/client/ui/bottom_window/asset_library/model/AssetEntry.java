package com.mine.geometry_node.client.ui.bottom_window.asset_library.model;

import java.io.File;

public final class AssetEntry {
    private final AssetSourceKind mSourceKind;
    private final String mKey;
    private final String mName;
    private final String mPath;
    private final boolean mDirectory;
    private final long mSize;
    private final File mLocalFile;

    private AssetEntry(AssetSourceKind sourceKind, String key, String name, String path, boolean directory, long size, File localFile) {
        mSourceKind = sourceKind;
        mKey = key;
        mName = name;
        mPath = path;
        mDirectory = directory;
        mSize = size;
        mLocalFile = localFile;
    }

    public static AssetEntry local(File file, String key, String displayPath) {
        String name = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        return new AssetEntry(AssetSourceKind.LOCAL, key, name, displayPath, file.isDirectory(), file.length(), file);
    }

    public static AssetEntry remote(String path, String name, boolean directory, long size) {
        String normalizedPath = path == null ? "" : path.replace('\\', '/');
        String key = "remote:" + normalizedPath;
        return new AssetEntry(AssetSourceKind.REMOTE, key, name, normalizedPath, directory, size, null);
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

    public File localFile() {
        return mLocalFile;
    }

    public boolean isJsonFile() {
        return !mDirectory && mName.toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }
}
