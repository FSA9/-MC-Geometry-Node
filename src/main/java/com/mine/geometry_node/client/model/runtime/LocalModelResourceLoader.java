package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.gpu.ModelGpuPreparationService;
import com.mine.geometry_node.client.model.gpu.ModelGpuRepository;
import com.mine.geometry_node.client.model.debug.ModelLoadProgressTracker;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mine.geometry_node.core.engine.system.model.importer.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;

import java.nio.file.Files;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

public final class LocalModelResourceLoader implements ModelResourceCoordinator.Loader {
    private static final ModelImportBudget IMPORT_BUDGET = ModelImportBudget.LOCAL_PREVIEW;

    private final Executor worker;
    private final ModelImporterRegistry importers;
    private final ModelGpuPreparationService preparation;
    private final ModelGpuRepository repository;

    public LocalModelResourceLoader(Executor worker, ModelImporterRegistry importers,
                                    ModelGpuPreparationService preparation, ModelGpuRepository repository) {
        this.worker = worker;
        this.importers = importers;
        this.preparation = preparation;
        this.repository = repository;
    }

    @Override
    public CompletableFuture<LoadedModelResource> load(LocalModelAssetRequest request,
                                                        ModelResourceCoordinator.Cancellation cancellation) {
        long started = System.nanoTime();
        return CompletableFuture.supplyAsync(() -> readAndImport(request, cancellation), worker)
                .thenApply(imported -> finish(imported, started, cancellation));
    }

    private Imported readAndImport(LocalModelAssetRequest request, ModelResourceCoordinator.Cancellation cancellation) {
        try {
            ModelLoadProgressTracker.update(request.path(), "Reading", 0.08);
            if (!request.path().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".glb")) {
                throw new IllegalArgumentException("local model loader only accepts .glb files");
            }
            if (request.sourceSize() > IMPORT_BUDGET.maxSourceBytes()) {
                throw new IllegalArgumentException("model source is " + request.sourceSize()
                        + " bytes; limit is " + IMPORT_BUDGET.maxSourceBytes() + " bytes");
            }
            cancelled(cancellation);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = readSource(request, cancellation, digest);
            ModelLoadProgressTracker.update(request.path(), "Importing", 0.28);
            cancelled(cancellation);
            long sizeAfter = Files.size(request.path());
            long modifiedAfter = Files.getLastModifiedTime(request.path()).toMillis();
            if (sizeAfter != request.sourceSize() || modifiedAfter != request.sourceLastModified()) {
                throw new IllegalStateException("model file changed while it was being read; retry the load");
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.LOCAL, "", request.path().toString(),
                    new ModelAssetRevision(bytes.length, modifiedAfter, hash));
            List<ModelImportDiagnostic> diagnostics = new ArrayList<>();
            ModelImportResult result = importers.importModel("geometry_node:glb",
                    new ModelImportSource(asset, bytes), new ModelImportContext(
                            IMPORT_BUDGET, cancellation::isCancelled, diagnostics::add));
            if (result instanceof ModelImportResult.Failure failed) {
                throw new ModelResourceLoadException(failed.failure());
            }
            logDiagnostics(asset, diagnostics);
            ModelDefinition definition = ((ModelImportResult.Success) result).definition();
            ModelLoadProgressTracker.update(request.path(), "Metadata", 0.58);
            StaticModelRenderMetadata metadata = StaticModelRenderMetadata.from(definition, cancellation::isCancelled);
            validateRenderableTransforms(metadata, cancellation);
            long triangles = triangleCount(definition, cancellation);
            ModelLoadProgressTracker.update(request.path(), "Waiting for HOST", 0.68);
            return new Imported(definition, metadata, bytes.length, triangles);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime : new CompletionException(exception);
        }
    }

    private static byte[] readSource(LocalModelAssetRequest request,
                                     ModelResourceCoordinator.Cancellation cancellation,
                                     MessageDigest digest) throws Exception {
        byte[] bytes = new byte[Math.toIntExact(request.sourceSize())];
        int cursor = 0;
        try (InputStream input = Files.newInputStream(request.path())) {
            while (cursor < bytes.length) {
                cancelled(cancellation);
                int read = input.read(bytes, cursor, Math.min(64 * 1024, bytes.length - cursor));
                if (read < 0) throw new IllegalStateException("model file ended while it was being read; retry the load");
                digest.update(bytes, cursor, read);
                cursor += read;
            }
            if (input.read() >= 0) throw new IllegalStateException("model file grew while it was being read; retry the load");
        }
        return bytes;
    }

    private static void logDiagnostics(ModelAssetReference asset, List<ModelImportDiagnostic> diagnostics) {
        Map<String, DiagnosticGroup> groups = new LinkedHashMap<>();
        for (ModelImportDiagnostic diagnostic : diagnostics) {
            groups.computeIfAbsent(diagnostic.code(), DiagnosticGroup::new).add(diagnostic);
            GeometryNode.LOGGER.debug("Model import diagnostic asset={} stage=IMPORT code={} location={} message={}",
                    asset.cacheIdentity(), diagnostic.code(), diagnostic.location(), diagnostic.message());
        }
        for (DiagnosticGroup group : groups.values()) {
            GeometryNode.LOGGER.warn("Model import diagnostics asset={} stage=IMPORT code={} count={} sampleLocations={}",
                    asset.cacheIdentity(), group.code, group.count, group.samples);
        }
    }

    private static void validateRenderableTransforms(StaticModelRenderMetadata metadata,
                                                     ModelResourceCoordinator.Cancellation cancellation) {
        for (int index = 0; index < metadata.nodeCount(); index++) {
            cancelled(cancellation);
            if (metadata.nodeDrawable(index) && !ModelTransformMath.isRenderable(metadata.nodeWorldTransform(index))) {
                throw new IllegalArgumentException("node " + index
                        + " has a singular composed transform that cannot render correct normals");
            }
        }
    }

    private static long triangleCount(ModelDefinition definition,
                                      ModelResourceCoordinator.Cancellation cancellation) {
        long triangles = 0L;
        for (var mesh : definition.meshes()) {
            cancelled(cancellation);
            for (var primitive : mesh.primitives()) triangles = Math.addExact(triangles, primitive.triangleCount());
        }
        return triangles;
    }

    private LoadedModelResource finish(Imported imported, long started,
                                       ModelResourceCoordinator.Cancellation cancellation) {
        cancelled(cancellation);
        ModelCancellationSource gpuCancellation = new ModelCancellationSource();
        return new LoadedModelResource(imported.definition(),
                () -> preparation.prepare(imported.definition(), gpuCancellation::isCancelled)
                        .thenCompose(repository::acquire),
                gpuCancellation::cancel,
                imported.metadata(), imported.sourceBytes(), imported.triangles(), System.nanoTime() - started);
    }

    private static void cancelled(ModelResourceCoordinator.Cancellation cancellation) {
        if (cancellation.isCancelled()) throw new CancellationException("model resource load was cancelled");
    }

    private record Imported(ModelDefinition definition, StaticModelRenderMetadata metadata,
                            long sourceBytes, long triangles) {}

    private static final class DiagnosticGroup {
        private static final int SAMPLE_LIMIT = 3;
        private final String code;
        private final List<String> samples = new ArrayList<>(SAMPLE_LIMIT);
        private int count;

        private DiagnosticGroup(String code) { this.code = code; }

        private void add(ModelImportDiagnostic diagnostic) {
            count++;
            if (samples.size() < SAMPLE_LIMIT && !samples.contains(diagnostic.location())) {
                samples.add(diagnostic.location());
            }
        }
    }
}
