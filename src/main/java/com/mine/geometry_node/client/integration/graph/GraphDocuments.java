package com.mine.geometry_node.client.integration.graph;

import com.mine.geometry_node.client.feature.asset.task.AssetTask;
import com.mine.geometry_node.client.feature.asset.task.AssetTaskContext;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.feature.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.feature.graph.document.GraphDocumentLoader;
import com.mine.geometry_node.client.feature.graph.document.GraphDocumentOpenController;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.feature.graph.persistence.LocalDraftManager;
import com.mine.geometry_node.client.feature.graph.persistence.file.GraphFileRegistry;
import com.mine.geometry_node.client.ui.components.integration.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.editor.graph.frame.FrameBoundsCalculator;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.ViewGroup;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Concrete graph layout, owner scheduling and transfer assembly for editor hosts. */
public final class GraphDocuments {
    private GraphDocuments() {}

    public static GraphDocumentLoader loader() {
        return new GraphDocumentLoader(FrameBoundsCalculator::computeCommittedBounds);
    }

    public static GraphSession loadGraphSession(File file, AssetTaskContext context) throws Exception {
        return loader().loadGraphSession(file, context);
    }

    /** Uses the caller's task lifecycle so cancellation and detached-owner cleanup stay shared. */
    public static void openLocal(File file, AssetTaskController tasks) {
        var documents = DocumentManager.INSTANCE;
        var reference = GraphFileRegistry.INSTANCE.reference(file.toPath());
        GraphSession existing = documents.findSession(reference);
        if (existing != null) {
            documents.openSession(existing);
            return;
        }
        tasks.run("打开图纸", context -> loadGraphSession(file, context), null, (session, progress) -> {
            GraphSession registered = documents.findSession(session.fileReference());
            if (registered != null && registered != session) {
                session.close();
                documents.openSession(registered);
            } else {
                documents.openSession(session);
            }
            progress.requestClose();
        }, (failure, progress) -> {
            // Another owner may have registered this file while our load was queued.
            // Its live document (including unsaved edits) wins over another disk load.
            GraphSession registered = documents.findSession(reference);
            if (registered != null) {
                documents.openSession(registered);
                progress.requestClose();
            } else {
                failure.printStackTrace();
                progress.fail(failure.getMessage() == null || failure.getMessage().isEmpty()
                        ? "操作失败" : failure.getMessage());
            }
        });
    }

    public static GraphDocumentOpenController openController(ViewGroup owner, BooleanSupplier destinationAvailable,
                                                              Consumer<GraphSession> openDestination) {
        return new GraphDocumentOpenController(new OwnerBackend(Objects.requireNonNull(owner, "owner")),
                loader(), destinationAvailable, openDestination);
    }

    private static final class OwnerBackend implements GraphDocumentOpenController.Backend {
        private final AssetTaskController tasks;

        OwnerBackend(ViewGroup owner) { tasks = new AssetTaskController(owner); }
        @Override public Object connection() { return Minecraft.getInstance().getConnection(); }
        @Override public Path resolveDraftPath(String graphId) { return LocalDraftManager.resolveDraftPath(graphId); }
        @Override public <T> void runSilent(AssetTask<T> task, Consumer<T> success, Consumer<Exception> failure) {
            tasks.runSilent(task, success, failure);
        }
        @Override public void download(ClientAssetTransferRequest request,
                                       BiConsumer<AssetTransferJobSnapshot, Throwable> completion) {
            var transfers = ClientAssetTransferService.INSTANCE;
            var job = transfers.submit(List.of(request));
            transfers.onCompletion(job, (result, failure) ->
                    MuiModApi.postToUiThread(() -> completion.accept(result, failure)));
        }
        @Override public void deactivate() { tasks.deactivate(); }
    }
}
