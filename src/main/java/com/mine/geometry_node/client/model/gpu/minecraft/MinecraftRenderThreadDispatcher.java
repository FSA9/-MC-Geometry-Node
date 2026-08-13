package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

public final class MinecraftRenderThreadDispatcher implements RenderThreadDispatcher {
    public static final MinecraftRenderThreadDispatcher INSTANCE = new MinecraftRenderThreadDispatcher();

    private MinecraftRenderThreadDispatcher() {}

    @Override
    public boolean isRenderThread() {
        return RenderSystem.isOnRenderThread();
    }

    @Override
    public void execute(Runnable task) {
        if (isRenderThread()) task.run();
        else Minecraft.getInstance().execute(task);
    }
}
