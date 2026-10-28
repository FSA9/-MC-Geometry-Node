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
import org.jetbrains.annotations.Nullable;

public abstract class DirectedVisualEffect extends AbstractVisualEffect {
    private final VisualAnchor startAnchor;
    private final VisualAnchor endAnchor;
    private final LiveValue.State<Vec3> start;
    private final LiveValue.State<Vec3> end;
    private final LiveValue.State<Float> size;
    private final LiveValue.State<Vec3> startLocal,endLocal;

    public DirectedVisualEffect(PacketSpawnDynamicVisual packet) {
        this(packet, DrawLaserBeam.START_PORT, DrawLaserBeam.END_PORT, DrawLaserBeam.SIZE_PORT);
    }

    protected DirectedVisualEffect(PacketSpawnDynamicVisual packet,
                                   PortDef startPort, PortDef endPort, PortDef sizePort) {
        super(packet);
        CompoundTag data = packet.extraData();
        this.startAnchor = captureAnchor(data, "startAnchor");
        this.endAnchor = captureAnchor(data, "endAnchor");
        Vec3 baseStart = new Vec3(data.getDoubleOr("startX", 0.0), data.getDoubleOr("startY", 0.0), data.getDoubleOr("startZ", 0.0));
        Vec3 baseEnd = new Vec3(data.getDoubleOr("endX", 0.0), data.getDoubleOr("endY", 0.0), data.getDoubleOr("endZ", 0.0));
        this.start = captureXyz(startPort, "start", baseStart);
        this.end = captureXyz(endPort, "end", baseEnd);
        this.startLocal=captureXyz(DrawLaserBeam.START_LOCAL_PORT,"startLocal",com.mine.geometry_node.core.node.util.VisualTransformInputs.read(data,"startLocal"));
        this.endLocal=captureXyz(DrawLaserBeam.END_LOCAL_PORT,"endLocal",com.mine.geometry_node.core.node.util.VisualTransformInputs.read(data,"endLocal"));
        this.size = captureFloat(sizePort, "size", data.getFloatOr("size", 1.0f));
    }

    protected @Nullable DirectedAnchors computeAnchors(float partialTick) {
        return computeAnchors(Minecraft.getInstance().level, partialTick, expressionContext(partialTick));
    }

    final @Nullable DirectedAnchors computeAnchors(@Nullable ClientLevel level, float partialTick,
                                                   ExpressionEvaluationContext expressionContext) {
        Vec3 startBase = startAnchor.resolve(level, partialTick, expressionContext);
        Vec3 endBase = endAnchor.resolve(level, partialTick, expressionContext);
        if (startBase == null || endBase == null) return null;
        var startRotation=startAnchor.rotation(level,partialTick);
        var endRotation=endAnchor.rotation(level,partialTick);
        if(startRotation==null||endRotation==null)return null;
        Vec3 startPosition = startBase.add(start.evaluate(expressionContext)).add(com.mine.geometry_node.core.engine.spatial.SpatialTransform.rotate(startRotation,startLocal.evaluate(expressionContext)));
        Vec3 endPosition = endBase.add(end.evaluate(expressionContext)).add(com.mine.geometry_node.core.engine.spatial.SpatialTransform.rotate(endRotation,endLocal.evaluate(expressionContext)));
        return new DirectedAnchors(startPosition, endPosition, Math.max(0.01f, size.evaluate(expressionContext)));
    }

    protected record DirectedAnchors(Vec3 start, Vec3 end, float size) {}
}
