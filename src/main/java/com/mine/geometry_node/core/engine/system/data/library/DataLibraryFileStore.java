package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;
import net.minecraft.core.HolderLookup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
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
        return updateAtomicResult(file, registries, update).loaded();
    }

    /** The no-change decision is made under the same lock as the read, validation, and write. */
    public static UpdateResult updateAtomicResult(Path file, HolderLookup.Provider registries,
                                                  UnaryOperator<DataLibraryDocument> update) throws IOException {
        Objects.requireNonNull(update, "update");
        Path normalized = normalize(file);
        ReentrantLock lock = LOCKS.computeIfAbsent(normalized, ignored -> new ReentrantLock());
        lock.lock();
        try {
            // All mutation entrypoints share this per-file lock, including different entry bindings.
            if (lock.getHoldCount() > 1) throw new IllegalStateException("Data Library mutation is already in progress: " + normalized);
            DataLibraryLoadResult loaded = read(normalized, registries);
            if (!loaded.diagnostics().isEmpty()) {
                throw new IOException("Data Library contains invalid objects; refusing to overwrite it");
            }
            String before = DataLibraryJsonCodec.encode(loaded.document(), registries);
            DataLibraryDocument updated = update.apply(loaded.document().copy());
            if (updated == null) throw new IllegalArgumentException("Data Library update returned null");
            String after = DataLibraryJsonCodec.encode(updated, registries);
            if (before.equals(after)) return new UpdateResult(loaded, false);
            writeAtomicUnlocked(normalized, after);
            return new UpdateResult(new DataLibraryLoadResult(updated, loaded.diagnostics()), true);
        } finally {
            lock.unlock();
        }
    }

    /** Shared persisted entry-tag transaction used by the server adapter, including its CAS precondition. */
    public static UpdateResult updateTagsAtomic(Path file, HolderLookup.Provider registries, UUID entryId,
                                                 String expectedFingerprint, Runnable requireAccess,
                                                 UnaryOperator<List<String>> transformation) throws IOException {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(requireAccess, "requireAccess");
        Objects.requireNonNull(transformation, "transformation");
        return updateAtomicResult(file, registries, document -> {
            requireAccess.run();
            DataLibraryEntry entry = document.find(entryId)
                    .orElseThrow(() -> new IllegalStateException("STALE_OBJECT: " + entryId));
            if (!DataLibraryObjectFingerprint.isValid(expectedFingerprint)
                    || !DataLibraryObjectFingerprint.entry(entry, registries).equals(expectedFingerprint)) {
                throw new IllegalStateException("STALE_OBJECT: " + entryId);
            }
            List<String> tags = TagSet.normalize(
                    Objects.requireNonNull(transformation.apply(entry.tags()), "tags"), TagPolicy.DATA_LIBRARY_LEGACY);
            requireAccess.run();
            if (!entry.tags().equals(tags)) {
                document.put(new DataLibraryEntry(entry.id(), entry.parentId(), entry.type(), entry.key(),
                        entry.value(), tags, entry.data()));
            }
            return document;
        });
    }

    /** Shared dictionary transaction, with the same object CAS and file lock as tag/value writes. */
    public static UpdateResult updateDataAtomic(Path file, HolderLookup.Provider registries, UUID entryId,
                                                String expectedFingerprint, Runnable requireAccess,
                                                UnaryOperator<java.util.Map<String, Object>> transformation) throws IOException {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(requireAccess, "requireAccess");
        Objects.requireNonNull(transformation, "transformation");
        return updateAtomicResult(file, registries, document -> {
            requireAccess.run();
            DataLibraryEntry entry = document.find(entryId)
                    .orElseThrow(() -> new IllegalStateException("STALE_OBJECT: " + entryId));
            if (!DataLibraryObjectFingerprint.isValid(expectedFingerprint)
                    || !DataLibraryObjectFingerprint.entry(entry, registries).equals(expectedFingerprint)) {
                throw new IllegalStateException("STALE_OBJECT: " + entryId);
            }
            var policy = com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy.of(registries);
            var data = com.mine.geometry_node.core.metadata.DataDictionary.snapshot(
                    Objects.requireNonNull(transformation.apply(entry.data()), "data"), policy);
            requireAccess.run();
            if (!com.mine.geometry_node.core.metadata.DataDictionary.equivalent(entry.data(), data, policy)) {
                document.put(new DataLibraryEntry(entry.id(), entry.parentId(), entry.type(), entry.key(),
                        entry.value(), entry.tags(), data));
            }
            return document;
        });
    }

    public record UpdateResult(DataLibraryLoadResult loaded, boolean changed) {}

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
