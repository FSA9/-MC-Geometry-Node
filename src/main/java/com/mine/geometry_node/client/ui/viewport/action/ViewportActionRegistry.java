package com.mine.geometry_node.client.ui.viewport.action;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class ViewportActionRegistry {
    private static final Map<ViewportActionId, ViewportAction> ACTIONS = new EnumMap<>(ViewportActionId.class);

    static {
        register(KeyScope.GLOBAL, ViewportActionId.UNDO, "撤销", config -> config.keyBindings.global.undo, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.REDO, "重做", config -> config.keyBindings.global.redo, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.SAVE, "保存", config -> config.keyBindings.global.save, state -> true);
        register(KeyScope.GLOBAL, ViewportActionId.COPY, "复制", config -> config.keyBindings.global.copy, ViewportActionState::isReady);
        register(KeyScope.GLOBAL, ViewportActionId.PASTE, "粘贴", config -> config.keyBindings.global.paste, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.DELETE, "删除", config -> config.keyBindings.viewport.delete, ViewportActionState::isReady);
        register(KeyScope.VIEWPORT, ViewportActionId.TOGGLE_SNAP_TO_GRID, "吸附", config -> config.keyBindings.viewport.toggleSnapToGrid, state -> true);
        register(KeyScope.VIEWPORT, ViewportActionId.TOGGLE_GRID_AND_AXIS, "坐标轴", config -> config.keyBindings.viewport.toggleGridAndAxis, state -> true);
        register(KeyScope.VIEWPORT, ViewportActionId.MOVE_SELECTION, "移动", config -> config.keyBindings.viewport.moveSelection,
                state -> state.isReady() && state.hasSelectedNodes());
        register(KeyScope.VIEWPORT, ViewportActionId.GROUP_INTO_FRAME, "并入图框", config -> config.keyBindings.viewport.groupIntoFrame,
                state -> state.isReady() && !state.isInsideGroupScope());
        register(KeyScope.VIEWPORT, ViewportActionId.GROUP_INTO_NODE_GROUP, "合并为图组", config -> config.keyBindings.viewport.groupIntoNodeGroup,
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
        return action != null ? action.label() : "";
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
            String label,
            ViewportAction.ShortcutReader shortcutReader,
            ViewportAction.EnabledReader<ViewportActionState> enabledReader
    ) {
        ACTIONS.put(id, new ViewportAction(scope, id, label, shortcutReader, enabledReader));
    }
}
