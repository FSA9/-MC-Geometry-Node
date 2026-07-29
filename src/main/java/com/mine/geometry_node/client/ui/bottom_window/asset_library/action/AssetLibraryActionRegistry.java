package com.mine.geometry_node.client.ui.bottom_window.asset_library.action;

import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class AssetLibraryActionRegistry {
    private static final Map<AssetLibraryActionId, ScopedAction<AssetLibraryActionId>> ACTIONS = new EnumMap<>(AssetLibraryActionId.class);

    static {
        register(AssetLibraryActionId.COPY, "geometry_node.asset_library.action.copy", config -> config.keyBindings.global.copy,
                state -> state != null && state.canCopySelection());
        register(AssetLibraryActionId.PASTE, "geometry_node.asset_library.action.paste", config -> config.keyBindings.global.paste,
                state -> state != null && state.canPasteClipboard());
        register(AssetLibraryActionId.CUT, "geometry_node.asset_library.action.cut", config -> config.keyBindings.global.cut,
                state -> state != null && state.canCutSelection());
        register(AssetLibraryActionId.DELETE, "geometry_node.asset_library.action.delete", config -> config.keyBindings.global.delete,
                state -> state != null && state.canDeleteSelection());
        register(AssetLibraryActionId.RENAME, "geometry_node.asset_library.action.rename", config -> config.keyBindings.global.rename,
                state -> state != null && state.canRenameSelection());
    }

    private AssetLibraryActionRegistry() {
    }

    public static Collection<ScopedAction<AssetLibraryActionId>> all() {
        return ACTIONS.values();
    }

    public static String label(AssetLibraryActionId id) {
        ScopedAction<AssetLibraryActionId> action = ACTIONS.get(id);
        return action != null ? Component.translatable(action.label()).getString() : "";
    }

    public static String shortcutText(AssetLibraryActionId id, AppConfig config) {
        ScopedAction<AssetLibraryActionId> action = ACTIONS.get(id);
        return action != null ? action.shortcutText(config) : "";
    }

    private static void register(AssetLibraryActionId id,
                                 String translationKey,
                                 ScopedAction.ShortcutReader shortcutReader,
                                 ScopedAction.EnabledReader<RightFileBrowserPanel> enabledReader) {
        ACTIONS.put(id, new ScopedAction<>(KeyScope.GLOBAL, id, translationKey, shortcutReader, enabledReader));
    }
}
