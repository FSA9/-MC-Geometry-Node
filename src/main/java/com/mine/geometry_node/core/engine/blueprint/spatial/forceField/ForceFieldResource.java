package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.metadata.state.RuntimeMetadata;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** A live force field whose center and finite support are supplied by an Area. */
public final class ForceFieldResource {
    final RuntimeMetadata metadata = new RuntimeMetadata();
    private final ForceFieldAddress address;
    private final GraphResourceId owner;
    private final long generation;
    private final AreaAddress area;
    private final long creationGameTime;
    private final LiveValue.State<Float> strength;
    @Nullable private final UUID authoredObjectId;
    /** Mirrors the referenced Area's spatial anchor for entity-unload cleanup. */
    @Nullable
    private UUID anchorEntityId;

    public ForceFieldResource(ForceFieldAddress address, GraphResourceId owner, long generation,
                              AreaAddress area, long creationGameTime,
                              LiveValue<Float> strength, @Nullable UUID anchorEntityId) {
        this(address, Objects.requireNonNull(owner, "owner"), generation, area, creationGameTime,
                strength, anchorEntityId, null);
    }

    public static ForceFieldResource authored(ForceFieldAddress address, UUID objectId, long revision,
            AreaAddress area, long creationGameTime, float strength) {
        if (!Float.isFinite(strength)) throw new IllegalArgumentException("Nonfinite force-field strength");
        return new ForceFieldResource(address, null, revision, area, creationGameTime,
                com.mine.geometry_node.core.engine.graph.expression.LiveValues.captureFloat(null, strength, null),
                null, Objects.requireNonNull(objectId));
    }

    private ForceFieldResource(ForceFieldAddress address, @Nullable GraphResourceId owner, long generation,
            AreaAddress area, long creationGameTime, LiveValue<Float> strength,
            @Nullable UUID anchorEntityId, @Nullable UUID authoredObjectId) {
        this.address = Objects.requireNonNull(address, "address");
        this.owner = owner;
        this.authoredObjectId = authoredObjectId;
        this.generation = generation;
        this.area = Objects.requireNonNull(area, "area");
        this.creationGameTime = creationGameTime;
        this.strength = Objects.requireNonNull(strength, "strength").newState();
        this.anchorEntityId = anchorEntityId;
    }

    public ForceFieldAddress address() { return address; }
    @Nullable public GraphResourceId owner() { return owner; }
    public boolean isAuthored() { return authoredObjectId != null; }
    @Nullable public UUID authoredObjectId() { return authoredObjectId; }
    public long generation() { return generation; }
    public AreaAddress area() { return area; }
    public long creationGameTime() { return creationGameTime; }
    @Nullable public UUID anchorEntityId() { return anchorEntityId; }

    void setAnchorEntityId(@Nullable UUID anchorEntityId) {
        this.anchorEntityId = anchorEntityId;
    }

    public double evaluateStrength(ExpressionEvaluationContext context) {
        return strength.evaluate(context);
    }

    public float currentStrength() {
        return strength.value();
    }
}
