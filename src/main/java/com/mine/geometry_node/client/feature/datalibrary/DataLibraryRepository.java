package com.mine.geometry_node.client.feature.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.metadata.MetadataStore;
import com.mine.geometry_node.core.metadata.TagAccess;
import com.mine.geometry_node.core.metadata.TagPolicy;

import java.util.List;
import java.util.Map;
import com.mine.geometry_node.core.metadata.DataDictionary;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import java.util.Set;
import java.util.UUID;

/** Client access boundary for the server-authoritative Data Library. */
public interface DataLibraryRepository {
    record EntryKey(UUID id) {}

    record Folder(UUID id, UUID parentId, String name) {}

    record Entry(UUID id, UUID parentId, PortType type, String key, Object value, List<String> tags,
                 Map<String, Object> data) {
        public Entry {
            tags = tags == null ? List.of() : List.copyOf(tags);
            data = DataDictionary.snapshot(java.util.Objects.requireNonNull(data, "data"), GraphMetadataValuePolicy.of(null));
        }

        public Entry(UUID id, UUID parentId, PortType type, String key, Object value) {
            this(id, parentId, type, key, value, List.of(), Map.of());
        }
        public Entry(UUID id, UUID parentId, PortType type, String key, Object value, List<String> tags) {
            this(id, parentId, type, key, value, tags, Map.of());
        }

        /** Semantic snapshot comparison: mutable graph leaves need not share Java identity. */
        public boolean sameContent(Entry other) {
            var policy = GraphMetadataValuePolicy.of(null);
            return other != null && java.util.Objects.equals(id, other.id)
                    && java.util.Objects.equals(parentId, other.parentId) && type == other.type
                    && java.util.Objects.equals(key, other.key) && tags.equals(other.tags)
                    && policy.equivalent(value, other.value)
                    && DataDictionary.equivalent(data, other.data, policy);
        }

        @Override public Map<String, Object> data() {
            return DataDictionary.snapshot(data, GraphMetadataValuePolicy.of(null));
        }
    }

    /** Custom dictionary capability, independent of the entry value and vanilla NBT. */
    default boolean canEditData(Entry entry) {
        return entry != null && hasLoadedSnapshot() && findEntry(entry.id()) != null;
    }

    /** A complete dictionary commit through the same CAS/pending lifecycle as value and tag changes. */
    default void replaceData(Entry expected, Map<String, Object> replacement) {
        if (!canEditData(expected) || isUpdatePending(expected.id()) || !expected.sameContent(findEntry(expected.id()))) {
            throw new IllegalStateException("Data Library entry changed, is unavailable, or is still saving");
        }
        var policy = GraphMetadataValuePolicy.of(null);
        Map<String, Object> data = DataDictionary.snapshot(replacement, policy);
        if (DataDictionary.equivalent(expected.data(), data, policy)) return;
        update(expected, new Entry(expected.id(), expected.parentId(), expected.type(), expected.key(),
                expected.value(), expected.tags(), data));
    }

    /** Completion follows the authoritative save/refresh; empty string means success. */
    default void replaceData(Entry expected, Map<String, Object> replacement,
                             java.util.function.Consumer<String> completion) {
        Map<String, Object> data = DataDictionary.snapshot(replacement, GraphMetadataValuePolicy.of(null));
        if (!canEditData(expected) || isUpdatePending(expected.id()) || !expected.sameContent(findEntry(expected.id())))
            throw new IllegalStateException("Data Library entry changed, is unavailable, or is still saving");
        if (DataDictionary.equivalent(expected.data(), data, GraphMetadataValuePolicy.of(null))) {
            if (completion != null) completion.accept("");
            return;
        }
        var finished = new java.util.concurrent.atomic.AtomicBoolean();
        var submitted = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable listener = new Runnable() {
            @Override public void run() {
                if (!submitted.get() || isUpdatePending(expected.id()) || !finished.compareAndSet(false, true)) return;
                removeChangeListener(this);
                Entry current = findEntry(expected.id());
                String message = dataUpdateMessage(expected.id());
                if (message.isEmpty() && (!hasLoadedSnapshot() || current == null
                        || !DataDictionary.equivalent(current.data(), data, GraphMetadataValuePolicy.of(null)))) {
                    message = "database.data_update_failed";
                }
                if (completion != null) completion.accept(message);
            }
        };
        addChangeListener(listener);
        try {
            replaceData(expected, data);
            submitted.set(true);
            listener.run();
        } catch (RuntimeException failure) {
            removeChangeListener(listener);
            throw failure;
        }
    }

    /** Unlocalized message key, or empty when no dictionary update failed. */
    default String dataUpdateMessage(UUID entryId) { return ""; }

    List<Folder> folders();

    List<Entry> entries();

    /** False while the current connection has no authoritative snapshot, including after a failed load. */
    default boolean hasLoadedSnapshot() { return true; }

    default Entry findEntry(UUID id) {
        return entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }

    default String folderPath(UUID folderId) {
        if (folderId == null) return "/";
        java.util.Map<UUID, Folder> byId = folders().stream()
                .collect(java.util.stream.Collectors.toMap(Folder::id, folder -> folder));
        java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
        java.util.Set<UUID> visited = new java.util.HashSet<>();
        UUID current = folderId;
        while (current != null && visited.add(current)) {
            Folder folder = byId.get(current);
            if (folder == null) break;
            names.addFirst(folder.name());
            current = folder.parentId();
        }
        return names.isEmpty() ? "/" : "/" + String.join("/", names);
    }

    void create(UUID parentId, PortType type);

    void createFolder(UUID parentId);

    void update(Entry expected, Entry replacement);

    /** Capability and snapshot availability only; use isUpdatePending separately for busy state. */
    default boolean canEditTags(Entry entry) {
        return entry != null && hasLoadedSnapshot() && findEntry(entry.id()) != null;
    }

    /** Stale, unavailable or pending targets throw IllegalStateException; invalid tags throw IllegalArgumentException. */
    default void addTag(Entry expected, String tag) {
        requireTagEdit(expected);
        tagAccess(expected).add(TagPolicy.DATA_LIBRARY_LEGACY.normalize(tag));
    }

    /** Uses the same CAS and pending lifecycle as update; duplicate/absent tags do not submit writes. */
    default void removeTag(Entry expected, String tag) {
        requireTagEdit(expected);
        tagAccess(expected).remove(TagPolicy.DATA_LIBRARY_LEGACY.normalize(tag));
    }

    private void requireTagEdit(Entry expected) {
        if (!canEditTags(expected) || isUpdatePending(expected.id()) || !expected.sameContent(findEntry(expected.id()))) {
            throw new IllegalStateException("Data Library entry changed, is unavailable, or is still saving");
        }
    }

    /** Unlocalized message key (without geometry_node. prefix), or empty when there is no message. */
    default String tagUpdateMessage(UUID entryId) { return ""; }

    private TagAccess tagAccess(Entry expected) {
        return new TagAccess(new MetadataStore<List<String>>() {
            @Override public List<String> read() { return expected.tags(); }

            @Override public boolean update(java.util.function.UnaryOperator<List<String>> transformation) {
                requireTagEdit(expected);
                List<String> tags = transformation.apply(expected.tags());
                if (expected.tags().equals(tags)) return false;
                DataLibraryRepository.this.update(expected, new Entry(expected.id(), expected.parentId(),
                        expected.type(), expected.key(), expected.value(), tags, expected.data()));
                return true;
            }
        }, TagPolicy.DATA_LIBRARY_LEGACY);
    }

    /** True until this entry's queued writes and their refreshes have finished. */
    default boolean isUpdatePending(UUID entryId) { return false; }

    void updateFolder(Folder expected, Folder replacement);

    void moveEntry(UUID entryId, UUID parentId);

    void moveFolder(UUID folderId, UUID parentId);

    void delete(Set<EntryKey> entries, Set<UUID> folders);

    default void addChangeListener(Runnable listener) {}

    default void removeChangeListener(Runnable listener) {}

    default void refresh(Runnable completion) {
        if (completion != null) completion.run();
    }

    DataLibraryRepository EMPTY = new DataLibraryRepository() {
        @Override public boolean canEditTags(Entry entry) { return false; }
        @Override public boolean canEditData(Entry entry) { return false; }
        @Override public List<Folder> folders() { return List.of(); }
        @Override public List<Entry> entries() { return List.of(); }
        @Override public void create(UUID parentId, PortType type) {}
        @Override public void createFolder(UUID parentId) {}
        @Override public void update(Entry expected, Entry replacement) {}
        @Override public void updateFolder(Folder expected, Folder replacement) {}
        @Override public void moveEntry(UUID entryId, UUID parentId) {}
        @Override public void moveFolder(UUID folderId, UUID parentId) {}
        @Override public void delete(Set<EntryKey> entries, Set<UUID> folders) {}
    };
}
