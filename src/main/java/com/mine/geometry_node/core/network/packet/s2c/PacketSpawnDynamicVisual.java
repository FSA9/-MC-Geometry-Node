package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public record PacketSpawnDynamicVisual(
        String effectType,
        int sourceEntityId,
        Vec3 baseStartPos,
        int targetEntityId,
        Vec3 baseEndPos,
        int color,
        float baseSize,
        int durationTicks,

        Map<String, String> expressions, // 表达式
        Map<String, String> bindings     // <--- 必须是 String！
) implements CustomPacketPayload {

    public static final Type<PacketSpawnDynamicVisual> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("geometry_node", "spawn_dynamic_visual")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSpawnDynamicVisual> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketSpawnDynamicVisual::new
    );

    public PacketSpawnDynamicVisual(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(),
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt(),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readInt(),
                buf.readFloat(),
                buf.readInt(),
                readStringMap(buf),
                readStringMap(buf) // <--- 读取也用 StringMap
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.effectType);

        buf.writeInt(this.sourceEntityId);
        buf.writeDouble(this.baseStartPos.x());
        buf.writeDouble(this.baseStartPos.y());
        buf.writeDouble(this.baseStartPos.z());

        buf.writeInt(this.targetEntityId);
        buf.writeDouble(this.baseEndPos.x());
        buf.writeDouble(this.baseEndPos.y());
        buf.writeDouble(this.baseEndPos.z());

        buf.writeInt(this.color);
        buf.writeFloat(this.baseSize);
        buf.writeInt(this.durationTicks);

        writeStringMap(buf, this.expressions);
        writeStringMap(buf, this.bindings); // <--- 写入
    }

    private static Map<String, String> readStringMap(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buf.readUtf(), buf.readUtf());
        }
        return map;
    }

    private static void writeStringMap(RegistryFriendlyByteBuf buf, Map<String, String> map) {
        buf.writeInt(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}