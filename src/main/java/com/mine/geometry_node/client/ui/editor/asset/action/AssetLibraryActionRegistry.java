package com.mine.geometry_node.client.ui.editor.asset.action;

import com.mine.geometry_node.client.ui.editor.asset.browser.AssetFileBrowserPanel;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class AssetLibraryActionRegistry {
    private static final Map<AssetLibraryActionId, ScopedAction<AssetLibraryActionId>> ACTIONS = new EnumMap<>(AssetLibraryActionId.class);

    static {
        register(AssetLibraryActionId.COPY, "geometry_node.asset_library.action.copy", BuiltinConfigEntries.GLOBAL_COPY,
                state -> state != null && state.canCopySelection());
        register(AssetLibraryActionId.PASTE, "geometry_node.asset_library.action.paste", BuiltinConfigEntries.GLOBAL_PASTE,
                state -> state != null && state.canPasteClipboard());
        register(AssetLibraryActionId.CUT, "geometry_node.asset_library.action.cut", BuiltinConfigEntries.GLOBAL_CUT,
                state -> state != null && state.canCutSelection());
        register(AssetLibraryActionId.DELETE, "geometry_node.asset_library.action.delete", BuiltinConfigEntries.GLOBAL_DELETE,
                state -> state != null && state.canDeleteSelection());
        register(AssetLibraryActionId.RENAME, "geometry_node.asset_library.action.rename", BuiltinConfigEntries.GLOBAL_RENAME,
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
                                 ConfigEntry<String> shortcutEntry,
                                 ScopedAction.EnabledReader<AssetFileBrowserPanel> enabledReader) {
        ACTIONS.put(id, new ScopedAction<>(KeyScope.GLOBAL, id, translationKey, shortcutEntry, enabledReader));
    }
}
