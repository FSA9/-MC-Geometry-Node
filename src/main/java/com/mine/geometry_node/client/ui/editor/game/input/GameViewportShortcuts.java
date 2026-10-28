package com.mine.geometry_node.client.ui.editor.game.input;

import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.config.ConfigEntry;
import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.input.binding.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Commands use the same configuration and scoped dispatch as the node editor. */
public final class GameViewportShortcuts {
    public enum Action { SNAP_TOGGLE, PROJECTION, MOVE, ROTATE, SCALE, AXIS_X, AXIS_Y, AXIS_Z, CONFIRM, ADD, FOCUS, ALIGN_ENTITY, EDIT, ENTITY_MOVE, COPY, PASTE, DELETE, UNDO, REDO, CANCEL, SELECT_ALL }

    public static final List<ScopedAction<Action>> ACTIONS = List.of(
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.SNAP_TOGGLE, "geometry_node.game_view.scene.snap", BuiltinConfigEntries.GAME_VIEW_SNAP, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.MOVE, "geometry_node.game_view.scene.move", BuiltinConfigEntries.GAME_VIEW_MOVE, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.ROTATE, "geometry_node.game_view.scene.rotate", BuiltinConfigEntries.GAME_VIEW_ROTATE, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.SCALE, "geometry_node.game_view.scene.scale", BuiltinConfigEntries.GAME_VIEW_SCALE, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.AXIS_X, "geometry_node.game_view.scene.axisX", BuiltinConfigEntries.GAME_VIEW_AXIS_X, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.AXIS_Y, "geometry_node.game_view.scene.axisY", BuiltinConfigEntries.GAME_VIEW_AXIS_Y, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.AXIS_Z, "geometry_node.game_view.scene.axisZ", BuiltinConfigEntries.GAME_VIEW_AXIS_Z, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.CONFIRM, "geometry_node.game_view.scene.confirm", BuiltinConfigEntries.GAME_VIEW_CONFIRM, null),

            new ScopedAction<>(KeyScope.GAME_VIEW, Action.PROJECTION, "geometry_node.game_view.toggle_projection",
                    BuiltinConfigEntries.GAME_VIEW_PROJECTION, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.ADD, "geometry_node.game_view.scene.add",
                    BuiltinConfigEntries.GAME_VIEW_ADD, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.FOCUS, "geometry_node.game_view.scene.focus",
                    BuiltinConfigEntries.GAME_VIEW_FOCUS, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.ALIGN_ENTITY, "geometry_node.game_view.align_entity",
                    BuiltinConfigEntries.GAME_VIEW_ALIGN_ENTITY, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.EDIT, "geometry_node.game_view.scene.edit",
                    BuiltinConfigEntries.GAME_VIEW_EDIT, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.ENTITY_MOVE, "geometry_node.game_view.entities.move",
                    BuiltinConfigEntries.GAME_VIEW_ENTITY_MOVE, null),
            new ScopedAction<>(KeyScope.GLOBAL, Action.COPY, "geometry_node.game_view.scene.copy",
                    BuiltinConfigEntries.GLOBAL_COPY, null),
            new ScopedAction<>(KeyScope.GLOBAL, Action.PASTE, "geometry_node.game_view.scene.paste",
                    BuiltinConfigEntries.GLOBAL_PASTE, null),
            new ScopedAction<>(KeyScope.GLOBAL, Action.DELETE, "geometry_node.game_view.scene.delete",
                    BuiltinConfigEntries.GLOBAL_DELETE, null),
            new ScopedAction<>(KeyScope.GLOBAL, Action.UNDO, "geometry_node.game_view.scene.undo",
                    BuiltinConfigEntries.GLOBAL_UNDO, null),
            new ScopedAction<>(KeyScope.GLOBAL, Action.REDO, "geometry_node.game_view.scene.redo",
                    BuiltinConfigEntries.GLOBAL_REDO, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.CANCEL, "geometry_node.game_view.scene.cancel",
                    BuiltinConfigEntries.GAME_VIEW_CANCEL, null),
            new ScopedAction<>(KeyScope.GLOBAL, Action.SELECT_ALL, "geometry_node.game_view.scene.selectAll",
                    BuiltinConfigEntries.GLOBAL_SELECT_ALL, null));

    public static String display(ConfigEntry<String> entry) {
        String binding = ConfigManager.INSTANCE.get(entry);
        if (binding == null || binding.isBlank())
            return Component.translatable("geometry_node.settings.value.unassigned").getString();
        return binding.replace("GRAVE_ACCENT", "`");
    }

    /** Menus use the same configured binding and label as scoped dispatch. */
    public static String shortcutText(Action id) {
        return command(id).shortcutText(ConfigManager.INSTANCE.getConfig());
    }

    public static String label(Action id) {
        return Component.translatable(command(id).label()).getString();
    }

    private static ScopedAction<Action> command(Action id) {
        return ACTIONS.stream().filter(action -> action.id() == id).findFirst().orElseThrow();
    }

    public static String flightHint() {
        return Component.translatable("geometry_node.game_view.flying",
                display(BuiltinConfigEntries.GAME_FLIGHT_FORWARD), display(BuiltinConfigEntries.GAME_FLIGHT_BACKWARD),
                display(BuiltinConfigEntries.GAME_FLIGHT_LEFT), display(BuiltinConfigEntries.GAME_FLIGHT_RIGHT),
                display(BuiltinConfigEntries.GAME_FLIGHT_UP), display(BuiltinConfigEntries.GAME_FLIGHT_DOWN),
                display(BuiltinConfigEntries.GAME_FLIGHT_EXIT)).getString();
    }

    private GameViewportShortcuts() {}
}
