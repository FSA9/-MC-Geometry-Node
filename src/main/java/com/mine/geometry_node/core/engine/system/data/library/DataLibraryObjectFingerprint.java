package com.mine.geometry_node.core.engine.system.data.library;

import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared canonical fingerprint used for per-object optimistic concurrency checks. */
public final class DataLibraryObjectFingerprint {
    public static final int LENGTH = 64;

    private DataLibraryObjectFingerprint() {}

    public static String entry(DataLibraryEntry entry, HolderLookup.Provider registries) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "entry");
        json.addProperty("id", entry.id().toString());
        addParent(json, entry.parentId());
        json.addProperty("type", entry.type().name());
        json.addProperty("key", entry.key());
        json.add("value", DataLibraryValueCodec.encode(entry.type(), entry.value(), registries));
        return sha256(json.toString());
    }

    public static String folder(DataLibraryFolder folder) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", "folder");
        json.addProperty("id", folder.id().toString());
        addParent(json, folder.parentId());
        json.addProperty("name", folder.name());
        return sha256(json.toString());
    }

    /** Fingerprints the exact objects affected by a delete, including complete folder subtrees. */
    public static String deletion(DataLibraryDocument document, Set<DataLibraryObjectKey> roots,
                                  HolderLookup.Provider registries) {
        if (document == null || roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("Delete fingerprint requires at least one object");
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (DataLibraryObjectKey root : roots) collectSubtree(document, root.id(), ids);
        List<UUID> ordered = new ArrayList<>(ids);
        ordered.sort(Comparator.comparing(UUID::toString));
        StringBuilder canonical = new StringBuilder();
        for (UUID id : ordered) {
            DataLibraryEntry entry = document.find(id).orElse(null);
            if (entry != null) {
                canonical.append("entry:").append(entry(entry, registries)).append('\n');
                continue;
            }
            DataLibraryFolder folder = document.findFolder(id).orElse(null);
            if (folder == null) throw new IllegalStateException("STALE_OBJECT: " + id);
            canonical.append("folder:").append(folder(folder)).append('\n');
        }
        return sha256(canonical.toString());
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() != LENGTH) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) return false;
        }
        return true;
    }

    private static void addParent(JsonObject json, java.util.UUID parentId) {
        if (parentId == null) json.add("parent", com.google.gson.JsonNull.INSTANCE);
        else json.addProperty("parent", parentId.toString());
    }

    private static void collectSubtree(DataLibraryDocument document, UUID id, Set<UUID> destination) {
        if (!destination.add(id)) return;
        if (document.find(id).isPresent()) return;
        if (document.findFolder(id).isEmpty()) throw new IllegalStateException("STALE_OBJECT: " + id);
        document.folders().values().stream()
                .filter(folder -> id.equals(folder.parentId()))
                .forEach(folder -> collectSubtree(document, folder.id(), destination));
        document.entries().values().stream()
                .filter(entry -> id.equals(entry.parentId()))
                .forEach(entry -> destination.add(entry.id()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
