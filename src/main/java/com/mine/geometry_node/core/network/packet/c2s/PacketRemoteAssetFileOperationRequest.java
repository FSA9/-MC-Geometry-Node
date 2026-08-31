package com.mine.geometry_node.core.network.packet.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteAssetFileOperationRequest(
        int requestId,
        Operation operation,
        String targetDirectory,
        List<String> paths
) implements CustomPacketPayload {
    public enum Operation {
        DELETE,
        COPY,
        MOVE
    }

    public static final Type<PacketRemoteAssetFileOperationRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_asset_file_operation_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteAssetFileOperationRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteAssetFileOperationRequest::new
    );

    public PacketRemoteAssetFileOperationRequest(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), readOperation(buf), buf.readUtf(32767), readPaths(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeVarInt(operation.ordinal());
        buf.writeUtf(targetDirectory == null ? "" : targetDirectory, 32767);
        buf.writeInt(paths.size());
        for (String path : paths) {
            buf.writeUtf(path, 32767);
        }
    }

    private static List<String> readPaths(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid remote asset file operation path count: " + size);
        }
        List<String> paths = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            paths.add(buf.readUtf(32767));
        }
        return paths;
    }

    private static Operation readOperation(RegistryFriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        Operation[] values = Operation.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid remote asset file operation: " + ordinal);
        }
        return values[ordinal];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
