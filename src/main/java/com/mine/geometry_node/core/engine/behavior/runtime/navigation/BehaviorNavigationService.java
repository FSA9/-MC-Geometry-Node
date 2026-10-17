package com.mine.geometry_node.core.engine.behavior.runtime.navigation;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.mixin.behavior.GroundPathNavigationAccessor;
import com.mine.geometry_node.mixin.behavior.MobNavigationInvoker;
import com.mine.geometry_node.mixin.behavior.PathNavigationAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Shared server-thread entry point for behavior navigation probes and path requests. */
public final class BehaviorNavigationService {
    public static final BehaviorNavigationService INSTANCE = new BehaviorNavigationService();

    private static final int MAX_OWNERS = 1_024;
    private static final int MAX_TARGETS_PER_OWNER = 8;
    private static final long REACHABLE_TTL_TICKS = 5L;
    private static final long UNREACHABLE_TTL_TICKS = 20L;
    private static final long OWNER_IDLE_NANOS = Duration.ofMinutes(5).toNanos();
    private static final String MINECRAFT_PACKAGE = "net.minecraft.";

    private final Map<OwnerKey, OwnerState> owners = new OwnerCache();
    private final CopyOnWriteArrayList<NavigationProbeFactory> probeFactories =
            new CopyOnWriteArrayList<>();

    private BehaviorNavigationService() {
    }

    public synchronized void registerProbeFactory(NavigationProbeFactory factory) {
        probeFactories.add(Objects.requireNonNull(factory, "factory"));
        owners.values().forEach(OwnerState::dispose);
        owners.clear();
    }

    public synchronized void clear(Level level) {
        Objects.requireNonNull(level, "level");
        removeOwners(state -> state.level.get() == level);
    }

    public synchronized void clear(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        removeOwners(state -> {
            Level level = state.level.get();
            return level != null && level.getServer() == server;
        });
    }

    /** Probes without mutating the Mob's live navigation state. */
    public synchronized ProbeResult probeReachability(Mob owner, Entity target, long tick) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");
        long accessNanos = System.nanoTime();
        pruneReleasedOwners(accessNanos);

        PathNavigation live = owner.getNavigation();
        Mob navigationMob = ((PathNavigationAccessor) live).geometryNode$getMob();
        OwnerKey ownerKey = new OwnerKey(owner.level().dimension(), owner.getUUID());
        OwnerState state = owners.get(ownerKey);
        if (state == null || !state.matches(owner, navigationMob, live)) {
            if (state != null) state.dispose();
            state = new OwnerState(owner, navigationMob, live, accessNanos);
            owners.put(ownerKey, state);
        }
        state.touch(accessNanos);

        NavigationProbeFactory factory = findProbeFactory(navigationMob, live);
        NavigationConfiguration configuration = NavigationConfiguration.capture(
                live, navigationMob, factory);
        if (!configuration.equals(state.configuration)) {
            state.invalidate(configuration);
        }

        UUID targetId = target.getUUID();
        BlockPos ownerPosition = navigationMob.blockPosition();
        BlockPos targetPosition = target.blockPosition();
        ProbeEntry cached = state.cached(targetId, ownerPosition, targetPosition, tick);
        if (cached != null) return new ProbeResult(cached.reachable, true);
        if (state.probeUnsupported()) return ProbeResult.unsupported();

        PathNavigation probe = state.probe();
        if (probe == null) {
            probe = createCompatibleProbe(owner, navigationMob, live, factory);
            if (probe == null) {
                state.markProbeUnsupported();
                return ProbeResult.unsupported();
            }
            state.installProbe(probe, configuration, factory);
        }

        boolean reachable;
        try {
            synchronizeProbe(navigationMob, live, probe, factory);
            Path path = probe.createPath(target, 1);
            reachable = path != null && path.canReach();
        } catch (RuntimeException | Error failure) {
            state.discardProbe();
            throw failure;
        }
        state.cache(targetId, new ProbeEntry(ownerPosition, targetPosition, tick, reachable));
        return new ProbeResult(reachable, false);
    }

    /** Starts live movement for both behavior actions and editor orders. Caller owns MOVEMENT. */
    public boolean moveTo(Mob owner, net.minecraft.world.phys.Vec3 target, double speed) {
        return owner.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }
    public boolean moveTo(Mob owner, Entity target, double speed) {
        return owner.getNavigation().moveTo(target, speed);
    }

    /** Creates a path on the live navigator; future world work quotas will gate this entry. */
    public LivePathResult requestLivePath(Mob owner, BlockPos target, int reachRange) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(target, "target");
        Path path = owner.getNavigation().createPath(target, reachRange);
        return path != null ? LivePathResult.found(path) : LivePathResult.unreachable();
    }

    @Nullable
    private NavigationProbeFactory findProbeFactory(Mob navigationMob, PathNavigation live) {
        for (NavigationProbeFactory factory : probeFactories) {
            if (factory.supports(navigationMob, live)) return factory;
        }
        return null;
    }

    private PathNavigation createCompatibleProbe(Mob owner, Mob navigationMob,
                                                 PathNavigation live,
                                                 @Nullable NavigationProbeFactory factory) {
        PathNavigation probe = null;
        if (factory != null) {
            probe = factory.createProbe(navigationMob, live);
        } else if (navigationMob.getClass().getName().startsWith(MINECRAFT_PACKAGE)
                && live.getClass().getName().startsWith(MINECRAFT_PACKAGE)) {
            probe = ((MobNavigationInvoker) (Object) navigationMob)
                    .geometryNode$createNavigation(navigationMob.level());
        }
        if (probe == null) return null;
        if (probe == live || owner.getNavigation() != live
                || ((PathNavigationAccessor) probe).geometryNode$getMob() != navigationMob
                || probe.getClass() != live.getClass()
                || probe.getNodeEvaluator().getClass() != live.getNodeEvaluator().getClass()) {
            disposeProbe(probe, factory);
            return null;
        }
        return probe;
    }

    private static void synchronizeProbe(Mob navigationMob, PathNavigation live,
                                         PathNavigation probe,
                                         @Nullable NavigationProbeFactory factory) {
        NodeEvaluator source = live.getNodeEvaluator();
        NodeEvaluator target = probe.getNodeEvaluator();
        target.setCanFloat(source.canFloat());
        target.setCanOpenDoors(source.canOpenDoors());
        target.setCanPassDoors(source.canPassDoors());
        target.setCanWalkOverFences(source.canWalkOverFences());

        PathNavigationAccessor tuning = (PathNavigationAccessor) live;
        probe.setMaxVisitedNodesMultiplier(tuning.geometryNode$getMaxVisitedNodesMultiplier());
        probe.setRequiredPathLength(tuning.geometryNode$getRequiredPathLength());
        probe.updatePathfinderMaxVisitedNodes();

        if (live instanceof GroundPathNavigation liveGround
                && probe instanceof GroundPathNavigation probeGround) {
            boolean allowBelowSurface = ((GroundPathNavigationAccessor) liveGround)
                    .geometryNode$getCanPathToTargetsBelowSurface();
            probeGround.setCanPathToTargetsBelowSurface(allowBelowSurface);
        }
        if (factory != null) factory.synchronize(navigationMob, live, probe);
    }

    private void pruneReleasedOwners(long accessNanos) {
        Iterator<OwnerState> iterator = owners.values().iterator();
        while (iterator.hasNext()) {
            OwnerState state = iterator.next();
            Mob owner = state.owner.get();
            Mob navigationMob = state.navigationMob.get();
            if (owner == null || navigationMob == null
                    || owner.isRemoved() || navigationMob.isRemoved()
                    || state.idleAt(accessNanos)) {
                state.dispose();
                iterator.remove();
            }
        }
    }

    private void removeOwners(java.util.function.Predicate<OwnerState> predicate) {
        Iterator<OwnerState> iterator = owners.values().iterator();
        while (iterator.hasNext()) {
            OwnerState state = iterator.next();
            if (!predicate.test(state)) continue;
            iterator.remove();
            state.dispose();
        }
    }

    private static void disposeProbe(PathNavigation probe,
                                     @Nullable NavigationProbeFactory factory) {
        if (factory == null) return;
        try {
            factory.dispose(probe);
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.warn("Unable to dispose behavior navigation probe", exception);
        }
    }

    public enum ProbeStatus {
        REACHABLE,
        UNREACHABLE,
        DEFERRED,
        UNSUPPORTED
    }

    public record LivePathResult(ProbeStatus status, @Nullable Path path) {
        public LivePathResult {
            Objects.requireNonNull(status, "status");
            if ((status == ProbeStatus.REACHABLE) != (path != null)) {
                throw new IllegalArgumentException(
                        "A live path is required exactly when the request is reachable");
            }
            if (status == ProbeStatus.UNSUPPORTED) {
                throw new IllegalArgumentException("Live path requests cannot be unsupported");
            }
        }

        private static LivePathResult found(Path path) {
            return new LivePathResult(ProbeStatus.REACHABLE,
                    Objects.requireNonNull(path, "path"));
        }

        private static LivePathResult unreachable() {
            return new LivePathResult(ProbeStatus.UNREACHABLE, null);
        }

        public static LivePathResult deferred() {
            return new LivePathResult(ProbeStatus.DEFERRED, null);
        }
    }

    public record ProbeResult(ProbeStatus status, boolean cached) {
        private ProbeResult(boolean reachable, boolean cached) {
            this(reachable ? ProbeStatus.REACHABLE : ProbeStatus.UNREACHABLE, cached);
        }

        private static ProbeResult unsupported() {
            return new ProbeResult(ProbeStatus.UNSUPPORTED, false);
        }
    }

    private record OwnerKey(ResourceKey<Level> dimension, UUID ownerId) {
    }

    private record ProbeEntry(BlockPos ownerPosition, BlockPos targetPosition,
                              long tick, boolean reachable) {
        private boolean validAt(BlockPos currentOwnerPosition, BlockPos currentTargetPosition,
                                long currentTick) {
            long ttl = reachable ? REACHABLE_TTL_TICKS : UNREACHABLE_TTL_TICKS;
            return ownerPosition.equals(currentOwnerPosition)
                    && targetPosition.equals(currentTargetPosition)
                    && currentTick >= tick && currentTick - tick < ttl;
        }
    }

    private record NavigationConfiguration(Class<?> navigationType, Class<?> evaluatorType,
                                           boolean canFloat, boolean canOpenDoors,
                                           boolean canPassDoors, boolean canWalkOverFences,
                                           boolean onGround, boolean inLiquid,
                                           boolean passenger, boolean pathBelowSurface,
                                           int maxVisitedMultiplierBits,
                                           int requiredPathLengthBits, long followRangeBits,
                                           int widthBits, int heightBits, int malusHash,
                                           @Nullable NavigationProbeFactory factory,
                                           @Nullable Object customConfiguration) {
        private static NavigationConfiguration capture(PathNavigation navigation, Mob mob,
                                                       @Nullable NavigationProbeFactory factory) {
            NodeEvaluator evaluator = navigation.getNodeEvaluator();
            PathNavigationAccessor tuning = (PathNavigationAccessor) navigation;
            int malusHash = 1;
            for (PathType type : PathType.values()) {
                malusHash = 31 * malusHash + Float.floatToIntBits(mob.getPathfindingMalus(type));
            }
            boolean pathBelowSurface = navigation instanceof GroundPathNavigation ground
                    && ((GroundPathNavigationAccessor) ground)
                    .geometryNode$getCanPathToTargetsBelowSurface();
            return new NavigationConfiguration(navigation.getClass(), evaluator.getClass(),
                    evaluator.canFloat(), evaluator.canOpenDoors(), evaluator.canPassDoors(),
                    evaluator.canWalkOverFences(), mob.onGround(), mob.isInLiquid(),
                    mob.isPassenger(), pathBelowSurface,
                    Float.floatToIntBits(tuning.geometryNode$getMaxVisitedNodesMultiplier()),
                    Float.floatToIntBits(tuning.geometryNode$getRequiredPathLength()),
                    Double.doubleToLongBits(mob.getAttributeValue(Attributes.FOLLOW_RANGE)),
                    Float.floatToIntBits(mob.getBbWidth()),
                    Float.floatToIntBits(mob.getBbHeight()), malusHash, factory,
                    factory != null ? factory.configurationKey(mob, navigation) : null);
        }

        private boolean structurallyMatches(NavigationConfiguration other) {
            return navigationType == other.navigationType
                    && evaluatorType == other.evaluatorType
                    && factory == other.factory;
        }
    }

    private static final class OwnerState {
        private final WeakReference<Mob> owner;
        private final WeakReference<Mob> navigationMob;
        private final WeakReference<PathNavigation> live;
        private final WeakReference<Level> level;
        @Nullable private PathNavigation probe;
        private final Map<UUID, ProbeEntry> entries = new ProbeCache();
        private NavigationConfiguration configuration;
        @Nullable private NavigationProbeFactory factory;
        private boolean probeUnsupported;
        private long lastAccessNanos;

        private OwnerState(Mob owner, Mob navigationMob, PathNavigation live,
                           long accessNanos) {
            this.owner = new WeakReference<>(owner);
            this.navigationMob = new WeakReference<>(navigationMob);
            this.live = new WeakReference<>(live);
            this.level = new WeakReference<>(owner.level());
            this.configuration = NavigationConfiguration.capture(live, navigationMob, null);
            this.lastAccessNanos = accessNanos;
        }

        private boolean matches(Mob currentOwner, Mob currentNavigationMob,
                                PathNavigation currentLive) {
            return owner.get() == currentOwner && navigationMob.get() == currentNavigationMob
                    && live.get() == currentLive && level.get() == currentOwner.level()
                    && currentNavigationMob.level() == currentOwner.level();
        }

        @Nullable
        private PathNavigation probe() {
            return probe;
        }

        private boolean probeUnsupported() {
            return probeUnsupported;
        }

        private void markProbeUnsupported() {
            probeUnsupported = true;
            entries.clear();
        }

        private void installProbe(PathNavigation value, NavigationConfiguration valueConfiguration,
                                  @Nullable NavigationProbeFactory valueFactory) {
            probe = value;
            configuration = valueConfiguration;
            factory = valueFactory;
            probeUnsupported = false;
        }

        private void discardProbe() {
            PathNavigation discardedProbe = probe;
            NavigationProbeFactory discardedFactory = factory;
            probe = null;
            factory = null;
            probeUnsupported = false;
            entries.clear();
            if (discardedProbe != null) disposeProbe(discardedProbe, discardedFactory);
        }

        private void invalidate(NavigationConfiguration value) {
            if (!configuration.structurallyMatches(value)) discardProbe();
            configuration = value;
            probeUnsupported = false;
            entries.clear();
        }

        @Nullable
        private ProbeEntry cached(UUID targetId, BlockPos ownerPosition,
                                  BlockPos targetPosition, long tick) {
            ProbeEntry entry = entries.get(targetId);
            if (entry == null) return null;
            if (entry.validAt(ownerPosition, targetPosition, tick)) return entry;
            entries.remove(targetId);
            return null;
        }

        private void cache(UUID targetId, ProbeEntry entry) {
            entries.put(targetId, entry);
        }

        private void touch(long accessNanos) {
            lastAccessNanos = accessNanos;
        }

        private boolean idleAt(long accessNanos) {
            return accessNanos - lastAccessNanos >= OWNER_IDLE_NANOS;
        }

        private void dispose() {
            discardProbe();
        }
    }

    private static final class OwnerCache extends LinkedHashMap<OwnerKey, OwnerState> {
        private OwnerCache() {
            super(64, 0.75F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<OwnerKey, OwnerState> eldest) {
            boolean remove = size() > MAX_OWNERS;
            if (remove) eldest.getValue().dispose();
            return remove;
        }
    }

    private static final class ProbeCache extends LinkedHashMap<UUID, ProbeEntry> {
        private ProbeCache() {
            super(16, 0.75F, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, ProbeEntry> eldest) {
            return size() > MAX_TARGETS_PER_OWNER;
        }
    }
}
