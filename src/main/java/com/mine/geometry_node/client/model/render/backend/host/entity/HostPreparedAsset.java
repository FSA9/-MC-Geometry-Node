package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.DecodedModelImage;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostCanonicalPrimitive;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostPreparedLightingAsset;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable CPU asset data. Safe for model and lighting workers; contains no render-thread resources. */
public final class HostPreparedAsset implements AutoCloseable {
    private final HostDrawPlan drawPlan;
    private final Map<Integer, DecodedModelImage> decodedImages;
    private final Map<Integer, String> imageFailures;
    private final Map<StaticModelMaterial, HostPreparedArtifact.LabPbrImages> labPbrImages;
    private final HostPreparedLightingAsset lightingAsset;

    HostPreparedAsset(HostDrawPlan drawPlan, Map<Integer, DecodedModelImage> decodedImages,
                      Map<Integer, String> imageFailures,
                      Map<StaticModelMaterial, HostPreparedArtifact.LabPbrImages> labPbrImages,
                      HostPreparedLightingAsset lightingAsset) {
        this.drawPlan = Objects.requireNonNull(drawPlan, "drawPlan");
        this.decodedImages = Map.copyOf(decodedImages);
        this.imageFailures = Map.copyOf(imageFailures);
        this.labPbrImages = Map.copyOf(labPbrImages);
        this.lightingAsset = Objects.requireNonNull(lightingAsset, "lightingAsset");
    }

    public HostDrawPlan drawPlan() { return drawPlan; }
    public List<HostCanonicalPrimitive> canonicalPrimitives() { return drawPlan.canonicalPrimitives(); }
    public HostPreparedLightingAsset lightingAsset() { return lightingAsset; }

    public DecodedModelImage decodedImage(int index) throws IOException {
        DecodedModelImage image = decodedImages.get(index);
        if (image != null) return image;
        throw new IOException(imageFailures.getOrDefault(index, "model image was not decoded"));
    }

    DecodedModelImage decodedImageOrNull(int index) { return decodedImages.get(index); }

    HostPreparedArtifact.LabPbrImages labPbrImages(StaticModelMaterial material) {
        return Objects.requireNonNull(labPbrImages.get(material), "prepared LabPBR material");
    }

    @Override public void close() { lightingAsset.close(); }
}
