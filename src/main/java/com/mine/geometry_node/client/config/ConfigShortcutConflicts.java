package com.mine.geometry_node.client.config;

import com.mine.geometry_node.client.input.binding.ControlKeyBinding;
import com.mine.geometry_node.client.input.binding.InputBinding;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.input.binding.KeyScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shortcut conflicts follow dispatch scopes, including captured flight controls and the view menu. */
final class ConfigShortcutConflicts {
    private ConfigShortcutConflicts() {}

    static List<ConfigEntry<String>> find(Iterable<ConfigEntry<?>> entries,
                                         ConfigEntry<String> target, String candidate, AppConfig config) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(config, "config");
        String binding = bindingText(target, candidate);
        if (binding == null || target.keyScope() == null) return List.of();

        List<ConfigEntry<String>> conflicts = new ArrayList<>();
        for (ConfigEntry<?> raw : entries) {
            if (raw == target || raw.keyScope() == null || !scopesOverlap(target, raw)) continue;
            if (raw.editorType() != ConfigEntry.EditorType.KEY_BINDING
                    && raw.editorType() != ConfigEntry.EditorType.SHORTCUT) continue;
            @SuppressWarnings("unchecked") ConfigEntry<String> entry = (ConfigEntry<String>) raw;
            String existing = bindingText(entry, entry.get(config));
            if (binding.equals(existing) || flightMenuConflict(target, binding, entry, existing)) {
                conflicts.add(entry);
            }
        }
        return List.copyOf(conflicts);
    }

    private static String bindingText(ConfigEntry<String> entry, String value) {
        if (entry.captureControlKeys()) {
            ControlKeyBinding binding = ControlKeyBinding.parse(value);
            return binding != null ? binding.text() : null;
        }
        InputBinding binding = InputBinding.parse(value);
        return binding != null ? binding.text() : null;
    }

    private static boolean scopesOverlap(ConfigEntry<?> first, ConfigEntry<?> second) {
        KeyScope left = first.keyScope();
        KeyScope right = second.keyScope();
        // Flight capture blocks regular UI commands, but explicitly dispatches the view menu.
        if (left == KeyScope.GAME_VIEW_FLIGHT || right == KeyScope.GAME_VIEW_FLIGHT) {
            return left == right || first == BuiltinConfigEntries.GAME_VIEW_MENU
                    || second == BuiltinConfigEntries.GAME_VIEW_MENU;
        }
        return left == right || left == KeyScope.GLOBAL || right == KeyScope.GLOBAL;
    }

    private static boolean flightMenuConflict(ConfigEntry<String> first, String left,
                                               ConfigEntry<String> second, String right) {
        if (first.keyScope() == KeyScope.GAME_VIEW_FLIGHT && second == BuiltinConfigEntries.GAME_VIEW_MENU) {
            ControlKeyBinding control = ControlKeyBinding.parse(left);
            return control != null && control.isHeldBy(KeyBinding.parse(right));
        }
        if (second.keyScope() == KeyScope.GAME_VIEW_FLIGHT && first == BuiltinConfigEntries.GAME_VIEW_MENU) {
            ControlKeyBinding control = ControlKeyBinding.parse(right);
            return control != null && control.isHeldBy(KeyBinding.parse(left));
        }
        return false;
    }
}
