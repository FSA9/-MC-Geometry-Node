package com.mine.geometry_node.client.ui.workspace.state;

import com.mine.geometry_node.client.ui.workspace.persistence.EditorSessionState;
import java.util.ArrayList;
import java.util.UUID;

/** Built-in editor codecs and the v1 compatibility adapter. */
public final class BuiltinUiStateKeys {
    private BuiltinUiStateKeys() {}
    public static final String SCOPE = "default";
    private static final int MAX_TERMINAL_TABS = 32, MAX_PATH_LENGTH = 4096,
            MAX_TERMINAL_TITLE_LENGTH = 80, MAX_TERMINAL_PROFILE_ID_LENGTH = 128;
    public static final UiStateKey<EditorSessionState.AssetBrowserState> ASSET_BROWSER = new UiStateKey<>(
            "asset_browser:session", 1, UiStateLifetime.PERSISTENT, EditorSessionState.AssetBrowserState.class,
            EditorSessionState.AssetBrowserState::new, BuiltinUiStateKeys::sanitizeAsset);
    public static final UiStateKey<EditorSessionState.TerminalState> TERMINAL = new UiStateKey<>(
            "terminal:session", 1, UiStateLifetime.PERSISTENT, EditorSessionState.TerminalState.class,
            EditorSessionState.TerminalState::new, state -> {
                sanitizeTerminal(state);
                state.activeTab = Math.max(0, Math.min(state.tabCount - 1, state.activeTab));
                return state;
            });
    public static void migrateLegacy(EditorSessionState.AreaState state) {
        UiStateContext context = UiStateContext.forArea(state, null);
        var entries = state.slots.get(SCOPE);
        if (state.assetBrowser != null && (entries == null || !entries.containsKey(ASSET_BROWSER.id())))
            context.write(ASSET_BROWSER, SCOPE, state.assetBrowser);
        if (state.terminal != null && (entries == null || !entries.containsKey(TERMINAL.id())))
            context.write(TERMINAL, SCOPE, state.terminal);
        state.assetBrowser = null;
        state.terminal = null;
    }
    private static EditorSessionState.AssetBrowserState sanitizeAsset(EditorSessionState.AssetBrowserState state) {
        state.location = "REMOTE".equals(state.location) || "FAVORITES".equals(state.location) ? state.location : "LOCAL";
        state.localPath = sanitizePath(state.localPath);
        state.remotePath = sanitizePath(state.remotePath);
        state.navigationWeight = sanitizeWeight(state.navigationWeight);
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
