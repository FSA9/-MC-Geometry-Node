package com.mine.geometry_node.client.runtime.marker;

import com.mine.geometry_node.core.engine.system.marker.model.MarkerAddress;
import com.mine.geometry_node.core.network.packet.marker.MarkerPayload;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerRemove;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerUpsert;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side read model populated exclusively by server marker packets.
 */
public final class ClientMarkerStore {
    private static final long REMOTE_INTERPOLATION_NANOS = 100_000_000L;
    private static final Map<MarkerAddress, ClientMarker> MARKERS = new LinkedHashMap<>();

    private ClientMarkerStore() {
    }

    public static synchronized void handleSnapshot(PacketMarkerSnapshot packet) {
        MARKERS.clear();
        for (MarkerPayload payload : packet.markers()) {
            MARKERS.put(payload.address(), ClientMarker.initial(payload));
        }
    }

    public static synchronized void handleUpsert(PacketMarkerUpsert packet) {
        MarkerPayload payload = packet.marker();
        MARKERS.compute(payload.address(), (address, existing) -> {
            if (existing == null) {
                return ClientMarker.initial(payload);
            }
            existing.update(payload);
            return existing;
        });
    }

    public static synchronized void handleRemove(PacketMarkerRemove packet) {
        MARKERS.remove(packet.address());
    }

    /** Read-only frame data; entity tracking and remote interpolation stay inside the runtime. */
    public static synchronized List<RenderSnapshot> renderSnapshot(Minecraft minecraft, float partialTick) {
        var result = new ArrayList<RenderSnapshot>(MARKERS.size());
        for (ClientMarker marker : MARKERS.values()) {
            if (marker.payload.active() && minecraft.level != null
                    && marker.payload.dimension().equals(minecraft.level.dimension().identifier().toString())) {
                result.add(new RenderSnapshot(marker.payload, marker.renderPosition(minecraft, partialTick)));
            }
        }
        return List.copyOf(result);
    }

    public record RenderSnapshot(MarkerPayload payload, Vec3 position) {}

    public static synchronized void clear() {
        MARKERS.clear();
    }

    static final class ClientMarker {
        private MarkerPayload payload;
        private Vec3 previousPosition;
        private Vec3 targetPosition;
        private long updateNanos;

        private ClientMarker(MarkerPayload payload) {
            this.payload = payload;
            this.previousPosition = payload.position();
            this.targetPosition = payload.position();
            this.updateNanos = System.nanoTime();
        }

        private static ClientMarker initial(MarkerPayload payload) {
            return new ClientMarker(payload);
        }

        private void update(MarkerPayload next) {
            long now = System.nanoTime();
            previousPosition = interpolatedPosition(now);
            targetPosition = next.position();
            updateNanos = now;
            payload = next;
        }

        MarkerPayload payload() {
            return payload;
        }

        Vec3 renderPosition(Minecraft minecraft, float partialTick) {
            if (minecraft.level != null) {
                Entity entity = payload.anchor().requiresEntity() ? minecraft.level.getEntity(payload.entityId()) : null;
                if (!payload.anchor().requiresEntity() || (entity != null && entity.isAlive())) {
                    Vec3 base = com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver.resolve(
                            payload.anchor(), minecraft.level, partialTick);
                    org.joml.Quaterniond rotation = com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver.resolveRotation(
                            payload.anchor(), minecraft.level, partialTick);
                    if (base != null && rotation != null) return new com.mine.geometry_node.core.engine.spatial.SpatialTransform(
                            payload.worldOffset(), Vec3.ZERO, payload.localOffset(), Vec3.ZERO).position(base, rotation);
                }
            }
            return interpolatedPosition(System.nanoTime());
        }

        private Vec3 interpolatedPosition(long now) {
            if (!payload.active() || previousPosition.equals(targetPosition)) {
                return targetPosition;
            }
            double progress = Math.min(1.0D, Math.max(0.0D,
                    (double) (now - updateNanos) / REMOTE_INTERPOLATION_NANOS));
            return previousPosition.lerp(targetPosition, progress);
        }
    }
}
