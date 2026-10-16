package com.mine.geometry_node.client.runtime.game;

import net.minecraft.util.thread.BlockableEventLoop;

/** Release old input before installing new input, even inside a reentrant client task. */
final class ViewportInputDispatch {
    static void run(BlockableEventLoop<?> client, Runnable action) {
        // Minecraft.execute queues nested tasks even on its own thread.
        if (client.isSameThread()) action.run();
        else client.execute(action);
    }

    private ViewportInputDispatch() {}
}
