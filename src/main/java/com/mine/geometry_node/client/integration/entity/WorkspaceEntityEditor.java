package com.mine.geometry_node.client.integration.entity;

import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerDialog;
import com.mine.geometry_node.client.ui.components.integration.asset.AssetPickerOptions;
import com.mine.geometry_node.client.ui.components.integration.entity.editor.EntityEditorDialog;
import com.mine.geometry_node.client.ui.components.integration.entity.editor.graph.EntityGraphActions;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.graph.document.GraphDocumentOpenController;
import com.mine.geometry_node.client.integration.graph.GraphDocuments;
import com.mine.geometry_node.client.ui.editor.graph.GraphEditorWindow;
import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.core.engine.graph.GraphType;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;
import com.mine.geometry_node.core.engine.system.asset.type.AssetTypeCatalog;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Connects the reusable entity dialog to this workspace's asset picker and graph editors. */
public final class WorkspaceEntityEditor {
    private WorkspaceEntityEditor() {}

    public static void show(View anchor, WorldObjectEntry entry, Runnable onApplied) {
        EntityEditorDialog.show(anchor, entry, onApplied, GraphActions::new);
    }

    static EntityGraphActions.Selection selection(AssetEntry entry) {
        if (entry == null || entry.sourceKind() != AssetSourceKind.REMOTE || entry.isDirectory()
                || !AssetTypeCatalog.GRAPH_TYPE_ID.equals(entry.metadata().typeId())) return null;
        GraphType type = GraphTypeRegistry.INSTANCE.get(entry.metadata().variantId());
        if (type == null) return null;
        try {
            AssetRepositoryPath path = AssetRepositoryPath.parse(entry.path(), false);
            if (!path.isCommon()) return null;
            return new EntityGraphActions.Selection(new GraphBindingKey(type.runtimeKind(), path.serialize()),
                    entry.name(), type.id(), List.of());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static final class GraphActions implements EntityGraphActions {
        private final ViewGroup owner;
        private final UiTheme theme;
        private final GraphDocumentOpenController documents;
        private boolean closed;

        private GraphActions(ViewGroup owner, UiTheme theme) {
            this.owner = owner;
            this.theme = theme;
            documents = GraphDocuments.openController(owner, () -> graphEditor() != null, this::open);
        }

        @Override
        public void chooseRemote(View anchor, String title, Consumer<Selection> selected) {
            if (closed) return;
            AssetPickerOptions options = new AssetPickerOptions(AssetPickerOptions.Sources.REMOTE,
                    AssetPickerOptions.Target.FILE, entry -> selection(entry) != null, null);
            AssetPickerDialog.show(anchor, title, options, entry -> {
                if (closed) return;
                Selection result = selection(entry);
                if (result != null) selected.accept(result);
            }, theme);
        }

        @Override
        public void openRemote(String id, Consumer<Outcome> completion) {
            if (closed) return;
            documents.openRemote(id, outcome -> {
                if (!closed) completion.accept(switch (outcome) {
                    case OPENED -> Outcome.OPENED;
                    case NO_EDITOR -> Outcome.NO_EDITOR;
                    case INVALID_ID -> Outcome.INVALID_ID;
                    case CONNECTION_CHANGED -> Outcome.CONNECTION_CHANGED;
                    case LOAD_FAILED -> Outcome.LOAD_FAILED;
                    case DOWNLOAD_FAILED -> Outcome.DOWNLOAD_FAILED;
                });
            });
        }

        private GraphEditorWindow graphEditor() {
            if (closed) return null;
            var services = MainUiServices.find(owner);
            if (services == null || services.isClosed()) return null;
            return UiSurfaceRegistry.INSTANCE.leases(UiSurfaceType.VIEWPORT, GraphEditorWindow.class).stream()
                    .filter(editor -> editor.visible() && editor.isCurrent()
                            && MainUiServices.find(editor.owner()) == services)
                    .max(Comparator.comparingLong(UiSurfaceRegistry.Lease::interactionSerial))
                    .map(UiSurfaceRegistry.Lease::owner).orElse(null);
        }

        private void open(GraphSession session) {
            GraphEditorWindow editor = graphEditor();
            if (editor == null) throw new IllegalStateException("Graph editor destination is unavailable");
            editor.openGraphSession(session);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            documents.close();
        }
    }
}
