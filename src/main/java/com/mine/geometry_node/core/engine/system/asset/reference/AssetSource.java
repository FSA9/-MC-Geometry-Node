package com.mine.geometry_node.core.engine.system.asset.reference;

/** A registered namespace for asset references. */
public interface AssetSource {
    String id();

    AssetResolver resolver();
}
