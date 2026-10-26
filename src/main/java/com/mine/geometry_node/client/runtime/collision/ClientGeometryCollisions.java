package com.mine.geometry_node.client.runtime.collision;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryCollision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Retains server definitions independently of the locally resident movement-prediction scene. */
public final class ClientGeometryCollisions {
    private static final Map<GraphResourceId, Entry> ENTRIES = new LinkedHashMap<>();
    private static ClientLevel world;
    private static boolean initialized;

    private ClientGeometryCollisions() {}

    private static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() == world) clear();
        });
    }

    public static void handle(PacketGeometryCollision packet) {
        init();
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) return;
        if (world != level) { clear(); world = level; }
        try {
            GraphResourceId id = GraphResourceIdCodec.decode(packet.resourceId());
            if (!id.type().equals(GraphResourceTypeRegistry.GEOMETRY_COLLISION)
                    || !id.scope().dimension().equals(level.dimension())) return;
            if (packet.operation() == PacketGeometryCollision.REMOVE) {
                Entry entry = ENTRIES.remove(id);
                if (entry != null) close(level, entry);
                return;
            }
            Entry entry = ENTRIES.get(id);
            ModelCollisionScene.Prepared geometry = packet.operation() == PacketGeometryCollision.UPSERT
                    ? ModelCollisionScene.prepare(packet.mesh()) : null;
            if (entry == null) {
                if (geometry == null) return;
                entry = new Entry(id);
                ENTRIES.put(id, entry);
            }
            entry.apply(packet.operation(), packet.position(), geometry, packet.excludedEntity());
            refreshResidency(level, entry);
        } catch (Exception exception) {
            GeometryNode.LOGGER.warn("Unable to synchronize geometry collision: {}", exception.getMessage());
        }
    }

    private static void refreshResidency(ClientLevel level, Entry entry) {
        if (entry.registration == null) {
            entry.registration = ResourceResidency.watch(level, entry.bounds(), entry.anchor,
                    resident -> reconcile(level, entry, resident));
        } else {
            entry.registration.update(entry.bounds(), entry.anchor);
        }
        reconcile(level, entry, entry.registration.isResident());
    }

    private static void reconcile(ClientLevel level, Entry entry, boolean resident) {
        entry.reconcile(resident, () -> ModelCollisionScene.publish(level, entry.id, entry.geometry,
                        entry.position.x, entry.position.y, entry.position.z, entry.anchor),
                () -> ModelCollisionScene.remove(level, entry.id));
    }

    private static void close(ClientLevel level, Entry entry) {
        entry.closed = true;
        if (entry.registration != null) entry.registration.close();
        reconcile(level, entry, false);
    }

    public static void clear() {
        if (world != null) for (Entry entry : ENTRIES.values()) close(world, entry);
        ENTRIES.clear();
        world = null;
    }

    static final class Entry {
        final GraphResourceId id;
        ModelCollisionScene.Prepared geometry;
        Vec3 position;
        UUID anchor;
        ResourceResidency.Registration registration;
        boolean active, closed, dirty;

        Entry(GraphResourceId id) { this.id = id; }

        void apply(int operation, Vec3 position, ModelCollisionScene.Prepared geometry, UUID anchor) {
            if (operation == PacketGeometryCollision.UPSERT) {
                this.geometry = geometry;
                this.anchor = anchor;
            }
            this.position = position;
            dirty = true;
        }

        AABB bounds() { return geometry.bounds().move(position); }

        void reconcile(boolean resident, Runnable publish, Runnable remove) {
            if (closed || !resident) {
                if (active) { active = false; remove.run(); }
                return;
            }
            if (active && !dirty) return;
            publish.run();
            active = true;
            dirty = false;
        }
    }
}
