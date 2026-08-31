package com.mine.geometry_node.core.engine.graph.expression;

import java.util.List;
import java.util.Map;

/** Immutable scalar or three-axis expression payload propagated through graph data links. */
public record ExpressionData(
        String formula,
        List<String> components,
        Map<String, ExpressionBinding> bindings
) {
    public static final ExpressionData ZERO = scalar("0", Map.of());

    public ExpressionData {
        formula = formula == null ? "" : formula.trim();
        components = components == null ? List.of() : List.copyOf(components);
        bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
        if (!components.isEmpty() && components.size() != 3) {
            throw new IllegalArgumentException("Vector expression requires exactly three components");
        }
    }

    public ExpressionData(String formula, Map<String, ExpressionBinding> bindings) {
        this(formula, List.of(), bindings);
    }

    public static ExpressionData scalar(String formula, Map<String, ExpressionBinding> bindings) {
        return new ExpressionData(formula, List.of(), bindings);
    }

    public static ExpressionData vector(String x, String y, String z,
                                        Map<String, ExpressionBinding> bindings) {
        return new ExpressionData("", List.of(normalize(x), normalize(y), normalize(z)), bindings);
    }

    public boolean isVector() {
        return components.size() == 3;
    }

    public String component(int index) {
        return isVector() ? components.get(index) : formula;
    }

    private static String normalize(String formula) {
        return formula == null || formula.isBlank() ? "0" : formula.trim();
    }
}
