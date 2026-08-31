package com.mine.geometry_node.core.engine.graph.expression;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable formula and its typed variable bindings at graph-value capture time. */
public record ExpressionSpec(String formula, Map<String, ExpressionBinding> bindings) {
    public ExpressionSpec {
        formula = formula == null ? "" : formula.trim();
        Map<String, ExpressionBinding> copy = new LinkedHashMap<>();
        if (bindings != null) {
            bindings.forEach((name, binding) -> {
                if (name != null && !name.isBlank() && binding != null) {
                    copy.put(name, binding);
                }
            });
        }
        bindings = Map.copyOf(copy);
    }

    public static ExpressionSpec of(String formula) {
        return new ExpressionSpec(formula, Map.of());
    }
}
