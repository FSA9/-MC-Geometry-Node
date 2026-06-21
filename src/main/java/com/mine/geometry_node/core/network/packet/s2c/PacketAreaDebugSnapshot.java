package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketAreaDebugSnapshot(
        boolean enabled,
        double radius,
        List<AreaBox> boxes
) implements CustomPacketPayload {
    public static final Type<PacketAreaDebugSnapshot> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "area_debug_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAreaDebugSnapshot> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketAreaDebugSnapshot::new
    );

    public PacketAreaDebugSnapshot {
        boxes = List.copyOf(boxes);
    }

    public PacketAreaDebugSnapshot(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readDouble(), readBoxes(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeDouble(radius);
        buf.writeInt(boxes.size());
        for (AreaBox box : boxes) {
            box.write(buf);
        }
    }

    private static List<AreaBox> readBoxes(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<AreaBox> boxes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            boxes.add(new AreaBox(buf));
        }
        return boxes;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record AreaBox(
            String id,
            String graphId,
            String shape,
            double centerX,
            double centerY,
            double centerZ,
            double sizeX,
            double sizeY,
            double sizeZ,
            double rotationX,
            double rotationY,
            double rotationZ
    ) {
        private AreaBox(RegistryFriendlyByteBuf buf) {
            this(
                    buf.readUtf(32767),
                    buf.readUtf(32767),
                    buf.readUtf(64),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id, 32767);
            buf.writeUtf(graphId, 32767);
            buf.writeUtf(shape, 64);
            buf.writeDouble(centerX);
            buf.writeDouble(centerY);
            buf.writeDouble(centerZ);
            buf.writeDouble(sizeX);
            buf.writeDouble(sizeY);
            buf.writeDouble(sizeZ);
            buf.writeDouble(rotationX);
            buf.writeDouble(rotationY);
            buf.writeDouble(rotationZ);
        }
    }
}
