package com.mine.geometry_node.client.ui.viewport.action;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public final class ViewportActionRegistry {
    private static final Map<ViewportActionId, ViewportAction> ACTIONS = new EnumMap<>(ViewportActionId.class);

    static {
        register(ViewportActionId.UNDO, "撤销", config -> config.keyBindings.undo, ViewportActionState::isReady);
        register(ViewportActionId.REDO, "重做", config -> config.keyBindings.redo, ViewportActionState::isReady);
        register(ViewportActionId.SAVE, "保存", config -> config.keyBindings.save, state -> true);
        register(ViewportActionId.COPY, "复制", config -> config.keyBindings.copy, ViewportActionState::isReady);
        register(ViewportActionId.PASTE, "粘贴", config -> config.keyBindings.paste, ViewportActionState::isReady);
        register(ViewportActionId.DELETE, "删除", config -> config.keyBindings.delete, ViewportActionState::isReady);
        register(ViewportActionId.TOGGLE_SNAP_TO_GRID, "吸附", config -> config.keyBindings.toggleSnapToGrid, state -> true);
        register(ViewportActionId.TOGGLE_GRID_AND_AXIS, "坐标轴", config -> config.keyBindings.toggleGridAndAxis, state -> true);
        register(ViewportActionId.GROUP_INTO_FRAME, "并入图框", config -> config.keyBindings.groupIntoFrame,
                state -> state.isReady() && !state.isInsideGroupScope());
        register(ViewportActionId.GROUP_INTO_NODE_GROUP, "合并为图组", config -> config.keyBindings.groupIntoNodeGroup,
                ViewportActionState::isReady);
        register(ViewportActionId.EXIT_GROUP, "退出图组", null,
                state -> state.isReady() && state.isInsideGroupScope());
        register(ViewportActionId.ADD_NODE, "添加节点", null, ViewportActionState::isReady);
        register(ViewportActionId.ADD_FRAME, "添加图框", null,
                state -> state.isReady() && !state.isInsideGroupScope());
        register(ViewportActionId.ADD_GROUP, "创建空图组", null, ViewportActionState::isReady);
        register(ViewportActionId.DISSOLVE_NODE_GROUP, "解散图组", null, ViewportActionState::isReady);
        register(ViewportActionId.SET_FRAME_PROPERTY, "设置图框", null, ViewportActionState::isReady);
        register(ViewportActionId.SET_GROUP_NODE_PROPERTY, "设置图组", null, ViewportActionState::isReady);
        register(ViewportActionId.RENAME_PORT, "重命名端口", null, ViewportActionState::isReady);
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
            ViewportActionId id,
            String label,
            ViewportAction.ShortcutReader shortcutReader,
            ViewportAction.EnabledReader enabledReader
    ) {
        ACTIONS.put(id, new ViewportAction(id, label, shortcutReader, enabledReader));
    }
}
