package com.mine.geometry_node.core.network.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * [通用视觉指令包]
 * 服务端将纯粹的视觉渲染指令下发给客户端。
 */
public record PacketSpawnVisual(
        String effectType,        // 类型标识
        int sourceEntityId,       // 起点绑定实体ID (若为 -1，则使用 startPos 绝对坐标)
        Vec3 startPos,            // 起点绝对坐标，或实体锚点局部偏移量
        int targetEntityId,       // 终点绑定实体ID (若为 -1，则使用 endPos 绝对坐标/方向)
        Vec3 endPos,              // 终点绝对坐标，或实体锚点局部偏移量
        int color,                // 颜色 (ARGB 格式)
        float size,               // 粗细/大小
        int durationTicks         // 持续刻数
) implements CustomPacketPayload {

    // 1. 定义 Packet 唯一标识
    public static final Type<PacketSpawnVisual> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("geometry_node", "spawn_visual")
    );

    // 2. 手动构建 StreamCodec (突破字段限制)
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSpawnVisual> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketSpawnVisual::new
    );

    // 3. 反序列化
    public PacketSpawnVisual(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt(),
                buf.readFloat(),
                buf.readInt()
        );
    }

    // 4. 序列化：写入到服务端 Buffer
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.effectType);

        buf.writeInt(this.sourceEntityId);
        buf.writeDouble(this.startPos.x());
        buf.writeDouble(this.startPos.y());
        buf.writeDouble(this.startPos.z());

        buf.writeInt(this.targetEntityId);
        buf.writeDouble(this.endPos.x());
        buf.writeDouble(this.endPos.y());
        buf.writeDouble(this.endPos.z());

        buf.writeInt(this.color);
        buf.writeFloat(this.size);
        buf.writeInt(this.durationTicks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}