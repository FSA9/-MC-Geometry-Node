package com.mine.geometry_node.core.engine.blueprint.spatial.area;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.expression.ServerExpressionBindingResolver;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** One live, binding-owned Area stored under a public dimension + id address. */
public final class AreaResource {
    private final AreaAddress address;
    private final GraphResourceId owner;
    private final long generation;
    private final AreaShape shape;
    private final long creationGameTime;
    private final LiveValue.State<Vec3> center;
    private final LiveValue.State<Vec3> size;
    private final LiveValue.State<Vec3> rotation;
    private final LiveValue.State<Float> radius;
    private final LiveValue.State<Float> height;
    @Nullable
    private final UUID anchorEntityId;

    private long resolvedGameTime = Long.MIN_VALUE;
    @Nullable
    private Resolved resolved;

    public AreaResource(AreaAddress address, GraphResourceId owner, long generation,
                        AreaShape shape, long creationGameTime,
                        LiveValue<Vec3> center, LiveValue<Vec3> size,
                        LiveValue<Vec3> rotation, LiveValue<Float> radius,
                        LiveValue<Float> height, @Nullable UUID anchorEntityId) {
        this.address = Objects.requireNonNull(address, "address");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.generation = generation;
        this.shape = Objects.requireNonNull(shape, "shape");
        this.creationGameTime = creationGameTime;
        this.center = Objects.requireNonNull(center, "center").newState();
        this.size = Objects.requireNonNull(size, "size").newState();
        this.rotation = Objects.requireNonNull(rotation, "rotation").newState();
        this.radius = Objects.requireNonNull(radius, "radius").newState();
        this.height = Objects.requireNonNull(height, "height").newState();
        this.anchorEntityId = anchorEntityId;
    }

    public AreaAddress address() { return address; }
    public GraphResourceId owner() { return owner; }
    public long generation() { return generation; }
    public AreaShape shape() { return shape; }
    public long creationGameTime() { return creationGameTime; }
    @Nullable public UUID anchorEntityId() { return anchorEntityId; }

    /** Evaluates live inputs at most once per game Tick, regardless of how many systems query this Area. */
    @Nullable
    public synchronized Resolved resolve(ServerLevel level) {
        if (level == null || !level.dimension().equals(address.dimension())) return null;
        long gameTime = level.getGameTime();
        if (resolvedGameTime == gameTime) return resolved;

        ExpressionEvaluationContext context = new ExpressionEvaluationContext(
                gameTime,
                Math.max(0L, gameTime - creationGameTime),
                new ServerExpressionBindingResolver(level.getServer()));
        Vec3 evaluatedCenter = center.evaluate(context);
        Vec3 evaluatedSize = switch (shape) {
            case SPHERE -> diameter(radius.evaluate(context));
            case CYLINDER -> cylinderSize(radius.evaluate(context), height.evaluate(context));
            case BOX -> AreaEntityQuery.sanitizeSize(size.evaluate(context));
        };
        Vec3 evaluatedRotation = shape == AreaShape.SPHERE ? Vec3.ZERO : rotation.evaluate(context);

        if (anchorEntityId != null) {
            Entity anchor = level.getEntity(anchorEntityId);
            if (anchor == null || anchor.isRemoved()) {
                resolved = null;
                resolvedGameTime = gameTime;
                return null;
            }
            evaluatedCenter = anchor.position().add(evaluatedCenter);
        }

        resolved = new Resolved(shape, evaluatedCenter, evaluatedSize, evaluatedRotation);
        resolvedGameTime = gameTime;
        return resolved;
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
    }
}
