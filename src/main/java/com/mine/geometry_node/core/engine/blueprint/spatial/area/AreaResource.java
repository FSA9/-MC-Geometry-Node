package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver;

import com.mine.geometry_node.core.engine.metadata.state.RuntimeTags;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.ServerExpressionBindingResolver;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import com.mine.geometry_node.core.engine.graph.scoped.storage.TransientScopedStateStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import com.mine.geometry_node.core.engine.residency.ResourceResidency;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** A graph-owned live area or an immutable scene-owned area under a public dimension + id address. */
public final class AreaResource {
    final RuntimeTags metadataTags = new RuntimeTags();
    private final Resolved authored;
    private final AreaAddress address;
    private final GraphResourceId owner;
    private final long generation;
    private final UUID incarnation;
    @Nullable private final UUID authoredObjectId;
    private TransientScopedStateStore scopedState = new TransientScopedStateStore();
    private final AreaShape shape;
    private final long creationGameTime;
    private final LiveValue.State<Vec3> center;
    private final LiveValue.State<Vec3> size;
    private final LiveValue.State<Vec3> rotation;
    private final LiveValue.State<Vec3> worldRotation;
    private final LiveValue.State<Vec3> localOffset;
    private Vec3 evaluatedWorldRotation, evaluatedLocalOffset;
    private final LiveValue.State<Float> radius;
    private final LiveValue.State<Float> height;
    private final SpatialAnchor spatialAnchor;

    private long resolvedGameTime = Long.MIN_VALUE;
    private long resolvedResidencyRevision = Long.MIN_VALUE;
    private long evaluatedGameTime = Long.MIN_VALUE;
    @Nullable private Resolved evaluated;
    private boolean enabled = true;
    @Nullable private Resolved lastResolved;
    @Nullable
    private Resolved resolved;

    public AreaResource(AreaAddress address, GraphResourceId owner, long generation,
                        AreaShape shape, long creationGameTime,
                        LiveValue<Vec3> center, LiveValue<Vec3> size,
                        LiveValue<Vec3> rotation, LiveValue<Float> radius,
                        LiveValue<Float> height, SpatialAnchor spatialAnchor) {
        this(address, owner, generation, shape, creationGameTime, center, size, rotation, radius, height,
                com.mine.geometry_node.core.engine.graph.expression.LiveValues.captureXyz(null, Vec3.ZERO, null, null, null),
                com.mine.geometry_node.core.engine.graph.expression.LiveValues.captureXyz(null, Vec3.ZERO, null, null, null), spatialAnchor);
    }

    public AreaResource(AreaAddress address, GraphResourceId owner, long generation,
                        AreaShape shape, long creationGameTime,
                        LiveValue<Vec3> center, LiveValue<Vec3> size,
                        LiveValue<Vec3> rotation, LiveValue<Float> radius,
                        LiveValue<Float> height, LiveValue<Vec3> worldRotation,
                        LiveValue<Vec3> localOffset, SpatialAnchor spatialAnchor) {
        this.authored = null;
        this.address = Objects.requireNonNull(address, "address");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.generation = generation;
        this.incarnation = UUID.randomUUID();
        this.authoredObjectId = null;
        this.shape = Objects.requireNonNull(shape, "shape");
        this.creationGameTime = creationGameTime;
        this.center = Objects.requireNonNull(center, "center").newState();
        this.size = Objects.requireNonNull(size, "size").newState();
        this.rotation = Objects.requireNonNull(rotation, "rotation").newState();
        this.worldRotation = Objects.requireNonNull(worldRotation, "worldRotation").newState();
        this.localOffset = Objects.requireNonNull(localOffset, "localOffset").newState();
        this.radius = Objects.requireNonNull(radius, "radius").newState();
        this.height = Objects.requireNonNull(height, "height").newState();
        Objects.requireNonNull(spatialAnchor, "spatialAnchor");
        if (!spatialAnchor.dimensionOr(address.dimension()).equals(address.dimension()))
            throw new IllegalArgumentException("Area anchor dimension mismatch");
        this.spatialAnchor = spatialAnchor.withDimension(address.dimension());
    }

    /** Persistent scene ownership is deliberately separate from graph binding ownership. */
    public static AreaResource authored(AreaAddress address, UUID id, long revision, long gameTime, Resolved resolved) {
        return authored(address, id, UUID.randomUUID(), revision, gameTime, resolved);
    }
    public static AreaResource authored(AreaAddress address, UUID id, UUID incarnation,
                                        long revision, long gameTime, Resolved resolved) {
        return new AreaResource(address, id, incarnation, revision, gameTime, resolved);
    }
    private AreaResource(AreaAddress address, UUID id, UUID incarnation, long revision, long gameTime, Resolved resolved) {
        this.address = Objects.requireNonNull(address);
        this.owner = null;
        this.generation = revision;
        this.incarnation = Objects.requireNonNull(incarnation);
        this.authoredObjectId = Objects.requireNonNull(id);
        this.authored = Objects.requireNonNull(resolved);
        this.lastResolved = resolved;
        this.shape = resolved.shape();
        this.creationGameTime = gameTime;
        this.center = this.size = this.rotation = this.worldRotation = this.localOffset = null;
        this.radius = this.height = null;
        this.spatialAnchor = SpatialAnchor.world(address.dimension(), Vec3.ZERO);
    }
    public boolean isAuthored() { return authored != null; }
    @Nullable public UUID authoredObjectId() { return authoredObjectId; }
    public AreaAddress address() { return address; }
    @Nullable public GraphResourceId owner() { return owner; }
    public long generation() { return generation; }
    public AreaRef reference() { return new AreaRef(address, incarnation); }
    public TransientScopedStateStore scopedState() { return scopedState; }
    /** Called before publication: geometry revisions do not end an authored object's scoped lifetime. */
    void retainAuthoredScopedState(AreaResource previous) {
        if (isAuthored() && previous != null && previous.isAuthored() && reference().equals(previous.reference())) {
            scopedState = previous.scopedState;
        }
    }
    public AreaShape shape() { return shape; }
    public long creationGameTime() { return creationGameTime; }
    @Nullable public UUID anchorEntityId() { return spatialAnchor.requiresEntity() ? spatialAnchor.entityId() : null; }
    public SpatialAnchor spatialAnchor() { return spatialAnchor; }

    /** Disabling an authored area affects activity, not its identity, metadata or scoped state. */
    public void setAuthoredEnabled(boolean enabled) {
        if (!isAuthored()) throw new IllegalStateException("Only authored areas have an enabled flag");
        this.enabled = enabled;
    }

    @Nullable public synchronized Resolved lastResolved() { return lastResolved; }

    /** Definition queries retain the last world pose when an attached entity is untracked. */
    @Nullable public synchronized Resolved resolveForQuery(ServerLevel level) {
        if (level == null || !level.dimension().equals(address.dimension())) return null;
        Resolved value = resolve(level);
        return value != null ? value : lastResolved;
    }

    /** Active consumers must use this path; query geometry never implies activity. */
    @Nullable public Resolved resolveActive(ServerLevel level) {
        Resolved value = resolve(level);
        return isActive(level, value) ? value : null;
    }

    public boolean isActive(Level level, @Nullable Resolved value) {
        return isActive(level, value, ResourceResidency::isResident);
    }

    boolean isActive(Level level, @Nullable Resolved value, Residency residency) {
        return enabled && value != null && residency.test(level, value.bounds(), anchorEntityId());
    }

    @FunctionalInterface
    interface Residency {
        boolean test(Level level, AABB bounds, @Nullable UUID anchor);
    }

    /** Evaluates live inputs at most once per game Tick, regardless of how many systems query this Area. */
    @Nullable
    public synchronized Resolved resolve(ServerLevel level) {
        if (level == null || !level.dimension().equals(address.dimension())) return null;
        if (authored != null) return authored;
        long gameTime = level.getGameTime();
        long residencyRevision = ResourceResidency.revision(level);
        if (resolvedGameTime == gameTime && resolvedResidencyRevision == residencyRevision) return resolved;
        resolvedResidencyRevision = residencyRevision;

        if (evaluated == null || evaluatedGameTime != gameTime) {
            ExpressionEvaluationContext context = new ExpressionEvaluationContext(
                    gameTime,
                    Math.max(0L, gameTime - creationGameTime),
                    new ServerExpressionBindingResolver(level.getServer()));
            Vec3 evaluatedSize = switch (shape) {
                case SPHERE -> diameter(radius.evaluate(context));
                case CYLINDER -> cylinderSize(radius.evaluate(context), height.evaluate(context));
                case BOX -> AreaEntityQuery.sanitizeSize(size.evaluate(context));
            };
            evaluated = new Resolved(shape, center.evaluate(context), evaluatedSize, rotation.evaluate(context));
            evaluatedWorldRotation = worldRotation.evaluate(context);
            evaluatedLocalOffset = localOffset.evaluate(context);
            evaluatedGameTime = gameTime;
        }
        Vec3 anchorPosition = SpatialAnchorResolver.resolve(spatialAnchor, level);
        Quaterniond anchorRotation = SpatialAnchorResolver.resolveRotation(spatialAnchor, level);
        if (anchorPosition == null || anchorRotation == null) {
            resolved = null;
            resolvedGameTime = gameTime;
            return null;
        }
        resolved = place(shape, evaluated.center(), evaluated.size(), evaluatedWorldRotation,
                evaluatedLocalOffset, evaluated.rotation(), anchorPosition, anchorRotation);
        lastResolved = resolved;
        resolvedGameTime = gameTime;
        return resolved;
    }

    static Resolved place(AreaShape shape, Vec3 worldOffset, Vec3 size, Vec3 worldRotation,
                          Vec3 localOffset, Vec3 localRotation, Vec3 anchorPosition, Quaterniond anchorRotation) {
        SpatialTransform placement = new SpatialTransform(worldOffset, worldRotation, localOffset, localRotation);
        Vec3 finalRotation = SpatialTransform.eulerDegrees(placement.orientation(anchorRotation));
        // Area query geometry uses positive-Y Euler yaw internally.
        finalRotation = new Vec3(finalRotation.x, -finalRotation.y, finalRotation.z);
        return new Resolved(shape, placement.position(anchorPosition, anchorRotation), size,
                shape == AreaShape.SPHERE ? Vec3.ZERO : finalRotation);
    }

    private static Vec3 diameter(float radius) {
        double diameter = positive(radius) * 2.0D;
        return new Vec3(diameter, diameter, diameter);
    }

    private static Vec3 cylinderSize(float radius, float height) {
        double diameter = positive(radius) * 2.0D;
        return new Vec3(diameter, positive(height), diameter);
    }

    private static double positive(float value) {
        return Float.isFinite(value) ? Math.max(0.001D, Math.abs(value)) : 1.0D;
    }

    public record Resolved(AreaShape shape, Vec3 center, Vec3 size, Vec3 rotation) {
        /** World support bounds in the same positive-Y YXZ convention as AreaEntityQuery. */
        public AABB bounds() {
            Vec3 safe = AreaEntityQuery.sanitizeSize(size);
            double x = safe.x * .5, y = safe.y * .5, z = safe.z * .5;
            if (shape == AreaShape.SPHERE) {
                double diameter = Math.max(x, Math.max(y, z)) * 2;
                return AABB.ofSize(center, diameter, diameter, diameter);
            }
            Quaterniond q = new Quaterniond().rotationYXZ(Math.toRadians(rotation.y),
                    Math.toRadians(rotation.x), Math.toRadians(rotation.z));
            Vector3d a = q.transform(new Vector3d(1, 0, 0));
            Vector3d b = q.transform(new Vector3d(0, 1, 0));
            Vector3d c = q.transform(new Vector3d(0, 0, 1));
            return AABB.ofSize(center,
                    extent(a.x, b.x, c.x, x, y, z) * 2,
                    extent(a.y, b.y, c.y, x, y, z) * 2,
                    extent(a.z, b.z, c.z, x, y, z) * 2);
        }

        private double extent(double a, double b, double c, double x, double y, double z) {
            return shape == AreaShape.CYLINDER
                    ? Math.hypot(a * x, c * z) + Math.abs(b) * y
                    : Math.abs(a) * x + Math.abs(b) * y + Math.abs(c) * z;
        }
    }
}
