package com.mine.geometry_node.core.network.packet.asset.repository;

import com.mine.geometry_node.core.network.packet.asset.AssetPacketCodecs;
import com.mine.geometry_node.core.network.packet.asset.AssetPacketLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketRemoteAssetFileOperationRequest(
        int requestId,
        Operation operation,
        String destinationPath,
        List<String> paths
) implements CustomPacketPayload {
    public enum Operation {
        DELETE,
        COPY,
        MOVE,
        CREATE_DIRECTORY,
        RENAME
    }

    public static final Type<PacketRemoteAssetFileOperationRequest> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "remote_asset_file_operation_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteAssetFileOperationRequest> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketRemoteAssetFileOperationRequest::new
    );

    public PacketRemoteAssetFileOperationRequest(RegistryFriendlyByteBuf buf) {
        this(buf.readInt(), readOperation(buf), buf.readUtf(AssetPacketLimits.MAX_PATH_LENGTH), readPaths(buf));
    }

    public PacketRemoteAssetFileOperationRequest {
        operation = java.util.Objects.requireNonNull(operation, "operation");
        destinationPath = destinationPath == null ? "" : destinationPath;
        paths = paths == null ? List.of() : List.copyOf(paths);
        AssetPacketLimits.requireCount(paths.size(), AssetPacketLimits.MAX_FILE_OPERATION_PATHS,
                "remote asset file operation path");
        switch (operation) {
            case DELETE -> requirePaths(paths, operation);
            case COPY, MOVE -> requirePaths(paths, operation);
            case CREATE_DIRECTORY -> {
                if (!paths.isEmpty()) throw new IllegalArgumentException("CREATE_DIRECTORY does not accept sources");
                if (destinationPath.isBlank()) throw new IllegalArgumentException("CREATE_DIRECTORY requires a path");
            }
            case RENAME -> {
                if (paths.size() != 1) throw new IllegalArgumentException("RENAME requires one source");
                if (destinationPath.isBlank()) throw new IllegalArgumentException("RENAME requires a destination");
            }
        }
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeInt(requestId);
        buf.writeVarInt(operation.ordinal());
        buf.writeUtf(destinationPath, AssetPacketLimits.MAX_PATH_LENGTH);
        AssetPacketCodecs.writeBoundedCount(buf, paths.size(),
                AssetPacketLimits.MAX_FILE_OPERATION_PATHS, "remote asset file operation path");
        for (String path : paths) {
            buf.writeUtf(path, AssetPacketLimits.MAX_PATH_LENGTH);
        }
    }

    private static List<String> readPaths(RegistryFriendlyByteBuf buf) {
        int size = AssetPacketCodecs.readBoundedCount(buf,
                AssetPacketLimits.MAX_FILE_OPERATION_PATHS, "remote asset file operation path");
        List<String> paths = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            paths.add(buf.readUtf(AssetPacketLimits.MAX_PATH_LENGTH));
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

    private static void requirePaths(List<String> paths, Operation operation) {
        if (paths.isEmpty()) throw new IllegalArgumentException(operation + " requires at least one source");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
