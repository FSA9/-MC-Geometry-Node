package com.mine.geometry_node.client.runtime.game.input;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportFlight;

import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ControlKeyBinding;

import java.util.EnumMap;
import java.util.Set;

/** Immutable bindings for one capture session; physical held keys are evaluated on every event. */
public final class EditViewportFlightBindings {
    private final EnumMap<EditViewportFlight.Direction, ControlKeyBinding> movement =
            new EnumMap<>(EditViewportFlight.Direction.class);
    private final ControlKeyBinding exit;

    public EditViewportFlightBindings(AppConfig.GameViewKeyBindingsConfig config) {
        movement.put(EditViewportFlight.Direction.FORWARD, ControlKeyBinding.parse(config.forward));
        movement.put(EditViewportFlight.Direction.BACKWARD, ControlKeyBinding.parse(config.backward));
        movement.put(EditViewportFlight.Direction.LEFT, ControlKeyBinding.parse(config.left));
        movement.put(EditViewportFlight.Direction.RIGHT, ControlKeyBinding.parse(config.right));
        movement.put(EditViewportFlight.Direction.UP, ControlKeyBinding.parse(config.up));
        movement.put(EditViewportFlight.Direction.DOWN, ControlKeyBinding.parse(config.down));
        exit = ControlKeyBinding.parse(config.exit);
    }

    public boolean shouldExit(Set<Integer> held) {
        return exit != null && exit.isHeld(held::contains);
    }

    public void update(EditViewportFlight flight, Set<Integer> held) {
        for (var entry : movement.entrySet()) {
            ControlKeyBinding binding = entry.getValue();
            boolean active = binding != null && binding.isHeld(held::contains);
            if (active) {
                for (ControlKeyBinding other : movement.values()) {
                    if (other != null && other.supersedes(binding) && other.isHeld(held::contains)) {
                        active = false;
                        break;
                    }
                }
            }
            flight.setHeld(entry.getKey(), active);
        }
    }
}
