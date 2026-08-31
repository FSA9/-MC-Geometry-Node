package com.mine.geometry_node.core.engine.system.asset.transfer.io;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class OutgoingAssetTransferFile implements AutoCloseable {
    private final Path sourcePath;
    private final long totalBytes;
    private final long lastModifiedMillis;
    private final String sha256;
    private final FileChannel channel;

    private OutgoingAssetTransferFile(Path sourcePath, long totalBytes, long lastModifiedMillis,
                                      String sha256, FileChannel channel) {
        this.sourcePath = sourcePath;
        this.totalBytes = totalBytes;
        this.lastModifiedMillis = lastModifiedMillis;
        this.sha256 = sha256;
        this.channel = channel;
    }

    public static OutgoingAssetTransferFile open(Path sourcePath, long maxFileBytes) throws IOException {
        Path normalized = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IOException("Transfer source is not a regular file: " + sourcePath);
        }
        long size = Files.size(normalized);
        long acceptedMaximum = Math.min(maxFileBytes, AssetTransferProtocolLimits.MAX_FILE_BYTES);
        if (size > acceptedMaximum) throw new IOException("Transfer source exceeds file limit: " + size);
        long modified = Files.getLastModifiedTime(normalized).toMillis();
        String hash = AssetTransferHashing.sha256(normalized);
        if (Files.size(normalized) != size || Files.getLastModifiedTime(normalized).toMillis() != modified) {
            throw new IOException("Transfer source changed while hashing: " + sourcePath);
        }
        return new OutgoingAssetTransferFile(normalized, size, modified, hash,
                FileChannel.open(normalized, StandardOpenOption.READ));
    }

    public synchronized byte[] readChunk(long offset, int requestedBytes) throws IOException {
        if (offset < 0L || offset > totalBytes) throw new IOException("Invalid transfer offset: " + offset);
        int length = Math.min(AssetTransferProtocolLimits.clampChunkBytes(requestedBytes),
                Math.toIntExact(Math.min(Integer.MAX_VALUE, totalBytes - offset)));
        if (length == 0) return new byte[0];
        ByteBuffer buffer = ByteBuffer.allocate(length);
        int readTotal = 0;
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, offset + readTotal);
            if (read < 0) break;
            readTotal += read;
        }
        if (readTotal != length) throw new IOException("Transfer source ended before declared size");
        return buffer.array();
    }

    public void verifyUnchanged() throws IOException {
        if (Files.size(sourcePath) != totalBytes || Files.getLastModifiedTime(sourcePath).toMillis() != lastModifiedMillis) {
            throw new IOException("Transfer source changed during transfer: " + sourcePath);
        }
    }

    public Path sourcePath() { return sourcePath; }
    public long totalBytes() { return totalBytes; }
    public long lastModifiedMillis() { return lastModifiedMillis; }
    public String sha256() { return sha256; }
    @Override public void close() throws IOException { channel.close(); }
}
