package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

public class LaserBeamEffect extends DirectedVisualEffect {

    public LaserBeamEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        DirectedAnchors anchors = computeAnchors(partialTick);
        Vec3 startRel = anchors.start().subtract(camPos);
        Vec3 endRel = anchors.end().subtract(camPos);

        BeamGeometry.drawPrism(
                bufferSource.getBuffer(RenderTypes.lightning()),
                poseStack.last().pose(),
                startRel,
                endRel,
                anchors.size() * 0.5,
                color
        );
    }
}
