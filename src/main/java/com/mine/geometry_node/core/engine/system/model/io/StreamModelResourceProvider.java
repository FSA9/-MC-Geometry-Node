package com.mine.geometry_node.core.engine.system.model.io;

import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Objects;

/** Adapts resource-pack streams to seekable files without buffering a whole asset in Java memory.
 * The opener must already be confined to its own namespace. Each returned resource deletes its spool on close. */
public final class StreamModelResourceProvider implements ModelResourceProvider {
    @FunctionalInterface public interface Opener { InputStream open(String logicalId) throws IOException; }
    private final Opener opener; private final Path spoolDirectory; private final ModelLimits limits; private final ModelCancellation token;
    public StreamModelResourceProvider(Opener opener, Path spoolDirectory, ModelCancellation token) {
        this(opener, spoolDirectory, ModelLimits.DEFAULT, token);
    }
    public StreamModelResourceProvider(Opener opener, Path spoolDirectory, ModelLimits limits, ModelCancellation token) {
        this.opener = Objects.requireNonNull(opener); this.spoolDirectory = Objects.requireNonNull(spoolDirectory);
        this.limits = Objects.requireNonNull(limits); this.token = Objects.requireNonNull(token);
    }
    @Override public ModelResource open(String logicalId) throws IOException {
        String id = ModelResourcePaths.normalize(logicalId); token.check(); Files.createDirectories(spoolDirectory);
        Path file = Files.createTempFile(spoolDirectory, "model-", ".source"); boolean adopted = false;
        try {
            try (InputStream input = opener.open(id); OutputStream output = Files.newOutputStream(file)) {
                byte[] buffer = new byte[64 * 1024]; long bytes = 0;
                while (true) {
                    token.check(); int count = input.read(buffer); if (count < 0) break; if (count == 0) continue;
                    if (count > limits.packageBytes() - bytes) throw new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED, id, "stream exceeds package limit");
                    output.write(buffer, 0, count); bytes += count;
                }
            }
            FileModelSource delegate = new FileModelSource(file, limits);
            ModelByteSource source = new ModelByteSource() {
                private boolean closed;
                @Override public long size() { return delegate.size(); }
                @Override public ByteBuffer read(long offset, int length, ModelCancellation cancellation) throws IOException { return delegate.read(offset, length, cancellation); }
                @Override public synchronized void close() throws IOException {
                    if (closed) return; closed = true;
                    try { delegate.close(); } finally { Files.deleteIfExists(file); }
                }
            };
            adopted = true; return new ModelResource(id, source);
        } finally { if (!adopted) Files.deleteIfExists(file); }
    }
}
