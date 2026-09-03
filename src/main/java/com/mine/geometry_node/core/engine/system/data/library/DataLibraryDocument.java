package com.mine.geometry_node.core.engine.system.data.library;

import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import com.mine.geometry_node.core.node.definition.port.PortType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory representation of the complete virtual Data Library tree. */
public final class DataLibraryDocument {
    private final LinkedHashMap<UUID, DataLibraryFolder> folders = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, DataLibraryEntry> entries = new LinkedHashMap<>();
    private final LinkedHashMap<FolderLocation, UUID> folderIdsByLocation = new LinkedHashMap<>();
    private final LinkedHashMap<EntryLocation, UUID> entryIdsByLocation = new LinkedHashMap<>();

    public Map<UUID, DataLibraryFolder> folders() {
        return Collections.unmodifiableMap(folders);
    }

    public Map<UUID, DataLibraryEntry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    public Optional<DataLibraryFolder> findFolder(UUID id) {
        return Optional.ofNullable(id == null ? null : folders.get(id));
    }

    public Optional<DataLibraryEntry> find(UUID id) {
        return Optional.ofNullable(id == null ? null : entries.get(id));
    }

    public Optional<DataLibraryFolder> findFolder(@Nullable UUID parentId, String name) {
        UUID id = folderIdsByLocation.get(new FolderLocation(parentId, normalizedLookupName(name)));
        return findFolder(id);
    }

    public Optional<DataLibraryEntry> findByLocation(@Nullable UUID parentId, PortType type, String key) {
        UUID id = entryIdsByLocation.get(new EntryLocation(parentId, type, normalizedLookupName(key)));
        return find(id);
    }

    /** Resolves a non-root slash-delimited folder path. Root is represented by a null parent ID. */
    public Optional<DataLibraryFolder> findFolderByPath(String path) {
        List<String> segments = pathSegments(path);
        if (segments.isEmpty()) return Optional.empty();
        UUID parentId = null;
        DataLibraryFolder current = null;
        for (String segment : segments) {
            current = findFolder(parentId, segment).orElse(null);
            if (current == null) return Optional.empty();
            parentId = current.id();
        }
        return Optional.of(current);
    }

    /** Creates missing path segments and returns the final folder, or null for the root. */
    @Nullable
    public UUID ensureFolderPath(String path) {
        UUID parentId = null;
        for (String segment : pathSegments(path)) {
            DataLibraryFolder folder = findFolder(parentId, segment).orElse(null);
            if (folder == null) {
                folder = new DataLibraryFolder(UUID.randomUUID(), parentId, segment);
                putFolder(folder);
            }
            parentId = folder.id();
        }
        return parentId;
    }

    public String folderPath(@Nullable UUID folderId) {
        if (folderId == null) return "";
        List<String> names = new ArrayList<>();
        UUID current = folderId;
        while (current != null) {
            DataLibraryFolder folder = folders.get(current);
            if (folder == null) throw new IllegalStateException("Unknown Data Library folder: " + current);
            names.add(folder.name());
            current = folder.parentId();
        }
        Collections.reverse(names);
        return String.join("/", names);
    }

    public void putFolder(DataLibraryFolder folder) {
        if (entries.containsKey(folder.id())) {
            throw new IllegalArgumentException("UUID is already used by an entry: " + folder.id());
        }
        if (folder.parentId() != null && !folders.containsKey(folder.parentId())) {
            throw new IllegalArgumentException("Unknown parent folder: " + folder.parentId());
        }
        if (folder.id().equals(folder.parentId()) || isDescendant(folder.parentId(), folder.id())) {
            throw new IllegalArgumentException("Data Library folder cycle: " + folder.id());
        }
        FolderLocation location = new FolderLocation(folder.parentId(), folder.name());
        UUID owner = folderIdsByLocation.get(location);
        if (owner != null && !owner.equals(folder.id())) {
            throw new IllegalArgumentException("Duplicate folder name in the same directory: " + folder.name());
        }
        DataLibraryFolder previous = folders.put(folder.id(), folder);
        if (previous != null) folderIdsByLocation.remove(new FolderLocation(previous.parentId(), previous.name()), previous.id());
        folderIdsByLocation.put(location, folder.id());
    }

    public void put(DataLibraryEntry entry) {
        if (folders.containsKey(entry.id())) {
            throw new IllegalArgumentException("UUID is already used by a folder: " + entry.id());
        }
        if (entry.parentId() != null && !folders.containsKey(entry.parentId())) {
            throw new IllegalArgumentException("Unknown parent folder: " + entry.parentId());
        }
        EntryLocation location = new EntryLocation(entry.parentId(), entry.type(), entry.key());
        UUID owner = entryIdsByLocation.get(location);
        if (owner != null && !owner.equals(entry.id())) {
            throw new IllegalArgumentException("Duplicate Data Library key in the same directory: " + entry.key());
        }
        DataLibraryEntry previous = entries.put(entry.id(), entry);
        if (previous != null) {
            entryIdsByLocation.remove(new EntryLocation(previous.parentId(), previous.type(), previous.key()), previous.id());
        }
        entryIdsByLocation.put(location, entry.id());
    }

    public boolean remove(UUID id) {
        DataLibraryEntry removed = entries.remove(id);
        if (removed == null) return false;
        entryIdsByLocation.remove(new EntryLocation(removed.parentId(), removed.type(), removed.key()), id);
        return true;
    }

    /** Removes a folder and every descendant folder and entry. */
    public boolean removeFolder(UUID folderId) {
        if (!folders.containsKey(folderId)) return false;
        List<UUID> childFolders = folders.values().stream()
                .filter(folder -> folderId.equals(folder.parentId())).map(DataLibraryFolder::id).toList();
        childFolders.forEach(this::removeFolder);
        entries.values().stream().filter(entry -> folderId.equals(entry.parentId()))
                .map(DataLibraryEntry::id).toList().forEach(this::remove);
        DataLibraryFolder removed = folders.remove(folderId);
        folderIdsByLocation.remove(new FolderLocation(removed.parentId(), removed.name()), folderId);
        return true;
    }

    public DataLibraryDocument copy() {
        DataLibraryDocument copy = new DataLibraryDocument();
        folders.values().forEach(copy::putFolder);
        entries.values().forEach(entry -> copy.put(new DataLibraryEntry(
                entry.id(), entry.parentId(), entry.type(), entry.key(), GraphValueSnapshot.snapshot(entry.value()))));
        return copy;
    }

    public int size() {
        return entries.size();
    }

    static String requireName(String value, String description) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(description + " must not be blank");
        if (normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(description + " must not contain path separators");
        }
        if (normalized.equals(".") || normalized.equals("..")) {
            throw new IllegalArgumentException(description + " must not be . or ..");
        }
        return normalized;
    }

    private boolean isDescendant(@Nullable UUID possibleDescendant, UUID ancestor) {
        UUID current = possibleDescendant;
        while (current != null) {
            if (current.equals(ancestor)) return true;
            DataLibraryFolder folder = folders.get(current);
            current = folder != null ? folder.parentId() : null;
        }
        return false;
    }

    private static List<String> pathSegments(String path) {
        if (path == null || path.isBlank() || path.equals("/")) return List.of();
        String normalized = path.replace('\\', '/');
        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isBlank()) continue;
            result.add(requireName(segment, "Folder path segment"));
        }
        return List.copyOf(result);
    }

    private static String normalizedLookupName(String value) {
        return value == null ? "" : value.trim();
    }

    private record FolderLocation(@Nullable UUID parentId, String name) {}
    private record EntryLocation(@Nullable UUID parentId, PortType type, String key) {}
}
