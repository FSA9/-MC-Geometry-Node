package com.mine.geometry_node.client.model.render.backend.host.entity;

/** Deterministic far-to-near key for HOST_NATIVE translucent draw submissions. */
public record HostTransparentOrderKey(float distanceSquared, String asset, int node, int primitive,
                                      String instance) implements Comparable<HostTransparentOrderKey> {
    public HostTransparentOrderKey {
        if (asset == null || instance == null) throw new IllegalArgumentException("stable identities are required");
    }

    @Override
    public int compareTo(HostTransparentOrderKey other) {
        int depth = compareDistanceFarToNear(distanceSquared, other.distanceSquared);
        if (depth != 0) return depth;
        int assetOrder = asset.compareTo(other.asset);
        if (assetOrder != 0) return assetOrder;
        int nodeOrder = Integer.compare(node, other.node);
        if (nodeOrder != 0) return nodeOrder;
        int primitiveOrder = Integer.compare(primitive, other.primitive);
        return primitiveOrder != 0 ? primitiveOrder : instance.compareTo(other.instance);
    }

    static int compareDistanceFarToNear(float left, float right) {
        if (!Float.isFinite(left)) return Float.isFinite(right) ? 1 : 0;
        if (!Float.isFinite(right)) return -1;
        return Float.compare(right, left);
    }
}
