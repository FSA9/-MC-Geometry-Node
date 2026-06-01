package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record PacketSpawnDynamicVisual(
        String effectType,
        int color,
        int durationTicks,
        Map<String, String> expressions,
        Map<String, String> bindings,
        CompoundTag extraData // <--- 核心修改：统一的动态数据夹
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
                buf.readInt(),
                readStringMap(buf),
                readStringMap(buf),
                (CompoundTag) buf.readNbt() // 从流中读取 NBT
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.effectType);
        buf.writeInt(this.color);
        buf.writeInt(this.durationTicks);
        writeStringMap(buf, this.expressions);
        writeStringMap(buf, this.bindings);
        buf.writeNbt(this.extraData); // 将 NBT 写入流
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