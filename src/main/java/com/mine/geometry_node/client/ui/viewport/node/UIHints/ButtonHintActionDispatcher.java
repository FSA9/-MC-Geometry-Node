package com.mine.geometry_node.client.ui.viewport.node.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.preview.EditorPreviewController;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ShopEditorOverlay;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.nodes.dialogue.OpenShop;
import com.mine.geometry_node.core.node.nodes.dialogue.ShowDialoguePage;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.view.View;
import org.jetbrains.annotations.Nullable;

public final class ButtonHintActionDispatcher {
    public static final String ACTION_NONE = "";
    public static final String ACTION_OPEN_SHOP_EDITOR = "open_shop_editor";

    private ButtonHintActionDispatcher() {
    }

    public static void dispatch(@Nullable EditorContext editorContext,
                                @Nullable NodeData nodeData,
                                @Nullable PortRow row,
                                @Nullable String action,
                                @Nullable View anchor) {
        String safeAction = action == null ? ACTION_NONE : action.trim();
        if (safeAction.isEmpty()) {
            return;
        }

        if (ACTION_OPEN_SHOP_EDITOR.equals(safeAction)) {
            ShopEditorOverlay.show(anchor, editorContext, nodeData, row);
            return;
        }
        if (ShowDialoguePage.ACTION_PREVIEW.equals(safeAction)) {
            EditorPreviewController.previewDialogue(nodeData);
            return;
        }
        if (OpenShop.ACTION_PREVIEW.equals(safeAction)) {
            EditorPreviewController.previewShop(nodeData);
            return;
        }

        System.out.println("[ButtonHint] Unhandled button action: " + safeAction);
    }
}
