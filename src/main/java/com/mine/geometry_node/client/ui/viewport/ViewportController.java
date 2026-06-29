package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.*;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionId;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionSink;
import com.mine.geometry_node.client.ui.viewport.connection.ConnectionLayer;
import com.mine.geometry_node.client.ui.viewport.frame.UIFrame;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionManager;
import com.mine.geometry_node.client.ui.viewport.node.UINode;
import com.mine.geometry_node.client.ui.viewport.node.UIRerouteNode;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.group.GroupNodeFactory;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.reroute.RerouteNodeSupport;
import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;

import java.util.*;

public class ViewportController implements EditorContext.EditorListener,
        InteractionManager.InteractionListener,
        ViewportActionSink {

    private final Viewport mViewport;
    private EditorContext mEditorContext;

    private GraphSession mCurrentSession;

    private static String sClipboardJson = null;

    public ViewportController(Viewport viewport, EditorContext editorContext) {
        this.mViewport = viewport;
        setEditorContext(editorContext);
    }

    public void bindSession(GraphSession session) {
        saveCurrentSessionState();

        this.mCurrentSession = session;

        if (session != null) {
            mViewport.prepareLayers();

            mViewport.getCamera().setPosition(session.viewportX, session.viewportY);
            mViewport.getCamera().setScale(session.currentScale);

            setEditorContext(session.editorContext);

            com.mine.geometry_node.core.node.NodeGraph graph = session.editorContext.getCurrentGraph();
            rebuildScopeVisuals(graph);

            mViewport.updateSelectionState(session.selectedNodeIds, session.selectedFrameIds);
            mViewport.rebuildVisualConnections(graph);

            mViewport.updateTransform();
        } else {
            setEditorContext(null);
            mViewport.showEmptyHint();
        }

        mViewport.requestLayout();
        mViewport.invalidate();
    }

    public void saveCurrentSessionState() {
        if (mCurrentSession != null) {
            mCurrentSession.viewportX = mViewport.getCamera().getX();
            mCurrentSession.viewportY = mViewport.getCamera().getY();
            mCurrentSession.currentScale = mViewport.getCamera().getScale();

            if (mEditorContext == null || !mEditorContext.isInsideGroupScope()) {
                mViewport.syncSelectionToSession(mCurrentSession.selectedNodeIds, mCurrentSession.selectedFrameIds);
            } else {
                mCurrentSession.selectedNodeIds.clear();
                mCurrentSession.selectedFrameIds.clear();
            }
        }
    }

    public boolean hasActiveSession() {
        return mCurrentSession != null && mCurrentSession.editorContext != null;
    }

    public GraphSession getCurrentSession() {
        return mCurrentSession;
    }

    public boolean isInsideGroupScope() {
        return mEditorContext != null && mEditorContext.isInsideGroupScope();
    }

    public boolean canConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        return mEditorContext != null
                && mEditorContext.getGraphController().canConnectPorts(outNodeId, outPortId, inNodeId, inPortId);
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

    public void executeEnterGroup(String nodeId) {
        if (mEditorContext == null || nodeId == null) return;
        NodeData node = mEditorContext.getCurrentGraph().getNode(nodeId);
        if (node == null || !node.isGroupNode()) return;

        if (mEditorContext.enterGroupScope(node)) {
            mEditorContext.getCommandManager().clearHistory();
            mViewport.clearSelection();
            mViewport.prepareLayers();
            rebuildScopeVisuals(mEditorContext.getCurrentGraph());
            rebuildVisualConnections();
            mViewport.getCamera().setPosition(mViewport.getWidth() / 2.0f, mViewport.getHeight() / 2.0f);
            mViewport.updateTransform();
            mViewport.invalidate();
        }
    }

    public void executeExitGroup() {
        if (mEditorContext == null || !mEditorContext.isInsideGroupScope()) return;

        if (mEditorContext.exitGroupScope()) {
            mEditorContext.getCommandManager().clearHistory();
            mViewport.clearSelection();
            mViewport.prepareLayers();
            rebuildScopeVisuals(mEditorContext.getCurrentGraph());
            rebuildVisualConnections();
            mViewport.updateTransform();
            mViewport.invalidate();
        }
    }

    private void rebuildScopeVisuals(NodeGraph graph) {
        if (graph == null) return;
        if (mEditorContext == null || !mEditorContext.isInsideGroupScope()) {
            if (graph.frames != null) {
                for (com.mine.geometry_node.core.node.FrameData frameData : graph.frames.values()) {
                    onFrameAdded(frameData);
                }
            }
        }
        if (graph.nodes != null) {
            for (NodeData data : graph.nodes.values()) {
                onNodeAdded(data);
            }
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
        if (mEditorContext == null || mEditorContext.isInsideGroupScope()) return;
        com.mine.geometry_node.core.node.FrameData frameData = new com.mine.geometry_node.core.node.FrameData(UUID.randomUUID().toString(), uiX, uiY);
        CmdAddFrame cmd = new CmdAddFrame(mEditorContext.getGraphController(), frameData);
        mEditorContext.getCommandManager().execute(cmd);
    }

    public void executeAddGroup(float uiX, float uiY) {
        if (mEditorContext == null) return;
        NodeData group = GroupNodeFactory.createGroupNode(UUID.randomUUID().toString(), uiX, uiY);
        CmdAddNode cmd = new CmdAddNode(mEditorContext.getGraphController(), group);
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

    public void executeGroupIntoNodeGroup() {
        if (mEditorContext == null) return;
        List<NodeVisualAdapter> selectedNodes = mViewport.getSelectedNodeVisuals();
        if (selectedNodes.isEmpty()) return;

        List<String> selectedIds = new ArrayList<>();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (NodeVisualAdapter node : selectedNodes) {
            selectedIds.add(node.getNodeId());
            NodeData data = node.getNodeData();
            minX = Math.min(minX, data.getX());
            minY = Math.min(minY, data.getY());
            maxX = Math.max(maxX, data.getX() + node.getVisualWidthDp());
            maxY = Math.max(maxY, data.getY() + node.getVisualHeightDp());
        }

        float groupX = minX == Float.MAX_VALUE ? mViewport.getLastMouseUiX() : (minX + maxX) * 0.5f;
        float groupY = minY == Float.MAX_VALUE ? mViewport.getLastMouseUiY() : (minY + maxY) * 0.5f;
        CmdGroupIntoNodeGroup cmd = new CmdGroupIntoNodeGroup(mEditorContext.getGraphController(), mEditorContext.getCurrentGraph(), selectedIds, groupX, groupY);
        if (cmd.canExecute()) {
            mEditorContext.getCommandManager().execute(cmd);
        }
    }

    public void executeDissolveNodeGroup(String nodeId) {
        if (mEditorContext == null || nodeId == null) return;
        CmdDissolveNodeGroup cmd = new CmdDissolveNodeGroup(
                mEditorContext.getGraphController(),
                mEditorContext.getCurrentGraph(),
                nodeId
        );
        if (cmd.canExecute()) {
            mEditorContext.getCommandManager().execute(cmd);
            mViewport.clearSelection();
        }
    }

    public void executeRenamePort(String nodeId, String category, String portId, String oldName, String newName) {
        if (mEditorContext == null) return;
        CmdRenamePort cmd = new CmdRenamePort(mEditorContext.getGraphController(), nodeId, category, portId, oldName, newName);
        mEditorContext.getCommandManager().execute(cmd);
    }

    public void executeSetFrameProperty(String frameId, String title, int color) {
        if (mEditorContext == null || frameId == null) return;
        CmdSetFrameProperty cmd = new CmdSetFrameProperty(mEditorContext.getGraphController(), frameId, title, color);
        mEditorContext.getCommandManager().execute(cmd);
    }

    public void executeSetGroupNodeProperty(String nodeId, String title, int color, String comment) {
        if (mEditorContext == null || nodeId == null) return;
        CmdSetGroupNodeProperty cmd = new CmdSetGroupNodeProperty(mEditorContext.getGraphController(), nodeId, title, color, comment);
        mEditorContext.getCommandManager().execute(cmd);
    }

    public void executeImportGraphJson(String json, float screenX, float screenY) {
        if (mEditorContext == null || json == null || json.isBlank()) return;
        float uiX = mViewport.getCamera().screenToUIX(screenX);
        float uiY = mViewport.getCamera().screenToUIY(screenY);
        CmdPasteElements cmd = new CmdPasteElements(mEditorContext.getGraphController(), json, uiX, uiY);
        mEditorContext.getCommandManager().execute(cmd);
        selectPastedElements(cmd);
    }

    private void selectPastedElements(CmdPasteElements cmd) {
        mViewport.clearSelection();
        for (NodeData node : cmd.getPastedNodes()) {
            NodeVisualAdapter visual = mViewport.getNodeVisual(node.id);
            if (visual != null) {
                mViewport.addToSelection(visual);
            }
        }
        for (com.mine.geometry_node.core.node.FrameData frame : cmd.getPastedFrames()) {
            FrameVisualAdapter visual = mViewport.getFrameVisuals().get(frame.id);
            if (visual != null) {
                mViewport.addToSelection(visual);
            }
        }
        if (mCurrentSession != null) {
            mViewport.syncSelectionToSession(mCurrentSession.selectedNodeIds, mCurrentSession.selectedFrameIds);
        }
    }

    private void rebuildVisualConnections() {
        mViewport.rebuildVisualConnections(mEditorContext != null ? mEditorContext.getCurrentGraph() : null);
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
    public void onMoveElementsTo(Map<String, float[]> nodePositions, Map<String, float[]> framePositions) {
        if (mEditorContext == null) return;
        CmdSetElementPositions cmdMove = new CmdSetElementPositions(mEditorContext.getGraphController(), nodePositions, framePositions);
        mEditorContext.getCommandManager().execute(cmdMove);
    }

    @Override
    public void onChangeParent(List<String> elementIds, boolean isNode, String newParentId) {
        if (mEditorContext == null || mEditorContext.isInsideGroupScope()) return;
        CmdChangeParent cmdParent = new CmdChangeParent(mEditorContext.getGraphController(), elementIds, isNode, newParentId);
        mEditorContext.getCommandManager().execute(cmdParent);
    }

    @Override
    public void onConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        if (mEditorContext == null) return;
        CmdConnect cmd = new CmdConnect(mEditorContext.getGraphController(), mEditorContext.getCurrentGraph(), outNodeId, outPortId, inNodeId, inPortId);
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
    public void onInsertReroute(ConnectionLayer.ConnectionHit connection) {
        if (mEditorContext == null || connection == null) return;

        float radius = UIConstants.Node.PORT_VISUAL_RADIUS;
        CmdInsertRerouteOnConnection cmd = new CmdInsertRerouteOnConnection(
                mEditorContext.getGraphController(),
                mEditorContext.getCurrentGraph(),
                connection.outNodeId(),
                connection.outPortId(),
                connection.inNodeId(),
                connection.inPortId(),
                connection.execution(),
                UUID.randomUUID().toString(),
                connection.uiX() - radius,
                connection.uiY() - radius
        );
        if (cmd.canExecute()) {
            mEditorContext.getCommandManager().execute(cmd);
        }
    }

    @Override
    public void onNodeDoubleClicked(String nodeId) {
        executeEnterGroup(nodeId);
    }

    @Override
    public boolean isCyclicFrame(String childId, String parentId) {
        if (mEditorContext == null || mEditorContext.isInsideGroupScope() || parentId == null) return false;
        if (childId.equals(parentId)) return true;

        com.mine.geometry_node.core.node.FrameData current = mEditorContext.getGraph().getFrame(parentId);
        while (current != null) {
            if (childId.equals(current.parentFrame)) return true;
            current = mEditorContext.getGraph().getFrame(current.parentFrame);
        }
        return false;
    }

    @Override
    public void performAction(ViewportActionId id, ViewportActionRequest request) {
        if (id == null) return;
        ViewportActionRequest actionRequest = request != null ? request : ViewportActionRequest.EMPTY;
        switch (id) {
            case UNDO -> undo();
            case REDO -> redo();
            case SAVE -> save();
            case COPY -> copySelection();
            case PASTE -> paste(actionRequest.uiXOr(mViewport.getLastMouseUiX()), actionRequest.uiYOr(mViewport.getLastMouseUiY()));
            case DELETE -> deleteSelection();
            case TOGGLE_SNAP_TO_GRID -> toggleSnapToGrid();
            case TOGGLE_GRID_AND_AXIS -> toggleGridAndAxis();
            case MOVE_SELECTION -> mViewport.beginKeyboardMoveSelection();
            case GROUP_INTO_FRAME -> groupIntoFrameFromAction();
            case GROUP_INTO_NODE_GROUP -> groupIntoNodeGroupFromAction();
            case EXIT_GROUP -> executeExitGroup();
            case ADD_NODE -> executeAddNode(
                    actionRequest.screenXOr(mViewport.getLastMouseUiX()),
                    actionRequest.screenYOr(mViewport.getLastMouseUiY()),
                    actionRequest.typeId()
            );
            case ADD_FRAME -> executeAddFrame(
                    actionRequest.uiXOr(mViewport.getLastMouseUiX()),
                    actionRequest.uiYOr(mViewport.getLastMouseUiY())
            );
            case ADD_GROUP -> executeAddGroup(
                    actionRequest.uiXOr(mViewport.getLastMouseUiX()),
                    actionRequest.uiYOr(mViewport.getLastMouseUiY())
            );
            case DISSOLVE_NODE_GROUP -> executeDissolveNodeGroup(actionRequest.nodeId());
            case SET_FRAME_PROPERTY -> executeSetFrameProperty(
                    actionRequest.frameId(),
                    actionRequest.title(),
                    actionRequest.colorOr(0xFF556677)
            );
            case SET_GROUP_NODE_PROPERTY -> executeSetGroupNodeProperty(
                    actionRequest.nodeId(),
                    actionRequest.title(),
                    actionRequest.colorOr(NodeData.DEFAULT_GROUP_COLOR),
                    actionRequest.comment()
            );
            case RENAME_PORT -> executeRenamePort(
                    actionRequest.nodeId(),
                    actionRequest.portCategory(),
                    actionRequest.portId(),
                    actionRequest.oldName(),
                    actionRequest.newName()
            );
        }
    }

    private void undo() {
        if (mEditorContext != null) mEditorContext.getCommandManager().undo();
    }

    private void redo() {
        if (mEditorContext != null) mEditorContext.getCommandManager().redo();
    }

    private void save() {
        mViewport.requestViewportFocus();
        DocumentManager.INSTANCE.saveSession(DocumentManager.INSTANCE.getActiveSession());
    }

    private void copySelection() {
        if (mEditorContext == null) return;

        List<NodeVisualAdapter> selectedNodes = mViewport.getSelectedNodeVisuals();
        List<FrameVisualAdapter> selectedFrames = mViewport.getSelectedFrameVisuals();
        if (selectedNodes.isEmpty() && selectedFrames.isEmpty()) return;

        Set<String> copiedFrameIds = new HashSet<>();
        Set<String> copiedNodeIds = new HashSet<>();

        for (FrameVisualAdapter frame : selectedFrames) copiedFrameIds.add(frame.getFrameId());
        for (NodeVisualAdapter node : selectedNodes) copiedNodeIds.add(node.getNodeId());

        NodeGraph mainGraph = mEditorContext.getCurrentGraph();

        // Include child frames inside selected frames.
        if (!mEditorContext.isInsideGroupScope()) {
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

            // Include nodes inside selected frames.
            if (mainGraph.nodes != null) {
                for (NodeData n : mainGraph.nodes.values()) {
                    if (n.parentFrame != null && copiedFrameIds.contains(n.parentFrame) && !copiedNodeIds.contains(n.id)) {
                        copiedNodeIds.add(n.id);
                    }
                }
            }
        } else {
            copiedFrameIds.clear();
        }

        NodeGraph tempGraph = new NodeGraph();
        for (String fid : copiedFrameIds) tempGraph.frames.put(fid, mainGraph.getFrame(fid));
        for (String nid : copiedNodeIds) tempGraph.nodes.put(nid, mainGraph.getNode(nid));

        sClipboardJson = GraphJsonIO.toJson(tempGraph);
        System.out.println("Copied " + copiedNodeIds.size() + " nodes and " + copiedFrameIds.size() + " frames.");
    }

    private void paste(float uiX, float uiY) {
        if (mEditorContext == null || sClipboardJson == null || sClipboardJson.isEmpty()) return;

        CmdPasteElements cmd = new CmdPasteElements(mEditorContext.getGraphController(), sClipboardJson, uiX, uiY);
        mEditorContext.getCommandManager().execute(cmd);

        mViewport.clearSelection();
        System.out.println("Pasted elements from clipboard.");
    }

    private void deleteSelection() {
        if (mEditorContext == null) return;
        List<NodeVisualAdapter> selectedNodes = mViewport.getSelectedNodeVisuals();
        List<FrameVisualAdapter> selectedFrames = mViewport.getSelectedFrameVisuals();

        List<String> nodeIdsToRemove = new java.util.ArrayList<>();
        for (NodeVisualAdapter node : selectedNodes) nodeIdsToRemove.add(node.getNodeId());

        List<String> frameIdsToRemove = new java.util.ArrayList<>();
        for (FrameVisualAdapter frame : selectedFrames) frameIdsToRemove.add(frame.getFrameId());

        if (!nodeIdsToRemove.isEmpty()) {
            CmdRemoveNodes cmdN = new CmdRemoveNodes(mEditorContext.getGraphController(), mEditorContext.getCurrentGraph(), nodeIdsToRemove);
            mEditorContext.getCommandManager().execute(cmdN);
        }

        if (!mEditorContext.isInsideGroupScope() && !frameIdsToRemove.isEmpty()) {
            CmdRemoveFrames cmdF = new CmdRemoveFrames(mEditorContext.getGraphController(), frameIdsToRemove);
            mEditorContext.getCommandManager().execute(cmdF);
        }

        mViewport.clearSelection();
    }

    private void toggleSnapToGrid() {
        ConfigManager.INSTANCE.update(config -> config.viewport.snapToGrid = !config.viewport.snapToGrid);
    }

    private void toggleGridAndAxis() {
        ConfigManager.INSTANCE.update(config -> config.viewport.showGridAndAxis = !config.viewport.showGridAndAxis);
    }

    private void groupIntoFrameFromAction() {
        if (mEditorContext != null && mEditorContext.isInsideGroupScope()) return;
        executeGroupIntoFrame();
        mViewport.clearSelection();
    }

    private void groupIntoNodeGroupFromAction() {
        executeGroupIntoNodeGroup();
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
        NodeVisualAdapter uiNode = RerouteNodeSupport.isReroute(nodeData)
                ? new UIRerouteNode(mViewport.getContext(), nodeData, def)
                : new UINode(mViewport.getContext(), nodeData, def, mEditorContext);
        uiNode.setPreviewPosition(nodeData.getX(), nodeData.getY());
        mViewport.addNodeVisual(nodeData.id, uiNode);
    }
    @Override public void onNodeRemoved(String nodeId) { mViewport.removeNodeVisual(nodeId); rebuildVisualConnections(); }
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
        rebuildVisualConnections();
    }

    @Override public void onGraphConnectionsRebuildRequested() { rebuildVisualConnections(); }
    @Override public void onExecutionConnectionAdded(String outN, String outP, String inN, String inP) { rebuildVisualConnections(); }
    @Override public void onExecutionConnectionRemoved(String outN, String outP, String inN, String inP) { rebuildVisualConnections(); }
    @Override public void onSelectionChanged(List<String> selectedNodeIds) { mViewport.updateSelectionState(selectedNodeIds); }
    @Override public void onNodeMoved(String nodeId, float x, float y) { mViewport.updateNodePosition(nodeId, x, y); mViewport.updateConnectionsForNode(nodeId); }
    @Override public void onConnectionAdded(String outN, String outP, String inN, String inP) { mViewport.notifyNodeLayoutUpdate(outN); mViewport.notifyNodeLayoutUpdate(inN); rebuildVisualConnections(); }
    @Override public void onConnectionRemoved(String outN, String outP, String inN, String inP) { mViewport.notifyNodeLayoutUpdate(outN); mViewport.notifyNodeLayoutUpdate(inN); rebuildVisualConnections(); }
}
