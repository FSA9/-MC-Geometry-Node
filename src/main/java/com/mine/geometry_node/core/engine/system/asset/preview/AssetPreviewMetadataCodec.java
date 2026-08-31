package com.mine.geometry_node.core.engine.system.asset.preview;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Shared persistent descriptor format used by client and server preview caches. */
public final class AssetPreviewMetadataCodec {
    private static final int FORMAT_VERSION = 1;

    private AssetPreviewMetadataCodec() {
    }

    public static void write(DataOutput output, int magic, AssetPreviewDescriptor descriptor) throws IOException {
        output.writeInt(magic);
        output.writeInt(FORMAT_VERSION);
        output.writeUTF(descriptor.revision().identity().remotePath());
        output.writeUTF(descriptor.revision().identity().kind().id());
        output.writeLong(descriptor.revision().sourceSize());
        output.writeLong(descriptor.revision().sourceLastModified());
        output.writeInt(descriptor.revision().formatVersion());
        output.writeInt(descriptor.format().ordinal());
        output.writeInt(descriptor.width());
        output.writeInt(descriptor.height());
        output.writeInt(descriptor.encodedBytes());
        output.writeUTF(descriptor.sha256());
    }

    public static AssetPreviewDescriptor read(DataInput input, int expectedMagic) throws IOException {
        try {
            if (input.readInt() != expectedMagic || input.readInt() != FORMAT_VERSION) {
                throw new IOException("Invalid asset preview metadata header");
            }
            String path = input.readUTF();
            AssetPreviewKind kind = AssetPreviewKind.fromId(input.readUTF());
            long size = input.readLong();
            long modified = input.readLong();
            int revisionVersion = input.readInt();
            int formatIndex = input.readInt();
            if (kind == null || !kind.isConcrete()
                    || formatIndex < 0 || formatIndex >= AssetPreviewFormat.values().length) {
                throw new IOException("Invalid asset preview metadata value");
            }
            AssetPreviewRevision revision = new AssetPreviewRevision(
                    new AssetPreviewIdentity(path, kind), size, modified, revisionVersion);
            return new AssetPreviewDescriptor(revision, AssetPreviewFormat.values()[formatIndex],
                    input.readInt(), input.readInt(), input.readInt(), input.readUTF());
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid asset preview metadata", exception);
        }
    }
}
