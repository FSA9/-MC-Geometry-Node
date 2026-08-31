package com.mine.geometry_node.client.ui.editor.asset.model;

import com.mine.geometry_node.core.engine.system.asset.AssetDescriptor;
import com.mine.geometry_node.core.engine.system.asset.AssetMetadata;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;

import java.io.File;

public final class AssetEntry {
    private final AssetSourceKind mSourceKind;
    private final String mKey;
    private final AssetDescriptor mDescriptor;
    private final File mLocalFile;
    private final AssetType mType;

    private AssetEntry(AssetSourceKind sourceKind, String key, AssetDescriptor descriptor,
                       File localFile, AssetType type) {
        mSourceKind = sourceKind;
        mKey = key;
        mDescriptor = descriptor;
        mLocalFile = localFile;
        mType = type != null ? type : AssetTypeRegistry.INSTANCE.get(AssetTypeRegistry.FILE_ID);
    }

    public static AssetEntry local(File file, String key, String displayPath) {
        String name = file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
        boolean directory = file.isDirectory();
        AssetMetadata metadata = directory ? AssetMetadata.UNKNOWN : AssetTypeCatalog.inspect(file.toPath());
        AssetDescriptor descriptor = new AssetDescriptor(displayPath, name, directory,
                file.length(), file.lastModified(), metadata);
        AssetType type = AssetTypeRegistry.INSTANCE.resolve(metadata.typeId(), AssetSourceKind.LOCAL, directory);
        return new AssetEntry(AssetSourceKind.LOCAL, key, descriptor, file, type);
    }

    public static AssetEntry remote(AssetDescriptor descriptor) {
        String normalizedPath = descriptor.path().replace('\\', '/');
        String key = "remote:" + normalizedPath;
        AssetDescriptor normalized = new AssetDescriptor(normalizedPath, descriptor.name(), descriptor.directory(),
                descriptor.size(), descriptor.lastModified(), descriptor.metadata());
        AssetType type = AssetTypeRegistry.INSTANCE.resolve(
                descriptor.metadata().typeId(), AssetSourceKind.REMOTE, descriptor.directory());
        return new AssetEntry(AssetSourceKind.REMOTE, key, normalized, null, type);
    }

    public AssetSourceKind sourceKind() {
        return mSourceKind;
    }

    public String key() {
        return mKey;
    }

    public String name() {
        return mDescriptor.name();
    }

    public String path() {
        return mDescriptor.path();
    }

    public boolean isDirectory() {
        return mDescriptor.directory();
    }

    public long size() {
        return mDescriptor.size();
    }

    public long lastModified() {
        return mDescriptor.lastModified();
    }

    public File localFile() {
        return mLocalFile;
    }

    public AssetType type() {
        return mType;
    }

    public AssetDescriptor descriptor() {
        return mDescriptor;
    }

    public AssetMetadata metadata() {
        return mDescriptor.metadata();
    }

    public boolean supports(AssetTypeAction action) {
        return mType != null && mType.supports(action);
    }
}
