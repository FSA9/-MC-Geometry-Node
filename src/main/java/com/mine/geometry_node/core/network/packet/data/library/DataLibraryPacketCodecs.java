package com.mine.geometry_node.core.network.packet.data.library;

import com.mine.geometry_node.core.engine.system.data.library.DataLibraryObjectKey;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class DataLibraryPacketCodecs {
    public static final int MAX_ENTRIES = 4_096;
    public static final int MAX_MESSAGE_LENGTH = 2_048;
    public static final int MAX_TOKEN_LENGTH = 128;
    public static final int MAX_NAME_LENGTH = 256;
    private DataLibraryPacketCodecs() {
    }

    public static void writeKeys(RegistryFriendlyByteBuf buffer, Set<DataLibraryObjectKey> keys) {
        requireCount(keys.size());
        buffer.writeVarInt(keys.size());
        for (DataLibraryObjectKey key : keys) buffer.writeUUID(key.id());
    }

    public static Set<DataLibraryObjectKey> readKeys(RegistryFriendlyByteBuf buffer) {
        int count = requireCount(buffer.readVarInt());
        Set<DataLibraryObjectKey> result = new LinkedHashSet<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new DataLibraryObjectKey(buffer.readUUID()));
        }
        return Set.copyOf(result);
    }

    public static RemoteDataLibraryOperation readOperation(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        RemoteDataLibraryOperation[] operations = RemoteDataLibraryOperation.values();
        if (ordinal < 0 || ordinal >= operations.length) throw new IllegalArgumentException("Invalid Data Library operation");
        return operations[ordinal];
    }

    public static void writeNullableUuid(RegistryFriendlyByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeUUID(value);
    }

    public static UUID readNullableUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private static int requireCount(int count) {
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid Data Library entry count: " + count);
        return count;
    }
}
