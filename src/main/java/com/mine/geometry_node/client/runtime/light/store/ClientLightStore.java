package com.mine.geometry_node.client.runtime.light.store;

import com.mine.geometry_node.core.engine.graph.light.GraphLightService;
import com.mine.geometry_node.core.engine.graph.light.LightDefinition;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceIdCodec;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceTypeRegistry;
import com.mine.geometry_node.core.network.packet.s2c.PacketGraphLight;
import com.mine.geometry_node.client.runtime.render.image.ClientImageAssetManager;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
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
        acceptedUpserts++;
        var definition = packet.definition();
        if (LOGGED_LIGHTS.size() < 8 && LOGGED_LIGHTS.add(packet.resourceId()))
            com.mine.geometry_node.GeometryNode.LOGGER.info("Node light client accept: resource={}, position={}, rotation={}, intensity={}, range={}, shadow={}",
                    packet.resourceId(), definition.position(), definition.rotation(), definition.intensity(),
                    definition.range(), definition.config().shadow().enabled());
        lastLight = Map.of("resource", packet.resourceId(), "position", List.of(definition.position().x,
                definition.position().y, definition.position().z), "rotation", List.of(definition.rotation().x,
                definition.rotation().y, definition.rotation().z), "intensity", definition.intensity(),
                "volume", definition.config().volume().mode());
        String path = packet.definition().config().projection().path();
        if (!"spot".equals(definition.type())) {
            discard(packet);
            close(LIGHTS.put(packet.resourceId(), new Entry(packet.resourceId(), packet.entityId(), definition, null)));
            return;
        }
        Entry old = LIGHTS.get(packet.resourceId());
        if (old != null && old.projection != null && old.definition.config().projection().path().equals(path)) {
            discard(packet);
            LIGHTS.put(packet.resourceId(), new Entry(packet.resourceId(), packet.entityId(), packet.definition(), old.projection));
            return;
        }
        ClientImageAssetManager.TextureLease lease = null;
        if (!path.isBlank()) {
            if (path.startsWith("server:")) lease = ClientImageAssetManager.acquire("server", path.substring(7));
            else if (path.startsWith("resource:")) lease = ClientImageAssetManager.acquire("resource", path.substring(9));
            else lease = ClientImageAssetManager.acquire("local", path);
        }
        close(LIGHTS.put(packet.resourceId(), new Entry(packet.resourceId(), packet.entityId(), packet.definition(), lease)));
    }

    public static List<ResolvedLight> resolved(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        selectWorld(level);
        if (level == null || LIGHTS.isEmpty()) return List.of();
        float alpha = Math.clamp(partialTick, 0, 1);
        List<ResolvedLight> result = new ArrayList<>(LIGHTS.size());
        for (var pair : LIGHTS.entrySet()) {
            Entry entry = pair.getValue();
            LightDefinition d = entry.definition;
            if (!d.config().lifecycle().enabled() || d.intensity() <= 0) continue;
            if ("spot".equals(d.type()) && !d.config().projection().path().isBlank() && entry.projection == null) continue;
            if (entry.stationary != null) {
                result.add(entry.stationary);
                continue;
            }
            var t = d.config().transform();
            Vec3 offset = entry.offset;
            Vec3 position = offset;
            Quaternionf anchorRotation = new Quaternionf();
            if (t.entityId() != null) {
                Entity entity = level.getEntity(entry.entityId);
                if (entity == null || entity.isRemoved() || !t.entityId().equals(entity.getUUID())) continue;
                float entityAlpha = level.tickRateManager().isEntityFrozen(entity)
                        ? Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false) : alpha;
                float yaw = entity.getViewYRot(entityAlpha);
                float pitch = t.followView() ? entity.getViewXRot(entityAlpha) : 0;
                if (!t.followView() && entity instanceof LivingEntity living)
                    yaw = net.minecraft.util.Mth.rotLerp(entityAlpha, living.yBodyRotO, living.yBodyRot);
                anchorRotation.rotationYXZ((float) Math.toRadians(-yaw), (float) Math.toRadians(pitch), 0);
                Vec3 base = t.eyeAnchor() ? entity.getEyePosition(entityAlpha) : entity.getPosition(entityAlpha);
                position = base.add(rotate(anchorRotation, offset));
            }
            Quaternionf orientation = anchorRotation.mul(entry.baseRotation).mul(entry.localRotation);
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
        return definition.position().add(rotate(localRotation(definition.rotation()), definition.config().transform().position()));
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
    private static void close(Entry entry) { if (entry != null && entry.projection != null) entry.projection.close(); }
    private static void clearEntries() { LIGHTS.values().forEach(ClientLightStore::close); LIGHTS.clear(); }
    public static void clear() { clearEntries(); world = null; }
    public static int size() { selectWorld(Minecraft.getInstance().level); return LIGHTS.size(); }
    private static final class Entry {
        final int entityId;
        final LightDefinition definition;
        final ClientImageAssetManager.TextureLease projection;
        final Quaternionf baseRotation, localRotation;
        final Vec3 offset;
        final ResolvedLight stationary;

        Entry(String id, int entityId, LightDefinition definition, ClientImageAssetManager.TextureLease projection) {
            this.entityId = entityId;
            this.definition = definition;
            this.projection = projection;
            baseRotation = localRotation(definition.rotation());
            localRotation = localRotation(definition.config().transform().rotation());
            offset = definition.position().add(rotate(baseRotation, definition.config().transform().position()));
            // Configuration changes replace the entry; only an entity anchor needs a per-frame pose.
            if (definition.config().transform().entityId() == null) {
                Quaternionf orientation = new Quaternionf().mul(baseRotation).mul(localRotation);
                stationary = new ResolvedLight(id, definition, offset,
                        rotate(orientation, new Vec3(0, 0, 1)), rotate(orientation, new Vec3(1, 0, 0)),
                        rotate(orientation, new Vec3(0, 1, 0)), projection == null ? null : projection.texture());
            } else stationary = null;
        }
    }
    public record ResolvedLight(String id, LightDefinition definition, Vec3 position, Vec3 direction, Vec3 right, Vec3 up,
                                Identifier projectionTexture) {}
}
