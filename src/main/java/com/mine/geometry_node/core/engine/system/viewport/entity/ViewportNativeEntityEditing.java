package com.mine.geometry_node.core.engine.system.viewport.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.viewport.EditCameraChunkService;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneEdit;
import com.mine.geometry_node.core.network.packet.scene.PacketSceneSnapshot;
import com.mine.geometry_node.core.network.packet.viewport.PacketViewportNativeEntityEdit;
import com.mine.geometry_node.core.network.packet.viewport.PacketViewportNativeEntitySnapshot;
import com.mine.geometry_node.core.utils.nbt.EntityNbtCompat;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Server-thread native entity lifecycle, without authored scene persistence. */
public final class ViewportNativeEntityEditing {
    private static final double RADIUS = 512;
    private static final Map<ServerLevel, ViewportNativeEntityState> STATES = new IdentityHashMap<>();
    private static final Map<ServerLevel, ViewportEntitySnapshots> ENTITY_SNAPSHOTS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, LinkedHashMap<UUID, Receipt>> RECEIPTS = new IdentityHashMap<>();
    // Never reuse stamps when a level unloads/reloads while an editor still holds an old capture.
    private static final AtomicLong VERSION_CLOCK = new AtomicLong();
    private static boolean initialized;
    private static final Map<String, NativeAdapter> ADAPTERS = new LinkedHashMap<>();
    static { registerAdapter(new InteractionAdapter()); registerAdapter(new OrdinaryEntityAdapter()); }

    /** Type policy only. The executor owns permissions, UUIDs, full-NBT restore, versions and atomic lifecycle. */
    public interface NativeAdapter {
        String typeId();
        boolean supports(Entity entity);
        SceneObject capture(Entity entity, long revision);
        /** Return a detached, unspawned instance; the executor applies saved NBT and assigns the exact UUID. */
        Entity create(ServerLevel level, SceneObject object);
        void apply(Entity entity, SceneObject replacement);
        /** current is null for a new identity; restoration equality is enforced by the executor beforehand. */
        void validateReplacement(ServerPlayer player, SceneObject current, SceneObject replacement);
        default boolean restoreMatches(SceneObject requested, SceneObject deleted) {
            return SceneObjectTypes.sameHistoryState(requested, deleted);
        }
        default void validateRestore(ServerPlayer player, SceneObject requested, SceneObject deleted) {
            validateReplacement(player, deleted, requested);
        }
        /** A retained server tombstone must not depend on an evictable clipboard token. */
        default Entity restore(ServerLevel level, SceneObject object, CompoundTag fullNbt) {
            Entity entity = create(level, object);
            if (entity != null) EntityNbtCompat.load(entity, fullNbt.copy());
            return entity;
        }
    }

    /** Register during common setup, before requests arrive. Matching adapters must not overlap. */
    public static void registerAdapter(NativeAdapter adapter) {
        Objects.requireNonNull(adapter);
        if (ADAPTERS.putIfAbsent(adapter.typeId(), adapter) != null)
            throw new IllegalArgumentException("Native adapter already registered: " + adapter.typeId());
    }

    private static NativeAdapter adapter(Entity entity) {
        NativeAdapter match = null;
        for (NativeAdapter candidate : ADAPTERS.values()) {
            if (!candidate.supports(entity)) continue;
            if (match != null) throw new IllegalStateException("Overlapping native entity adapters");
            match = candidate;
        }
        return match;
    }
    private static NativeAdapter requireAdapter(String typeId) {
        NativeAdapter adapter = ADAPTERS.get(typeId);
        if (adapter == null) throw new IllegalArgumentException("Unsupported native entity type: " + typeId);
        return adapter;
    }

    private ViewportNativeEntityEditing() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketViewportNativeEntityEdit.TYPE,
                PacketViewportNativeEntityEdit.STREAM_CODEC, (packet, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) receive(player, packet.edit());
                }));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> RECEIPTS.remove(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            LinkedHashMap<UUID, Receipt> retained = null;
            for (ServerPlayer previous : List.copyOf(RECEIPTS.keySet()))
                if (previous.getUUID().equals(player.getUUID())) retained = RECEIPTS.remove(previous);
            if (retained != null) RECEIPTS.put(player, retained);
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            STATES.remove(event.getLevel());
            ViewportEntitySnapshots snapshots = ENTITY_SNAPSHOTS.remove(event.getLevel());
            if (snapshots != null) snapshots.clear();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            STATES.keySet().removeIf(level -> level.getServer() == event.getServer());
            ENTITY_SNAPSHOTS.entrySet().removeIf(entry -> {
                if (entry.getKey().getServer() != event.getServer()) return false;
                entry.getValue().clear();
                return true;
            });
            RECEIPTS.keySet().removeIf(player -> player.level().getServer() == event.getServer());
        });
    }

    public static void receive(ServerPlayer player, PacketSceneEdit request) {
        if (!player.level().dimension().identifier().toString().equals(request.dimension())) {
            send(player, snapshot(player, request.requestId(), false, "Native entity dimension changed", List.of()));
            return;
        }
        if (request.changes().isEmpty()) {
            send(player, snapshot(player, request.requestId(), true, "", List.of()));
            return;
        }
        var receipts = RECEIPTS.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        Receipt receipt = receipts.get(request.requestId());
        if (receipt != null) {
            if (!receipt.request().equals(request))
                send(player, snapshot(player, request.requestId(), false, "Request ID reused with different content", request.changes()));
            else send(player, receipt.response(canEdit(player)));
            return;
        }

        boolean success = false, changed = false;
        String message = "";
        List<Vec3> affectedPositions = new ArrayList<>();
        try {
            if (!canEdit(player) || !player.isAlive() || player.hasDisconnected()
                    || EditCameraChunkService.positions(player).isEmpty())
                throw new SecurityException("Native entity editing requires management permission and an active edit camera");
            ViewportNativeEntityState state = state(player.level());
            state.protectTombstones(request.changes().stream().map(SceneChange::id).toList());
            reconcileRemovals(player, state);
            Map<UUID, SceneObject> live = new LinkedHashMap<>();
            Map<UUID, Entity> entities = new LinkedHashMap<>();
            // Inspect all touched identities, including collisions outside the query/camera budget.
            for (SceneChange change : request.changes()) {
                Entity existing = player.level().getEntity(change.id());
                if (existing != null) {
                    NativeAdapter adapter = adapter(existing);
                    if (adapter == null || !available(player, existing))
                        throw new IllegalStateException("Native entity UUID is occupied or outside the loaded edit area");
                    entities.put(change.id(), existing);
                    live.put(change.id(), state.observe(existing, adapter));
                }
            }
            state.validate(request.changes(), live, (requested, deleted) -> requireAdapter(deleted.typeId()).restoreMatches(requested, deleted));
            List<Mutation> mutations = new ArrayList<>();
            for (SceneChange change : request.changes()) {
                SceneObject replacement = change.replacement();
                Entity entity = entities.get(change.id());
                SceneObject original = live.get(change.id());
                ViewportNativeEntityState.Tombstone tombstone = state.tombstone(change.id());
                NativeAdapter adapter = requireAdapter(original != null ? original.typeId() : replacement.typeId());
                if (replacement != null) {
                    if (entity == null && tombstone != null) adapter.validateRestore(player, replacement, tombstone.object());
                    else adapter.validateReplacement(player, original, replacement);
                }
                if (entity != null) {
                    if (replacement != null && !validPosition(player, replacement.position()))
                        throw new IllegalArgumentException("Native entity destination is outside the loaded edit area");
                    if (replacement == null || !replacement.sameContent(live.get(change.id()))) {
                        // Rider graphs are a multi-entity relationship transaction, not a single-entity NBT restore.
                        if (entity.isPassenger() || entity.isVehicle())
                            throw new IllegalArgumentException("Native lifecycle editing does not support riding/passenger relationships");
                        mutations.add(new Mutation(change.id(), adapter, entity, replacement,
                                EntityNbtCompat.saveWithoutId(entity), entity.position(), null, original));
                        affectedPositions.add(entity.position());
                        if (replacement != null) affectedPositions.add(replacement.position());
                    }
                } else {
                    SceneObject restoredValue = tombstone == null ? replacement : tombstone.object();
                    if (!validPosition(player, restoredValue.position()))
                        throw new IllegalArgumentException("Native entity creation/restore is outside the loaded edit area");
                    // Construct and validate every detached entity before the first world write.
                    if (tombstone != null && tombstone.nativeData().contains("Passengers"))
                        throw new IllegalArgumentException("Native restore cannot reconstruct passenger relationships");
                    Entity prepared = construct(player.level(), adapter, restoredValue,
                            tombstone == null ? null : tombstone.nativeData());
                    if (tombstone == null) adapter.apply(prepared, replacement);
                    else if (!adapter.restoreMatches(adapter.capture(prepared, 0), tombstone.object()))
                        throw new IllegalStateException("Native entity tombstone cannot be restored exactly");
                    mutations.add(new Mutation(change.id(), adapter, null, replacement, null, null, prepared, null));
                    affectedPositions.add(prepared.position());
                }
            }
            Map<UUID, CompoundTag> retainedAfter = new LinkedHashMap<>();
            for (Mutation mutation : mutations)
                retainedAfter.put(mutation.id(), mutation.entity() == null
                        ? EntityNbtCompat.saveWithoutId(mutation.prepared()) : mutation.before());
            state.validateBatchBudget(retainedAfter);
            applyAtomically(player, mutations, state);
            changed = !mutations.isEmpty();
            success = true;
        } catch (RuntimeException failure) {
            message = failure.getMessage() == null ? "Native entity edit rejected" : failure.getMessage();
        }
        PacketSceneSnapshot response = snapshot(player, request.requestId(), success, message, request.changes());
        receipts.put(request.requestId(), new Receipt(request, response));
        while (receipts.size() > 32) receipts.remove(receipts.keySet().iterator().next());
        send(player, response);
        if (changed) {
            for (ServerPlayer other : player.level().players()) {
                if (other == player || EditCameraChunkService.positions(other).isEmpty()) continue;
                if (affectedPositions.stream().noneMatch(position -> nearCamera(other, position))) continue;
                send(other, snapshot(other, PacketSceneSnapshot.BROADCAST, true, "", request.changes()));
            }
        }
    }

    private static Entity construct(ServerLevel level, NativeAdapter adapter, SceneObject object, CompoundTag saved) {
        Entity entity = saved == null ? adapter.create(level, object) : adapter.restore(level, object, saved.copy());
        if (entity == null || entity.level() != level || entity.isRemoved() || !adapter.supports(entity))
            throw new IllegalStateException("Unable to construct detached native entity");
        entity.setUUID(object.id());
        return entity;
    }

    private static void applyAtomically(ServerPlayer player, List<Mutation> mutations, ViewportNativeEntityState state) {
        ServerLevel level = player.level();
        int attempted = 0;
        try {
            for (Mutation mutation : mutations) {
                if (mutation.entity() != null && (level.getEntity(mutation.id()) != mutation.entity()
                        || mutation.entity().isRemoved()
                        || !mutation.before().equals(EntityNbtCompat.saveWithoutId(mutation.entity()))))
                    throw new IllegalStateException("Native entity changed during lifecycle callbacks");
                attempted++;
                if (mutation.entity() == null) {
                    // Never overwrite a UUID claimed by a live entity (including event-handler races).
                    if (level.getEntity(mutation.id()) != null || !level.addFreshEntity(mutation.prepared()))
                        throw new IllegalStateException("Native entity UUID is occupied or entity creation was rejected");
                    if (level.getEntity(mutation.id()) != mutation.prepared() || mutation.prepared().isRemoved())
                        throw new IllegalStateException("Native entity creation did not retain its UUID");
                } else if (mutation.replacement() == null) {
                    mutation.entity().discard();
                    if (level.getEntity(mutation.id()) != null)
                        throw new IllegalStateException("Native entity removal failed");
                } else mutation.adapter().apply(mutation.entity(), mutation.replacement());
            }
            // Capture everything before publishing lifecycle stamps; capture failure rolls the world batch back too.
            List<SceneObject> captures = new ArrayList<>();
            for (Mutation mutation : mutations) {
                Entity entity = mutation.entity() == null ? mutation.prepared() : mutation.entity();
                if (mutation.replacement() != null && (level.getEntity(mutation.id()) != entity
                        || entity.isRemoved() || !validPosition(player, entity.position())))
                    throw new IllegalStateException("Native entity left the loaded edit area during lifecycle callbacks");
                if (mutation.replacement() == null && level.getEntity(mutation.id()) != null)
                    throw new IllegalStateException("Deleted native UUID was reclaimed during lifecycle callbacks");
                captures.add(mutation.replacement() == null ? null : mutation.adapter().capture(entity, 0));
            }
            for (int i = 0; i < mutations.size(); i++) {
                Mutation mutation = mutations.get(i);
                if (captures.get(i) == null) state.missing(mutation.id());
                else state.observe(mutation.entity() == null ? mutation.prepared() : mutation.entity(), mutation.adapter());
            }
        } catch (RuntimeException failure) {
            for (int i = attempted - 1; i >= 0; i--) {
                Mutation mutation = mutations.get(i);
                try {
                    if (mutation.entity() == null) {
                        // Remove only our prepared instance; an unrelated claimant must never be discarded.
                        if (!mutation.prepared().isRemoved()) mutation.prepared().discard();
                    } else if (!mutation.entity().isRemoved() && level.getEntity(mutation.id()) == mutation.entity()) {
                        EntityNbtCompat.load(mutation.entity(), mutation.before().copy());
                        mutation.entity().setPos(mutation.position().x, mutation.position().y, mutation.position().z);
                        state.observe(mutation.entity(), mutation.adapter());
                    } else {
                        if (level.getEntity(mutation.id()) != null)
                            throw new IllegalStateException("Rollback UUID occupied by another entity");
                        Entity restored = construct(level, mutation.adapter(), mutation.original(), mutation.before());
                        restored.setPos(mutation.position().x, mutation.position().y, mutation.position().z);
                        if (!level.addFreshEntity(restored))
                            throw new IllegalStateException("Native entity rollback spawn rejected");
                        state.observe(restored, mutation.adapter());
                    }
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    GeometryNode.LOGGER.error("Unable to restore native entity {} after failed lifecycle batch",
                            mutation.id(), rollbackFailure);
                }
            }
            // All attempted identities get fresh proof after rollback, invalidating any partial/ABA observations.
            for (int i = 0; i < attempted; i++) {
                UUID id = mutations.get(i).id();
                try {
                    Entity live = level.getEntity(id);
                    if (live != null && !live.isRemoved()) {
                        NativeAdapter adapter = adapter(live);
                        if (adapter != null) state.observe(live, adapter);
                    } else state.missing(id);
                } catch (RuntimeException observationFailure) {
                    failure.addSuppressed(observationFailure);
                    GeometryNode.LOGGER.error("Unable to observe native entity {} after rollback", id, observationFailure);
                }
            }
            throw failure;
        }
    }

    /** Entity removal reason is stronger evidence than absence from the level's bounded/visible lookup. */
    private static void reconcileRemovals(ServerPlayer player, ViewportNativeEntityState state) {
        for (ViewportNativeEntityState.Observation observation : state.observations()) {
            Entity entity = observation.entity();
            if (entity == null || !entity.isRemoved() || entity.getRemovalReason() == null
                    || !entity.getRemovalReason().shouldDestroy() || !validPosition(player, observation.object().position())) continue;
            Entity current = player.level().getEntity(observation.object().id());
            if (current != null && adapter(current) != null && available(player, current))
                state.observe(current, adapter(current));
            else if (current == null) {
                // Preserve metadata written after the last query, right up to the actual external removal.
                try {
                    state.observe(entity, requireAdapter(observation.object().typeId()));
                } catch (IllegalArgumentException malformed) {
                    // Retain the last valid captured NBT if an external writer made the removed entity invalid.
                }
                state.missing(observation.object().id());
            }
        }
    }

    private static PacketSceneSnapshot snapshot(ServerPlayer player, UUID requestId, boolean success,
                                                String message, List<SceneChange> touched) {
        var level = player.level();
        var state = state(level);
        state.protectTombstones(touched.stream().map(SceneChange::id).toList());
        try {
            reconcileRemovals(player, state);
            Map<UUID, SceneObject> objects = new LinkedHashMap<>();
            Map<UUID, Long> deleted = new LinkedHashMap<>();
            // Failure and success acknowledgements must retain touched loaded IDs ahead of the query budget.
            for (SceneChange change : touched) {
                Entity entity = level.getEntity(change.id());
                if (entity != null && adapter(entity) != null && available(player, entity))
                    capture(state, entity, objects);
                else if (entity == null) {
                    ViewportNativeEntityState.Tombstone tombstone = state.tombstone(change.id());
                    if (tombstone != null && validPosition(player, tombstone.object().position()))
                        deleted.put(change.id(), tombstone.version());
                }
            }
            for (Vec3 camera : EditCameraChunkService.positions(player)) {
                if (objects.size() >= SceneDocument.MAX_OBJECTS) break;
                List<Entity> nearby = new ArrayList<>();
                level.getEntities(EntityTypeTest.forClass(Entity.class), new AABB(camera, camera).inflate(RADIUS),
                        entity -> !objects.containsKey(entity.getUUID()) && adapter(entity) != null && available(player, entity),
                        nearby, SceneDocument.MAX_OBJECTS - objects.size());
                for (Entity entity : nearby) capture(state, entity, objects);
            }
            // Retain touched deletion proofs first, independently of the live-object query budget.
            for (var entry : state.tombstones().entrySet()) {
                if (deleted.size() >= SceneDocument.MAX_TOMBSTONES) break;
                if (!objects.containsKey(entry.getKey()) && level.getEntity(entry.getKey()) == null && validPosition(player, entry.getValue().object().position()))
                    deleted.putIfAbsent(entry.getKey(), entry.getValue().version());
            }
            String boundedMessage = message.length() > 256 ? message.substring(0, 256) : message;
            return new PacketSceneSnapshot(requestId, level.dimension().identifier().toString(), success, boundedMessage,
                    canEdit(player), nextVersion(), List.copyOf(objects.values()), deleted);
        } finally {
            state.protectTombstones(List.of());
        }
    }

    private static void capture(ViewportNativeEntityState state, Entity entity, Map<UUID, SceneObject> objects) {
        try {
            SceneObject object = state.observe(entity, Objects.requireNonNull(adapter(entity)));
            objects.put(object.id(), object);
        } catch (IllegalArgumentException failure) {
            // An externally malformed entity must not prevent other loaded entities from being queried.
        }
    }

    private static ViewportNativeEntityState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new ViewportNativeEntityState(VERSION_CLOCK.get(), ViewportNativeEntityEditing::nextVersion));
    }
    static long nextVersion() { return VERSION_CLOCK.updateAndGet(Math::incrementExact); }
    private static boolean canEdit(ServerPlayer player) {
        return RemoteAssetPermissions.canManageAssets(player);
    }
    private static boolean available(ServerPlayer player, Entity entity) {
        return !entity.isRemoved() && entity.isAlive() && entity.level() == player.level() && validPosition(player, entity.position());
    }
    private static boolean validPosition(ServerPlayer player, Vec3 position) {
        BlockPos block = BlockPos.containing(position);
        return Double.isFinite(position.x) && Double.isFinite(position.y) && Double.isFinite(position.z)
                && !player.level().isOutsideBuildHeight(block) && nearCamera(player, position)
                && player.level().getWorldBorder().isWithinBounds(block)
                && player.level().getChunkSource().getChunkNow(block.getX() >> 4, block.getZ() >> 4) != null;
    }
    private static boolean nearCamera(ServerPlayer player, Vec3 position) {
        return EditCameraChunkService.positions(player).stream().anyMatch(camera -> camera.distanceToSqr(position) <= RADIUS * RADIUS);
    }
    private static void send(ServerPlayer player, PacketSceneSnapshot snapshot) {
        NetworkHandler.sendToPlayer(player, new PacketViewportNativeEntitySnapshot(snapshot));
    }

    private record Mutation(UUID id, NativeAdapter adapter, Entity entity, SceneObject replacement, CompoundTag before,
                            Vec3 position, Entity prepared, SceneObject original) {}

    private static ViewportEntitySnapshots entitySnapshots(ServerLevel level) {
        return ENTITY_SNAPSHOTS.computeIfAbsent(level, ViewportEntitySnapshots::new);
    }

    private static final class OrdinaryEntityAdapter implements NativeAdapter {
        @Override public String typeId() { return SceneNativeEntities.TYPE_ID; }
        @Override public boolean supports(Entity entity) { return SceneNativeEntities.eligible(entity); }
        @Override public SceneObject capture(Entity entity, long revision) {
            return entitySnapshots((ServerLevel) entity.level()).capture(entity, revision);
        }
        @Override public Entity create(ServerLevel level, SceneObject object) {
            return entitySnapshots(level).create(object);
        }
        @Override public Entity restore(ServerLevel level, SceneObject object, CompoundTag fullNbt) {
            return entitySnapshots(level).restore(object, fullNbt);
        }
        @Override public void apply(Entity entity, SceneObject replacement) {
            // Fresh copies were fully constructed by the immutable snapshot store; live updates are denied below.
            SceneNativeEntities.validate(replacement);
        }
        @Override public void validateReplacement(ServerPlayer player, SceneObject current, SceneObject replacement) {
            if (current != null) throw new IllegalArgumentException("Ordinary native entity updates are not supported");
            entitySnapshots(player.level()).validate(replacement);
        }
        @Override public void validateRestore(ServerPlayer player, SceneObject requested, SceneObject deleted) {
            SceneNativeEntities.validate(requested);
            // Exact deletion version and identity were checked by the transaction; only the server tombstone is used.
        }
    }

    private static final class InteractionAdapter implements NativeAdapter {
        @Override public String typeId() { return SceneNativeInteractions.TYPE_ID; }
        @Override public boolean supports(Entity entity) { return entity instanceof Interaction; }
        @Override public SceneObject capture(Entity entity, long revision) {
            return SceneNativeInteractions.capture((Interaction) entity, revision);
        }
        @Override public Entity create(ServerLevel level, SceneObject object) {
            return EntityType.INTERACTION.create(level, EntitySpawnReason.COMMAND);
        }
        @Override public void apply(Entity entity, SceneObject replacement) {
            SceneNativeInteractions.apply((Interaction) entity, replacement);
        }
        @Override public void validateReplacement(ServerPlayer player, SceneObject current, SceneObject replacement) {
            validateInteractionReplacement(current, replacement);
        }
    }

    static void validateInteractionReplacement(SceneObject current, SceneObject replacement) {
        if (!SceneNativeInteractions.TYPE_ID.equals(replacement.typeId()))
            throw new IllegalArgumentException("Only native Interaction edits are supported");
        SceneObjectTypes.validate(replacement);
        // New names are localized UI labels, not custom native metadata. Capture supplies the canonical name.
        if (!replacement.rotation().equals(Vec3.ZERO) || !replacement.color().equals(new Vec3(1, 1, 1))
                || !replacement.enabled() || current != null && !replacement.name().equals(current.name()))
            throw new IllegalArgumentException("Native Interaction supports position, scale and Interaction properties only");
    }
    record Receipt(PacketSceneEdit request, PacketSceneSnapshot snapshot) {
        PacketSceneSnapshot response(boolean canEdit) {
            return new PacketSceneSnapshot(snapshot.requestId(), snapshot.dimension(), snapshot.success(), snapshot.message(),
                    canEdit, snapshot.revision(), snapshot.objects(), snapshot.deletedVersions());
        }
    }
}
