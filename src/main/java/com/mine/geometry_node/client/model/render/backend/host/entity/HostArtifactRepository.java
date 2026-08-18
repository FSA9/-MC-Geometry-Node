package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftRenderThreadDispatcher;
import com.mine.geometry_node.client.model.runtime.BackendArtifactLease;
import com.mine.geometry_node.client.model.runtime.BackendArtifactKey;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.time.Duration;

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
    private final StaticVariantRetirement staticVariantRetirement;
    private long generation;
    private boolean staticRetirementPending;
    private boolean staticReclaimActive;
    private final List<InitialAdmission> initialAdmissions = new ArrayList<>();
    static final long STATIC_HIGH_WATER_BYTES = HostStaticVariantBudget.GLOBAL_BYTES * 4 / 5;
    static final long STATIC_LOW_WATER_BYTES = HostStaticVariantBudget.GLOBAL_BYTES * 3 / 5;
    static final long STATIC_COLD_NANOS = Duration.ofSeconds(5).toNanos();

    private HostArtifactRepository() {
        this(MinecraftRenderThreadDispatcher.INSTANCE,
                bindings -> RenderSystem.queueFencedTask(() -> HostPreparedArtifact.releaseBindings(
                        Minecraft.getInstance().getTextureManager(), bindings)),
                artifact -> RenderSystem.queueFencedTask(() -> artifact.close(
                        Minecraft.getInstance().getTextureManager())),
                artifact -> artifact.close(Minecraft.getInstance().getTextureManager()),
                (variants, completion) -> RenderSystem.queueFencedTask(() -> {
                    try {
                        HostStaticVariantUpload.closeAll(variants);
                    } finally {
                        completion.run();
                    }
                }));
    }

    HostArtifactRepository(com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher renderThread,
                           Consumer<List<HostPreparedArtifact.CompatibilityTexture>> bindingRetirement,
                           Consumer<HostPreparedArtifact> artifactRetirement,
                           Consumer<HostPreparedArtifact> immediateClose) {
        this(renderThread, bindingRetirement, artifactRetirement, immediateClose, (variants, completion) -> {
            try {
                HostStaticVariantUpload.closeAll(variants);
            } finally {
                completion.run();
            }
        });
    }

    HostArtifactRepository(com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher renderThread,
                           Consumer<List<HostPreparedArtifact.CompatibilityTexture>> bindingRetirement,
                           Consumer<HostPreparedArtifact> artifactRetirement,
                           Consumer<HostPreparedArtifact> immediateClose,
                           StaticVariantRetirement staticVariantRetirement) {
        this.renderThread = Objects.requireNonNull(renderThread, "renderThread");
        this.bindingRetirement = Objects.requireNonNull(bindingRetirement, "bindingRetirement");
        this.artifactRetirement = Objects.requireNonNull(artifactRetirement, "artifactRetirement");
        this.immediateClose = Objects.requireNonNull(immediateClose, "immediateClose");
        this.staticVariantRetirement = Objects.requireNonNull(staticVariantRetirement, "staticVariantRetirement");
    }

    public BackendArtifactLease<HostPreparedArtifact> acquire(ModelDefinition definition,
                                                               StaticModelRenderMetadata metadata) {
        renderThread.assertRenderThread();
        HostPreparedArtifact artifact = HostPreparedArtifact.prepare(definition, metadata);
        live.add(artifact);
        return new BackendArtifactLease<>(artifact, () -> retire(artifact));
    }

    public CompletableFuture<BackendArtifactLease<HostPreparedArtifact>> acquireAsync(
            ModelDefinition definition, StaticModelRenderMetadata metadata, Executor worker,
            DoubleConsumer progress) {
        renderThread.assertRenderThread();
        long requestedGeneration = generation;
        CompletableFuture<HostPreparedArtifact> prepared = CompletableFuture.supplyAsync(
                () -> HostPreparedArtifact.prepare(definition, metadata, progress), worker);
        return prepared.thenCompose(artifact -> {
            CompletableFuture<BackendArtifactLease<HostPreparedArtifact>> published = new CompletableFuture<>();
            renderThread.execute(() -> {
                try {
                    if (generation != requestedGeneration) {
                        immediateClose.accept(artifact);
                        published.completeExceptionally(new java.util.concurrent.CancellationException(
                                "HOST artifact repository generation changed"));
                        return;
                    }
                    live.add(artifact);
                    published.complete(new BackendArtifactLease<>(artifact, () -> retire(artifact)));
                } catch (RuntimeException failure) {
                    immediateClose.accept(artifact);
                    published.completeExceptionally(failure);
                }
            });
            return published;
        });
    }

    public void invalidateBindings() {
        renderThread.assertRenderThread();
        for (HostPreparedArtifact artifact : live) {
            var detached = artifact.detachBindings();
            if (!detached.isEmpty()) bindingRetirement.accept(detached);
            List<HostStaticGeometryVariant> staticVariants = artifact.detachStaticVariants();
            if (!staticVariants.isEmpty()) staticVariantRetirement.retire(staticVariants, () -> {});
        }
    }

    public void close() {
        renderThread.assertRenderThread();
        generation++;
        HostPreparedArtifact[] artifacts = live.toArray(HostPreparedArtifact[]::new);
        live.clear();
        initialAdmissions.clear();
        staticReclaimActive = false;
        // Published artifacts may own GPU variants; retire them behind the render fence.
        for (HostPreparedArtifact artifact : artifacts) artifactRetirement.accept(artifact);
    }

    int liveCount() { return live.size(); }

    public Diagnostics diagnostics() {
        renderThread.assertRenderThread();
        long textureBytes = 0;
        int textureObjects = 0;
        int lightingReady = 0;
        int lightingFallback = 0;
        int lightingSurfaces = 0;
        long lightingOpaqueTriangles = 0;
        int lightingOccupiedVoxels = 0;
        for (HostPreparedArtifact artifact : live) {
            textureBytes = Math.addExact(textureBytes, artifact.bindingResidentBytes());
            textureObjects = Math.addExact(textureObjects, artifact.bindingResidentObjects());
            var lighting = artifact.preparedAsset().lightingAsset();
            if (lighting.ready()) {
                lightingReady++;
                lightingSurfaces = Math.addExact(lightingSurfaces, lighting.diagnostics().surfaces());
                lightingOpaqueTriangles = Math.addExact(lightingOpaqueTriangles,
                        lighting.diagnostics().opaqueTriangles());
                lightingOccupiedVoxels = Math.addExact(lightingOccupiedVoxels,
                        lighting.diagnostics().occupiedVoxels());
            } else {
                lightingFallback++;
            }
        }
        return new Diagnostics(live.size(), textureBytes, textureObjects, lightingReady, lightingFallback,
                lightingSurfaces, lightingOpaqueTriangles, lightingOccupiedVoxels);
    }

    public void touchStatic(HostPreparedArtifact artifact, Object instanceIdentity, long nowNanos) {
        renderThread.assertRenderThread();
        if (live.contains(artifact)) artifact.touchStaticInstance(instanceIdentity, nowNanos);
    }

    public void maintainStaticCache(long nowNanos) {
        renderThread.assertRenderThread();
        long reserved = HostStaticVariantBudget.INSTANCE.diagnostics().reservedBytes();
        if (reserved >= STATIC_HIGH_WATER_BYTES) staticReclaimActive = true;
        if (!staticReclaimActive || staticRetirementPending) return;
        if (reserved <= STATIC_LOW_WATER_BYTES) {
            staticReclaimActive = false;
            return;
        }
        retireOldestColdStaticInstance(null, nowNanos);
    }

    public void requestStaticCapacity(HostPreparedArtifact requestingArtifact, long nowNanos) {
        renderThread.assertRenderThread();
        staticReclaimActive = true;
        if (staticRetirementPending) return;
        if (!retireOldestColdStaticInstance(requestingArtifact, nowNanos)
                && HostStaticVariantBudget.INSTANCE.diagnostics().reservedBytes() >= STATIC_HIGH_WATER_BYTES) {
            retireOldestColdStaticInstance(null, nowNanos);
        }
    }

    HostPreparedArtifact.InitialWorksetStatus requestInitialStaticWorkset(
            HostPreparedArtifact artifact, Object instanceIdentity,
            List<HostPreparedArtifact.InitialStaticRequirement> requirements, long nowNanos) {
        renderThread.assertRenderThread();
        HostPreparedArtifact.InitialWorksetStatus status = artifact.initialStaticWorksetStatus(instanceIdentity);
        if (status == HostPreparedArtifact.InitialWorksetStatus.READY
                || status == HostPreparedArtifact.InitialWorksetStatus.BUILDING
                || status == HostPreparedArtifact.InitialWorksetStatus.FAILED) return status;
        InitialAdmission admission = null;
        for (InitialAdmission candidate : initialAdmissions) {
            if (candidate.instanceIdentity == instanceIdentity) {
                admission = candidate;
                break;
            }
        }
        if (admission == null) {
            admission = new InitialAdmission(artifact, instanceIdentity, requirements);
            initialAdmissions.add(admission);
            artifact.waitForInitialStaticWorkset(instanceIdentity);
        } else {
            admission.artifact = artifact;
            admission.requirements = List.copyOf(requirements);
        }
        if (initialAdmissions.getFirst() != admission) return HostPreparedArtifact.InitialWorksetStatus.WAITING;
        long total = 0;
        for (HostPreparedArtifact.InitialStaticRequirement requirement : admission.requirements) {
            total = Math.addExact(total, requirement.bytes());
        }
        if (total > HostStaticVariantBudget.PER_ARTIFACT_BYTES
                || total > HostStaticVariantBudget.GLOBAL_BYTES) {
            initialAdmissions.removeFirst();
            artifact.rejectInitialStaticWorkset(instanceIdentity);
            return HostPreparedArtifact.InitialWorksetStatus.FAILED;
        }
        HostStaticVariantBudget.BatchReservation reservation =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact,
                        HostPreparedArtifact.uniqueInitialStaticRequirementSizes(admission.requirements));
        if (reservation == null) {
            requestStaticCapacity(artifact, nowNanos);
            return HostPreparedArtifact.InitialWorksetStatus.WAITING;
        }
        initialAdmissions.removeFirst();
        artifact.beginInitialStaticWorkset(instanceIdentity, admission.requirements, reservation);
        return HostPreparedArtifact.InitialWorksetStatus.BUILDING;
    }

    HostPreparedArtifact.InitialWorksetStatus requestStaticWorksetReplacement(
            HostPreparedArtifact artifact, Object instanceIdentity,
            List<HostPreparedArtifact.InitialStaticRequirement> requirements) {
        renderThread.assertRenderThread();
        if (artifact.initialStaticWorksetStatus(instanceIdentity)
                != HostPreparedArtifact.InitialWorksetStatus.READY) {
            return artifact.initialStaticWorksetStatus(instanceIdentity);
        }
        long replacedBytes = artifact.activeStaticWorksetBytes(instanceIdentity);
        if (replacedBytes < 1) return HostPreparedArtifact.InitialWorksetStatus.READY;
        HostStaticVariantBudget.BatchReservation reservation =
                HostStaticVariantBudget.INSTANCE.tryReserveReplacementBatch(artifact,
                        HostPreparedArtifact.uniqueInitialStaticRequirementSizes(requirements), replacedBytes);
        if (reservation == null) return HostPreparedArtifact.InitialWorksetStatus.READY;
        artifact.beginStaticWorksetReplacement(instanceIdentity, requirements, reservation);
        return HostPreparedArtifact.InitialWorksetStatus.REPLACING;
    }

    void retainInitialStaticAdmissions(Set<?> liveInstanceIdentities) {
        renderThread.assertRenderThread();
        initialAdmissions.removeIf(admission -> {
            if (liveInstanceIdentities.contains(admission.instanceIdentity)) return false;
            admission.artifact.cancelWaitingInitialStaticWorkset(admission.instanceIdentity);
            return true;
        });
        for (HostPreparedArtifact artifact : live) {
            List<HostStaticGeometryVariant> cancelled =
                    artifact.cancelInactiveInitialStaticWorksets(liveInstanceIdentities);
            if (!cancelled.isEmpty()) staticVariantRetirement.retire(cancelled, () -> {});
            artifact.retainInitialStaticWorksets(liveInstanceIdentities);
        }
    }

    private boolean retireOldestColdStaticInstance(HostPreparedArtifact scope, long nowNanos) {
        HostPreparedArtifact selectedArtifact = null;
        HostPreparedArtifact.ColdStaticInstance selected = null;
        for (HostPreparedArtifact artifact : live) {
            if (scope != null && artifact != scope) continue;
            HostPreparedArtifact.ColdStaticInstance candidate =
                    artifact.oldestColdStaticInstance(nowNanos, STATIC_COLD_NANOS);
            if (candidate != null && (selected == null
                    || candidate.lastUsedNanos() < selected.lastUsedNanos())) {
                selectedArtifact = artifact;
                selected = candidate;
            }
        }
        if (selectedArtifact == null) return false;
        List<HostStaticGeometryVariant> detached =
                selectedArtifact.detachStaticVariantsForInstance(selected.instanceIdentity());
        if (detached.isEmpty()) return false;
        long retiredBytes = 0;
        for (HostStaticGeometryVariant variant : detached) {
            retiredBytes = Math.addExact(retiredBytes, variant.byteSize());
        }
        HostStaticCacheMetrics.INSTANCE.recordRetired(detached.size(), retiredBytes);
        staticRetirementPending = true;
        staticVariantRetirement.retire(detached, () -> staticRetirementPending = false);
        return true;
    }

    public record Diagnostics(int artifacts, long textureBytes, int textureObjects,
                              int lightingReady, int lightingFallback, int lightingSurfaces,
                              long lightingOpaqueTriangles, int lightingOccupiedVoxels) {}

    @FunctionalInterface
    interface StaticVariantRetirement {
        void retire(List<HostStaticGeometryVariant> variants, Runnable completion);
    }

    private void retire(HostPreparedArtifact artifact) {
        renderThread.execute(() -> {
            if (!live.remove(artifact)) return;
            initialAdmissions.removeIf(admission -> admission.artifact == artifact);
            artifactRetirement.accept(artifact);
        });
    }

    private static final class InitialAdmission {
        private HostPreparedArtifact artifact;
        private final Object instanceIdentity;
        private List<HostPreparedArtifact.InitialStaticRequirement> requirements;

        private InitialAdmission(HostPreparedArtifact artifact, Object instanceIdentity,
                                 List<HostPreparedArtifact.InitialStaticRequirement> requirements) {
            this.artifact = artifact;
            this.instanceIdentity = instanceIdentity;
            this.requirements = List.copyOf(requirements);
        }
    }
}
