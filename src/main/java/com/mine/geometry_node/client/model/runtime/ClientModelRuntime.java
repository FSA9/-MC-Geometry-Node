package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.ModelGpuPreparationService;
import com.mine.geometry_node.client.model.gpu.ModelGpuRepository;
import com.mine.geometry_node.client.model.gpu.ModelUploadScheduler;
import com.mine.geometry_node.client.model.gpu.minecraft.*;
import com.mine.geometry_node.client.model.render.backend.host.entity.HostArtifactRepository;
import com.mine.geometry_node.client.model.render.backend.host.light.diagnostics.HostLocalLightDiagnostics;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.HostWorldLightCaptureBudget;
import com.mine.geometry_node.client.model.render.backend.host.light.instance.HostLocalLightRepository;
import com.mine.geometry_node.client.model.render.backend.host.light.solve.HostLightingExecutor;
import com.mine.geometry_node.client.model.render.backend.host.light.solve.HostLightingMemoryBudget;
import com.mine.geometry_node.client.model.debug.ModelLoadProgressTracker;
import com.mine.geometry_node.core.engine.system.model.importer.BuiltinModelImporters;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client model composition root. The preview command is only an adapter over the shared M5 registry. */
public final class ClientModelRuntime {
    public static final ClientModelRuntime INSTANCE = new ClientModelRuntime();
    public static final ModelInstanceId PREVIEW_INSTANCE_ID = new ModelInstanceId("geometry_node:local_preview");

    private final ExecutorService workers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "geometry-node-model-loader");
        thread.setDaemon(true);
        return thread;
    });
    private ModelGpuRepository gpuRepository;
    private ModelUploadScheduler uploadScheduler;
    private ModelResourceCoordinator resources;
    private ClientModelInstanceRegistry instances;
    private HostLocalLightRepository localLights;
    private HostLightingExecutor lightingExecutor;
    private HostLightingMemoryBudget lightingMemory;
    private HostWorldLightCaptureBudget lightCaptureBudget;
    private int drawCalls;
    private long lastRenderCpuNanos;
    private long lastGpuNanos;
    private long submittedTriangles;
    private int singularTransformSkips;
    private int candidateDraws = -1;
    private int culledDraws = -1;
    private long submittedVertices = -1;
    private ModelFrameBenchmark benchmark;

    private ClientModelRuntime() { rebuild(); }

    public void load(Path path, ModelInstancePlacement placement) {
        Objects.requireNonNull(path, "path");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        instances().upsertLocal(PREVIEW_INSTANCE_ID, path,
                ModelInstanceState.preview(new ModelDimensionId(
                        minecraft.level.dimension().identifier().toString()), placement));
    }

    public void closeModel() { instances().remove(PREVIEW_INSTANCE_ID); }

    public synchronized ClientModelInstanceRegistry instances() { return instances; }

    public synchronized int resourceCount() { return resources.entryCount(); }

    public ExecutorService modelWorkers() { return workers; }
    public synchronized ModelUploadScheduler uploadScheduler() { return uploadScheduler; }

    public synchronized HostLocalLightRepository localLights() { return localLights; }
    public synchronized HostLightingExecutor lightingExecutor() { return lightingExecutor; }
    public synchronized long lightingSession() { return lightingExecutor.session(); }
    public synchronized HostLightingMemoryBudget lightingMemory() { return lightingMemory; }
    public synchronized HostWorldLightCaptureBudget lightCaptureBudget() { return lightCaptureBudget; }
    public synchronized HostLocalLightDiagnostics localLightDiagnostics() { return localLights.diagnostics(); }

    public synchronized com.mine.geometry_node.client.model.gpu.ModelGpuRepositoryDiagnostics gpuDiagnostics() {
        return gpuRepository.diagnostics();
    }

    public synchronized FrameDiagnostics frameDiagnostics() {
        return new FrameDiagnostics(drawCalls, submittedTriangles, singularTransformSkips,
                lastRenderCpuNanos, lastGpuNanos, candidateDraws, culledDraws, submittedVertices);
    }

    public synchronized void beginBenchmark(String asset, int instances, int warmupFrames, int measuredFrames) {
        benchmark = new ModelFrameBenchmark(asset, instances, warmupFrames, measuredFrames);
    }

    public synchronized ModelFrameBenchmark.Snapshot benchmarkSnapshot() {
        return benchmark == null ? null : benchmark.snapshot();
    }

    public synchronized void cancelBenchmark() { benchmark = null; }

    public synchronized void resetGpuBackend() {
        lightingExecutor.close();
        instances.close();
        resources.close();
        HostArtifactRepository.INSTANCE.close();
        ModelLoadProgressTracker.clear();
        gpuRepository.close();
        uploadScheduler.close();
        rebuild();
        drawCalls = 0;
        lastRenderCpuNanos = 0;
        lastGpuNanos = 0;
        submittedTriangles = 0;
        singularTransformSkips = 0;
        candidateDraws = -1;
        culledDraws = -1;
        submittedVertices = -1;
        benchmark = null;
    }

    public synchronized LocalModelStatus status() {
        ClientModelInstanceRegistry.InstanceStatus status = instances.status(PREVIEW_INSTANCE_ID);
        LoadedModelResource resource = status.resource();
        return new LocalModelStatus(status.state(), status.path(), status.failure(),
                resource == null ? 0 : resource.sourceBytes(), resource == null ? 0 : resource.triangles(),
                drawCalls, submittedTriangles, singularTransformSkips,
                resource == null ? 0 : resource.loadNanos(), lastRenderCpuNanos, lastGpuNanos);
    }

    public synchronized void recordFrame(int drawCalls, long submittedTriangles, int singularTransformSkips,
                                         long cpuNanos, long gpuNanos) {
        recordFrame(drawCalls, submittedTriangles, singularTransformSkips, cpuNanos, gpuNanos, -1, -1, -1);
    }

    public synchronized void pumpUploads() { uploadScheduler.pump(); }

    public synchronized ModelUploadScheduler.Diagnostics uploadDiagnostics() {
        return uploadScheduler.diagnostics();
    }

    public synchronized void recordFrame(int drawCalls, long submittedTriangles, int singularTransformSkips,
                                         long cpuNanos, long gpuNanos,
                                         int candidateDraws, int culledDraws, long submittedVertices) {
        this.drawCalls = drawCalls;
        this.submittedTriangles = submittedTriangles;
        this.singularTransformSkips = singularTransformSkips;
        this.lastRenderCpuNanos = cpuNanos;
        this.candidateDraws = candidateDraws;
        this.culledDraws = culledDraws;
        this.submittedVertices = submittedVertices;
        if (gpuNanos >= 0) {
            this.lastGpuNanos = gpuNanos;
            if (benchmark != null) benchmark.recordGpu(gpuNanos);
        } else if (benchmark != null) {
            benchmark.recordCpu(cpuNanos, drawCalls, submittedTriangles);
        }
    }

    private void rebuild() {
        localLights = new HostLocalLightRepository(MinecraftRenderThreadDispatcher.INSTANCE,
                (field, completion) -> {
                    try { field.close(); }
                    finally { completion.run(); }
                });
        lightingExecutor = new HostLightingExecutor("geometry-node-local-light", 2, 64);
        lightingMemory = new HostLightingMemoryBudget(32L << 20, 64L << 20, 96L << 20);
        lightCaptureBudget = new HostWorldLightCaptureBudget(32_768);
        uploadScheduler = new ModelUploadScheduler(MinecraftRenderThreadDispatcher.INSTANCE);
        gpuRepository = new ModelGpuRepository(new MinecraftModelGpuDevice(),
                MinecraftRenderThreadDispatcher.INSTANCE, uploadScheduler);
        ModelGpuPreparationService preparation = new ModelGpuPreparationService(workers, new NativeImageModelDecoder());
        resources = new ModelResourceCoordinator(new LocalModelResourceLoader(
                workers, BuiltinModelImporters.createRegistry(), preparation, gpuRepository));
        instances = new ClientModelInstanceRegistry(resources, MinecraftRenderThreadDispatcher.INSTANCE,
                (resource, path) -> {
                    ModelLoadProgressTracker.update(path, "Preparing HOST", 0.70);
                    return resource.prepareBackendArtifactAsync(HostArtifactRepository.KEY,
                                    () -> HostArtifactRepository.INSTANCE.acquireAsync(resource.definition(),
                                            resource.metadata(), workers, fraction ->
                                                    ModelLoadProgressTracker.update(path, "Preparing HOST",
                                                            0.70 + fraction * 0.28)))
                            .thenApply(ignored -> null);
                }, new ClientModelInstanceRegistry.InstanceLifecycle() {
                    @Override public void removed(ModelInstanceId id) {
                        localLights.remove(id);
                        lightingExecutor.cancel(id);
                    }
                    @Override public void changed(ModelInstanceId id, ModelInstanceState previous,
                                                  ModelInstanceState current) {
                        localLights.remove(id);
                        lightingExecutor.cancel(id);
                    }
                    @Override public void cleared() {
                        localLights.close();
                        lightingExecutor.cancelAll();
                    }
                });
    }

    public record FrameDiagnostics(int drawCalls, long submittedTriangles, int singularTransformSkips,
                                   long renderCpuNanos, long gpuNanos,
                                   int candidateDraws, int culledDraws, long submittedVertices) {}
}
