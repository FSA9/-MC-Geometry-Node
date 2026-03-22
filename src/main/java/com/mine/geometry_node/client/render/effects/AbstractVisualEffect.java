package com.mine.geometry_node.client.render.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public abstract class AbstractVisualEffect {
    public final Vec3 start;
    public final Vec3 end;
    public final int color;
    protected int remainingTicks;

    public AbstractVisualEffect(Vec3 start, Vec3 end, int color, int durationTicks) {
        this.start = start;
        this.end = end;
        this.color = color;
        this.remainingTicks = durationTicks;
    }

    /**
     * @return true 如果特效寿命耗尽，需要被移除
     */
    public boolean tick() {
        remainingTicks--;
        return remainingTicks <= 0;
    }

    /**
     * 子类实现自己的渲染逻辑
     */
    public abstract void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos);

    // 抽离：获取相对相机的坐标
    protected Vec3 getRelativePos(Vec3 pos, Vec3 camPos) {
        return pos.subtract(camPos);
    }
}