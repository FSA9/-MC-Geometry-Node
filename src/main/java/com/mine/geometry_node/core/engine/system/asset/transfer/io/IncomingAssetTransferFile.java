package com.mine.geometry_node.core.engine.system.asset.transfer.io;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferProtocolLimits;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Objects;

public final class IncomingAssetTransferFile implements AutoCloseable {
    private final Path temporaryPath;
    private final long declaredBytes;
    private final String declaredSha256;
    private final FileChannel channel;
    private final MessageDigest digest = AssetTransferHashing.newSha256();
    private int nextSequence;
    private long nextOffset;
    private boolean verified;
    private boolean retained;
    private boolean closed;

    private IncomingAssetTransferFile(Path temporaryPath, long declaredBytes, String declaredSha256,
                                      FileChannel channel) {
        this.temporaryPath = temporaryPath;
        this.declaredBytes = declaredBytes;
        this.declaredSha256 = declaredSha256;
        this.channel = channel;
    }

    public static IncomingAssetTransferFile create(Path temporaryDirectory, long declaredBytes,
                                                   String declaredSha256) throws IOException {
        if (declaredBytes < 0L || declaredBytes > AssetTransferProtocolLimits.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Invalid declared asset size: " + declaredBytes);
        }
        String hash = Objects.requireNonNullElse(declaredSha256, "").trim().toLowerCase(java.util.Locale.ROOT);
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid declared SHA-256");
        Files.createDirectories(temporaryDirectory);
        Path temporary = Files.createTempFile(temporaryDirectory, ".geometrynode-transfer-", ".part");
        try {
            FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            return new IncomingAssetTransferFile(temporary, declaredBytes, hash, channel);
        } catch (Throwable throwable) {
            Files.deleteIfExists(temporary);
            throw throwable;
        }
    }

    public synchronized ChunkResult writeChunk(int sequence, long offset, byte[] content) throws IOException {
        ensureOpen();
        if (content == null || content.length == 0 || content.length > AssetTransferProtocolLimits.MAX_CHUNK_BYTES) {
            throw new AssetTransferSequenceException("Invalid chunk length");
        }
        if (sequence == nextSequence - 1 && offset + content.length == nextOffset) return ChunkResult.DUPLICATE;
        if (sequence != nextSequence || offset != nextOffset || offset + content.length > declaredBytes) {
            throw new AssetTransferSequenceException("Expected sequence " + nextSequence + " at " + nextOffset
                    + ", received " + sequence + " at " + offset);
        }
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) channel.write(buffer);
        digest.update(content);
        nextSequence++;
        nextOffset += content.length;
        return ChunkResult.ACCEPTED;
    }

    public synchronized void verifyAndClose() throws IOException {
        ensureOpen();
        if (nextOffset != declaredBytes) {
            throw new AssetTransferIntegrityException("Expected " + declaredBytes + " bytes, received " + nextOffset);
        }
        channel.force(true);
        channel.close();
        closed = true;
        String actualHash = AssetTransferHashing.toHex(digest.digest());
        if (!actualHash.equals(declaredSha256)) {
            throw new AssetTransferIntegrityException("SHA-256 mismatch");
        }
        verified = true;
    }

    /** Transfers cleanup ownership to the committer after successful verification. */
    public synchronized Path retainVerifiedFile() {
        if (!verified) throw new IllegalStateException("Transfer has not been verified");
        retained = true;
        return temporaryPath;
    }

    public synchronized int nextSequence() { return nextSequence; }
    public synchronized long nextOffset() { return nextOffset; }
    public Path temporaryPath() { return temporaryPath; }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            channel.close();
            closed = true;
        }
        if (!retained) Files.deleteIfExists(temporaryPath);
    }

    private void ensureOpen() throws IOException {
        if (closed) throw new IOException("Incoming transfer is closed");
    }

    public enum ChunkResult { ACCEPTED, DUPLICATE }
    public static class AssetTransferSequenceException extends IOException {
        public AssetTransferSequenceException(String message) { super(message); }
    }
    public static class AssetTransferIntegrityException extends IOException {
        public AssetTransferIntegrityException(String message) { super(message); }
    }
}
