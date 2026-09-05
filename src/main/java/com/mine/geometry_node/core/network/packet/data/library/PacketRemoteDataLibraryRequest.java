package com.mine.geometry_node.core.network.packet.data.library;

import com.mine.geometry_node.core.engine.system.data.library.DataLibraryObjectKey;
import com.mine.geometry_node.core.engine.system.data.library.DataLibraryObjectFingerprint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public record PacketRemoteDataLibraryRequest(
        int requestId,
        RemoteDataLibraryOperation operation,
        Set<DataLibraryObjectKey> keys,
        @Nullable UUID objectId,
        @Nullable UUID parentId,
        String name,
        String expectedFingerprint
) implements CustomPacketPayload {
    public static final Type<PacketRemoteDataLibraryRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "remote_data_library_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketRemoteDataLibraryRequest> STREAM_CODEC =
            StreamCodec.of(PacketRemoteDataLibraryRequest::write, PacketRemoteDataLibraryRequest::read);

    public PacketRemoteDataLibraryRequest {
        if (operation == null) throw new IllegalArgumentException("Data Library operation is required");
        keys = keys == null ? Set.of() : Set.copyOf(keys);
        name = name == null ? "" : name;
        expectedFingerprint = expectedFingerprint == null ? "" : expectedFingerprint;
        if (keys.size() > DataLibraryPacketCodecs.MAX_ENTRIES) throw new IllegalArgumentException("Too many Data Library keys");
        if (name.length() > DataLibraryPacketCodecs.MAX_NAME_LENGTH) throw new IllegalArgumentException("Data Library name is too large");
        if (expectedFingerprint.length() > DataLibraryObjectFingerprint.LENGTH) {
            throw new IllegalArgumentException("Data Library fingerprint is too large");
        }
        validateFields(operation, keys, objectId, name, expectedFingerprint);
    }

    public PacketRemoteDataLibraryRequest(int requestId, RemoteDataLibraryOperation operation,
                                          Set<DataLibraryObjectKey> keys) {
        this(requestId, operation, keys, null, null, "", "");
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketRemoteDataLibraryRequest packet) {
        buffer.writeInt(packet.requestId);
        buffer.writeVarInt(packet.operation.ordinal());
        DataLibraryPacketCodecs.writeKeys(buffer, packet.keys);
        DataLibraryPacketCodecs.writeNullableUuid(buffer, packet.objectId);
        DataLibraryPacketCodecs.writeNullableUuid(buffer, packet.parentId);
        buffer.writeUtf(packet.name, DataLibraryPacketCodecs.MAX_NAME_LENGTH);
        buffer.writeUtf(packet.expectedFingerprint, DataLibraryObjectFingerprint.LENGTH);
    }

    private static PacketRemoteDataLibraryRequest read(RegistryFriendlyByteBuf buffer) {
        return new PacketRemoteDataLibraryRequest(
                buffer.readInt(),
                DataLibraryPacketCodecs.readOperation(buffer),
                DataLibraryPacketCodecs.readKeys(buffer),
                DataLibraryPacketCodecs.readNullableUuid(buffer),
                DataLibraryPacketCodecs.readNullableUuid(buffer),
                buffer.readUtf(DataLibraryPacketCodecs.MAX_NAME_LENGTH),
                buffer.readUtf(DataLibraryObjectFingerprint.LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void validateFields(RemoteDataLibraryOperation operation, Set<DataLibraryObjectKey> keys,
                                       UUID objectId, String name, String expectedFingerprint) {
        switch (operation) {
            case CREATE_FOLDER -> {
                if (!keys.isEmpty() || objectId != null || name.isBlank() || !expectedFingerprint.isEmpty()) {
                    throw new IllegalArgumentException("Invalid create-folder request");
                }
            }
            case UPDATE_FOLDER -> {
                requireObjectMutation(keys, objectId, name, expectedFingerprint, true);
            }
            case MOVE_ENTRY, MOVE_FOLDER -> {
                requireObjectMutation(keys, objectId, name, expectedFingerprint, false);
            }
            case DELETE -> {
                if (keys.isEmpty() || objectId != null || !name.isEmpty()
                        || !DataLibraryObjectFingerprint.isValid(expectedFingerprint)) {
                    throw new IllegalArgumentException("Invalid delete request");
                }
            }
        }
    }

    private static void requireObjectMutation(Set<DataLibraryObjectKey> keys, UUID objectId, String name,
                                              String expectedFingerprint, boolean requiresName) {
        if (!keys.isEmpty() || objectId == null || requiresName == name.isEmpty()
                || !DataLibraryObjectFingerprint.isValid(expectedFingerprint)) {
            throw new IllegalArgumentException("Invalid Data Library object mutation request");
        }
    }
}
