package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.viewport.interaction.*;
import com.mine.geometry_node.client.ui.viewport.connection.ConnectionLayer;
import com.mine.geometry_node.client.ui.viewport.frame.FrameLayer;
import com.mine.geometry_node.client.ui.viewport.layers.BackgroundLayer;
import com.mine.geometry_node.client.ui.viewport.node.NodeLayer;
import com.mine.geometry_node.client.ui.viewport.menu.ViewportMenu;
import com.mine.geometry_node.client.ui.viewport.connection.ConnectionNodeVisual;
import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;
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
import java.util.List;
import java.util.Map;

public class Viewport extends FrameLayout implements InteractionContext {

    // ==========================================
    // 1. 模块状态
    // ==========================================

    private final ViewportCamera mCamera;
    private final ViewportEventDispatcher mEventDispatcher;
    private final InteractionManager mInteractionManager;
    private final KeyManager mKeyManager;
    private final ViewportController mController;
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

        mInteractionManager.setListener(mController);
        mKeyManager.setListener(mController);

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

        mToolbar = new ViewportToolbar(getContext(), new ViewportToolbar.Listener() {
            @Override
            public void onSnapToGridChanged(boolean enabled) {
                ConfigManager.INSTANCE.update(config -> config.viewport.snapToGrid = enabled);
            }

            @Override
            public void onGridAndAxisVisibilityChanged(boolean visible) {
                ConfigManager.INSTANCE.update(config -> config.viewport.showGridAndAxis = visible);
            }
        });
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
    public void removeNodeVisual(String nodeId) { if (mNodeLayer != null) { mNodeLayer.removeNodeVisual(nodeId); invalidate(); } }
    public NodeVisualAdapter getNodeVisual(String nodeId) { return mNodeLayer != null ? mNodeLayer.getNodeVisual(nodeId) : null; }
    public void updateNodePosition(String nodeId, float x, float y) { if (mNodeLayer != null) { mNodeLayer.updateNodePosition(nodeId, x, y); invalidate(); } }
    public void notifyNodeLayoutUpdate(String nodeId) { if (mNodeLayer != null) mNodeLayer.notifyNodeLayoutUpdate(nodeId); }
    public void notifyNodeVisualMoved(NodeVisualAdapter node) { if (mNodeLayer != null) mNodeLayer.updateOverlayForNode(node); }

    public Map<String, ? extends FrameVisualAdapter> getFrameVisuals() { return mFrameLayer != null ? mFrameLayer.getFrameVisuals() : new HashMap<>(); }
    public void addFrameVisual(String frameId, FrameVisualAdapter frame) { if (mFrameLayer != null) { mFrameLayer.addFrameVisual(frameId, frame); invalidate(); } }
    public void removeFrameVisual(String frameId) { if (mFrameLayer != null) { mFrameLayer.removeFrameVisual(frameId); invalidate(); } }
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

    @Override public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) { if (mNodeLayer != null) mNodeLayer.updateBoxSelection(uiX, uiY, uiW, uiH); }
    @Override public void moveSelectedNodes(float uiDx, float uiDy) { if (mNodeLayer != null) mNodeLayer.moveSelectedNodes(uiDx, uiDy); }
    @Override public boolean isSnapToGridEnabled() { return mSnapToGridEnabled; }
    @Override public float getSnapGridSize() { return Math.max(1.0f, ConfigManager.INSTANCE.getConfig().viewport.gridSize); }
    @Override public List<NodeVisualAdapter> getSelectedNodeVisuals() { return mNodeLayer != null ? mNodeLayer.getSelectedNodeVisuals() : new ArrayList<>(); }
    @Override public void previewFrameMove(String frameId, float totalUiDx, float totalUiDy) { if (mFrameLayer != null) { mFrameLayer.previewFrameMove(frameId, totalUiDx, totalUiDy); invalidate(); } }

    @Override
    public void cutIntersectingConnections(float lastUiX, float lastUiY, float currentUiX, float currentUiY, InteractionManager.InteractionListener listener) {
        if (mConnectionLayer != null) {
            mConnectionLayer.intersectAndCut(lastUiX, lastUiY, currentUiX, currentUiY, listener);
        }
    }

    @Override
    public void clearSelection() {
        if (mNodeLayer != null) mNodeLayer.clearSelection();
        if (mFrameLayer != null) mFrameLayer.clearSelection();
    }
    @Override
    public void addToSelection(NodeVisualAdapter node) {
        if (mNodeLayer != null) mNodeLayer.addToSelection(node);
    }
    @Override
    public void addToSelection(FrameVisualAdapter frame) {
        if (mFrameLayer != null) mFrameLayer.addToSelection(frame);
    }
    @Override
    public List<FrameVisualAdapter> getSelectedFrameVisuals() {
        return mFrameLayer != null ? mFrameLayer.getSelectedFrameVisuals() : new ArrayList<>();
    }
    public boolean isNodeSelected(String nodeId) { return mNodeLayer != null && mNodeLayer.isNodeSelected(nodeId); }
    public void updateSelectionState(List<String> selectedNodeIds) { if (mNodeLayer != null) { mNodeLayer.updateSelectionState(selectedNodeIds); invalidate(); } }

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

    @Override
    public void showMenu(float screenX, float screenY) {
        closeMenu();
        mActiveMenu = new ViewportMenu(getContext());
        addView(mActiveMenu);
        mActiveMenu.showAt(screenX, screenY, this);
    }

    @Override
    public void closeMenu() {
        if (mActiveMenu != null) {
            if (mActiveMenu.getParent() == this) removeView(mActiveMenu);
            mActiveMenu = null;
            requestViewportFocus();
        }
    }

    @Override public void requestAddNode(float screenX, float screenY, String typeId) { if (mController != null) mController.executeAddNode(screenX, screenY, typeId); }
    @Override public void requestAddFrame(float uiX, float uiY) { if (mController != null) mController.executeAddFrame(uiX, uiY); }
    @Override public void requestGroupIntoFrame() { if (mController != null) mController.executeGroupIntoFrame(); }
    @Override public void requestAddGroup(float uiX, float uiY) { if (mController != null) mController.executeAddGroup(uiX, uiY); }
    @Override public void requestGroupIntoNodeGroup() { if (mController != null) mController.executeGroupIntoNodeGroup(); }
    @Override public void requestDissolveNodeGroup(String nodeId) { if (mController != null) mController.executeDissolveNodeGroup(nodeId); }
    @Override public void requestExitGroup() { if (mController != null) mController.executeExitGroup(); }
    @Override public void requestSetFrameProperty(String frameId, String title, int color) { if (mController != null) mController.executeSetFrameProperty(frameId, title, color); }
    @Override public void requestSetGroupNodeProperty(String nodeId, String title, int color, String comment) { if (mController != null) mController.executeSetGroupNodeProperty(nodeId, title, color, comment); }
    @Override public void requestRenamePort(String nodeId, String category, String portId, String oldName, String newName) { if (mController != null) mController.executeRenamePort(nodeId, category, portId, oldName, newName); }
    @Override public void requestSave() { if (mController != null) mController.onSaveRequested(); }
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

    @Override public boolean dispatchTouchEvent(MotionEvent ev) { if (mEventDispatcher.handleTouchEvent(ev, isHitOverlay(ev.getX(), ev.getY()))) return true; return super.dispatchTouchEvent(ev); }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent ev) { if (mEventDispatcher.handleGenericMotionEvent(ev, isHitOverlay(ev.getX(), ev.getY()))) return true; return super.dispatchGenericMotionEvent(ev); }
    @Override public PointerIcon onResolvePointerIcon(MotionEvent event) { PointerIcon icon = mEventDispatcher.resolvePointerIcon(event); return icon != null ? icon : super.onResolvePointerIcon(event); }

    @Override
    public boolean dispatchKeyEvent(icyllis.modernui.view.KeyEvent event) {
        if (event.getAction() == icyllis.modernui.view.KeyEvent.ACTION_DOWN) {
            View focusedView = findFocus();
            if (focusedView instanceof EditText) return super.dispatchKeyEvent(event);
            if (mKeyManager.onKeyDown(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) { return mInteractionManager.onGenericMotionEvent(event) || super.onGenericMotionEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event) { return mInteractionManager.onTouchEvent(event) || super.onTouchEvent(event); }
    @Override public boolean onKeyDown(int keyCode, icyllis.modernui.view.KeyEvent event) { return mKeyManager.onKeyDown(event) || super.onKeyDown(keyCode, event); }

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
