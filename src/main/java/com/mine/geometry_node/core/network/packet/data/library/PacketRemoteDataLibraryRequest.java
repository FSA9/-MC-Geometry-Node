package com.mine.geometry_node.core.network.packet.data.library;

import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntryKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Set;

public record PacketRemoteDataLibraryRequest(
        int requestId,
        RemoteDataLibraryOperation operation,
        Set<DataLibraryEntryKey> keys
) implements CustomPacketPayload {
    public static final Type<PacketRemoteDataLibraryRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "remote_data_library_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteDataLibraryRequest> STREAM_CODEC =
            StreamCodec.of(PacketRemoteDataLibraryRequest::write, PacketRemoteDataLibraryRequest::read);

    public PacketRemoteDataLibraryRequest {
        if (operation == null) throw new IllegalArgumentException("Data Library operation is required");
        keys = keys == null ? Set.of() : Set.copyOf(keys);
        if (keys.size() > DataLibraryPacketCodecs.MAX_ENTRIES) throw new IllegalArgumentException("Too many Data Library keys");
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketRemoteDataLibraryRequest packet) {
        buffer.writeInt(packet.requestId);
        buffer.writeVarInt(packet.operation.ordinal());
        DataLibraryPacketCodecs.writeKeys(buffer, packet.keys);
    }

    private static PacketRemoteDataLibraryRequest read(RegistryFriendlyByteBuf buffer) {
        return new PacketRemoteDataLibraryRequest(
                buffer.readInt(),
                DataLibraryPacketCodecs.readOperation(buffer),
                DataLibraryPacketCodecs.readKeys(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
