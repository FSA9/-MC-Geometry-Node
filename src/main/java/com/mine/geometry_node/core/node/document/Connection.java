package com.mine.geometry_node.core.node;

import com.google.gson.annotations.SerializedName;

public record Connection(
        @SerializedName("target_node") String targetNodeId,
        @SerializedName("target_port") String targetPortName
) {
    public boolean isValid() {
        return targetNodeId != null && !targetNodeId.isEmpty() &&
                targetPortName != null && !targetPortName.isEmpty();
    }
}