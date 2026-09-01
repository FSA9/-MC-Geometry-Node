package com.mine.geometry_node.core.engine.system.data.library;

import net.minecraft.core.HolderLookup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/** Reads and atomically replaces one complete Data Library JSON file. */
public final class DataLibraryFileStore {
    public static final String DEFAULT_FILE_NAME = "data_library.json";

    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private DataLibraryFileStore() {
    }

    public static DataLibraryLoadResult read(Path file, HolderLookup.Provider registries) throws IOException {
        Path normalized = normalize(file);
        if (!Files.exists(normalized)) {
            return new DataLibraryLoadResult(new DataLibraryDocument(), java.util.List.of());
        }
        return DataLibraryJsonCodec.decode(Files.readString(normalized, StandardCharsets.UTF_8), registries);
    }

    /** Serializes concurrent read-modify-write operations for the same normalized file. */
    public static DataLibraryLoadResult updateAtomic(Path file, HolderLookup.Provider registries,
                                                     UnaryOperator<DataLibraryDocument> update) throws IOException {
        Path normalized = normalize(file);
        ReentrantLock lock = LOCKS.computeIfAbsent(normalized, ignored -> new ReentrantLock());
        lock.lock();
        try {
            DataLibraryLoadResult loaded = read(normalized, registries);
            DataLibraryDocument updated = update.apply(loaded.document().copy());
            if (updated == null) throw new IllegalArgumentException("Data Library update returned null");
            writeAtomicUnlocked(normalized, DataLibraryJsonCodec.encode(updated, registries));
            return new DataLibraryLoadResult(updated, loaded.diagnostics());
        } finally {
            lock.unlock();
        }
    }

    private static void writeAtomicUnlocked(Path file, String json) throws IOException {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = file.resolveSibling("." + file.getFileName() + ".geometrynode-write-" + UUID.randomUUID());
        try {
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path normalize(Path file) {
        if (file == null) throw new IllegalArgumentException("Data Library path is required");
        return file.toAbsolutePath().normalize();
    }
}
