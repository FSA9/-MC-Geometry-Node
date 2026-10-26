package com.mine.geometry_node.client.feature.entity.preview;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Demand-owned wakeups; independent of game ticks so paused screens still flush and expire. */
final class EntityPreviewPump {
    private final Consumer<Runnable> delayedOwnerQueue;
    private final Runnable pulse;
    private final BooleanSupplier hasWork;
    private boolean scheduled;
    private long generation;

    EntityPreviewPump(Consumer<Runnable> delayedOwnerQueue, Runnable pulse, BooleanSupplier hasWork) {
        this.delayedOwnerQueue = delayedOwnerQueue;
        this.pulse = pulse;
        this.hasWork = hasWork;
    }

    void request() {
        if (scheduled || !hasWork.getAsBoolean()) return;
        scheduled = true;
        long expected = generation;
        delayedOwnerQueue.accept(() -> {
            if (expected != generation) return;
            scheduled = false;
            pulse.run();
            request();
        });
    }

    void cancel() {
        generation++;
        scheduled = false;
    }
}
