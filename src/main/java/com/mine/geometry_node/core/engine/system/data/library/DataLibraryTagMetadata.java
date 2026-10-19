package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import com.mine.geometry_node.core.metadata.MetadataStore;
import com.mine.geometry_node.core.metadata.TagAccess;
import com.mine.geometry_node.core.metadata.TagPolicy;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Server-authoritative library entry bindings; client mirrors continue to use their async gateway. */
public final class DataLibraryTagMetadata {
    public static final String ENTRY = "geometry_node:data_library_entry";

    private DataLibraryTagMetadata() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(ENTRY, EntryTarget.class, Set.of(MetadataCapabilities.Capability.TAG),
                target -> new MetadataCapabilities.Access(target.tags(), null));
    }

    /**
     * An authorized server context and stable UUID, with an optimistic-concurrency token. Bindings
     * advance their token after their own commits; concurrent outside changes still reject a write.
     * requireAccess must recheck the caller's current authority/lifetime, and must not mutate state.
     */
    public static final class EntryTarget implements MetadataStore<List<String>> {
        private final MinecraftServer server;
        private final UUID entryId;
        private final Runnable requireAccess;
        private String expectedFingerprint;
        private boolean updating;

        public EntryTarget(MinecraftServer server, UUID entryId, String expectedFingerprint, Runnable requireAccess) {
            this.server = Objects.requireNonNull(server, "server");
            this.entryId = Objects.requireNonNull(entryId, "entryId");
            if (!DataLibraryObjectFingerprint.isValid(expectedFingerprint)) {
                throw new IllegalArgumentException("Invalid expected entry fingerprint");
            }
            this.expectedFingerprint = expectedFingerprint;
            this.requireAccess = Objects.requireNonNull(requireAccess, "requireAccess");
        }

        public TagAccess tags() { return new TagAccess(this, TagPolicy.DATA_LIBRARY_LEGACY); }

        /** Last accepted CAS token; no new client notification protocol is implied. */
        public synchronized String fingerprint() { return expectedFingerprint; }

        @Override public synchronized List<String> read() {
            try { return RemoteDataLibraryService.INSTANCE.readTags(server, entryId, requireAccess); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }

        @Override public synchronized boolean update(UnaryOperator<List<String>> transformation) {
            Objects.requireNonNull(transformation, "transformation");
            if (updating) throw new IllegalStateException("Library entry update is already in progress");
            updating = true;
            try {
                var result = RemoteDataLibraryService.INSTANCE.updateTags(
                        server, entryId, expectedFingerprint, requireAccess, transformation);
                if (result.changed()) {
                    expectedFingerprint = DataLibraryObjectFingerprint.entry(
                            result.loaded().document().find(entryId).orElseThrow(), server.overworld().registryAccess());
                }
                return result.changed();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            } finally { updating = false; }
        }
    }
}
