package com.mine.geometry_node.client.ui.workspace.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.platform.ClientPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class EditorSessionStore {
    public static final EditorSessionStore INSTANCE = new EditorSessionStore(
            () -> ClientPaths.getEditorSessionFile().toPath());

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_DEPTH = 32;
    private static final int MAX_LEAVES = 32;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final Set<String> EDITOR_TYPES = Set.of(
            "GRAPH_EDITOR", "ASSET_BROWSER", "DATA_LIBRARY", "TERMINAL", "PERFORMANCE", "GAME_VIEWPORT");

    private final Supplier<Path> sessionFile;

    EditorSessionStore(Supplier<Path> sessionFile) {
        this.sessionFile = Objects.requireNonNull(sessionFile, "sessionFile");
    }

    public synchronized EditorSessionState load() {
        Path file = sessionFile.get();
        if (!Files.isRegularFile(file)) {
            return new EditorSessionState();
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) return new EditorSessionState();
            EditorSessionState loaded = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), EditorSessionState.class);
            if (loaded == null || (loaded.version < 1 || loaded.version > EditorSessionState.CURRENT_VERSION)) {
                return new EditorSessionState();
            }
            loaded.version = EditorSessionState.CURRENT_VERSION;
            int[] leafCount = {0};
            loaded.layout = sanitizeArea(loaded.layout, 0, leafCount, new HashSet<>());
            return loaded;
        } catch (Exception exception) {
            System.err.println("[EditorSession] Failed to load editor document: " + exception.getMessage());
            return new EditorSessionState();
        }
    }

    public synchronized void save(EditorSessionState state) {
        if (state == null || state.layout == null) {
            return;
        }
        Path target = sessionFile.get();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            // Never replace documents written by a newer application version.
            if (Files.isRegularFile(target)) {
                if (Files.size(target) > MAX_FILE_BYTES) return;
                try {
                    var document = com.google.gson.JsonParser.parseString(Files.readString(target)).getAsJsonObject();
                    if (document.has("version") && document.get("version").getAsInt() > EditorSessionState.CURRENT_VERSION) return;
                } catch (RuntimeException ignored) { /* A valid snapshot may repair a corrupt file. */ }
            }
            if (state.version > EditorSessionState.CURRENT_VERSION || !validTree(state.layout, 0,
                    new int[]{0}, Collections.newSetFromMap(new IdentityHashMap<>()))) return;
            EditorSessionState snapshot = com.mine.geometry_node.client.ui.workspace.state.UiStateContext.snapshotSession(state);
            snapshot.version = EditorSessionState.CURRENT_VERSION;
            snapshot.layout = sanitizeArea(snapshot.layout, 0, new int[]{0}, new HashSet<>());
            if (snapshot.layout == null) return;
            String json = GSON.toJson(snapshot);
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) return;
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) {
            System.err.println("[EditorSession] Failed to save editor document: " + exception.getMessage());
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }

    private static boolean validTree(EditorSessionState.AreaState state, int depth, int[] leaves,
                                     Set<EditorSessionState.AreaState> seen) {
        if (state == null || depth > MAX_DEPTH || !seen.add(state)) return false;
        if ("split".equals(state.kind)) return validTree(state.first, depth + 1, leaves, seen)
                && validTree(state.second, depth + 1, leaves, seen);
        return "leaf".equals(state.kind) && ++leaves[0] <= MAX_LEAVES;
    }

    private static EditorSessionState.AreaState sanitizeArea(
            EditorSessionState.AreaState state, int depth, int[] leafCount, Set<String> owners) {
        if (state == null || depth > MAX_DEPTH) {
            return null;
        }
        if ("split".equals(state.kind)) {
            state.ownerId = null;
            state.slots = null;
            state.assetBrowser = null;
            state.terminal = null;
            state.direction = "VERTICAL".equals(state.direction) ? "VERTICAL" : "HORIZONTAL";
            state.ratio = Float.isFinite(state.ratio)
                    ? Math.max(0.12f, Math.min(0.88f, state.ratio))
                    : 0.5f;
            state.first = sanitizeArea(state.first, depth + 1, leafCount, owners);
            state.second = sanitizeArea(state.second, depth + 1, leafCount, owners);
            return state.first != null && state.second != null ? state : null;
        }
        if (!"leaf".equals(state.kind) || ++leafCount[0] > MAX_LEAVES) {
            return null;
        }
        if (state.editorType == null || !EDITOR_TYPES.contains(state.editorType)) {
            state.editorType = "GRAPH_EDITOR";
        }
        com.mine.geometry_node.client.ui.workspace.state.BuiltinUiStateKeys.migrateLegacy(state);
        if (!owners.add(state.ownerId)) {
            state.ownerId = UUID.randomUUID().toString();
            owners.add(state.ownerId);
        }
        state.first = null;
        state.second = null;
        return state;
    }

}
