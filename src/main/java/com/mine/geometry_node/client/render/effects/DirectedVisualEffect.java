package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public abstract class DirectedVisualEffect extends AbstractVisualEffect {
    protected final int sourceEntityId;
    protected final Vec3 baseStart;
    protected final int targetEntityId;
    protected final Vec3 baseEnd;
    protected final float baseSize;

    public DirectedVisualEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();

        if (data != null) {
            this.sourceEntityId = data.contains("sourceId") ? data.getInt("sourceId") : -1;
            this.baseStart = new Vec3(data.getDouble("startX"), data.getDouble("startY"), data.getDouble("startZ"));
            this.targetEntityId = data.contains("targetId") ? data.getInt("targetId") : -1;
            this.baseEnd = new Vec3(data.getDouble("endX"), data.getDouble("endY"), data.getDouble("endZ"));
            this.baseSize = data.contains("size") ? data.getFloat("size") : 1.0f;
        } else {
            this.sourceEntityId = -1;
            this.baseStart = Vec3.ZERO;
            this.targetEntityId = -1;
            this.baseEnd = Vec3.ZERO;
            this.baseSize = 1.0f;
        }
    }

    protected DirectedAnchors computeAnchors(float partialTick) {
        Map<String, Double> vars = buildVariableTable(partialTick);
        ClientLevel level = Minecraft.getInstance().level;

        Vec3 start = (level != null && sourceEntityId != -1) ?
                level.getEntity(sourceEntityId).getPosition(partialTick).add(baseStart) : baseStart;
        Vec3 end = (level != null && targetEntityId != -1) ?
                level.getEntity(targetEntityId).getPosition(partialTick).add(baseEnd) : baseEnd;

        double dynStartX = eval("startX", vars, Double.NaN);
        double dynStartY = eval("startY", vars, Double.NaN);
        double dynStartZ = eval("startZ", vars, Double.NaN);
        start = new Vec3(
                Double.isNaN(dynStartX) ? start.x : dynStartX,
                Double.isNaN(dynStartY) ? start.y : dynStartY,
                Double.isNaN(dynStartZ) ? start.z : dynStartZ
        );

        double dynEndX = eval("endX", vars, Double.NaN);
        double dynEndY = eval("endY", vars, Double.NaN);
        double dynEndZ = eval("endZ", vars, Double.NaN);
        end = new Vec3(
                Double.isNaN(dynEndX) ? end.x : dynEndX,
                Double.isNaN(dynEndY) ? end.y : dynEndY,
                Double.isNaN(dynEndZ) ? end.z : dynEndZ
        );

        float size = (float) eval("size", vars, baseSize);
        return new DirectedAnchors(start, end, Math.max(0.01f, size));
    }

    protected record DirectedAnchors(Vec3 start, Vec3 end, float size) {}
}