package com.mine.geometry_node.client.ui.editor.game.input;

import com.mine.geometry_node.client.ui.persistence.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.client.ui.shortcut.ScopedAction;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Commands use the same configuration and scoped dispatch as the node editor. */
public final class GameViewportShortcuts {
    public enum Action { FLY, PROJECTION }

    public static final List<ScopedAction<Action>> ACTIONS = List.of(
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.FLY, "geometry_node.game_view.fly",
                    BuiltinConfigEntries.GAME_VIEW_FLY, null),
            new ScopedAction<>(KeyScope.GAME_VIEW, Action.PROJECTION, "geometry_node.game_view.toggle_projection",
                    BuiltinConfigEntries.GAME_VIEW_PROJECTION, null));

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
