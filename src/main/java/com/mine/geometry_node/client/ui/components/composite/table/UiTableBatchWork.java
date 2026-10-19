package com.mine.geometry_node.client.ui.components.composite.table;

import icyllis.modernui.view.View;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Cooperative UI-thread row work. Replacing/cancelling a plan immediately retires all old callbacks. */
public final class UiTableBatchWork {
    public static final int MAX_STEPS_PER_FRAME = 32;
    private static final long BUDGET_NANOS = 3_000_000L;
    private final Consumer<Runnable> post, remove;
    private final LongSupplier clock;
    private Iterator<Runnable> steps;
    private Runnable complete, scheduled;
    private long generation;
    private boolean running;

    public UiTableBatchWork(View owner) {
        this(task -> owner.postDelayed(task, 16), owner::removeCallbacks, System::nanoTime);
    }

    UiTableBatchWork(Consumer<Runnable> post, Consumer<Runnable> remove, LongSupplier clock) {
        this.post = Objects.requireNonNull(post); this.remove = Objects.requireNonNull(remove);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean isRunning() { return running; }

    /** First batch runs immediately; every continuation yields to input/layout for another frame. */
    public void start(Iterator<Runnable> steps, Runnable complete) {
        Objects.requireNonNull(steps); Objects.requireNonNull(complete);
        cancel();
        this.steps = steps; this.complete = complete; running = true;
        runBatch(generation);
    }

    public void cancel() {
        generation++;
        if (scheduled != null) remove.accept(scheduled);
        scheduled = null; steps = null; complete = null; running = false;
    }

    private void runBatch(long token) {
        if (!running || token != generation) return;
        scheduled = null;
        long start = clock.getAsLong();
        try {
            int count = 0;
            while (steps.hasNext()) {
                steps.next().run();
                // A step can synchronously replace the plan (e.g. a focus-loss commit/refresh).
                if (!running || token != generation) return;
                if (++count >= MAX_STEPS_PER_FRAME || clock.getAsLong() - start >= BUDGET_NANOS) break;
            }
            if (!steps.hasNext()) {
                Runnable done = complete;
                steps = null; complete = null; running = false;
                done.run();
            } else {
                scheduled = () -> runBatch(token);
                post.accept(scheduled);
            }
        } catch (RuntimeException | Error failure) {
            if (token == generation) cancel();
            throw failure;
        }
    }
}
