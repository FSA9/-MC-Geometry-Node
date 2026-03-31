package com.mine.geometry_node.client.ui.Viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdAddNode;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;

import java.util.List;
import java.util.UUID;

/**
 * 视口控制器 (Controller)
 * <p>
 * 职责：
 * 1. 监听底层数据 (EditorContext) 的变化。
 * 2. 负责 UINode 视图的实例化与生命周期管理。
 * 3. 调度 Viewport 进行纯粹的 UI 更新。
 * 4. 接收来自 Viewport 的交互意图并转化为数据命令 (Command)。
 */
public class ViewportController implements EditorContext.EditorListener {

    private final Viewport mViewport;
    private final EditorContext mEditorContext;

    public ViewportController(Viewport viewport, EditorContext editorContext) {
        this.mViewport = viewport;
        this.mEditorContext = editorContext;
        // 注册监听器，由控制器来监听数据变化
        this.mEditorContext.addListener(this);
    }

    // ==========================================
    // 视图到数据的反向调度 (View -> Controller -> Data)
    // ==========================================

    /**
     * 处理添加节点的意图 (由 Viewport/Menu 触发)
     */
    public void executeAddNode(float screenX, float screenY, String typeId) {
        float uiX = mViewport.screenToUIX(screenX);
        float uiY = mViewport.screenToUIY(screenY);
        String mockId = UUID.randomUUID().toString();
        NodeData data = new NodeData(mockId, typeId, uiX, uiY);

        CmdAddNode cmd = new CmdAddNode(mEditorContext.getGraphController(), data);
        mEditorContext.getCommandManager().execute(cmd);
    }

    // ==========================================
    // 数据到视图的正向驱动 (Data -> Controller -> View)
    // ==========================================

    @Override
    public void onNodeAdded(NodeData nodeData) {
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        if (def == null) return;

        // 实例化 UINode 视图对象
        UINode uiNode = new UINode(mViewport.getContext(), nodeData, def, mEditorContext);
        uiNode.setTranslationX(nodeData.getX());
        uiNode.setTranslationY(nodeData.getY());

        // 通知 Viewport 将其添加到画布
        mViewport.addNodeView(nodeData.id, uiNode);
    }

    @Override
    public void onNodeRemoved(String nodeId) {
        mViewport.removeNodeView(nodeId);
    }

    @Override
    public void onNodeStructureChanged(NodeData nodeData) {
        if (nodeData == null || nodeData.id == null) return;

        // 记录结构变化前的选中状态
        boolean wasSelected = mViewport.isNodeSelected(nodeData.id);

        // 移除旧节点视图
        mViewport.removeNodeView(nodeData.id);

        // 重新添加新节点视图
        onNodeAdded(nodeData);

        // 恢复选中状态
        if (wasSelected) {
            UINode rebuilt = mViewport.getNodeView(nodeData.id);
            if (rebuilt != null) {
                mViewport.addToSelection(rebuilt);
            }
        }
    }

    @Override
    public void onGraphConnectionsRebuildRequested() {
        mViewport.invalidate();
    }

    @Override
    public void onExecutionConnectionAdded(String outNodeId, String outPortId, String inNodeId) {
        mViewport.invalidate();
    }

    @Override
    public void onExecutionConnectionRemoved(String outNodeId, String outPortId, String inNodeId) {
        mViewport.invalidate();
    }

    @Override
    public void onSelectionChanged(List<String> selectedNodeIds) {
        mViewport.updateSelectionState(selectedNodeIds);
    }

    @Override
    public void onNodeMoved(String nodeId, float x, float y) {
        mViewport.updateNodePosition(nodeId, x, y);
    }

    @Override
    public void onConnectionAdded(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        mViewport.notifyNodeLayoutUpdate(outNodeId);
        mViewport.notifyNodeLayoutUpdate(inNodeId);
        mViewport.invalidate();
    }

    @Override
    public void onConnectionRemoved(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        mViewport.notifyNodeLayoutUpdate(outNodeId);
        mViewport.notifyNodeLayoutUpdate(inNodeId);
        mViewport.invalidate();
    }
}