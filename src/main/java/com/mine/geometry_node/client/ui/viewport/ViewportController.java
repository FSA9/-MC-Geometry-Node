// --- START OF FILE ViewportController.java ---
package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdAddNode;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;

import java.util.List;
import java.util.UUID;

public class ViewportController implements EditorContext.EditorListener {

    private final Viewport mViewport;
    private EditorContext mEditorContext;

    public ViewportController(Viewport viewport, EditorContext editorContext) {
        this.mViewport = viewport;
        // 核心修复：使用 setter 方法安全地初始化
        setEditorContext(editorContext);
    }

    public void setEditorContext(EditorContext context) {
        // 1. 如果有旧的上下文，先解绑监听，防止内存泄漏和鬼影事件
        if (this.mEditorContext != null) {
            this.mEditorContext.removeListener(this);
        }

        this.mEditorContext = context;

        // 2. 绑定新上下文（只在非白板模式下绑定）
        if (this.mEditorContext != null) {
            this.mEditorContext.addListener(this);
        }
    }

    public void executeAddNode(float screenX, float screenY, String typeId) {
        // 防御：白板模式下禁止操作
        if (mEditorContext == null) return;

        // --- 修复：通过 Camera 进行坐标转换 ---
        float uiX = mViewport.getCamera().screenToUIX(screenX);
        float uiY = mViewport.getCamera().screenToUIY(screenY);

        String mockId = UUID.randomUUID().toString();
        NodeData data = new NodeData(mockId, typeId, uiX, uiY);

        CmdAddNode cmd = new CmdAddNode(mEditorContext.getGraphController(), data);
        mEditorContext.getCommandManager().execute(cmd);
    }

    @Override
    public void onNodeAdded(NodeData nodeData) {
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        if (def == null) return;
        UINode uiNode = new UINode(mViewport.getContext(), nodeData, def, mEditorContext);

        // 注意：这里目前保持 setTranslationX 不变，等我们进行 UINode 性能优化时再统一改
        uiNode.setTranslationX(nodeData.getX());
        uiNode.setTranslationY(nodeData.getY());

        mViewport.addNodeView(nodeData.id, uiNode);
    }

    @Override
    public void onNodeRemoved(String nodeId) {
        mViewport.removeNodeView(nodeId);
        mViewport.rebuildVisualConnections();
    }

    @Override
    public void onNodeStructureChanged(NodeData nodeData) {
        if (nodeData == null || nodeData.id == null) return;
        boolean wasSelected = mViewport.isNodeSelected(nodeData.id);
        mViewport.removeNodeView(nodeData.id);
        onNodeAdded(nodeData);
        if (wasSelected) {
            UINode rebuilt = mViewport.getNodeView(nodeData.id);
            if (rebuilt != null) {
                mViewport.addToSelection(rebuilt);
            }
        }
        mViewport.rebuildVisualConnections();
    }

    @Override
    public void onGraphConnectionsRebuildRequested() {
        mViewport.rebuildVisualConnections();
    }

    @Override
    public void onExecutionConnectionAdded(String outNodeId, String outPortId, String inNodeId) {
        mViewport.rebuildVisualConnections();
    }

    @Override
    public void onExecutionConnectionRemoved(String outNodeId, String outPortId, String inNodeId) {
        mViewport.rebuildVisualConnections();
    }

    @Override
    public void onSelectionChanged(List<String> selectedNodeIds) {
        mViewport.updateSelectionState(selectedNodeIds);
    }

    @Override
    public void onNodeMoved(String nodeId, float x, float y) {
        mViewport.updateNodePosition(nodeId, x, y);
        mViewport.updateConnectionsForNode(nodeId);
    }

    @Override
    public void onConnectionAdded(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        mViewport.notifyNodeLayoutUpdate(outNodeId);
        mViewport.notifyNodeLayoutUpdate(inNodeId);
        mViewport.rebuildVisualConnections();
    }

    @Override
    public void onConnectionRemoved(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        mViewport.notifyNodeLayoutUpdate(outNodeId);
        mViewport.notifyNodeLayoutUpdate(inNodeId);
        mViewport.rebuildVisualConnections();
    }
}
// --- END OF FILE ViewportController.java ---