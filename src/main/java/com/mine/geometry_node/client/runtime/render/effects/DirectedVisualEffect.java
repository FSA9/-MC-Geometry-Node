package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawLaserBeam;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public abstract class DirectedVisualEffect extends AbstractVisualEffect {
    protected final int sourceEntityId;
    protected final Vec3 baseStart;
    protected final int targetEntityId;
    protected final Vec3 baseEnd;
    protected final float baseSize;
    private final LiveValue.State<Vec3> start;
    private final LiveValue.State<Vec3> end;
    private final LiveValue.State<Float> size;

    public DirectedVisualEffect(PacketSpawnDynamicVisual packet) {
        this(packet, DrawLaserBeam.START_PORT, DrawLaserBeam.END_PORT, DrawLaserBeam.SIZE_PORT);
    }

    protected DirectedVisualEffect(PacketSpawnDynamicVisual packet,
                                   PortDef startPort, PortDef endPort, PortDef sizePort) {
        super(packet);
        CompoundTag data = packet.extraData();

        if (data != null) {
            this.sourceEntityId = data.getIntOr("sourceId", -1);
            this.baseStart = new Vec3(data.getDoubleOr("startX", 0.0), data.getDoubleOr("startY", 0.0), data.getDoubleOr("startZ", 0.0));
            this.targetEntityId = data.getIntOr("targetId", -1);
            this.baseEnd = new Vec3(data.getDoubleOr("endX", 0.0), data.getDoubleOr("endY", 0.0), data.getDoubleOr("endZ", 0.0));
            this.baseSize = data.getFloatOr("size", 1.0f);
        } else {
            this.sourceEntityId = -1;
            this.baseStart = Vec3.ZERO;
            this.targetEntityId = -1;
            this.baseEnd = Vec3.ZERO;
            this.baseSize = 1.0f;
        }
        this.start = captureXyz(startPort, "start", baseStart);
        this.end = captureXyz(endPort, "end", baseEnd);
        this.size = captureFloat(sizePort, "size", baseSize);
    }

    protected DirectedAnchors computeAnchors(float partialTick) {
        ExpressionEvaluationContext expressionContext = expressionContext(partialTick);

        ClientLevel level = Minecraft.getInstance().level;

        Vec3 sourceEntityPos = (level != null && sourceEntityId != -1 && level.getEntity(sourceEntityId) != null) ?
                level.getEntity(sourceEntityId).getPosition(partialTick) : Vec3.ZERO;

        Vec3 targetEntityPos = (level != null && targetEntityId != -1 && level.getEntity(targetEntityId) != null) ?
                level.getEntity(targetEntityId).getPosition(partialTick) : Vec3.ZERO;

        Vec3 startOffset = start.evaluate(expressionContext);
        Vec3 endOffset = end.evaluate(expressionContext);

        Vec3 start = sourceEntityPos.add(startOffset);
        Vec3 end = targetEntityPos.add(endOffset);

        float evaluatedSize = size.evaluate(expressionContext);
        return new DirectedAnchors(start, end, Math.max(0.01f, evaluatedSize));
    }

    protected record DirectedAnchors(Vec3 start, Vec3 end, float size) {}
}
