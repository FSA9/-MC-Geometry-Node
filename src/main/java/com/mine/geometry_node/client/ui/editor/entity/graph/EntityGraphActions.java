package com.mine.geometry_node.client.ui.editor.entity.graph;

import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.graph.persistence.LocalDraftManager;
import com.mine.geometry_node.client.graph.persistence.file.GraphFileReference;
import com.mine.geometry_node.client.graph.persistence.file.GraphFileRegistry;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.document.DocumentManager;
import com.mine.geometry_node.client.ui.document.GraphSession;
import com.mine.geometry_node.client.ui.editor.asset.service.GraphAssetService;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskController;
import com.mine.geometry_node.client.ui.editor.graph.properties.session.GraphSessionPropertiesTarget;
import com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.GraphPropertiesPanel;
import com.mine.geometry_node.client.ui.overlay.OverlayCloseReason;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.graph.storage.GraphAssetId;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Bound graph UI only; entity binding/apply policy belongs to the caller. */
public final class EntityGraphActions {
    private EntityGraphActions() {}

    /**
     * Must be called on the ModernUI thread with an attached MainUI anchor.
     * Completion runs once on that thread, with the canonical graph id, after a real diagram
     * is bound. Failure/cancellation does not call completion. It is a readiness notification,
     * never permission to discard entity edits or a signal that publishing finished.
     *
     * The parent remains mounted. The shell owns this as its child modal: closing the parent
     * also closes this sidecar. Edit sessions remain in DocumentManager after either closes.
     * To keep editing/publishing in the sidecar, leave the parent open until the sidecar closes;
     * to reveal the workspace instead, run the parent's guard/apply after readiness, then close.
     */
    public static void open(View anchor, String graphId, boolean edit, Consumer<String> completion) {
        Objects.requireNonNull(anchor, "anchor");
        var services = MainUiServices.require(anchor);
        Window window = new Window(anchor, graphId, edit, completion);
        services.layerManager().showModal(window, new ModalOptions(false, true, anchor));
    }

    static String tr(String key, Object... args) {
        return Component.translatable("geometry_node.entity_editor.graph." + key, args).getString();
    }

    private static final class Window extends ModalWindowView {
        private final AssetTaskController tasks = new AssetTaskController(this);
        private final GraphAssetService graphs = new GraphAssetService();
        private final Consumer<String> completion;
        private final Object connection = Minecraft.getInstance().getConnection();
        private final TextView status;
        private final LinearLayout body;
        private final UiActionButton openEditor;
        private final UiActionButton save;
        private final UiActionButton publish;
        private String graphId;
        private Path draft;
        private GraphFileReference reference;
        private GraphSession session;
        private EntityGraphViewport viewport;
        private GraphPropertiesPanel properties;
        private boolean edit;
        private boolean alive = true;
        private boolean ready;
        private boolean busy;
        private boolean local;
        private final Runnable documentsChanged = this::checkDocument;

        Window(View anchor, String rawId, boolean edit, Consumer<String> completion) {
            super(anchor.getContext(), tr(edit ? "edit_title" : "view_title"));
            this.edit = edit;
            this.completion = completion;
            setPreferredSizeDp(1100, 720);
            body = new LinearLayout(getContext());
            body.setOrientation(LinearLayout.VERTICAL);
            status = UIUtils.createLockedTextView(getContext(), tr("loading"), 12,
                    UiThemes.EDITOR.text().normal());
            body.addView(status, new LinearLayout.LayoutParams(-1, -2));
            setContent(body);
            LinearLayout actions = new LinearLayout(getContext());
            openEditor = button(actions, "open_editor", this::enableEditing);
            save = button(actions, "save_draft", this::saveDraft);
            publish = button(actions, "publish", this::publish);
            button(actions, "close", this::requestClose);
            setActions(actions);
            try {
                graphId = GraphAssetId.require(rawId);
                draft = LocalDraftManager.resolveDraftPath(graphId);
            } catch (RuntimeException invalid) {
                status.setText(tr("invalid_id"));
            }
            updateActions();
        }

        private UiActionButton button(LinearLayout row, String key, Runnable action) {
            UiActionButton button = new UiActionButton(getContext(), tr(key),
                    UiActionButton.Role.SECONDARY, UiActionButton.Density.NORMAL);
            button.setOnClickListener(v -> action.run());
            row.addView(button, new LinearLayout.LayoutParams(-2, -1));
            return button;
        }

        @Override protected void onWindowShown() {
            if (draft == null) return;
            DocumentManager.INSTANCE.addOnTabChangedListener(documentsChanged);
            // Resolve the registered identity on the IO queue; never perform path IO on the UI.
            tasks.runSilent(context -> GraphFileRegistry.INSTANCE.reference(draft), resolved -> {
                reference = resolved;
                GraphSession existing = DocumentManager.INSTANCE.findSession(reference);
                if (existing != null) {
                    local = true;
                    bind(existing);
                } else {
                    tasks.runSilent(context -> Files.exists(draft), exists -> {
                        local = exists;
                        if (exists) load();
                        else download();
                    }, failure -> fail("load_failed"));
                }
            }, failure -> fail("load_failed"));
        }

        private boolean sameConnection() {
            return connection != null && Minecraft.getInstance().getConnection() == connection;
        }

        private void download() {
            if (!sameConnection()) { fail("connection_changed"); return; }
            status.setText(tr("downloading", graphId));
            try {
                var transfers = ClientAssetTransferService.INSTANCE;
                var job = transfers.submit(List.of(ClientAssetTransferRequest.download(graphId, draft,
                        AssetTransferConflictPolicy.FAIL_IF_EXISTS)));
                transfers.onCompletion(job, (result, error) -> MuiModApi.postToUiThread(() -> {
                    if (!alive) return;
                    if (!sameConnection()) { fail("connection_changed"); return; }
                    if (!EntityGraphTransfer.succeeded(result, error)) { fail("download_failed"); return; }
                    load();
                }));
            } catch (RuntimeException failure) { fail("download_failed"); }
        }

        private void load() {
            // Another opener may have registered the draft during the download/existence check.
            GraphSession existing = DocumentManager.INSTANCE.findSession(reference);
            if (existing != null) { local = true; bind(existing); return; }
            tasks.runSilent(context -> graphs.loadGraphSession(draft.toFile(), context), this::bind, failure -> {
                GraphSession concurrent = DocumentManager.INSTANCE.findSession(reference);
                if (concurrent != null) { local = true; bind(concurrent); }
                else fail("load_failed");
            });
        }

        private void bind(GraphSession loaded) {
            if (!alive) { loaded.close(); return; }
            GraphSession existing = DocumentManager.INSTANCE.findSession(loaded.fileReference());
            if (existing != null && existing != loaded) { loaded.close(); loaded = existing; }
            session = loaded;
            if (edit) DocumentManager.INSTANCE.openSession(session);
            viewport = new EntityGraphViewport(getContext(), edit, this::saveDraft);
            viewport.getController().bindSession(session);
            body.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
            ready = true;
            if (edit) installProperties();
            status.setText(tr(local ? "local_draft" : "ready", graphId));
            updateActions();
            viewport.post(viewport::fitGraph);
            if (completion != null) completion.accept(graphId);
        }

        private void enableEditing() {
            if (!ready || busy) return;
            DocumentManager.INSTANCE.openSession(session);
            edit = true;
            viewport.setEditing(true);
            installProperties();
            setTitle(tr("edit_title"));
            updateActions();
        }

        private void installProperties() {
            if (properties != null) return;
            // Share the actual document target with the standard graph properties editor.
            properties = new GraphPropertiesPanel(getContext());
            properties.bind(new GraphSessionPropertiesTarget(session));
            body.removeView(viewport);
            LinearLayout editor = new LinearLayout(getContext());
            editor.setOrientation(LinearLayout.HORIZONTAL);
            editor.addView(viewport, new LinearLayout.LayoutParams(0, -1, 1));
            editor.addView(properties, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(280), -1));
            body.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        private void saveDraft() {
            if (!canWrite()) return;
            properties.commitPendingEdits();
            if (!session.editorContext.getCommandManager().canSave()) return;
            busy = true;
            updateActions();
            status.setText(tr("saving"));
            EntityGraphTransfer.save(session, result -> {
                if (!alive) return;
                busy = false;
                status.setText(tr(result ? "saved" : "save_failed"));
                updateActions();
            });
        }

        private boolean canWrite() {
            return alive && ready && edit && !busy && session != null
                    && DocumentManager.INSTANCE.getSessions().contains(session)
                    && !session.fileReference().isDeleted();
        }

        private void publish() {
            if (!canWrite()) return;
            if (!sameConnection()) { fail("connection_changed"); return; }
            properties.commitPendingEdits();
            if (!session.editorContext.getCommandManager().canSave()) return;
            busy = true;
            updateActions();
            status.setText(tr("publishing", graphId));
            // Publish is an explicit overwrite of this binding's server asset, even if the local
            // draft was renamed. Capture now; subsequent edits remain dirty and are never lost.
            EntityGraphTransfer.publish(session, graphId, this::sameConnection, outcome -> {
                if (!alive) return;
                busy = false;
                status.setText(tr(switch (outcome) {
                    case COMPLETE -> "published";
                    case WARNING -> "published_warning";
                    case FAILED -> "publish_failed";
                }, graphId));
                updateActions();
            });
        }

        private void checkDocument() {
            if (session == null || !ready) return;
            if (session.fileReference().isDeleted()
                    || (edit && !DocumentManager.INSTANCE.getSessions().contains(session))) {
                ready = false;
                viewport.getController().bindSession(null);
                if (properties != null) properties.bind(null);
                status.setText(tr("document_closed"));
            }
            updateActions();
        }

        private void fail(String key) { status.setText(tr(key)); updateActions(); }

        private void updateActions() {
            openEditor.setEnabled(ready && !edit && !busy);
            save.setEnabled(ready && edit && !busy);
            publish.setEnabled(ready && edit && !busy);
        }

        @Override public boolean canClose(OverlayCloseReason reason) {
            if (ready && edit && properties != null) properties.commitPendingEdits();
            // A snapshot already handed to the transfer service outlives this modal safely.
            return true;
        }

        @Override protected void onWindowDestroyed() {
            alive = false;
            tasks.deactivate();
            DocumentManager.INSTANCE.removeOnTabChangedListener(documentsChanged);
            if (properties != null) properties.bind(null);
            if (viewport != null) {
                viewport.deactivateLifecycle();
                viewport.getController().dispose();
            }
            // Never close somebody else's document or discard dirty edits. Preview-only claims
            // are owned by this modal until promoted with Open in editor.
            if (session != null && !DocumentManager.INSTANCE.getSessions().contains(session)) session.close();
        }
    }
}
