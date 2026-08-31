package com.mine.geometry_node.core.engine.graph.expression;

import java.util.Objects;

/**
 * Values supplied by a graph resource when it evaluates a live expression. The caller computes
 * {@code resourceAge} from the current game time and that resource's creation game time.
 */
public record ExpressionEvaluationContext(
        double worldGameTime,
        double resourceAge,
        BindingResolver bindingResolver
) {
    public ExpressionEvaluationContext {
        bindingResolver = Objects.requireNonNull(bindingResolver, "bindingResolver");
    }

    public static ExpressionEvaluationContext withoutBindings(double worldGameTime, double resourceAge) {
        return new ExpressionEvaluationContext(worldGameTime, resourceAge, ExpressionBinding::fallbackValue);
    }

    double resolve(ExpressionBinding binding) {
        if (binding instanceof ExpressionBinding.Constant constant) {
            return constant.fallbackValue();
        }
        return bindingResolver.resolve(binding);
    }

    @FunctionalInterface
    public interface BindingResolver {
        double resolve(ExpressionBinding binding);
    }
}
