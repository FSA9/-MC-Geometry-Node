package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftRenderThreadDispatcher;
import com.mine.geometry_node.client.model.runtime.BackendArtifactLease;
import com.mine.geometry_node.client.model.runtime.BackendArtifactKey;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Tracks live HOST artifacts so binding reload and final ownership release are exact. */
public final class HostArtifactRepository {
    public static final HostArtifactRepository INSTANCE = new HostArtifactRepository();
    public static final BackendArtifactKey<HostPreparedArtifact> KEY =
            new BackendArtifactKey<>("HOST_NATIVE");

    private final Set<HostPreparedArtifact> live = Collections.newSetFromMap(new IdentityHashMap<>());
    private final com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher renderThread;
    private final Consumer<List<HostPreparedArtifact.CompatibilityTexture>> bindingRetirement;
    private final Consumer<HostPreparedArtifact> artifactRetirement;
    private final Consumer<HostPreparedArtifact> immediateClose;

    private HostArtifactRepository() {
        this(MinecraftRenderThreadDispatcher.INSTANCE,
                bindings -> RenderSystem.queueFencedTask(() -> HostPreparedArtifact.releaseBindings(
                        Minecraft.getInstance().getTextureManager(), bindings)),
                artifact -> RenderSystem.queueFencedTask(() -> artifact.close(
                        Minecraft.getInstance().getTextureManager())),
                artifact -> artifact.close(Minecraft.getInstance().getTextureManager()));
    }

    HostArtifactRepository(com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher renderThread,
                           Consumer<List<HostPreparedArtifact.CompatibilityTexture>> bindingRetirement,
                           Consumer<HostPreparedArtifact> artifactRetirement,
                           Consumer<HostPreparedArtifact> immediateClose) {
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.bindingRetirement = Objects.requireNonNull(bindingRetirement, "bindingRetirement");
        this.artifactRetirement = Objects.requireNonNull(artifactRetirement, "artifactRetirement");
        this.immediateClose = Objects.requireNonNull(immediateClose, "immediateClose");
    }

    public BackendArtifactLease<HostPreparedArtifact> acquire(ModelDefinition definition,
                                                               StaticModelRenderMetadata metadata) {
        renderThread.assertRenderThread();
        HostPreparedArtifact artifact = HostPreparedArtifact.prepare(definition, metadata);
        live.add(artifact);
        return new BackendArtifactLease<>(artifact, () -> retire(artifact));
    }

    public void invalidateBindings() {
        renderThread.assertRenderThread();
        for (HostPreparedArtifact artifact : live) {
            var detached = artifact.detachBindings();
            if (!detached.isEmpty()) bindingRetirement.accept(detached);
        }
    }

    public void close() {
        renderThread.assertRenderThread();
        HostPreparedArtifact[] artifacts = live.toArray(HostPreparedArtifact[]::new);
        live.clear();
        for (HostPreparedArtifact artifact : artifacts) immediateClose.accept(artifact);
    }

    int liveCount() { return live.size(); }

    private void retire(HostPreparedArtifact artifact) {
        renderThread.execute(() -> {
            if (!live.remove(artifact)) return;
            artifactRetirement.accept(artifact);
        });
    }
}
