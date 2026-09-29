package com.mine.geometry_node.core.engine.system.model.runtime;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

@FunctionalInterface
public interface ModelAssetLoader<K, A> {
    A load(K key, ModelCancellation cancellation) throws Exception;
}
