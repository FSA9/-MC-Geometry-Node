package com.mine.geometry_node.client.feature.graph.document;

import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileReference;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileRegistry;
import com.mine.geometry_node.client.feature.asset.task.AssetTask;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Opens remote graphs in a caller-owned destination without presenting UI.
 * Construct, call and close on the editor owner thread; close when the owner is disposed.
 * Only the latest request may open a document or report an outcome. Superseded requests
 * and requests abandoned by close have no completion callback. Downloads may finish on disk.
 */
public final class GraphDocumentOpenController implements AutoCloseable {
    public enum Outcome {
        OPENED, NO_EDITOR, INVALID_ID, CONNECTION_CHANGED, LOAD_FAILED, DOWNLOAD_FAILED
    }

    private final Backend backend;
    private final BooleanSupplier destinationAvailable;
    private final Consumer<GraphSession> openDestination;
    private final GraphDocumentLoader graphs;
    private final GraphDocumentManager documents = GraphDocumentManager.INSTANCE;
    private long generation;
    private boolean closed;

    public GraphDocumentOpenController(Backend backend, GraphDocumentLoader graphs,
                                       BooleanSupplier destinationAvailable, Consumer<GraphSession> openDestination) {
        this.graphs = Objects.requireNonNull(graphs, "graphs");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.destinationAvailable = Objects.requireNonNull(destinationAvailable, "destinationAvailable");
        this.openDestination = Objects.requireNonNull(openDestination, "openDestination");
    }

    public void openRemote(String rawId, Consumer<Outcome> completion) {
        long requestGeneration = ++generation;
        if (closed) return;
        // An unattached page must be able to report NO_EDITOR without touching Minecraft.
        if (!destinationAvailable.getAsBoolean()) {
            if (completion != null) completion.accept(Outcome.NO_EDITOR);
            return;
        }
        Operation operation = new Operation(requestGeneration, backend.connection(), completion);
        if (!validate(operation)) return;
        try {
            operation.graphId = GraphAssetId.require(rawId);
        } catch (RuntimeException invalid) {
            finish(operation, Outcome.INVALID_ID);
            return;
        }
        backend.runSilent(context -> {
            Path draft = backend.resolveDraftPath(operation.graphId);
            return GraphFileRegistry.INSTANCE.reference(draft);
        }, reference -> {
            if (!validate(operation)) return;
            operation.reference = reference;
            operation.draft = reference.path();
            if (openExisting(operation)) return;
            backend.runSilent(context -> Files.exists(operation.draft), exists -> {
                if (!validate(operation) || openExisting(operation)) return;
                if (exists) load(operation);
                else download(operation);
            }, failure -> fail(operation, Outcome.LOAD_FAILED));
        }, failure -> fail(operation, Outcome.LOAD_FAILED));
    }

    private void download(Operation operation) {
        if (!validate(operation) || openExisting(operation)) return;
        try {
            backend.download(ClientAssetTransferRequest.download(operation.graphId, operation.draft,
                    AssetTransferConflictPolicy.FAIL_IF_EXISTS), (result, failure) -> {
                if (!validate(operation) || openExisting(operation)) return;
                if (succeeded(result, failure)) load(operation);
                else downloadFailed(operation);
            });
        } catch (RuntimeException failure) {
            if (operation.finished) throw failure;
            downloadFailed(operation);
        }
    }

    private void downloadFailed(Operation operation) {
        if (!validate(operation) || openExisting(operation)) return;
        // A competing download/save may have created the draft after our existence check.
        // FAIL_IF_EXISTS protects that file; use it rather than retrying with overwrite.
        backend.runSilent(context -> Files.exists(operation.draft), exists -> {
            if (!validate(operation) || openExisting(operation)) return;
            if (exists) load(operation);
            else finish(operation, Outcome.DOWNLOAD_FAILED);
        }, failure -> fail(operation, Outcome.DOWNLOAD_FAILED));
    }

    private void load(Operation operation) {
        if (!validate(operation) || openExisting(operation)) return;
        backend.runSilent(context -> graphs.loadGraphSession(operation.draft.toFile(), context), loaded -> {
            try {
                if (!validate(operation)) return;
                GraphSession existing = documents.findSession(loaded.fileReference());
                if (existing != null && existing != loaded) {
                    open(operation, existing);
                } else {
                    open(operation, loaded);
                }
            } finally {
                // Validation and outcome observers may throw too. Until registration,
                // the loaded session and its file claim remain owned by this callback.
                closeAbandoned(loaded);
            }
        }, failure -> {
            if (validate(operation) && !openExisting(operation)) finish(operation, Outcome.LOAD_FAILED);
        });
    }

    private boolean openExisting(Operation operation) {
        GraphSession existing = documents.findSession(operation.reference);
        if (existing == null) return false;
        open(operation, existing);
        return true;
    }

    private void open(Operation operation, GraphSession session) {
        if (!validate(operation)) {
            closeAbandoned(session);
            return;
        }
        if (session.fileReference().isDeleted()) {
            closeAbandoned(session);
            finish(operation, Outcome.LOAD_FAILED);
            return;
        }
        // A route can throw or dispose its owner while registering/focusing the document.
        // Only an unregistered newly loaded session remains ours to release.
        try {
            openDestination.accept(session);
        } catch (RuntimeException failure) {
            closeAbandoned(session);
            fail(operation, Outcome.LOAD_FAILED);
            return;
        }
        if (!validate(operation)) {
            closeAbandoned(session);
            return;
        }
        finish(operation, Outcome.OPENED);
    }

    private void closeAbandoned(GraphSession session) {
        if (!documents.getSessions().contains(session)) session.close();
    }

    private boolean validate(Operation operation) {
        if (closed || operation.generation != generation || operation.finished) return false;
        if (operation.connection == null || backend.connection() != operation.connection) {
            finish(operation, Outcome.CONNECTION_CHANGED);
            return false;
        }
        if (!destinationAvailable.getAsBoolean()) {
            finish(operation, Outcome.NO_EDITOR);
            return false;
        }
        return true;
    }

    private void fail(Operation operation, Outcome outcome) {
        if (validate(operation)) finish(operation, outcome);
    }

    private void finish(Operation operation, Outcome outcome) {
        if (closed || operation.generation != generation || operation.finished) return;
        operation.finished = true;
        if (operation.completion != null) operation.completion.accept(outcome);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        generation++;
        backend.deactivate();
    }

    static boolean succeeded(AssetTransferJobSnapshot job, Throwable failure) {
        return failure == null && job != null && !job.files().isEmpty()
                && job.isTerminal() && job.completedFileCount() == job.files().size();
    }

    private static final class Operation {
        final long generation;
        final Object connection;
        final Consumer<Outcome> completion;
        String graphId;
        Path draft;
        GraphFileReference reference;
        boolean finished;

        Operation(long generation, Object connection, Consumer<Outcome> completion) {
            this.generation = generation;
            this.connection = connection;
            this.completion = completion;
        }
    }

    /** Scheduling/connection seam; document ownership and file loading stay real in tests. */
    public interface Backend {
        Object connection();
        Path resolveDraftPath(String graphId);
        <T> void runSilent(AssetTask<T> task, Consumer<T> success, Consumer<Exception> failure);
        void download(ClientAssetTransferRequest request, BiConsumer<AssetTransferJobSnapshot, Throwable> completion);
        void deactivate();
    }

}
