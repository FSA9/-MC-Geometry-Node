package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.system.viewport.CameraChunkRegions;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraObservers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/** Complete granted regions, sent before their chunks. Dimension rejects stale-world ACKs. */
public record PacketEditCameraView(Identifier dimension, List<CameraChunkRegions.Region> regions) implements CustomPacketPayload {
    public PacketEditCameraView { EditCameraObservers.checkCount(regions.size()); regions = List.copyOf(regions); }
    public static final Type<PacketEditCameraView> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "edit_camera_view_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEditCameraView> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeIdentifier(p.dimension); buf.writeVarInt(p.regions.size());
                for (var region : p.regions) { buf.writeInt(region.x()); buf.writeInt(region.z()); buf.writeVarInt(region.radius()); }
            }, buf -> {
                Identifier dimension = buf.readIdentifier();
                int count = buf.readVarInt();
                EditCameraObservers.checkCount(count);
                var regions = new ArrayList<CameraChunkRegions.Region>(count);
                for (int i = 0; i < count; i++) regions.add(new CameraChunkRegions.Region(buf.readInt(), buf.readInt(), buf.readVarInt()));
                return new PacketEditCameraView(dimension, regions);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
