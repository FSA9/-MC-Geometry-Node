package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.ModelGpuPreparationService;
import com.mine.geometry_node.client.model.gpu.ModelGpuRepository;
import com.mine.geometry_node.client.model.gpu.minecraft.*;
import com.mine.geometry_node.client.model.render.backend.host.entity.HostArtifactRepository;
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
    private ModelResourceCoordinator resources;
    private ClientModelInstanceRegistry instances;
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
        instances.close();
        resources.close();
        HostArtifactRepository.INSTANCE.close();
        gpuRepository.close();
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
        gpuRepository = new ModelGpuRepository(new MinecraftModelGpuDevice(), MinecraftRenderThreadDispatcher.INSTANCE);
        ModelGpuPreparationService preparation = new ModelGpuPreparationService(workers, new NativeImageModelDecoder());
        resources = new ModelResourceCoordinator(new LocalModelResourceLoader(
                workers, BuiltinModelImporters.createRegistry(), preparation, gpuRepository));
        instances = new ClientModelInstanceRegistry(resources, MinecraftRenderThreadDispatcher.INSTANCE);
    }

    public record FrameDiagnostics(int drawCalls, long submittedTriangles, int singularTransformSkips,
                                   long renderCpuNanos, long gpuNanos,
                                   int candidateDraws, int culledDraws, long submittedVertices) {}
}
