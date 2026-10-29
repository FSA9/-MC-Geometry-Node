package com.mine.geometry_node.core.engine.blueprint.event.dispatcher;

import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.events.player.OnCustomDataChanged;
import com.mine.geometry_node.core.node.value.GraphNumberNormalizer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bridges committed player data writes to owner-bound graphs, without polling or global broadcasts. */
public final class CustomDataDispatcher {
    private static final System.Logger LOGGER = System.getLogger(CustomDataDispatcher.class.getName());
    private static final int MAX_NOTIFICATIONS = 4096;
    private static final ThreadLocal<Deferred> DEFERRED = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<Runnable>> DISPATCHING = new ThreadLocal<>();

    private CustomDataDispatcher() {}

    /** Only immutable scalar leaves are retained; unrelated dictionaries and resources are never copied here. */
    public record Change(String key, Object oldValue, Object newValue) {}

    public static List<Change> changes(Map<String, Object> before, Map<String, Object> after) {
        List<Change> changes = new ArrayList<>();
        after.forEach((key, rawValue) -> {
            Object value = rawValue instanceof Number number ? GraphNumberNormalizer.normalize(number) : rawValue;
            if (!supported(value)) return;
            Object previous = before.get(key);
            if (!Objects.equals(previous, value)) {
                changes.add(new Change(key, supported(previous) ? previous : null, value));
            }
        });
        return List.copyOf(changes);
    }

    private static boolean supported(Object value) {
        return value instanceof Integer || value instanceof Float || value instanceof Double || value instanceof Boolean;
    }

    public static boolean observes(ServerPlayer player) {
        return !player.isRemoved() && BlueprintRuntime.INSTANCE.hasEntityEventSubscription(player, OnCustomDataChanged.TYPE_ID);
    }

    /** Called only after the dictionary has committed and released its mutation guard. */
    public static void publish(ServerPlayer player, List<Change> changes) {
        if (changes.isEmpty()) return;
        // Queue the entire commit before listeners run, so listener writes follow this batch in order.
        try (Deferred batch = defer()) {
            for (Change change : changes) {
                submit(() -> {
                    if (player.isRemoved() || !(player.level() instanceof ServerLevel level)
                            || level.getServer().isStopped()) return;
                    BlueprintRuntime.INSTANCE.dispatchBoundEntityEvent(level, player, OnCustomDataChanged.TYPE_ID,
                            EventPayload.of(StandardPorts.NAME.getId(), change.key(),
                                    OnCustomDataChanged.OLD_VALUE, change.oldValue(),
                                    OnCustomDataChanged.NEW_VALUE, change.newValue()).values());
                });
            }
            batch.commit();
        }
    }

    /** An enclosing editor transaction can discard notifications if a later field rolls back. */
    public static Deferred defer() {
        Deferred scope = new Deferred(DEFERRED.get());
        DEFERRED.set(scope);
        return scope;
    }

    static void submit(Runnable notification) {
        Deferred scope = DEFERRED.get();
        if (scope != null) scope.notifications.add(notification);
        else drain(List.of(notification));
    }

    private static void drain(List<Runnable> notifications) {
        ArrayDeque<Runnable> queue = DISPATCHING.get();
        if (queue != null) {
            queue.addAll(notifications);
            return;
        }
        queue = new ArrayDeque<>(notifications);
        DISPATCHING.set(queue);
        try {
            int delivered = 0;
            while (!queue.isEmpty()) {
                if (delivered++ >= MAX_NOTIFICATIONS) {
                    LOGGER.log(System.Logger.Level.WARNING, "Custom data event feedback exceeded {0} notifications; remaining events discarded", MAX_NOTIFICATIONS);
                    break;
                }
                try { queue.removeFirst().run(); }
                catch (RuntimeException failure) {
                    LOGGER.log(System.Logger.Level.WARNING, "Committed custom data notification failed", failure);
                }
            }
        } finally { DISPATCHING.remove(); }
    }

    public static final class Deferred implements AutoCloseable {
        private final Deferred parent;
        private final List<Runnable> notifications = new ArrayList<>();
        private boolean committed;
        private boolean closed;

        private Deferred(Deferred parent) { this.parent = parent; }

        public void commit() {
            if (closed) throw new IllegalStateException("Data notification scope is closed");
            committed = true;
        }

        @Override public void close() {
            if (closed) return;
            if (DEFERRED.get() != this) throw new IllegalStateException("Data notification scopes must close in order");
            closed = true;
            if (parent == null) DEFERRED.remove();
            else DEFERRED.set(parent);
            if (committed) {
                if (parent != null) parent.notifications.addAll(notifications);
                else drain(notifications);
            }
            notifications.clear();
        }
    }
}
