package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.integration.ModelCompatibilityLoss;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.*;

/** Asset-owned HOST plan plus render-thread-owned binding variants. */
public final class HostPreparedArtifact {
    private final HostDrawPlan drawPlan;
    final Map<TextureKey, CompatibilityTexture> textures = new HashMap<>();
    final Set<TextureKey> failedTextures = new HashSet<>();
    final Set<TextureKey> loggedRuntimeTextureFailures = new HashSet<>();
    final Set<String> loggedGeometryFailures = new HashSet<>();
    private boolean closed;

    HostPreparedArtifact(HostDrawPlan drawPlan) {
        this.drawPlan = Objects.requireNonNull(drawPlan, "drawPlan");
    }

    public static HostPreparedArtifact prepare(ModelDefinition definition, StaticModelRenderMetadata metadata) {
        return new HostPreparedArtifact(HostDrawPlan.compile(definition, metadata));
    }

    public HostDrawPlan drawPlan() { return drawPlan; }

    List<CompatibilityTexture> detachBindings() {
        if (closed) return List.of();
        List<CompatibilityTexture> detached = List.copyOf(textures.values());
        textures.clear();
        failedTextures.clear();
        loggedRuntimeTextureFailures.clear();
        return detached;
    }

    void close(TextureManager manager) {
        if (closed) return;
        List<CompatibilityTexture> detached = detachBindings();
        closed = true;
        releaseBindings(manager, detached);
    }

    static void releaseBindings(TextureManager manager, List<CompatibilityTexture> bindings) {
        for (CompatibilityTexture binding : bindings) {
            try {
                try {
                    IrisLabPbrProjector.beforeAlbedoRelease(binding.texture());
                } finally {
                    manager.release(binding.identifier());
                }
            } catch (RuntimeException failure) {
                GeometryNode.LOGGER.warn("Failed to retire HOST texture binding {}", binding.identifier(), failure);
            }
        }
    }

    record TextureKey(StaticModelMaterial material, boolean labPbr, boolean opaqueFallback) {
        ModelAlphaMode alphaMode() { return material.alphaMode(); }
    }

    record CompatibilityTexture(Identifier identifier,
                                IrisLabPbrProjector.LabPbrAlbedoTexture texture,
                                Set<ModelCompatibilityLoss> losses,
                                boolean defaultMaterialFallback) {
        CompatibilityTexture {
            losses = Set.copyOf(losses);
        }
    }
}
