package com.mine.geometry_node.client.runtime.behavior;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketBehaviorDebugSubscription;
import com.mine.geometry_node.core.network.packet.s2c.PacketBehaviorDebugSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client read model populated exclusively by authoritative debug packets. */
public final class ClientBehaviorDebugStore {
    private static final int MAX_ENTRIES = 16;
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();

    private ClientBehaviorDebugStore() {
    }

    public static void subscribe(UUID instanceId) {
        NetworkHandler.sendToServer(new PacketBehaviorDebugSubscription(instanceId, true));
    }

    public static void unsubscribe(UUID instanceId) {
        NetworkHandler.sendToServer(new PacketBehaviorDebugSubscription(instanceId, false));
    }

    public static synchronized void handle(PacketBehaviorDebugSnapshot packet) {
        if (packet.status() == PacketBehaviorDebugSnapshot.Status.CANCELLED) {
            ENTRIES.remove(packet.instanceId());
            return;
        }
        ENTRIES.put(packet.instanceId(), new Entry(packet.status(), packet.detail(), packet.snapshot()));
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(ENTRIES.keySet().iterator().next());
        }
    }

    @Nullable
    public static synchronized Entry get(UUID instanceId) {
        return ENTRIES.get(instanceId);
    }

    public static synchronized List<Map.Entry<UUID, Entry>> entries() {
        return ENTRIES.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    public record Entry(PacketBehaviorDebugSnapshot.Status status, String detail,
                        @Nullable PacketBehaviorDebugSnapshot.Snapshot snapshot) {
    }
}
