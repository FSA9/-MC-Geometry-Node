package com.mine.geometry_node.core.engine.graph.expression;

import java.util.List;

/** Immutable live-value definition captured from a graph input. */
public interface LiveValue<T> {
    T snapshot();

    List<String> diagnostics();

    State<T> newState();

    /** Mutable, consumer-local evaluation state. It must not be shared between graph resources. */
    interface State<T> {
        T value();

        T evaluate(ExpressionEvaluationContext context);
    }
}
