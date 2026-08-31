package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.graph.resource.GraphResourceId;

import java.util.Objects;

/** A live force field whose center and finite support are supplied by an Area. */
public final class ForceFieldResource {
    private final ForceFieldAddress address;
    private final GraphResourceId owner;
    private final long generation;
    private final AreaAddress area;
    private final long creationGameTime;
    private final LiveValue.State<Float> strength;

    public ForceFieldResource(ForceFieldAddress address, GraphResourceId owner, long generation,
                              AreaAddress area, long creationGameTime,
                              LiveValue<Float> strength) {
        this.address = Objects.requireNonNull(address, "address");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.generation = generation;
        this.area = Objects.requireNonNull(area, "area");
        this.creationGameTime = creationGameTime;
        this.strength = Objects.requireNonNull(strength, "strength").newState();
    }

    public ForceFieldAddress address() { return address; }
    public GraphResourceId owner() { return owner; }
    public long generation() { return generation; }
    public AreaAddress area() { return area; }
    public long creationGameTime() { return creationGameTime; }

    public double evaluateStrength(ExpressionEvaluationContext context) {
        return strength.evaluate(context);
    }

    public float currentStrength() {
        return strength.value();
    }
}
