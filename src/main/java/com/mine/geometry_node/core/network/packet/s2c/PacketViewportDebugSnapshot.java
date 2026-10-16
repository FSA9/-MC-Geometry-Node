package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.graph.debug.ViewportDebugLayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Map;
import java.util.EnumMap;

/** Separate from the player's command-driven diagnostic subscription. */
public record PacketViewportDebugSnapshot(Identifier dimension, Map<ViewportDebugLayer, PacketGeometryDebugSnapshot> layers) implements CustomPacketPayload {
    public PacketViewportDebugSnapshot { layers = Map.copyOf(layers); }
    public static final Type<PacketViewportDebugSnapshot> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "viewport_debug_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportDebugSnapshot> STREAM_CODEC = StreamCodec.of(
        (buf,p) -> {
            buf.writeIdentifier(p.dimension); buf.writeVarInt(p.layers.size());
            p.layers.forEach((layer,snapshot) -> { buf.writeByte(layer.id); snapshot.write(buf); });
        }, buf -> {
            Identifier dimension = buf.readIdentifier();
            int count = buf.readVarInt();
            if (count < 0 || count > ViewportDebugLayer.values().length) throw new IllegalArgumentException("Invalid debug layer count");
            Map<ViewportDebugLayer,PacketGeometryDebugSnapshot> layers = new EnumMap<>(ViewportDebugLayer.class);
            int meshes = 0;
            for (int i=0;i<count;i++) {
                var layer=ViewportDebugLayer.fromId(buf.readUnsignedByte());
                var snapshot=new PacketGeometryDebugSnapshot(buf);
                meshes += snapshot.meshes().size();
                if (meshes > 100 || layers.put(layer,snapshot) != null) throw new IllegalArgumentException("Invalid debug snapshot budget");
            }
            return new PacketViewportDebugSnapshot(dimension,layers);
        });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
