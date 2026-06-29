package com.mine.geometry_node.client.ui.bottom_window.asset_library.action;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class AssetLibraryActionRegistry {
    private static final Map<AssetLibraryActionId, ScopedAction<AssetLibraryActionId>> ACTIONS = new EnumMap<>(AssetLibraryActionId.class);

    static {
        register(AssetLibraryActionId.COPY, "复制", config -> config.keyBindings.global.copy,
                state -> state != null && state.canCopySelection());
        register(AssetLibraryActionId.PASTE, "粘贴", config -> config.keyBindings.global.paste,
                state -> state != null && state.canPasteClipboard());
    }

    private AssetLibraryActionRegistry() {
    }

    public static Collection<ScopedAction<AssetLibraryActionId>> all() {
        return ACTIONS.values();
    }

    public static String label(AssetLibraryActionId id) {
        ScopedAction<AssetLibraryActionId> action = ACTIONS.get(id);
        return action != null ? action.label() : "";
    }

    public static String shortcutText(AssetLibraryActionId id, AppConfig config) {
        ScopedAction<AssetLibraryActionId> action = ACTIONS.get(id);
        return action != null ? action.shortcutText(config) : "";
    }

    private static void register(AssetLibraryActionId id,
                                 String label,
                                 ScopedAction.ShortcutReader shortcutReader,
                                 ScopedAction.EnabledReader<RightFileBrowserPanel> enabledReader) {
        ACTIONS.put(id, new ScopedAction<>(KeyScope.GLOBAL, id, label, shortcutReader, enabledReader));
    }
}
