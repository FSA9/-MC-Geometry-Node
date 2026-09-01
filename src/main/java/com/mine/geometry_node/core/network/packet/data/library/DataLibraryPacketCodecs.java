package com.mine.geometry_node.core.network.packet.data.library;

import com.mine.geometry_node.core.engine.system.data.library.DataLibraryEntryKey;
import com.mine.geometry_node.core.node.definition.port.PortType;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DataLibraryPacketCodecs {
    public static final int MAX_ENTRIES = 4_096;
    public static final int MAX_MESSAGE_LENGTH = 2_048;
    public static final int MAX_TOKEN_LENGTH = 128;
    private DataLibraryPacketCodecs() {
    }

    public static void writeKeys(RegistryFriendlyByteBuf buffer, Set<DataLibraryEntryKey> keys) {
        requireCount(keys.size());
        buffer.writeVarInt(keys.size());
        for (DataLibraryEntryKey key : keys) {
            buffer.writeVarInt(key.type().ordinal());
            buffer.writeUUID(key.id());
        }
    }

    public static Set<DataLibraryEntryKey> readKeys(RegistryFriendlyByteBuf buffer) {
        int count = requireCount(buffer.readVarInt());
        Set<DataLibraryEntryKey> result = new LinkedHashSet<>(count);
        PortType[] types = PortType.values();
        for (int index = 0; index < count; index++) {
            int ordinal = buffer.readVarInt();
            if (ordinal < 0 || ordinal >= types.length) throw new IllegalArgumentException("Invalid PortType ordinal");
            result.add(new DataLibraryEntryKey(types[ordinal], buffer.readUUID()));
        }
        return Set.copyOf(result);
    }

    public static RemoteDataLibraryOperation readOperation(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        RemoteDataLibraryOperation[] operations = RemoteDataLibraryOperation.values();
        if (ordinal < 0 || ordinal >= operations.length) throw new IllegalArgumentException("Invalid Data Library operation");
        return operations[ordinal];
    }

    private static int requireCount(int count) {
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid Data Library entry count: " + count);
        return count;
    }
}
