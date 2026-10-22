package com.mine.geometry_node.client.ui.editor.entity.graph;

import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.graph.persistence.GraphJsonIO;
import com.mine.geometry_node.client.graph.persistence.file.GraphDocumentStore;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.document.edit.history.GraphHistorySnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import icyllis.modernui.mc.MuiModApi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Detached document snapshots and short-lived upload staging, never an asset cache. */
final class EntityGraphTransfer {
    private static final AssetTransferIoExecutor IO =
            new AssetTransferIoExecutor("GeometryNode-EntityGraph-IO", 1, 16);

    enum Outcome { COMPLETE, WARNING, FAILED }

    private EntityGraphTransfer() {}

    static boolean succeeded(AssetTransferJobSnapshot job, Throwable failure) {
        return failure == null && job != null && !job.files().isEmpty()
                && job.isTerminal() && job.completedFileCount() == job.files().size();
    }

    static Outcome outcome(AssetTransferJobSnapshot job, Throwable failure) {
        if (!succeeded(job, failure)) return Outcome.FAILED;
        return job.files().stream().anyMatch(file -> file.failure() != null)
                ? Outcome.WARNING : Outcome.COMPLETE;
    }

    static GraphHistorySnapshot capture(GraphSession session) {
        return session.editorContext.getCommandManager().prepareEdit(
                () -> GraphHistorySnapshot.capture(session.editorContext.getGraph(), null));
    }

    static void save(GraphSession session, Consumer<Boolean> completion) {
        try {
            long revision = session.revision();
            GraphHistorySnapshot snapshot = capture(session);
            IO.run(() -> GraphDocumentStore.INSTANCE.writeStringAtomic(session.fileReference(),
                    GraphJsonIO.toJson(snapshot.restore()))).whenComplete((ignored, error) ->
                    MuiModApi.postToUiThread(() -> {
                        if (error == null) markSavedIfCurrent(session, revision);
                        completion.accept(error == null);
                    }));
        } catch (RuntimeException failure) { completion.accept(false); }
    }

    /** A successful older save must never clear edits made while disk IO was queued. */
    static boolean markSavedIfCurrent(GraphSession session, long revision) {
        var commands = session.editorContext.getCommandManager();
        if (!DocumentManager.INSTANCE.getSessions().contains(session)
                || session.fileReference().isDeleted() || !commands.canSave()
                || session.revision() != revision) return false;
        commands.markSaved();
        DocumentManager.INSTANCE.notifyTabChanged();
        return true;
    }

    static Path stage(GraphHistorySnapshot snapshot) throws Exception {
        Path temporary = Files.createTempFile("geometrynode-entity-graph-", ".json");
        try {
            Files.writeString(temporary, GraphJsonIO.toJson(snapshot.restore()));
            return temporary;
        } catch (Throwable failure) {
            Files.deleteIfExists(temporary);
            throw failure;
        }
    }

    static void publish(GraphSession session, String graphId, BooleanSupplier sameConnection,
                        Consumer<Outcome> completion) {
        try {
            GraphHistorySnapshot snapshot = capture(session);
            IO.submit(() -> stage(snapshot)).whenComplete((temporary, error) -> MuiModApi.postToUiThread(() -> {
                if (error != null) { completion.accept(Outcome.FAILED); return; }
                if (!sameConnection.getAsBoolean()) {
                    cleanup(temporary);
                    completion.accept(Outcome.FAILED);
                    return;
                }
                try {
                    var transfers = ClientAssetTransferService.INSTANCE;
                    var job = transfers.submit(List.of(ClientAssetTransferRequest.upload(temporary, graphId,
                            AssetTransferConflictPolicy.OVERWRITE)));
                    transfers.onCompletion(job, (result, failure) -> {
                        // Cleanup is independent of modal lifetime, including disconnect/timeout.
                        cleanup(temporary);
                        MuiModApi.postToUiThread(() -> completion.accept(outcome(result, failure)));
                    });
                } catch (RuntimeException failure) {
                    cleanup(temporary);
                    completion.accept(Outcome.FAILED);
                }
            }));
        } catch (RuntimeException failure) { completion.accept(Outcome.FAILED); }
    }

    private static void cleanup(Path path) {
        IO.run(() -> Files.deleteIfExists(path));
    }
}
