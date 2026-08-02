package com.mine.geometry_node.core.engine.blueprint.debug;

public enum GeometryDebugType {
    MESH(0),
    PERFECT_CYLINDER(1),
    PERFECT_SPHERE(2);

    private final int networkId;

    GeometryDebugType(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static GeometryDebugType fromNetworkId(int networkId) {
        return switch (networkId) {
            case 1 -> PERFECT_CYLINDER;
            case 2 -> PERFECT_SPHERE;
            default -> MESH;
        };
    }
}
