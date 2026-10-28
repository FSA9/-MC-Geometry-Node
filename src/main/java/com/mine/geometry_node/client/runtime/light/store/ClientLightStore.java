package com.mine.geometry_node.client.runtime.light.store;

import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.network.packet.s2c.PacketGraphLight;
import com.mine.geometry_node.client.runtime.render.image.ClientImageAssetManager;
import com.mine.geometry_node.core.engine.system.asset.reference.AssetReference;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Main-thread client mirror. Entity poses are sampled at render partial-tick, never server-tick stepped. */
public final class ClientLightStore {
    private static final Map<String, Entry> LIGHTS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static boolean initialized;
    private static long receivedPackets, acceptedUpserts, rejectedPackets;
    private static String lastRejection = "";
    private static Map<String, Object> lastLight = Map.of();
    private static final Set<String> LOGGED_LIGHTS = new HashSet<>();
    private ClientLightStore() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() == world) clear();
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre event) -> {
            if (event.getLevel() != world) return;
            // Also retries late asset bytes; no render call is required to suspend/reacquire leases.
            for (Entry entry : LIGHTS.values()) entry.reconcile(entry.registration.isResident());
        });
    }

    public static void handle(PacketGraphLight packet) {
        receivedPackets++;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(packet.dimension())) { reject(packet, "world_or_dimension_unavailable"); return; }
        selectWorld(level);
        if (packet.operation() == PacketGraphLight.CLEAR) { clearEntries(); return; }
        var id = GraphResourceIdCodec.decode(packet.resourceId());
        if (!GraphResourceTypeRegistry.LIGHT.equals(id.type()) || !id.scope().dimension().equals(level.dimension())) { reject(packet, "resource_scope_mismatch"); return; }
        if (packet.operation() == PacketGraphLight.REMOVE) { close(LIGHTS.remove(packet.resourceId())); return; }
        if (!LIGHTS.containsKey(packet.resourceId()) && LIGHTS.size() >= GraphLightService.MAX_LIGHTS_PER_LEVEL) { reject(packet, "client_resource_budget"); return; }
        var definition = packet.definition();
        if (!definition.config().transform().anchor().dimensionOr(level.dimension()).equals(level.dimension())) {
            reject(packet, "anchor_dimension_mismatch");
            return;
        }
        acceptedUpserts++;
        if (LOGGED_LIGHTS.size() < 8 && LOGGED_LIGHTS.add(packet.resourceId()))
            com.mine.geometry_node.GeometryNode.LOGGER.info("Node light client accept: resource={}, position={}, rotation={}, intensity={}, range={}, shadow={}",
                    packet.resourceId(), definition.position(), definition.rotation(), definition.intensity(),
                    definition.range(), definition.config().shadow().enabled());
        lastLight = Map.of("resource", packet.resourceId(), "position", List.of(definition.position().x,
                definition.position().y, definition.position().z), "rotation", List.of(definition.rotation().x,
                definition.rotation().y, definition.rotation().z), "intensity", definition.intensity(),
                "volume", definition.config().volume().mode());
        String path = definition.config().projection().path();
        Entry old = LIGHTS.get(packet.resourceId());
        Projection projection = null;
        if ("spot".equals(definition.type()) && !path.isBlank()) {
            if (old != null && old.projection != null && old.definition.config().projection().path().equals(path)) {
                projection = old.projection;
                old.projection = null; // Transfer ownership before closing the obsolete subscription.
                discard(packet);
            } else {
                projection = new ManagedProjection(path);
            }
        } else {
            discard(packet);
        }
        Entry entry = new Entry(packet.resourceId(), packet.entityId(), definition, projection);
        close(LIGHTS.put(packet.resourceId(), entry));
        entry.registration = ResourceResidency.watch(level, GraphLightService.worldBounds(definition, entry.offset),
                GraphLightService.liveEntity(definition), entry::reconcile);
        entry.reconcile(entry.registration.isResident());
    }

    public static List<ResolvedLight> resolved(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        selectWorld(level);
        if (level == null) return List.of();
        float alpha = Math.clamp(partialTick, 0, 1);
        List<ResolvedLight> result = new ArrayList<>(LIGHTS.size());
        result.addAll(com.mine.geometry_node.client.runtime.game.scene.SceneLightInputs.resolved());
        for (var pair : LIGHTS.entrySet()) {
            Entry entry = pair.getValue();
            LightDefinition d = entry.definition;
            // Direct decision catches unload immediately, before queued watch callbacks drain.
            entry.reconcile(ResourceResidency.isResident(level, GraphLightService.worldBounds(d, entry.offset),
                    GraphLightService.liveEntity(d)));
            if (!entry.active) continue;
            if (entry.projection != null && entry.projection.texture() == null) continue;
            if (entry.stationary != null) {
                result.add(entry.stationary);
                continue;
            }
            var anchor=d.config().transform().anchor();
            float entityAlpha=alpha;
            if(anchor.requiresEntity()) {
                Entity entity=level.getEntity(anchor.entityId());
                if(entity==null||entity.isRemoved())continue;
                if(level.tickRateManager().isEntityFrozen(entity))
                    entityAlpha=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            }
            Vec3 base=com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver.resolve(anchor,level,entityAlpha);
            var anchorRotation=com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver.resolveRotation(anchor,level,entityAlpha);
            if(base==null||anchorRotation==null)continue;
            var transform=GraphLightService.spatialTransform(d);
            Vec3 position=transform.position(base,anchorRotation);
            Quaternionf orientation=new Quaternionf(transform.orientation(anchorRotation));
            result.add(new ResolvedLight(pair.getKey(), d, position,
                    rotate(orientation, new Vec3(0, 0, 1)), rotate(orientation, new Vec3(1, 0, 0)),
                    rotate(orientation, new Vec3(0, 1, 0)), entry.projection == null ? null : entry.projection.texture()));
        }
        return List.copyOf(result);
    }

    public static Quaternionf localRotation(Vec3 degrees) {
        return new Quaternionf().rotationYXZ((float) Math.toRadians(-degrees.y % 360),
                (float) Math.toRadians(degrees.x % 360), (float) Math.toRadians(degrees.z % 360));
    }
    public static Vec3 localPosition(LightDefinition definition) {
        return GraphLightService.localPosition(definition);
    }

    public static Vec3 rotate(Quaternionf rotation, Vec3 value) {
        Vector3f rotated = rotation.transform(new Vector3f((float) value.x, (float) value.y, (float) value.z));
        return new Vec3(rotated.x, rotated.y, rotated.z);
    }
    private static void selectWorld(ClientLevel level) {
        if (world != level) { clearEntries(); world = level; }
    }
    private static void discard(PacketGraphLight packet) {
        if (packet.definition() == null) return;
        String path = packet.definition().config().projection().path();
        if (path.startsWith("server:")) ClientImageAssetManager.discardPendingServerAsset(path.substring(7));
    }
    private static void reject(PacketGraphLight packet, String reason) {
        rejectedPackets++;
        lastRejection = reason;
        discard(packet);
    }
    public static Map<String, Object> diagnostics() {
        return Map.of("scope", "this_process_lifetime", "storedLights", LIGHTS.size(),
                "receivedPackets", receivedPackets, "acceptedUpserts", acceptedUpserts,
                "rejectedPackets", rejectedPackets, "lastRejection", lastRejection, "lastLight", lastLight);
    }
    private static void close(Entry entry) { if (entry != null) entry.close(); }
    private static void clearEntries() { LIGHTS.values().forEach(ClientLightStore::close); LIGHTS.clear(); }
    public static void clear() { clearEntries(); world = null; }
    public static int size() { selectWorld(Minecraft.getInstance().level); return LIGHTS.size(); }
    interface Projection extends AutoCloseable {
        void activate();
        void suspend();
        Identifier texture();
        @Override void close();
    }

    private static final class ManagedProjection implements Projection {
        private final String source, reference;
        private final ClientImageAssetManager.ServerAssetLease content;
        private ClientImageAssetManager.TextureLease lease;
        private boolean closed, acquisitionAttempted;

        ManagedProjection(String path) {
            if (path.startsWith("server:")) {
                source = "server";
                reference = path.substring("server:".length());
            } else if (path.startsWith("resource:")) {
                source = "resource";
                reference = path.substring("resource:".length());
            } else {
                AssetReference assetReference = AssetReference.parse(path);
                source = assetReference.source().id();
                reference = assetReference.location();
            }
            content = "server".equals(source) ? ClientImageAssetManager.retainServerAsset(reference) : null;
        }

        @Override public void activate() {
            if (closed || lease != null || acquisitionAttempted || content != null && !content.ready()) return;
            acquisitionAttempted = true;
            lease = content == null ? ClientImageAssetManager.acquire(source, reference) : content.acquireTexture();
        }

        @Override public void suspend() {
            if (lease != null) { lease.close(); lease = null; }
            acquisitionAttempted = false;
        }

        @Override public Identifier texture() { return lease == null ? null : lease.texture(); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            suspend();
            if (content != null) content.close();
        }
    }

    static final class Entry {
        final String id;
        final int entityId;
        final LightDefinition definition;
        Projection projection;
        ResourceResidency.Registration registration;
        final Quaternionf baseRotation, localRotation;
        final Vec3 offset;
        ResolvedLight stationary;
        boolean active, closed;

        Entry(String id, int entityId, LightDefinition definition, Projection projection) {
            this.id = id;
            this.entityId = entityId;
            this.definition = definition;
            this.projection = projection;
            baseRotation = localRotation(definition.rotation());
            localRotation = localRotation(definition.config().transform().rotation());
            offset = localPosition(definition);
        }

        void reconcile(boolean resident) {
            if (closed) return;
            active = resident && definition.config().lifecycle().enabled() && definition.intensity() > 0;
            if (!active) {
                if (projection != null) projection.suspend();
                stationary = null;
                return;
            }
            if (projection != null) projection.activate();
            Identifier texture = projection == null ? null : projection.texture();
            // Never cache a released identifier or a missing texture from before late byte arrival.
            if (!definition.config().transform().anchor().requiresEntity()
                    && (stationary == null || !Objects.equals(stationary.projectionTexture(), texture))) {
                Quaternionf orientation = new Quaternionf().mul(baseRotation).mul(localRotation);
                stationary = new ResolvedLight(id, definition, offset,
                        rotate(orientation, new Vec3(0, 0, 1)), rotate(orientation, new Vec3(1, 0, 0)),
                        rotate(orientation, new Vec3(0, 1, 0)), texture);
            }
        }

        void close() {
            if (closed) return;
            closed = true;
            active = false;
            stationary = null;
            if (registration != null) registration.close();
            if (projection != null) { projection.close(); projection = null; }
        }
    }
    public record ResolvedLight(String id, LightDefinition definition, Vec3 position, Vec3 direction, Vec3 right, Vec3 up,
                                Identifier projectionTexture) {}
}
