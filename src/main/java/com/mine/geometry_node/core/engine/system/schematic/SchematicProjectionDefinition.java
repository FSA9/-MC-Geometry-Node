package com.mine.geometry_node.core.engine.system.schematic;

import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import net.minecraft.world.phys.AABB;

/** Reconstructable projection data; the owning store budgets both awake and sleeping definitions. */
public final class SchematicProjectionDefinition {
    private final PacketSchematicProjection payload;
    private final long expiresAt;
    private final AABB bounds;

    public SchematicProjectionDefinition(PacketSchematicProjection packet, long currentTick) {
        // Packet records expose mutable NBT, so retaining the caller's packet would bypass budgets.
        this.payload = copy(packet, packet.durationTicks());
        long duration = Math.max(1, packet.durationTicks());
        this.expiresAt = currentTick > Long.MAX_VALUE - duration ? Long.MAX_VALUE : currentTick + duration;
        this.bounds = new AABB(packet.originX(), packet.originY(), packet.originZ(),
                packet.originX() + packet.width(), packet.originY() + packet.height(),
                packet.originZ() + packet.length());
    }

    public AABB bounds() { return bounds; }
    public String dimension() { return payload.dimension(); }
    public long expiresAt() { return expiresAt; }
    public boolean expired(long currentTick) { return currentTick >= expiresAt; }

    /** Null means expired: never let the packet's minimum duration resurrect an expired definition. */
    public PacketSchematicProjection packetAt(long currentTick) {
        if (expired(currentTick)) return null;
        long remaining = expiresAt - currentTick;
        int duration = remaining < 0 ? payload.durationTicks()
                : (int) Math.min(payload.durationTicks(), remaining);
        return copy(payload, duration);
    }

    private static PacketSchematicProjection copy(PacketSchematicProjection packet, int duration) {
        return new PacketSchematicProjection(packet.resourceId(), packet.graphId(), packet.dimension(),
                packet.originX(), packet.originY(), packet.originZ(), packet.width(), packet.height(), packet.length(),
                duration, packet.alpha(), packet.truncated(), packet.states(), packet.blocks(),
                packet.blockEntities().stream().map(data -> new PacketSchematicProjection.BlockEntity(
                        data.x(), data.y(), data.z(), data.tag())).toList(),
                packet.entities().stream().map(data -> new PacketSchematicProjection.Entity(
                        data.x(), data.y(), data.z(), data.tag())).toList());
    }
}
