package com.mine.geometry_node.core.network.packet.data.library;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketRemoteDataLibraryResponse(
        int requestId,
        boolean success,
        String message,
        String token
) implements CustomPacketPayload {
    public static final Type<PacketRemoteDataLibraryResponse> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "remote_data_library_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteDataLibraryResponse> STREAM_CODEC =
            StreamCodec.of(PacketRemoteDataLibraryResponse::write, PacketRemoteDataLibraryResponse::read);

    public PacketRemoteDataLibraryResponse {
        message = message == null ? "" : message;
        token = token == null ? "" : token;
        if (message.length() > DataLibraryPacketCodecs.MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("Data Library message is too large");
        if (token.length() > DataLibraryPacketCodecs.MAX_TOKEN_LENGTH) throw new IllegalArgumentException("Data Library token is too large");
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketRemoteDataLibraryResponse packet) {
        buffer.writeInt(packet.requestId);
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.message, DataLibraryPacketCodecs.MAX_MESSAGE_LENGTH);
        buffer.writeUtf(packet.token, DataLibraryPacketCodecs.MAX_TOKEN_LENGTH);
    }

    private static PacketRemoteDataLibraryResponse read(RegistryFriendlyByteBuf buffer) {
        return new PacketRemoteDataLibraryResponse(
                buffer.readInt(), buffer.readBoolean(),
                buffer.readUtf(DataLibraryPacketCodecs.MAX_MESSAGE_LENGTH),
                buffer.readUtf(DataLibraryPacketCodecs.MAX_TOKEN_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
