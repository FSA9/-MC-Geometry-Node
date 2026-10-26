package com.mine.geometry_node.client.integration.graph;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

/** Reads graph assets for an import; owns temporary files independently of the destination View. */
public final class GraphAssetImports {
    private GraphAssetImports() {}

    public static CompletableFuture<String> read(AssetEntry entry) {
        if (entry.sourceKind() == AssetSourceKind.LOCAL) {
            return CompletableFuture.supplyAsync(() -> readFile(entry.localFile().toPath()));
        }
        return readRemote(entry.path(), Path.of(System.getProperty("java.io.tmpdir"),
                "geometrynode-graph-import", UUID.randomUUID() + ".json"), (request, completion) -> {
            var transfers = ClientAssetTransferService.INSTANCE;
            transfers.onCompletion(transfers.submit(List.of(request)), completion);
        });
    }

    @FunctionalInterface
    interface Downloader {
        void download(ClientAssetTransferRequest request, BiConsumer<AssetTransferJobSnapshot, Throwable> completion);
    }

    static CompletableFuture<String> readRemote(String source, Path temporary, Downloader downloader) {
        var downloaded = new CompletableFuture<Void>();
        try {
            downloader.download(ClientAssetTransferRequest.download(source, temporary,
                    AssetTransferConflictPolicy.OVERWRITE), (job, error) -> {
                if (error != null) downloaded.completeExceptionally(error);
                else if (job == null || job.files().isEmpty() || job.completedFileCount() != job.files().size())
                    downloaded.completeExceptionally(new IllegalStateException("Remote graph download failed"));
                else downloaded.complete(null);
            });
        } catch (RuntimeException error) {
            downloaded.completeExceptionally(error);
        }
        return downloaded.handleAsync((ignored, failure) -> {
            try {
                if (failure != null) throw new CompletionException(failure);
                return readFile(temporary);
            } finally {
                try { Files.deleteIfExists(temporary); } catch (java.io.IOException ignoredError) { }
            }
        });
    }

    private static String readFile(Path path) {
        try { return Files.readString(path); }
        catch (java.io.IOException error) { throw new CompletionException(error); }
    }
}
