package com.mine.geometry_node.client.ui.persistence.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mine.geometry_node.client.ui.persistence.PathUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public final class EditorSessionStore {
    public static final EditorSessionStore INSTANCE = new EditorSessionStore();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_DEPTH = 32;
    private static final int MAX_LEAVES = 32;
    private static final int MAX_TERMINAL_TABS = 32;
    private static final int MAX_PATH_LENGTH = 4096;
    private static final int MAX_TERMINAL_TITLE_LENGTH = 80;
    private static final int MAX_TERMINAL_PROFILE_ID_LENGTH = 128;
    private static final Set<String> EDITOR_TYPES = Set.of(
            "GRAPH_EDITOR", "ASSET_BROWSER", "TERMINAL", "PERFORMANCE", "GAME_VIEWPORT");

    private EditorSessionStore() {
    }

    public synchronized EditorSessionState load() {
        Path file = PathUtils.getEditorSessionFile().toPath();
        if (!Files.isRegularFile(file)) {
            return new EditorSessionState();
        }
        try {
            EditorSessionState loaded = GSON.fromJson(
                    Files.readString(file, StandardCharsets.UTF_8), EditorSessionState.class);
            if (loaded == null || loaded.version != EditorSessionState.CURRENT_VERSION) {
                return new EditorSessionState();
            }
            int[] leafCount = {0};
            loaded.layout = sanitizeArea(loaded.layout, 0, leafCount);
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
        state.version = EditorSessionState.CURRENT_VERSION;
        Path target = PathUtils.getEditorSessionFile().toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, GSON.toJson(state), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("[EditorSession] Failed to save editor document: " + exception.getMessage());
        }
    }

    private static EditorSessionState.AreaState sanitizeArea(
            EditorSessionState.AreaState state, int depth, int[] leafCount) {
        if (state == null || depth > MAX_DEPTH) {
            return null;
        }
        if ("split".equals(state.kind)) {
            state.direction = "VERTICAL".equals(state.direction) ? "VERTICAL" : "HORIZONTAL";
            state.ratio = Float.isFinite(state.ratio)
                    ? Math.max(0.12f, Math.min(0.88f, state.ratio))
                    : 0.5f;
            state.first = sanitizeArea(state.first, depth + 1, leafCount);
            state.second = sanitizeArea(state.second, depth + 1, leafCount);
            return state.first != null && state.second != null ? state : null;
        }
        if (!"leaf".equals(state.kind) || ++leafCount[0] > MAX_LEAVES) {
            return null;
        }
        if (!EDITOR_TYPES.contains(state.editorType)) {
            state.editorType = "GRAPH_EDITOR";
        }
        if (state.assetBrowser == null) {
            state.assetBrowser = new EditorSessionState.AssetBrowserState();
        }
        String location = state.assetBrowser.location;
        state.assetBrowser.location = "REMOTE".equals(location) || "FAVORITES".equals(location)
                ? location
                : "LOCAL";
        state.assetBrowser.localPath = sanitizePath(state.assetBrowser.localPath);
        state.assetBrowser.remotePath = sanitizePath(state.assetBrowser.remotePath);
        state.assetBrowser.navigationWeight = sanitizeWeight(state.assetBrowser.navigationWeight);
        if (state.terminal == null) {
            state.terminal = new EditorSessionState.TerminalState();
        }
        sanitizeTerminal(state.terminal);
        state.terminal.activeTab = Math.max(0, Math.min(state.terminal.tabCount - 1, state.terminal.activeTab));
        state.first = null;
        state.second = null;
        return state;
    }

    private static void sanitizeTerminal(EditorSessionState.TerminalState terminal) {
        if (terminal.tabs == null) {
            terminal.tabs = new ArrayList<>();
        }
        if (terminal.tabs.size() > MAX_TERMINAL_TABS) {
            terminal.tabs = new ArrayList<>(terminal.tabs.subList(0, MAX_TERMINAL_TABS));
        }
        if (terminal.tabs.isEmpty()) {
            int count = Math.max(1, Math.min(MAX_TERMINAL_TABS, terminal.tabCount));
            for (int i = 0; i < count; i++) {
                terminal.tabs.add(defaultTerminalTab(i + 1));
            }
        }
        for (int i = 0; i < terminal.tabs.size(); i++) {
            EditorSessionState.TerminalTabState tab = terminal.tabs.get(i);
            if (tab == null) {
                tab = defaultTerminalTab(i + 1);
                terminal.tabs.set(i, tab);
            }
            tab.id = sanitizeUuid(tab.id);
            tab.title = sanitizeText(tab.title, "Terminal " + (i + 1), MAX_TERMINAL_TITLE_LENGTH);
            tab.mode = "SHELL".equals(tab.mode) ? tab.mode : "COMMAND";
            tab.profileId = sanitizeText(tab.profileId, "", MAX_TERMINAL_PROFILE_ID_LENGTH);
        }
        terminal.tabCount = terminal.tabs.size();
    }

    private static EditorSessionState.TerminalTabState defaultTerminalTab(int number) {
        EditorSessionState.TerminalTabState tab = new EditorSessionState.TerminalTabState();
        tab.id = UUID.randomUUID().toString();
        tab.title = "Terminal " + number;
        return tab;
    }

    private static String sanitizeUuid(String value) {
        try {
            return UUID.fromString(value).toString();
        } catch (RuntimeException ignored) {
            return UUID.randomUUID().toString();
        }
    }

    private static String sanitizeText(String value, String fallback, int maxLength) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static String sanitizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        return trimmed.length() <= MAX_PATH_LENGTH ? trimmed : trimmed.substring(0, MAX_PATH_LENGTH);
    }

    private static float sanitizeWeight(float weight) {
        return Float.isFinite(weight) ? Math.max(0.05f, Math.min(0.45f, weight)) : 0.2f;
    }
}
