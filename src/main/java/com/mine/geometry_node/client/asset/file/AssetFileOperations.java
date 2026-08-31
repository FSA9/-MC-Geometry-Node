package com.mine.geometry_node.client.asset.file;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Filesystem primitives shared by every local asset type. */
public final class AssetFileOperations {
    private AssetFileOperations() {
    }

    public static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void moveNew(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    public static Path siblingTemporary(Path target, String purpose) {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("path must have a parent: " + target);
        }
        String name = normalized.getFileName().toString();
        return parent.resolve("." + name + ".geometrynode-" + purpose + "-" + UUID.randomUUID());
    }
}
