package com.mine.geometry_node.core.engine.resource.residency;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.BiPredicate;

/**
 * Shared spatial availability for owner adapters, on both logical sides. Never requests a chunk.
 * Owners keep definitions, enablement, expiry, and async generation tokens. This class owns only
 * coverage and transition subscriptions; a resident resource is not necessarily enabled or ready.
 */
public final class ResourceResidency {
    private static final Map<Level, State> LEVELS = new WeakHashMap<>();
    private static boolean initialized;
    private static final System.Logger LOGGER = System.getLogger(ResourceResidency.class.getName());

    private ResourceResidency() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        var bus = NeoForge.EVENT_BUS;
        // Load can run before FULL promotion, including on a generation worker. No owner callback
        // or world lookup is allowed here. Publication waits for a non-loading check on level tick.
        bus.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof Level level) {
                var state = state(level);
                synchronized (state) {
                    if (!state.closed) {
                        long position = event.getChunk().getPos().pack();
                        state.knownChunks.put(position, new WeakReference<>(event.getChunk()));
                        state.pendingLoads.put(position, Boolean.TRUE);
                    }
                }
            }
        });
        bus.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof Level level) {
                var state = state(level);
                var position = event.getChunk().getPos();
                synchronized (state) {
                    var current = state.knownChunks.get(position.pack());
                    // An old chunk's delayed unload cannot evict its replacement at the same coordinate.
                    if (current != null && current.get() != event.getChunk()) return;
                    state.pendingLoads.remove(position.pack());
                    state.knownChunks.remove(position.pack());
                    state.chunks.unload(position.x(), position.z());
                }
            }
        });
        bus.addListener((EntityJoinLevelEvent event) -> {
            var state = state(event.getLevel());
            state.departingEntities.remove(event.getEntity().getUUID());
            state.entityRevision++;
        });
        bus.addListener((EntityLeaveLevelEvent event) -> {
            var state = state(event.getLevel());
            // Tracking callbacks can occur while getEntity still returns the departing object.
            state.departingEntities.put(event.getEntity().getUUID(), new WeakReference<>(event.getEntity()));
            state.entityRevision++;
        });
        bus.addListener((LevelTickEvent.Pre event) -> tick(event.getLevel()));
        bus.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof Level level) close(level);
        });
    }

    /** Anchor precedence is strict: loaded surrounding chunks cannot keep a detached resource active. */
    public static boolean isResident(Level level, AABB worldBounds, @Nullable UUID anchor) {
        Objects.requireNonNull(level, "level");
        if (anchor != null) return isEntityResident(level, anchor);
        return state(level).intersects(level, ChunkCoverage.of(worldBounds));
    }

    public static boolean isEntityResident(Level level, UUID anchor) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(anchor, "anchor");
        State state = state(level);
        if (state.closed) return false;
        Entity entity = level.getEntity(anchor);
        if (entity == null || entity.isRemoved() || entity.level() != level) return false;
        WeakReference<Entity> departing = state.departingEntities.get(anchor);
        return departing == null || departing.get() != entity;
    }

    /** Coarse invalidation token, not a persistence revision or guarantee of entity availability. */
    public static long revision(Level level) {
        State state = state(Objects.requireNonNull(level));
        return state.chunks.revision() + state.entityRevision + state.availabilityRevision;
    }

    /** Initial state is obtained with isResident(); callbacks announce subsequent transitions only. */
    public static Registration watch(Level level, AABB bounds, @Nullable UUID anchor, Consumer<Boolean> changed) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(changed);
        WeakReference<Level> reference = new WeakReference<>(level);
        Registration registration = new Registration(bounds, anchor, (box, entity) -> {
            Level current = reference.get();
            return current != null && ResourceResidency.isResident(current, box, entity);
        }, changed);
        State state = state(level);
        registration.onClose = () -> state.registrations.remove(registration);
        if (state.closed) registration.closed = true;
        else state.registrations.add(registration);
        return registration;
    }

    /** Owner-thread reconciliation. Does not perform blocking loads or IO. */
    public static void tick(Level level) {
        State state = state(level);
        if (state.closed) return;
        for (Long packed : List.copyOf(state.pendingLoads.keySet())) {
            if (state.pendingLoads.remove(packed) != null && state.knownChunks.containsKey(packed))
                state.chunks.load(ChunkPos.getX(packed), ChunkPos.getZ(packed));
        }
        // FULL availability can disappear/reappear without Load/Unload (ticket or client cache
        // range changes). Keep a weak candidate until explicit unload or collection, never pinning
        // the chunk and never treating getChunkNow == null as permanent destruction.
        state.chunks.reconcile(256, (x,z) -> {
            long packed = ChunkPos.pack(x,z);
            var reference = state.knownChunks.get(packed);
            if (reference != null && reference.get() != null) {
                state.available(level, packed);
                return true;
            }
            state.observeAvailability(packed, false);
            if (reference != null) state.knownChunks.remove(packed, reference);
            return false;
        });
        state.readyChunks.removeIf(position -> !state.knownChunks.containsKey(position));
        state.decisions.clear();
        // Tracking end has returned by this tick. Lookup is authoritative again, including a
        // same-object stop/start in one tick (tracking start need not emit EntityJoinLevelEvent).
        if (!state.departingEntities.isEmpty()) {
            state.departingEntities.clear();
            state.entityRevision++;
        }
        for (Registration registration : List.copyOf(state.registrations)) registration.refresh();
    }

    private static synchronized State state(Level level) {
        return LEVELS.computeIfAbsent(level, ignored -> new State());
    }

    private static void close(Level level) {
        State state = state(level);
        synchronized (state) {
            state.closed = true;
            state.pendingLoads.clear();
            state.knownChunks.clear();
            state.readyChunks.clear();
            state.chunks.clear();
            state.departingEntities.clear();
        }
        for (Registration registration : List.copyOf(state.registrations)) {
            registration.refresh();
            registration.close();
        }
    }

    public static final class Registration implements AutoCloseable {
        private BiPredicate<AABB, UUID> available;
        private Runnable onClose = () -> {};
        private AABB bounds;
        private UUID anchor;
        private Consumer<Boolean> changed;
        private boolean resident, closed;

        Registration(AABB bounds, UUID anchor, BiPredicate<AABB, UUID> available, Consumer<Boolean> changed) {
            ChunkCoverage.of(bounds);
            this.available = Objects.requireNonNull(available);
            this.bounds = bounds;
            this.anchor = anchor;
            this.changed = changed;
            resident = available.test(bounds, anchor);
        }

        public boolean isResident() {
            return !closed && available.test(bounds, anchor);
        }

        public void update(AABB bounds, @Nullable UUID anchor) {
            if (closed) return;
            ChunkCoverage.of(bounds);
            this.bounds = bounds;
            this.anchor = anchor;
            refresh();
        }

        void refresh() {
            if (closed) return;
            boolean next = isResident();
            if (next == resident) return;
            boolean previous = resident;
            resident = next;
            try { changed.accept(next); }
            catch (RuntimeException failure) {
                // A failed adapter transition is retried; never leave a falsely acknowledged state.
                if (!closed && resident == next) resident = previous;
                LOGGER.log(System.Logger.Level.ERROR, "Resource residency adapter transition failed", failure);
            }
        }

        /** Idempotent unregistration. Owner teardown is deliberately not invoked by close(). */
        @Override public void close() {
            if (closed) return;
            closed = true;
            onClose.run();
            onClose = () -> {};
            changed = ignored -> {};
            available = (bounds, anchor) -> false;
        }
    }

    private static final class State {
        final LoadedChunkIndex chunks = new LoadedChunkIndex();
        final Map<Long, Boolean> pendingLoads = new ConcurrentHashMap<>();
        final Map<Long, WeakReference<LevelChunk>> knownChunks = new ConcurrentHashMap<>();
        final Map<UUID, WeakReference<Entity>> departingEntities = new HashMap<>();
        final Set<Registration> registrations = new LinkedHashSet<>();
        final LinkedHashMap<ChunkCoverage, Decision> decisions = new LinkedHashMap<>(64, .75f, true);
        final Set<Long> readyChunks = new HashSet<>();
        long availabilityRevision;
        long decisionRevision = -1;
        volatile long entityRevision;
        volatile boolean closed;

        boolean intersects(Level level, ChunkCoverage coverage) {
            if (closed) return false;
            long revision = chunks.revision();
            if (revision != decisionRevision) { decisions.clear(); decisionRevision = revision; }
            Decision cached = decisions.get(coverage);
            if (cached != null) {
                if (cached.witness == null) return false;
                if (available(level, cached.witness)) return true;
            }
            Long witness = chunks.firstIntersection(coverage, packed -> available(level, packed));
            if (decisionRevision != chunks.revision()) { decisions.clear(); decisionRevision = chunks.revision(); }
            decisions.put(coverage, new Decision(witness));
            if (decisions.size() > 4096) decisions.remove(decisions.keySet().iterator().next());
            return witness != null;
        }
        private boolean available(Level level, long packed) {
            boolean available = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) != null;
            observeAvailability(packed, available);
            return available;
        }
        private void observeAvailability(long packed, boolean available) {
            if (available ? readyChunks.add(packed) : readyChunks.remove(packed)) availabilityRevision++;
        }
        private record Decision(Long witness) {}
    }
}
