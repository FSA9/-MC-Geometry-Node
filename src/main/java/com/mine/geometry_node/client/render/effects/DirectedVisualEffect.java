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

        // 1. 获取实体基准坐标（如果没有实体，或者实体死了，就以原点 0,0,0 为基准）
        Vec3 sourceEntityPos = (level != null && sourceEntityId != -1 && level.getEntity(sourceEntityId) != null) ?
                level.getEntity(sourceEntityId).getPosition(partialTick) : Vec3.ZERO;

        Vec3 targetEntityPos = (level != null && targetEntityId != -1 && level.getEntity(targetEntityId) != null) ?
                level.getEntity(targetEntityId).getPosition(partialTick) : Vec3.ZERO;

        // 2. 计算起点 Offset (动态表达式优先，死数值 baseStart 兜底)
        double dynStartX = eval("startX", vars, Double.NaN);
        double dynStartY = eval("startY", vars, Double.NaN);
        double dynStartZ = eval("startZ", vars, Double.NaN);
        Vec3 startOffset = new Vec3(
                Double.isNaN(dynStartX) ? baseStart.x : dynStartX,
                Double.isNaN(dynStartY) ? baseStart.y : dynStartY,
                Double.isNaN(dynStartZ) ? baseStart.z : dynStartZ
        );

        // 3. 计算终点 Offset
        double dynEndX = eval("endX", vars, Double.NaN);
        double dynEndY = eval("endY", vars, Double.NaN);
        double dynEndZ = eval("endZ", vars, Double.NaN);
        Vec3 endOffset = new Vec3(
                Double.isNaN(dynEndX) ? baseEnd.x : dynEndX,
                Double.isNaN(dynEndY) ? baseEnd.y : dynEndY,
                Double.isNaN(dynEndZ) ? baseEnd.z : dynEndZ
        );

        // 4. 最终渲染坐标 = 实体真实坐标 + 动态/静态偏移量
        Vec3 start = sourceEntityPos.add(startOffset);
        Vec3 end = targetEntityPos.add(endOffset);

        float size = (float) eval("size", vars, baseSize);
        return new DirectedAnchors(start, end, Math.max(0.01f, size));
    }

    protected record DirectedAnchors(Vec3 start, Vec3 end, float size) {}
}