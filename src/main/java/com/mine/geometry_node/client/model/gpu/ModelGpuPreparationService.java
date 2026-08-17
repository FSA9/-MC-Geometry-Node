package com.mine.geometry_node.client.model.gpu;

import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mine.geometry_node.core.engine.system.model.domain.ModelImageSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class ModelGpuPreparationService {
    private final Executor workerExecutor;
    private final ModelImageDecoder imageDecoder;
    private final ModelGpuUploadPlanner planner;

    public ModelGpuPreparationService(Executor workerExecutor, ModelImageDecoder imageDecoder) {
        this(workerExecutor, imageDecoder, new ModelGpuUploadPlanner());
    }

    ModelGpuPreparationService(Executor workerExecutor, ModelImageDecoder imageDecoder, ModelGpuUploadPlanner planner) {
        this.workerExecutor = Objects.requireNonNull(workerExecutor, "workerExecutor");
        this.imageDecoder = Objects.requireNonNull(imageDecoder, "imageDecoder");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public CompletableFuture<ModelGpuUploadPlan> prepare(ModelDefinition definition) {
        return prepare(definition, () -> false);
    }

    public CompletableFuture<ModelGpuUploadPlan> prepare(ModelDefinition definition, BooleanSupplier cancellation) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(cancellation, "cancellation");
        return CompletableFuture.supplyAsync(() -> {
            cancelled(cancellation);
            List<DecodedModelImage> images = decodeImages(definition.images(), cancellation);
            cancelled(cancellation);
            return planner.plan(definition, images, cancellation);
        }, workerExecutor);
    }

    private List<DecodedModelImage> decodeImages(List<ModelImageSource> sources, BooleanSupplier cancellation) {
        List<DecodedModelImage> decoded = new ArrayList<>(sources.size());
        for (int index = 0; index < sources.size(); index++) {
            cancelled(cancellation);
            ModelImageSource source = sources.get(index);
            try {
                DecodedModelImage image = imageDecoder.decode(source);
                if (image.width() != source.width() || image.height() != source.height()) {
                    throw new IOException("decoded dimensions differ from validated image header at index " + index);
                }
                decoded.add(image);
            } catch (IOException exception) {
                throw new CompletionException("failed to decode model image " + index, exception);
            }
        }
        return List.copyOf(decoded);
    }

    static void cancelled(BooleanSupplier cancellation) {
        if (cancellation.getAsBoolean()) throw new CancellationException("model GPU preparation was cancelled");
    }
}
