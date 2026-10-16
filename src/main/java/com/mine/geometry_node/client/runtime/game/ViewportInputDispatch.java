package com.mine.geometry_node.client.runtime.game;

import net.minecraft.util.thread.BlockableEventLoop;

import java.util.concurrent.atomic.AtomicReference;

/** Release old input before installing new input, even inside a reentrant client task. */
final class ViewportInputDispatch {
    static void run(BlockableEventLoop<?> client, Runnable action) {
        // Minecraft.execute queues nested tasks even on its own thread.
        if (client.isSameThread()) action.run();
        else client.execute(action);
    }

    private ViewportInputDispatch() {}

    /** UI releases must also cancel captures that have not reached the client thread yet. */
    static final class CaptureRequests {
        private record Request(Object owner) {}
        private final AtomicReference<Request> pending = new AtomicReference<>();

        void submit(BlockableEventLoop<?> client, Object owner, Runnable capture) {
            Request request = new Request(owner);
            pending.set(request);
            client.execute(() -> {
                if (pending.compareAndSet(request, null)) capture.run();
            });
        }

        void cancel(Object owner) {
            pending.updateAndGet(request -> request != null && request.owner() == owner ? null : request);
        }

        void clear() { pending.set(null); }
    }
}
