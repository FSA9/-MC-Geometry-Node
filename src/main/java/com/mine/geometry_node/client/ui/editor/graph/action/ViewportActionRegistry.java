package com.mine.geometry_node.client.ui.editor.graph.action;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class ViewportActionRegistry {
    private static final Map<ViewportActionId, ViewportAction> ACTIONS = new EnumMap<>(ViewportActionId.class);

    static {
        register(KeyScope.VIEWPORT, ViewportActionId.SELECT_ALL, BuiltinConfigEntries.VIEWPORT_SELECT_ALL,
                ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.UNDO, BuiltinConfigEntries.GLOBAL_UNDO, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.REDO, BuiltinConfigEntries.GLOBAL_REDO, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.SAVE, BuiltinConfigEntries.GLOBAL_SAVE, state -> true);
        register(KeyScope.VIEWPORT, ViewportActionId.EXPORT_IMAGE, "导出为图片", null, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.COPY, BuiltinConfigEntries.GLOBAL_COPY, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.PASTE, BuiltinConfigEntries.GLOBAL_PASTE, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.DELETE, BuiltinConfigEntries.VIEWPORT_DELETE, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.TOGGLE_SNAP_TO_GRID, BuiltinConfigEntries.VIEWPORT_TOGGLE_SNAP, state -> true);
        register(KeyScope.VIEWPORT, ViewportActionId.TOGGLE_GRID_AND_AXIS, BuiltinConfigEntries.VIEWPORT_TOGGLE_GRID, state -> true);
        register(KeyScope.VIEWPORT, ViewportActionId.MOVE_SELECTION, BuiltinConfigEntries.VIEWPORT_MOVE,
                state -> state.isReady() && state.hasSelection());
        register(KeyScope.VIEWPORT, ViewportActionId.GROUP_INTO_FRAME, BuiltinConfigEntries.VIEWPORT_GROUP_FRAME,
                state -> state.isReady() && !state.isInsideGroupScope());
        register(KeyScope.VIEWPORT, ViewportActionId.GROUP_INTO_NODE_GROUP, BuiltinConfigEntries.VIEWPORT_GROUP_NODE,
                ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.EXIT_GROUP, "退出图组", null,
                state -> state.isReady() && state.isInsideGroupScope());
        register(KeyScope.VIEWPORT, ViewportActionId.ADD_NODE, "添加节点", null, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.ADD_FRAME, "添加图框", null,
                state -> state.isReady() && !state.isInsideGroupScope());
        register(KeyScope.VIEWPORT, ViewportActionId.ADD_GROUP, "创建空图组", null, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.DISSOLVE_NODE_GROUP, "解散图组", null, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.SET_FRAME_PROPERTY, "设置图框", null, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.SET_GROUP_NODE_PROPERTY, "设置图组", null, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.RENAME_PORT, "重命名端口", null, ViewportActionState::isReady);
    }

    private ViewportActionRegistry() {}

    public static ViewportAction get(ViewportActionId id) {
        return ACTIONS.get(id);
    }

    public static Collection<ViewportAction> all() {
        return ACTIONS.values();
    }

    public static String label(ViewportActionId id) {
        ViewportAction action = get(id);
        return action != null ? Component.translatable(action.label()).getString() : "";
    }

    public static String shortcutText(ViewportActionId id, AppConfig config) {
        ViewportAction action = get(id);
        return action != null ? action.shortcutText(config) : "";
    }

    public static boolean isEnabled(ViewportActionId id, ViewportActionState state) {
        ViewportAction action = get(id);
        return action != null && action.isEnabled(state);
    }

    private static void register(
            KeyScope scope,
            ViewportActionId id,
            ConfigEntry<String> shortcutEntry,
            ViewportAction.EnabledReader<ViewportActionState> enabledReader
    ) {
        ACTIONS.put(id, new ViewportAction(scope, id, shortcutEntry.labelTranslationKey(), shortcutEntry, enabledReader));
    }

    private static void register(
            KeyScope scope,
            ViewportActionId id,
            String label,
            ConfigEntry<String> shortcutEntry,
            ViewportAction.EnabledReader<ViewportActionState> enabledReader
    ) {
        ACTIONS.put(id, new ViewportAction(scope, id, label, shortcutEntry, enabledReader));
    }
}
