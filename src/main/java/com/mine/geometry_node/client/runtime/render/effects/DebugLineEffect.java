package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawDebugLine;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public class DebugLineEffect extends DirectedVisualEffect {

    // 1. 构造函数改为直接接收网络包，并交给父类处理 AST 编译
    public DebugLineEffect(PacketSpawnDynamicVisual packet) {
        super(packet, DrawDebugLine.START_PORT, DrawDebugLine.END_PORT, DrawDebugLine.SIZE_PORT);
    }

    // 2. 渲染方法加上 partialTick 参数
    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        // 3. 获取当前帧动态计算后的起点和终点
        DirectedAnchors anchors = computeAnchors(partialTick);

        Vec3 relStart = anchors.start().subtract(camPos);
        Vec3 relEnd = anchors.end().subtract(camPos);
        BeamGeometry.drawPrism(
                bufferSource.getBuffer(RenderTypes.lightning()),
                poseStack.last().pose(),
                relStart,
                relEnd,
                anchors.size() * 0.5D,
                color);
    }
}
