package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.ModelGpuLease;
import com.mine.geometry_node.client.model.gpu.ModelGpuPreparationService;
import com.mine.geometry_node.client.model.gpu.ModelGpuRepository;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mine.geometry_node.core.engine.system.model.importer.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.*;

public final class LocalModelResourceLoader implements ModelResourceCoordinator.Loader {
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
                .thenCompose(imported -> {
                    cancelled(cancellation);
                    return preparation.prepare(imported.definition()).thenApply(plan -> new Prepared(imported, plan));
                })
                .thenCompose(prepared -> {
                    cancelled(cancellation);
                    return repository.acquire(prepared.plan()).thenApply(lease -> finish(prepared, lease, started, cancellation));
                });
    }

    private Imported readAndImport(LocalModelAssetRequest request, ModelResourceCoordinator.Cancellation cancellation) {
        try {
            if (!request.path().getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".glb")) {
                throw new IllegalArgumentException("local model loader only accepts .glb files");
            }
            if (request.sourceSize() > ModelImportBudget.DEFAULT.maxSourceBytes()) {
                throw new IllegalArgumentException("model exceeds source byte budget");
            }
            cancelled(cancellation);
            byte[] bytes = Files.readAllBytes(request.path());
            cancelled(cancellation);
            long sizeAfter = Files.size(request.path());
            long modifiedAfter = Files.getLastModifiedTime(request.path()).toMillis();
            if (sizeAfter != request.sourceSize() || modifiedAfter != request.sourceLastModified()) {
                throw new IllegalStateException("model file changed while it was being read; retry the load");
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.LOCAL, "", request.path().toString(),
                    new ModelAssetRevision(bytes.length, modifiedAfter, hash));
            ModelImportResult result = importers.importModel("geometry_node:glb",
                    new ModelImportSource(asset, bytes), new ModelImportContext(
                            ModelImportBudget.DEFAULT, cancellation::isCancelled, null));
            if (result instanceof ModelImportResult.Failure failed) {
                throw new IllegalArgumentException(failed.failure().location() + ": " + failed.failure().message());
            }
            ModelDefinition definition = ((ModelImportResult.Success) result).definition();
            StaticModelRenderMetadata metadata = StaticModelRenderMetadata.from(definition);
            validateRenderableTransforms(metadata);
            long triangles = definition.meshes().stream().flatMap(mesh -> mesh.primitives().stream())
                    .mapToLong(primitive -> primitive.triangleCount()).sum();
            return new Imported(definition, metadata, bytes.length, triangles);
        } catch (Exception exception) {
            throw exception instanceof RuntimeException runtime ? runtime : new CompletionException(exception);
        }
    }

    private static void validateRenderableTransforms(StaticModelRenderMetadata metadata) {
        for (int index = 0; index < metadata.nodeCount(); index++) {
            if (metadata.nodeDrawable(index) && !ModelTransformMath.isRenderable(metadata.nodeWorldTransform(index))) {
                throw new IllegalArgumentException("node " + index
                        + " has a singular composed transform that cannot render correct normals");
            }
        }
    }

    private static LoadedModelResource finish(Prepared prepared, ModelGpuLease lease, long started,
                                               ModelResourceCoordinator.Cancellation cancellation) {
        if (cancellation.isCancelled()) {
            lease.close();
            throw new CancellationException("model resource load was cancelled");
        }
        return new LoadedModelResource(prepared.imported().definition(), lease,
                prepared.imported().metadata(),
                prepared.imported().sourceBytes(), prepared.imported().triangles(), System.nanoTime() - started);
    }

    private static void cancelled(ModelResourceCoordinator.Cancellation cancellation) {
        if (cancellation.isCancelled()) throw new CancellationException("model resource load was cancelled");
    }

    private record Imported(ModelDefinition definition, StaticModelRenderMetadata metadata,
                            long sourceBytes, long triangles) {}
    private record Prepared(Imported imported, com.mine.geometry_node.client.model.gpu.ModelGpuUploadPlan plan) {}
}
