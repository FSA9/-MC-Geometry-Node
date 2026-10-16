package com.mine.geometry_node.core.network.packet.c2s;

import com.mine.geometry_node.core.engine.system.viewport.EditCameraObservers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Complete replacement snapshot; the first observer has chunk-send priority. */
public record PacketEditCamera(Identifier dimension, List<Vec3> positions) implements CustomPacketPayload {
    public PacketEditCamera { positions = EditCameraObservers.copyPositions(positions); }
    public static final Type<PacketEditCamera> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "edit_camera_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEditCamera> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeIdentifier(p.dimension); buf.writeVarInt(p.positions.size());
                for (Vec3 position : p.positions) { buf.writeDouble(position.x); buf.writeDouble(position.y); buf.writeDouble(position.z); }
            }, buf -> {
                Identifier dimension = buf.readIdentifier();
                int count = buf.readVarInt();
                EditCameraObservers.checkCount(count);
                var positions = new ArrayList<Vec3>(count);
                for (int i = 0; i < count; i++) positions.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                return new PacketEditCamera(dimension, positions);
            });
    public boolean validPositions() { return positions.stream().allMatch(EditCameraObservers::validPosition); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
