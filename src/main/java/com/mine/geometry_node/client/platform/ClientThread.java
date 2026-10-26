package com.mine.geometry_node.client.platform;

import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Shared callback executor for capabilities owned by the Minecraft client thread. */
public final class ClientThread {
    private static final Executor EXECUTOR = ClientThread::execute;

    private ClientThread() {}

    public static void execute(Runnable callback) {
        Runnable task = Objects.requireNonNull(callback, "callback");
        Minecraft minecraft = Objects.requireNonNull(Minecraft.getInstance(), "Minecraft client is unavailable");
        if (minecraft.isSameThread()) task.run();
        else minecraft.execute(task);
    }

    public static Executor executor() { return EXECUTOR; }
}
