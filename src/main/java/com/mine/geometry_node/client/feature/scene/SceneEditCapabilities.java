package com.mine.geometry_node.client.feature.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectCapabilities;

import java.util.Collection;
import java.util.Objects;

/** One operation query for tools, inspectors and automation; registration stays with each owner. */
public final class SceneEditCapabilities {
    public enum Action { CREATE, COPY, PASTE, DELETE, PROPERTIES, MOVE, ROTATE, SCALE }
    public enum Reason { AVAILABLE, EMPTY, UNRESOLVED_TARGET, UNSUPPORTED, MIXED_BACKENDS, UNAVAILABLE, BUSY }
    public record Availability(Reason reason) {
        public Availability { Objects.requireNonNull(reason); }
        public boolean allowed() { return reason == Reason.AVAILABLE; }
        public String message() {
            return allowed() ? "" : net.minecraft.network.chat.Component.translatable(
                    "geometry_node.game_view.scene.operation." + reason.name().toLowerCase(java.util.Locale.ROOT)).getString();
        }
    }

    private SceneEditCapabilities() {}

    public static Availability evaluate(Action action, Collection<SceneObject> objects, boolean unresolvedTargets) {
        Objects.requireNonNull(action);
        if (unresolvedTargets) return new Availability(Reason.UNRESOLVED_TARGET);
        if (objects.isEmpty()) return new Availability(Reason.EMPTY);
        SceneEditBackends.Mutation mutation = switch (action) {
            case CREATE, COPY, PASTE -> SceneEditBackends.Mutation.CREATE;
            case DELETE -> SceneEditBackends.Mutation.DELETE;
            default -> SceneEditBackends.Mutation.UPDATE;
        };
        SceneObjectCapabilities.Operation transform = switch (action) {
            case MOVE -> SceneObjectCapabilities.Operation.MOVE;
            case ROTATE -> SceneObjectCapabilities.Operation.ROTATE;
            case SCALE -> SceneObjectCapabilities.Operation.SCALE;
            default -> null;
        };
        String authority = null;
        for (SceneObject object : objects) {
            if (action == Action.CREATE && !com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.require(object.typeId()).creatable())
                return new Availability(Reason.UNSUPPORTED);
            if (!SceneEditBackends.supports(object, mutation)
                    || (transform != null && !SceneObjectCapabilities.supports(object, transform)))
                return new Availability(Reason.UNSUPPORTED);
            String next = SceneEditBackends.owner(object);
            if (authority != null && !authority.equals(next)) return new Availability(Reason.MIXED_BACKENDS);
            authority = next;
        }
        return new Availability(Reason.AVAILABLE);
    }
}
