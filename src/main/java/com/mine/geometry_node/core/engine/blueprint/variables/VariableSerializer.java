package com.mine.geometry_node.core.engine.blueprint.variables;

import com.mine.geometry_node.core.engine.graph.value.GraphValueCodec;

/**
 * @deprecated Graph values are shared by all graph families. Use {@link GraphValueCodec}.
 */
@Deprecated
public interface VariableSerializer<T> extends GraphValueCodec<T> {
}
