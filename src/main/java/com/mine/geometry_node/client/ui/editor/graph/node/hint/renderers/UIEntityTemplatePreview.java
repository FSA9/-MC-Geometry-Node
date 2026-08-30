package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.components.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.client.ui.editor.graph.picker.EntityTemplatePickerController;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;

/** Editor adapter for the reusable entity-template preview. */
public final class UIEntityTemplatePreview extends EntityTemplatePreviewView
        implements ViewportScaledHint, ViewportTransformedHint, InteractiveHintTarget {
    private final NodeData mNodeData;
    private final String mPortId;
    private final EditorContext mEditorContext;

    public UIEntityTemplatePreview(Context context, NodeData nodeData, String portId, EditorContext editorContext) {
        this(context, nodeData, portId, editorContext, RotationMode.HORIZONTAL);
    }

    public UIEntityTemplatePreview(
            Context context,
            NodeData nodeData,
            String portId,
            EditorContext editorContext,
            RotationMode rotationMode
    ) {
        super(context, rotationMode);
        mNodeData = nodeData;
        mPortId = portId;
        mEditorContext = editorContext;
        setEditable(nodeData != null && editorContext != null);
        refreshDisplayValue();
    }

    @Override
    protected void refreshDisplayValue() {
        if (mNodeData != null) {
            applyTemplate(mNodeData.inputs.get(mPortId));
        }
    }

    @Override
    protected void onOpenEditorRequested() {
        if (mNodeData == null || mEditorContext == null) return;
        EntityTemplatePickerController.open(this::commitTemplate, this::requestFocus);
    }

    @Override
    protected void commitTemplate(EntityTemplateValue template) {
        if (template == null || template.isEmpty()) return;
        if (dispatchDisplayPaste(template)) return;
        UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, template.toMap());
        invalidateDisplayValue();
        refreshDisplayValue();
    }
}
