package com.mine.geometry_node.core.engine.system.asset.transfer.io;

import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class VerifiedAssetCommitter {
    private VerifiedAssetCommitter() {
    }

    public static CommitResult commit(Path verifiedTemporaryFile, Path target,
                                      AssetTransferConflictPolicy conflictPolicy) throws IOException {
        Path source = verifiedTemporaryFile.toAbsolutePath().normalize();
        Path destination = target.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Verified transfer file is unavailable");
        }
        if (Files.exists(destination)) {
            if (!Files.isRegularFile(destination) || Files.isSymbolicLink(destination)) {
                throw new IOException("Transfer target is not a replaceable regular file");
            }
            if (conflictPolicy == AssetTransferConflictPolicy.SKIP) {
                Files.deleteIfExists(source);
                return CommitResult.SKIPPED;
            }
            if (conflictPolicy == AssetTransferConflictPolicy.FAIL_IF_EXISTS) {
                throw new AssetTransferConflictException("Transfer target now exists");
            }
        }

        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        StandardCopyOption[] options = conflictPolicy == AssetTransferConflictPolicy.OVERWRITE
                ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        try {
            Files.move(source, destination, options);
        } catch (AtomicMoveNotSupportedException ignored) {
            if (conflictPolicy == AssetTransferConflictPolicy.OVERWRITE) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, destination);
            }
        }
        return CommitResult.COMMITTED;
    }

    public enum CommitResult { COMMITTED, SKIPPED }
    public static final class AssetTransferConflictException extends IOException {
        public AssetTransferConflictException(String message) { super(message); }
    }
}
