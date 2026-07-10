package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.viewport.interaction.*;
import com.mine.geometry_node.client.ui.viewport.connection.ConnectionLayer;
import com.mine.geometry_node.client.ui.viewport.frame.FrameLayer;
import com.mine.geometry_node.client.ui.viewport.layers.BackgroundLayer;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.InventoryItemPickerOverlay;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.ShopEditorOverlay;
import com.mine.geometry_node.client.ui.viewport.node.NodeLayer;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionSink;
import com.mine.geometry_node.client.ui.viewport.menu.ViewportMenu;
import com.mine.geometry_node.client.ui.viewport.connection.ConnectionNodeVisual;
import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.selection.ViewportSelection;
import com.mine.geometry_node.client.ui.viewport.toolbar.ViewportToolbar;
import com.mine.geometry_node.client.ui.persistence.config.AppConfig;
import com.mine.geometry_node.client.ui.persistence.config.ConfigChangeListener;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeGraph;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Viewport extends FrameLayout implements InteractionContext {

    // ==========================================
    // 1. 模块状态
    // ==========================================

    private final ViewportCamera mCamera;
    private final ViewportEventDispatcher mEventDispatcher;
    private final InteractionManager mInteractionManager;
    private final KeyManager mKeyManager;
    private final ViewportController mController;
    private final ViewportSelection mSelection = new ViewportSelection();
    private ViewportMenu mActiveMenu;

    private final BackgroundLayer mBackgroundLayer;
    private final ConnectionLayer mConnectionLayer;
    private NodeLayer mNodeLayer;
    private FrameLayer mFrameLayer;

    private boolean mFirstLayout = true;
    private final float[] mTempPos = new float[2];
    private boolean mSnapToGridEnabled = false;
    private final ConfigChangeListener mConfigChangeListener = this::applyViewportConfig;

    private TextView mEmptyHint;
    private ViewportToolbar mToolbar;


    // ==========================================
    // 2. 初始化与生命周期
    // ==========================================

    public Viewport(Context context) {
        super(context);
        initViewportProps();

        mCamera = new ViewportCamera(this::updateTransform);
        mBackgroundLayer = new BackgroundLayer();
        mConnectionLayer = new ConnectionLayer(this);
        mEventDispatcher = new ViewportEventDispatcher(this);

        mInteractionManager = new InteractionManager(this);
        mKeyManager = new KeyManager(this);

        mController = new ViewportController(this, null);
        mToolbar.setActionSink(mController);

        mInteractionManager.setListener(mController);
        mKeyManager.setActionSink(mController);

        setFocusable(true);
        setFocusableInTouchMode(true);
        applyViewportConfig(ConfigManager.INSTANCE.getConfig());
        ConfigManager.INSTANCE.addChangeListener(mConfigChangeListener);
    }

    private void initViewportProps() {
        setWillNotDraw(false);
        setClipChildren(false);

        mEmptyHint = new TextView(getContext());
        mEmptyHint.setText("当前没有打开的蓝图\n请在资产浏览器中双击 JSON 文件开始编辑");
        mEmptyHint.setTextSize(18);
        mEmptyHint.setTextColor(0xFF888888);
        mEmptyHint.setGravity(Gravity.CENTER);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        addView(mEmptyHint, lp);

        mToolbar = new ViewportToolbar(getContext(), null);
        mToolbar.setVisibility(View.GONE);
        LayoutParams toolbarLp = new LayoutParams(UIUtils.dp2pxInt(140), LayoutParams.WRAP_CONTENT);
        toolbarLp.gravity = Gravity.RIGHT | Gravity.TOP;
        toolbarLp.topMargin = UIUtils.dp2pxInt(10);
        toolbarLp.rightMargin = UIUtils.dp2pxInt(10);
        addView(mToolbar, toolbarLp);
    }

    @Override
    protected void onDetachedFromWindow() {
        ConfigManager.INSTANCE.removeChangeListener(mConfigChangeListener);
        mKeyManager.dispose();
        mController.saveCurrentSessionState();
        super.onDetachedFromWindow();
    }


    // ==========================================
    // 3. Controller 视图接口
    // ==========================================

    /**
     * 清理并重建画布所需的图层环境
     */
    public void prepareLayers() {
        if (mNodeLayer != null) removeView(mNodeLayer);
        if (mFrameLayer != null) removeView(mFrameLayer);

        mFrameLayer = new FrameLayer(getContext(), this);
        addView(mFrameLayer, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mEmptyHint.setVisibility(View.GONE);

        mNodeLayer = new NodeLayer(getContext(), this);
        addView(mNodeLayer, 1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (mToolbar != null) mToolbar.setVisibility(View.VISIBLE);
        applySelectionToLayers();

        // 子 layer 刚 attach 时可能还没有完成测量；延后一帧同步 overlay 和 culling 状态。
        post(this::updateTransform);
    }

    /**
     * 切回白板空闲状态
     */
    public void showEmptyHint() {
        if (mNodeLayer != null) removeView(mNodeLayer);
        if (mFrameLayer != null) removeView(mFrameLayer);
        mNodeLayer = null;
        mFrameLayer = null;
        mSelection.clear();
        mEmptyHint.setVisibility(View.VISIBLE);
        if (mToolbar != null) {
            mToolbar.setVisibility(View.GONE);
            mToolbar.hideTooltip();
        }
    }

    public ViewportController getController() {
        return mController;
    }

    private void applyViewportConfig(AppConfig config) {
        if (config == null || config.viewport == null) return;
        mSnapToGridEnabled = config.viewport.snapToGrid;
        mBackgroundLayer.setGridAndAxisVisible(config.viewport.showGridAndAxis);
        if (mToolbar != null) {
            mToolbar.setSnapToGridEnabled(config.viewport.snapToGrid, false);
            mToolbar.setGridAndAxisVisible(config.viewport.showGridAndAxis, false);
        }
        invalidate();
    }


    // ==========================================
    // 4. 渲染与视图变换
    // ==========================================

    @Override
    public ViewportCamera getCamera() { return mCamera; }

    public void updateTransform() {
        if (mNodeLayer != null) {
            mNodeLayer.updateOverlayTransforms();

            if (mFrameLayer != null) {
                mFrameLayer.invalidate();
            }
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mFirstLayout && w > 0 && h > 0) {
            mCamera.setPosition(w / 2f, h / 2f);
            mFirstLayout = false;
        } else {
            updateTransform();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mBackgroundLayer.draw(canvas, mCamera, getWidth(), getHeight());
        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mFrameLayer != null) {
            mFrameLayer.drawFrames(canvas);
        }
        mConnectionLayer.draw(canvas, mCamera);
        super.dispatchDraw(canvas);
        mInteractionManager.drawOverlay(canvas);
    }


    // ==========================================
    // 5. 图元显示管道接口
    // ==========================================

    public Map<String, ? extends NodeVisualAdapter> getNodeVisuals() { return mNodeLayer != null ? mNodeLayer.getNodeVisuals() : new HashMap<>(); }
    public Map<String, ? extends ConnectionNodeVisual> getConnectionNodeVisuals() { return mNodeLayer != null ? mNodeLayer.getNodeVisuals() : new HashMap<>(); }
    public void addNodeVisual(String nodeId, NodeVisualAdapter node) { if (mNodeLayer != null) { mNodeLayer.addNodeVisual(nodeId, node); invalidate(); } }
    public void removeNodeVisual(String nodeId) {
        mSelection.removeNode(nodeId);
        if (mNodeLayer != null) {
            mNodeLayer.removeNodeVisual(nodeId);
            applySelectionToLayers();
        }
    }
    public NodeVisualAdapter getNodeVisual(String nodeId) { return mNodeLayer != null ? mNodeLayer.getNodeVisual(nodeId) : null; }
    public void updateNodePosition(String nodeId, float x, float y) { if (mNodeLayer != null) { mNodeLayer.updateNodePosition(nodeId, x, y); invalidate(); } }
    public void notifyNodeLayoutUpdate(String nodeId) { if (mNodeLayer != null) mNodeLayer.notifyNodeLayoutUpdate(nodeId); }
    public void notifyNodeVisualMoved(NodeVisualAdapter node) { if (mNodeLayer != null) mNodeLayer.updateOverlayForNode(node); }

    public Map<String, ? extends FrameVisualAdapter> getFrameVisuals() { return mFrameLayer != null ? mFrameLayer.getFrameVisuals() : new HashMap<>(); }
    public void addFrameVisual(String frameId, FrameVisualAdapter frame) { if (mFrameLayer != null) { mFrameLayer.addFrameVisual(frameId, frame); invalidate(); } }
    public void removeFrameVisual(String frameId) {
        mSelection.removeFrame(frameId);
        if (mFrameLayer != null) {
            mFrameLayer.removeFrameVisual(frameId);
            applySelectionToLayers();
        }
    }
    public void updateFrameVisual(String frameId) { if (mFrameLayer != null) { mFrameLayer.updateFrameVisual(frameId); invalidate(); } }

    @Override public void updateFrameBounds(String frameId) { if (mFrameLayer != null) { mFrameLayer.updateFrameBounds(frameId); invalidate(); } }
    @Override public void updateConnectionsForNode(String nodeId) { mConnectionLayer.updateConnectionsForNode(nodeId); }
    public void rebuildVisualConnections(NodeGraph graph) { mConnectionLayer.rebuildVisualConnections(graph, getConnectionNodeVisuals()); }
    public void previewFrameBounds(String frameId) { if (mFrameLayer != null) { mFrameLayer.previewFrameBounds(frameId); invalidate(); } }


    // ==========================================
    // 6. InteractionContext 实现
    // ==========================================

    @Override public boolean isReady() { return mController != null && mController.hasActiveSession(); }
    @Override public NodeVisualAdapter findNodeAt(float uiX, float uiY) { return mNodeLayer != null ? mNodeLayer.findNodeAt(uiX, uiY) : null; }
    @Override public PortInfo findPortAt(float uiX, float uiY) { return mNodeLayer != null ? mNodeLayer.findPortAt(uiX, uiY) : null; }
    @Override public FrameVisualAdapter findFrameAt(float uiX, float uiY) { return mFrameLayer != null ? mFrameLayer.findFrameAt(uiX, uiY) : null; }
    @Override public FrameVisualAdapter getSmallestContainingFrame(float uiX, float uiY) { return mFrameLayer != null ? mFrameLayer.getSmallestContainingFrame(uiX, uiY) : null; }
    @Override public Iterable<NodeVisualAdapter> getAllNodeVisuals() { return mNodeLayer != null ? mNodeLayer.getNodeVisuals().values() : new ArrayList<>(); }
    @Override public Iterable<FrameVisualAdapter> getAllFrameVisuals() { return mFrameLayer != null ? mFrameLayer.getFrameVisuals().values() : new ArrayList<>(); }

    @Override
    public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) {
        mSelection.setNodes(mNodeLayer != null ? mNodeLayer.findNodeIdsInRect(uiX, uiY, uiW, uiH) : new ArrayList<>());
        mSelection.setFrames(mFrameLayer != null ? mFrameLayer.findFrameIdsInRect(uiX, uiY, uiW, uiH) : new ArrayList<>());
        applySelectionToLayers();
    }

    @Override public boolean isSnapToGridEnabled() { return mSnapToGridEnabled; }
    @Override public float getSnapGridSize() { return Math.max(1.0f, ConfigManager.INSTANCE.getConfig().viewport.gridSize); }
    @Override public boolean hasSelection() { return !mSelection.isEmpty(); }
    @Override public List<NodeVisualAdapter> getSelectedNodeVisuals() { return mNodeLayer != null ? mNodeLayer.getNodeVisuals(mSelection.nodeIds()) : new ArrayList<>(); }
    @Override public void previewFrameMove(String frameId, float totalUiDx, float totalUiDy) { if (mFrameLayer != null) { mFrameLayer.previewFrameMove(frameId, totalUiDx, totalUiDy); invalidate(); } }

    @Override
    public void previewSelectedElementsMove(float totalUiDx, float totalUiDy) {
        Set<String> selectedFrameIds = new HashSet<>(mSelection.frameIds());
        List<String> rootFrameIds = getRootSelectedFrameIds(selectedFrameIds);

        for (String frameId : rootFrameIds) {
            if (mFrameLayer != null) {
                mFrameLayer.previewFrameMove(frameId, totalUiDx, totalUiDy);
            }
        }

        Set<String> affectedParentFrameIds = new HashSet<>();
        for (NodeVisualAdapter node : getSelectedNodeVisuals()) {
            if (isInsideSelectedFrame(node.getParentFrameId(), selectedFrameIds)) {
                continue;
            }
            node.setPreviewPosition(
                    node.getNodeData().getX() + totalUiDx,
                    node.getNodeData().getY() + totalUiDy
            );
            notifyNodeVisualMoved(node);
            updateConnectionsForNode(node.getNodeId());
            if (node.getParentFrameId() != null) {
                affectedParentFrameIds.add(node.getParentFrameId());
            }
        }

        for (String frameId : affectedParentFrameIds) {
            previewFrameBounds(frameId);
        }
        invalidate();
    }

    @Override
    public void resetSelectedElementsPreview() {
        previewSelectedElementsMove(0.0f, 0.0f);
    }

    @Override
    public void cutIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY, InteractionManager.InteractionListener listener) {
        if (mConnectionLayer != null) {
            mConnectionLayer.intersectAndCut(lastUiX, lastUiY, currentUiX, currentUiY, listener);
        }
    }

    @Override
    public List<ConnectionLayer.ConnectionHit> findIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY) {
        return mConnectionLayer != null
                ? mConnectionLayer.findIntersectingConnections(lastUiX, lastUiY, currentUiX, currentUiY)
                : List.of();
    }

    @Override
    public void clearSelection() {
        mSelection.clear();
        applySelectionToLayers();
    }
    @Override
    public void addToSelection(NodeVisualAdapter node) {
        mSelection.selectNode(node);
        applySelectionToLayers();
    }
    @Override
    public void addToSelection(FrameVisualAdapter frame) {
        mSelection.selectFrame(frame);
        applySelectionToLayers();
    }
    @Override
    public List<FrameVisualAdapter> getSelectedFrameVisuals() {
        return mFrameLayer != null ? mFrameLayer.getFrameVisuals(mSelection.frameIds()) : new ArrayList<>();
    }
    @Override
    public boolean isNodeSelected(String nodeId) { return mSelection.containsNode(nodeId); }
    public void updateSelectionState(List<String> selectedNodeIds) {
        updateSelectionState(selectedNodeIds, null);
    }

    public void updateSelectionState(List<String> selectedNodeIds, List<String> selectedFrameIds) {
        mSelection.setNodes(selectedNodeIds);
        mSelection.setFrames(selectedFrameIds);
        applySelectionToLayers();
    }

    public void syncSelectionToSession(List<String> selectedNodeIds, List<String> selectedFrameIds) {
        mSelection.syncSessionLists(selectedNodeIds, selectedFrameIds);
    }

    private void applySelectionToLayers() {
        if (mNodeLayer != null) mNodeLayer.applySelection(mSelection.nodeIds());
        if (mFrameLayer != null) mFrameLayer.applySelection(mSelection.frameIds());
        invalidate();
    }

    private List<String> getRootSelectedFrameIds(Set<String> selectedFrameIds) {
        List<String> rootFrameIds = new ArrayList<>();
        for (String frameId : selectedFrameIds) {
            FrameVisualAdapter frame = mFrameLayer != null ? mFrameLayer.getFrameVisuals().get(frameId) : null;
            if (frame != null && !isInsideSelectedFrame(frame.getParentFrameId(), selectedFrameIds)) {
                rootFrameIds.add(frameId);
            }
        }
        return rootFrameIds;
    }

    private boolean isInsideSelectedFrame(String frameId, Set<String> selectedFrameIds) {
        String currentFrameId = frameId;
        while (currentFrameId != null) {
            if (selectedFrameIds.contains(currentFrameId)) {
                return true;
            }
            FrameVisualAdapter frame = mFrameLayer != null ? mFrameLayer.getFrameVisuals().get(currentFrameId) : null;
            currentFrameId = frame != null ? frame.getParentFrameId() : null;
        }
        return false;
    }

    @Override
    public boolean hasConnection(NodeVisualAdapter outN, String outId, NodeVisualAdapter inN, String inId) {
        List<com.mine.geometry_node.core.node.Connection> links = outN.getNodeData().getConnections(outId);
        if (links == null) return false;
        for (com.mine.geometry_node.core.node.Connection link : links) {
            if (link.targetNodeId().equals(inN.getNodeId()) && link.targetPortName().equals(inId)) return true;
        }
        return false;
    }

    @Override
    public boolean canConnectPorts(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        return mController != null && mController.canConnectPorts(outNodeId, outPortId, inNodeId, inPortId);
    }

    @Override
    public boolean isInsideGroupScope() {
        return mController != null && mController.isInsideGroupScope();
    }

    public ViewportActionSink getActionSink() {
        return mController;
    }

    @Override
    public void showMenu(float screenX, float screenY) {
        closeMenu();
        mActiveMenu = new ViewportMenu(getContext());
        addView(mActiveMenu);
        mActiveMenu.showAt(screenX, screenY, this, mController);
    }

    @Override
    public void closeMenu() {
        if (mActiveMenu != null) {
            if (mActiveMenu.getParent() == this) removeView(mActiveMenu);
            mActiveMenu = null;
            requestViewportFocus();
        }
    }

    @Override public void requestViewportFocus() { requestFocus(); }
    @Override public Context getUIContext() { return getContext(); }

    @Override
    public float getLastMouseUiX() { return mCamera.screenToUIX(mEventDispatcher.getLastMouseScreenX()); }
    @Override
    public float getLastMouseUiY() { return mCamera.screenToUIY(mEventDispatcher.getLastMouseScreenY()); }


    // ==========================================
    // 7. 事件拦截分发
    // ==========================================

    private boolean isHitOverlay(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == mNodeLayer || child == mFrameLayer || child == mEmptyHint || child.getVisibility() != View.VISIBLE) continue;
            if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom()) return true;
        }
        return false;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (hasBlockingOverlay()) {
            super.dispatchTouchEvent(ev);
            return true;
        }
        if (mInteractionManager.isKeyboardMoveActive()) {
            return onTouchEvent(ev);
        }
        if (mEventDispatcher.handleTouchEvent(ev, isHitOverlay(ev.getX(), ev.getY()))) return true;
        return super.dispatchTouchEvent(ev);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (hasBlockingOverlay()) {
            super.dispatchGenericMotionEvent(ev);
            return true;
        }
        if (mInteractionManager.isKeyboardMoveActive()) {
            return mInteractionManager.onHoverMove(ev.getX(), ev.getY())
                    || mInteractionManager.onGenericMotionEvent(ev)
                    || super.dispatchGenericMotionEvent(ev);
        }
        if (mEventDispatcher.handleGenericMotionEvent(ev, isHitOverlay(ev.getX(), ev.getY()))) return true;
        return super.dispatchGenericMotionEvent(ev);
    }
    @Override public PointerIcon onResolvePointerIcon(MotionEvent event) {
        if (hasBlockingOverlay()) {
            PointerIcon icon = super.onResolvePointerIcon(event);
            return icon != null ? icon : PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
        }
        if (isHitOverlay(event.getX(), event.getY())) {
            return super.onResolvePointerIcon(event);
        }
        PointerIcon icon = mEventDispatcher.resolvePointerIcon(event);
        return icon != null ? icon : super.onResolvePointerIcon(event);
    }

    private boolean hasBlockingOverlay() {
        return ShopEditorOverlay.hasVisibleOverlay()
                || InventoryItemPickerOverlay.hasVisibleOverlay();
    }

    @Override
    public boolean dispatchKeyEvent(icyllis.modernui.view.KeyEvent event) {
        if (event.getAction() == icyllis.modernui.view.KeyEvent.ACTION_DOWN) {
            View focusedView = findFocus();
            if (focusedView instanceof EditText) return super.dispatchKeyEvent(event);
            if (mInteractionManager.onKeyDown(event)) return true;
            if (mKeyManager.onKeyDown(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) { return mInteractionManager.onHoverMove(event.getX(), event.getY()) || mInteractionManager.onGenericMotionEvent(event) || super.onGenericMotionEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event) { return mInteractionManager.onTouchEvent(event) || super.onTouchEvent(event); }
    @Override public boolean onKeyDown(int keyCode, icyllis.modernui.view.KeyEvent event) { return mInteractionManager.onKeyDown(event) || mKeyManager.onKeyDown(event) || super.onKeyDown(keyCode, event); }
    public void beginKeyboardMoveSelection() { mInteractionManager.beginKeyboardMoveSelection(); }

    // ==========================================
    // 8. 内部桥接结构
    // ==========================================

    public static class PortInfo {
        public final NodeVisualAdapter node;
        public final String portId;
        public final boolean isInput;
        public PortInfo(NodeVisualAdapter n, String id, boolean in) { this.node = n; this.portId = id; this.isInput = in; }
    }
}
