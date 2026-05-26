package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.*;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionManager;
import com.mine.geometry_node.client.ui.viewport.interaction.KeyManager;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.client.ui.viewport.visual.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.visual.NodeVisualAdapter;

import java.util.*;

public class ViewportController implements EditorContext.EditorListener,
        InteractionManager.InteractionListener,
        KeyManager.KeyListener {

    private final Viewport mViewport;
    private EditorContext mEditorContext;

    // 移交：由 Controller 独立接管的 Session 状态记忆
    private GraphSession mCurrentSession;

    private static String sClipboardJson = null;

    public ViewportController(Viewport viewport, EditorContext editorContext) {
        this.mViewport = viewport;
        setEditorContext(editorContext);
    }

    /**
     * 【核心改动】由 Controller 全权指挥的蓝图会话绑定与数据层还原管道
     */
    public void bindSession(GraphSession session) {
        // 1. 先保存旧 Session 的相机与选中状态
        saveCurrentSessionState();

        this.mCurrentSession = session;

        if (session != null) {
            // 2. 命令 Viewport 刷新图层画布容器
            mViewport.prepareLayers();

            // 3. 还原相机的位置与缩放
            mViewport.getCamera().setPosition(session.viewportX, session.viewportY);
            mViewport.getCamera().setScale(session.currentScale);

            // 4. 绑定上下文监听
            setEditorContext(session.editorContext);

            // 5. 驱动数据加载，还原所有的 Frame 与 Node 视图
            com.mine.geometry_node.core.node.NodeGraph graph = session.editorContext.getGraph();
            if (graph != null) {
                if (graph.frames != null) {
                    for (com.mine.geometry_node.core.node.FrameData frameData : graph.frames.values()) {
                        onFrameAdded(frameData);
                    }
                }
                if (graph.nodes != null) {
                    for (NodeData data : graph.nodes.values()) {
                        onNodeAdded(data);
                    }
                }
            }

            // 6. 还原选中图元以及视觉连线层
            mViewport.updateSelectionState(session.selectedNodeIds);
            mViewport.rebuildVisualConnections();

            // 7. 同步仿射变换矩阵
            mViewport.updateTransform();
        } else {
            // 卸载数据，命令 Viewport 退回空闲白板状态
            setEditorContext(null);
            mViewport.showEmptyHint();
        }

        mViewport.requestLayout();
        mViewport.invalidate();
    }

    /**
     * 【核心改动】抽取：收集当前的画布相机与选中节点，暂存至会话内存中
     */
    public void saveCurrentSessionState() {
        if (mCurrentSession != null) {
            mCurrentSession.viewportX = mViewport.getCamera().getX();
            mCurrentSession.viewportY = mViewport.getCamera().getY();
            mCurrentSession.currentScale = mViewport.getCamera().getScale();

            mCurrentSession.selectedNodeIds.clear();
            for (NodeVisualAdapter node : mViewport.getSelectedNodeVisuals()) {
                mCurrentSession.selectedNodeIds.add(node.getNodeId());
            }
        }
    }

    public boolean hasActiveSession() {
        return mCurrentSession != null && mCurrentSession.editorContext != null;
    }

    public GraphSession getCurrentSession() {
        return mCurrentSession;
    }

    public void setEditorContext(EditorContext context) {
        if (this.mEditorContext != null) {
            this.mEditorContext.removeListener(this);
        }
        this.mEditorContext = context;
        if (this.mEditorContext != null) {
            this.mEditorContext.addListener(this);
        }
    }

    public void executeAddNode(float screenX, float screenY, String typeId) {
        if (mEditorContext == null) return;
        float uiX = mViewport.getCamera().screenToUIX(screenX);
        float uiY = mViewport.getCamera().screenToUIY(screenY);
        String mockId = UUID.randomUUID().toString();
        NodeData data = new NodeData(mockId, typeId, uiX, uiY);
        CmdAddNode cmd = new CmdAddNode(mEditorContext.getGraphController(), data);
        mEditorContext.getCommandManager().execute(cmd);
    }

    public void executeAddFrame(float uiX, float uiY) {
        if (mEditorContext == null) return;
        com.mine.geometry_node.core.node.FrameData frameData = new com.mine.geometry_node.core.node.FrameData(UUID.randomUUID().toString(), uiX, uiY);
        CmdAddFrame cmd = new CmdAddFrame(mEditorContext.getGraphController(), frameData);
        mEditorContext.getCommandManager().execute(cmd);
    }

    public void executeGroupIntoFrame() {
        if (mEditorContext == null) return;
        List<String> selectedIds = new ArrayList<>();
        for (NodeVisualAdapter node : mViewport.getSelectedNodeVisuals()) {
            selectedIds.add(node.getNodeId());
        }
        if (!selectedIds.isEmpty()) {
            CmdGroupIntoFrame cmd = new CmdGroupIntoFrame(mEditorContext.getGraphController(), selectedIds);
            mEditorContext.getCommandManager().execute(cmd);
        }
    }

    public void executeRenamePort(String nodeId, String category, String portId, String oldName, String newName) {
        if (mEditorContext == null) return;
        CmdRenamePort cmd = new CmdRenamePort(mEditorContext.getGraphController(), nodeId, category, portId, oldName, newName);
        mEditorContext.getCommandManager().execute(cmd);
    }

    // ==========================================
    // InteractionListener 接口实现
    // ==========================================

    @Override
    public void onMoveElements(List<String> nodeIds, List<String> frameIds, float dx, float dy) {
        if (mEditorContext == null) return;
        CmdMoveElements cmdMove = new CmdMoveElements(mEditorContext.getGraphController(), nodeIds, frameIds, dx, dy);
        mEditorContext.getCommandManager().execute(cmdMove);
    }

    @Override
    public void onChangeParent(List<String> elementIds, boolean isNode, String newParentId) {
        if (mEditorContext == null) return;
        CmdChangeParent cmdParent = new CmdChangeParent(mEditorContext.getGraphController(), elementIds, isNode, newParentId);
        mEditorContext.getCommandManager().execute(cmdParent);
    }

    @Override
    public void onConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        if (mEditorContext == null) return;
        CmdConnect cmd = new CmdConnect(mEditorContext.getGraphController(), mEditorContext.getGraph(), outNodeId, outPortId, inNodeId, inPortId);
        mEditorContext.getCommandManager().execute(cmd);
    }

    @Override
    public void onDisconnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        if (mEditorContext == null) return;

        CmdDisconnect cmd = new CmdDisconnect(
                mEditorContext.getGraphController(),
                outNodeId,
                outPortId,
                inNodeId,
                inPortId
        );
        mEditorContext.getCommandManager().execute(cmd);
    }

    @Override
    public boolean isCyclicFrame(String childId, String parentId) {
        if (mEditorContext == null || parentId == null) return false;
        if (childId.equals(parentId)) return true;

        com.mine.geometry_node.core.node.FrameData current = mEditorContext.getGraph().getFrame(parentId);
        while (current != null) {
            if (childId.equals(current.parentFrame)) return true;
            current = mEditorContext.getGraph().getFrame(current.parentFrame);
        }
        return false;
    }

    // ==========================================
    // KeyListener 接口实现
    // ==========================================

    @Override public void onUndo() { if (mEditorContext != null) mEditorContext.getCommandManager().undo(); }
    @Override public void onRedo() { if (mEditorContext != null) mEditorContext.getCommandManager().redo(); }

    @Override
    public void onSaveRequested() {
        mViewport.requestViewportFocus();
        DocumentManager.INSTANCE.saveSession(DocumentManager.INSTANCE.getActiveSession());
    }

    @Override
    public void onCopyRequested() {
        if (mEditorContext == null) return;

        List<NodeVisualAdapter> selectedNodes = mViewport.getSelectedNodeVisuals();
        List<FrameVisualAdapter> selectedFrames = mViewport.getSelectedFrameVisuals();
        if (selectedNodes.isEmpty() && selectedFrames.isEmpty()) return;

        Set<String> copiedFrameIds = new HashSet<>();
        Set<String> copiedNodeIds = new HashSet<>();

        // 1. 录入明面选中的图元
        for (FrameVisualAdapter frame : selectedFrames) copiedFrameIds.add(frame.getFrameId());
        for (NodeVisualAdapter node : selectedNodes) copiedNodeIds.add(node.getNodeId());

        NodeGraph mainGraph = mEditorContext.getGraph();

        // 2. 深层渗透：递归揪出被选中的大图框内部包裹的子图框
        boolean addedNew = true;
        while (addedNew) {
            addedNew = false;
            if (mainGraph.frames != null) {
                for (com.mine.geometry_node.core.node.FrameData f : mainGraph.frames.values()) {
                    if (f.parentFrame != null && copiedFrameIds.contains(f.parentFrame) && !copiedFrameIds.contains(f.id)) {
                        copiedFrameIds.add(f.id);
                        addedNew = true;
                    }
                }
            }
        }

        // 3. 深层渗透：揪出属于这些图框的全部节点
        if (mainGraph.nodes != null) {
            for (NodeData n : mainGraph.nodes.values()) {
                if (n.parentFrame != null && copiedFrameIds.contains(n.parentFrame) && !copiedNodeIds.contains(n.id)) {
                    copiedNodeIds.add(n.id);
                }
            }
        }

        // 4. 将提取的内容打包装箱，序列化存入剪贴板
        NodeGraph tempGraph = new NodeGraph("Clipboard");
        for (String fid : copiedFrameIds) tempGraph.frames.put(fid, mainGraph.getFrame(fid));
        for (String nid : copiedNodeIds) tempGraph.nodes.put(nid, mainGraph.getNode(nid));

        sClipboardJson = GraphJsonIO.toJson(tempGraph);
        System.out.println("Copied " + copiedNodeIds.size() + " nodes and " + copiedFrameIds.size() + " frames.");
    }

    @Override
    public void onPasteRequested(float uiX, float uiY) {
        if (mEditorContext == null || sClipboardJson == null || sClipboardJson.isEmpty()) return;

        CmdPasteElements cmd = new CmdPasteElements(mEditorContext.getGraphController(), sClipboardJson, uiX, uiY);
        mEditorContext.getCommandManager().execute(cmd);

        mViewport.clearSelection(); // 粘贴后清空原有选择，体验更好
        System.out.println("Pasted elements from clipboard.");
    }

    @Override
    public void onDeleteRequested() {
        if (mEditorContext == null) return;
        List<NodeVisualAdapter> selectedNodes = mViewport.getSelectedNodeVisuals();
        List<FrameVisualAdapter> selectedFrames = mViewport.getSelectedFrameVisuals();

        List<String> nodeIdsToRemove = new java.util.ArrayList<>();
        for (NodeVisualAdapter node : selectedNodes) nodeIdsToRemove.add(node.getNodeId());

        List<String> frameIdsToRemove = new java.util.ArrayList<>();
        for (FrameVisualAdapter frame : selectedFrames) frameIdsToRemove.add(frame.getFrameId());

        if (!nodeIdsToRemove.isEmpty()) {
            CmdRemoveNodes cmdN = new CmdRemoveNodes(mEditorContext.getGraphController(), mEditorContext.getGraph(), nodeIdsToRemove);
            mEditorContext.getCommandManager().execute(cmdN);
        }

        if (!frameIdsToRemove.isEmpty()) {
            CmdRemoveFrames cmdF = new CmdRemoveFrames(mEditorContext.getGraphController(), frameIdsToRemove);
            mEditorContext.getCommandManager().execute(cmdF);
        }

        mViewport.clearSelection();
    }

    // ==========================================
    // EditorListener 数据驱动视图更新接口实现
    // ==========================================

    @Override public void onFrameAdded(com.mine.geometry_node.core.node.FrameData frame) { mViewport.addFrameVisual(frame.id, new UIFrame(frame)); }
    @Override public void onFrameRemoved(String frameId) { mViewport.removeFrameVisual(frameId); }
    @Override public void onFrameBoundsUpdated(String frameId, float x, float y, float w, float h) { mViewport.updateFrameBounds(frameId); }
    @Override public void onFrameTitleChanged(String frameId, String newTitle) { mViewport.updateFrameVisual(frameId); }

    @Override
    public void onNodeAdded(NodeData nodeData) {
        NodeDef def = NodeRegistry.INSTANCE.resolveDefinition(nodeData);
        if (def == null) return;
        UINode uiNode = new UINode(mViewport.getContext(), nodeData, def, mEditorContext);
        uiNode.setPreviewPosition(nodeData.getX(), nodeData.getY());
        mViewport.addNodeVisual(nodeData.id, uiNode);
    }
    @Override public void onNodeRemoved(String nodeId) { mViewport.removeNodeVisual(nodeId); mViewport.rebuildVisualConnections(); }
    @Override
    public void onNodeStructureChanged(NodeData nodeData) {
        if (nodeData == null || nodeData.id == null) return;
        boolean wasSelected = mViewport.isNodeSelected(nodeData.id);
        mViewport.removeNodeVisual(nodeData.id);
        onNodeAdded(nodeData);
        if (wasSelected) {
            NodeVisualAdapter rebuilt = mViewport.getNodeVisual(nodeData.id);
            if (rebuilt != null) mViewport.addToSelection(rebuilt);
        }
        mViewport.rebuildVisualConnections();
    }

    @Override public void onGraphConnectionsRebuildRequested() { mViewport.rebuildVisualConnections(); }
    @Override public void onExecutionConnectionAdded(String outN, String outP, String inN, String inP) { mViewport.rebuildVisualConnections(); }
    @Override public void onExecutionConnectionRemoved(String outN, String outP, String inN, String inP) { mViewport.rebuildVisualConnections(); }
    @Override public void onSelectionChanged(List<String> selectedNodeIds) { mViewport.updateSelectionState(selectedNodeIds); }
    @Override public void onNodeMoved(String nodeId, float x, float y) { mViewport.updateNodePosition(nodeId, x, y); mViewport.updateConnectionsForNode(nodeId); }
    @Override public void onConnectionAdded(String outN, String outP, String inN, String inP) { mViewport.notifyNodeLayoutUpdate(outN); mViewport.notifyNodeLayoutUpdate(inN); mViewport.rebuildVisualConnections(); }
    @Override public void onConnectionRemoved(String outN, String outP, String inN, String inP) { mViewport.notifyNodeLayoutUpdate(outN); mViewport.notifyNodeLayoutUpdate(inN); mViewport.rebuildVisualConnections(); }
}
