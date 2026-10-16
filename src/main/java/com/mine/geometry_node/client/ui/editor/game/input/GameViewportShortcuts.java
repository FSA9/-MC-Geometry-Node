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
    public enum Action { PROJECTION, ADD, FOCUS, DELETE, UNDO, REDO, CANCEL, SELECT_ALL }

    public static final List<ScopedAction<Action>> ACTIONS = List.of(
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.PROJECTION, "geometry_node.game_view.toggle_projection",
                    BuiltinConfigEntries.GAME_VIEW_PROJECTION, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.ADD, "geometry_node.game_view.scene.add",
                    BuiltinConfigEntries.GAME_VIEW_ADD, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.FOCUS, "geometry_node.game_view.scene.focus",
                    BuiltinConfigEntries.GAME_VIEW_FOCUS, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.DELETE, "geometry_node.game_view.scene.delete",
                    BuiltinConfigEntries.GAME_VIEW_DELETE, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.UNDO, "geometry_node.game_view.scene.undo",
                    BuiltinConfigEntries.GAME_VIEW_UNDO, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.REDO, "geometry_node.game_view.scene.redo",
                    BuiltinConfigEntries.GAME_VIEW_REDO, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.CANCEL, "geometry_node.game_view.scene.cancel",
                    BuiltinConfigEntries.GAME_VIEW_CANCEL, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.SELECT_ALL, "geometry_node.game_view.scene.selectAll",
                    BuiltinConfigEntries.GAME_VIEW_SELECT_ALL, null));

    public static String display(ConfigEntry<String> entry) {
        String binding = ConfigManager.INSTANCE.get(entry);
        if (binding == null || binding.isBlank())
            return Component.translatable("geometry_node.settings.value.unassigned").getString();
        return binding.replace("GRAVE_ACCENT", "`");
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
