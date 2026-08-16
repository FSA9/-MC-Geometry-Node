package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;

import java.util.List;

public record ModelGpuUploadPlan(
        ModelAssetReference source,
        List<ModelGpuLayoutGroupPlan> layoutGroups,
        List<ModelGpuDrawRange> drawRanges,
        List<ModelGpuImagePlan> images
) {
    public ModelGpuUploadPlan {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        layoutGroups = layoutGroups == null ? List.of() : List.copyOf(layoutGroups);
        drawRanges = drawRanges == null ? List.of() : List.copyOf(drawRanges);
        images = images == null ? List.of() : List.copyOf(images);
    }
}
