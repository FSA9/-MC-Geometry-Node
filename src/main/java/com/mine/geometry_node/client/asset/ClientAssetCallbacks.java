package com.mine.geometry_node.client.asset;

import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Dispatches public asset callbacks onto the Minecraft client thread. */
public final class ClientAssetCallbacks {
    private static final Executor EXECUTOR = ClientAssetCallbacks::execute;

    private ClientAssetCallbacks() {
    }

    public static void execute(Runnable callback) {
        Runnable task = Objects.requireNonNull(callback, "callback");
        Minecraft minecraft = Objects.requireNonNull(Minecraft.getInstance(), "Minecraft client is unavailable");
        if (minecraft.isSameThread()) {
            task.run();
        } else {
            minecraft.execute(task);
        }
    }

    public static Executor executor() {
        return EXECUTOR;
    }
}
