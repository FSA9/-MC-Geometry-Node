// --- START OF FILE Viewport.java ---
package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.viewport.interaction.*;
import com.mine.geometry_node.client.ui.viewport.layers.*;
import com.mine.geometry_node.client.ui.viewport.menu.ViewportMenu;
import com.mine.geometry_node.core.node.NodeData;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Viewport extends FrameLayout implements InteractionContext {

    // ==========================================
    // 1. 核心变量声明
    // ==========================================

    private final ViewportCamera mCamera;
    private final ViewportEventDispatcher mEventDispatcher;
    private final InteractionManager mInteractionManager;
    private final KeyManager mKeyManager;
    private final ViewportController mController;
    private ViewportMenu mActiveMenu;

    // 渲染层
    private final BackgroundLayer mBackgroundLayer;
    private final ConnectionLayer mConnectionLayer;
    private NodeLayer mNodeLayer;
    private FrameLayer mFrameLayer;

    private boolean mFirstLayout = true;
    private final float[] mTempPos = new float[2];

    // UI 组件
    private TextView mEmptyHint;


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

        // 让 Controller 持有自己
        mController = new ViewportController(this, null);

        mInteractionManager.setListener(mController);
        mKeyManager.setListener(mController);

        setFocusable(true);
        setFocusableInTouchMode(true);
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
    }

    @Override
    protected void onDetachedFromWindow() {
        // 移交：由 Controller 负责保存当前的 Session 状态
        mController.saveCurrentSessionState();
        super.onDetachedFromWindow();
    }


    // ==========================================
    // 3. 指挥官专用视图控制接口 (供 Controller 调用)
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
    }

    public ViewportController getController() {
        return mController;
    }


    // ==========================================
    // 4. 渲染与视图变换
    // ==========================================

    @Override
    public ViewportCamera getCamera() { return mCamera; }

    public void updateTransform() {
        if (mNodeLayer != null) {
            mNodeLayer.setTranslationX(0);
            mNodeLayer.setTranslationY(0);

            LayoutParams lp = (LayoutParams) mNodeLayer.getLayoutParams();
            if (lp == null) lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            lp.leftMargin = (int) mCamera.getX();
            lp.topMargin = (int) mCamera.getY();
            mNodeLayer.setLayoutParams(lp);

            mNodeLayer.setScaleX(mCamera.getScale());
            mNodeLayer.setScaleY(mCamera.getScale());

            if (mFrameLayer != null) {
                mFrameLayer.setTranslationX(0);
                mFrameLayer.setTranslationY(0);
                LayoutParams lpF = (LayoutParams) mFrameLayer.getLayoutParams();
                if (lpF == null) lpF = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                lpF.leftMargin = (int) mCamera.getX();
                lpF.topMargin = (int) mCamera.getY();
                mFrameLayer.setLayoutParams(lpF);
                mFrameLayer.setScaleX(mCamera.getScale());
                mFrameLayer.setScaleY(mCamera.getScale());
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
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mBackgroundLayer.draw(canvas, mCamera, getWidth(), getHeight());
        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mConnectionLayer.draw(canvas, mCamera);
        super.dispatchDraw(canvas);
        mInteractionManager.drawOverlay(canvas);
    }


    // ==========================================
    // 5. 纯粹的图元显示管道接口
    // ==========================================

    public Map<String, UINode> getNodeViews() { return mNodeLayer != null ? mNodeLayer.getNodeViews() : new HashMap<>(); }
    public void addNodeView(String nodeId, UINode uiNode) { if (mNodeLayer != null) { mNodeLayer.addNodeView(nodeId, uiNode); invalidate(); } }
    public void removeNodeView(String nodeId) { if (mNodeLayer != null) { mNodeLayer.removeNodeView(nodeId); invalidate(); } }
    public UINode getNodeView(String nodeId) { return mNodeLayer != null ? mNodeLayer.getNodeView(nodeId) : null; }
    public void updateNodePosition(String nodeId, float x, float y) { if (mNodeLayer != null) { mNodeLayer.updateNodePosition(nodeId, x, y); invalidate(); } }
    public void notifyNodeLayoutUpdate(String nodeId) { if (mNodeLayer != null) mNodeLayer.notifyNodeLayoutUpdate(nodeId); }

    public Map<String, UIFrame> getFrameViews() { return mFrameLayer != null ? mFrameLayer.getFrameViews() : new HashMap<>(); }
    public void addFrameView(String frameId, UIFrame uiFrame) { if (mFrameLayer != null) { mFrameLayer.addFrameView(frameId, uiFrame); invalidate(); } }
    public void removeFrameView(String frameId) { if (mFrameLayer != null) { mFrameLayer.removeFrameView(frameId); invalidate(); } }

    @Override public void updateFrameBounds(String frameId) { if (mFrameLayer != null) { mFrameLayer.updateFrameBounds(frameId); invalidate(); } }
    @Override public void updateConnectionsForNode(String nodeId) { mConnectionLayer.updateConnectionsForNode(nodeId); }
    public void rebuildVisualConnections() { mConnectionLayer.rebuildVisualConnections(); }
    public void previewFrameBounds(String frameId) { if (mFrameLayer != null) { mFrameLayer.previewFrameBounds(frameId); invalidate(); } }


    // ==========================================
    // 6. InteractionContext 接口闭环实现
    // ==========================================

    @Override public boolean isReady() { return mController != null && mController.hasActiveSession(); }
    @Override public UINode findNodeAt(float uiX, float uiY) { return mNodeLayer != null ? mNodeLayer.findNodeAt(uiX, uiY) : null; }
    @Override public PortInfo findPortAt(float uiX, float uiY) { return mNodeLayer != null ? mNodeLayer.findPortAt(uiX, uiY) : null; }
    @Override public UIFrame findFrameAt(float uiX, float uiY) { return mFrameLayer != null ? mFrameLayer.findFrameAt(uiX, uiY) : null; }
    @Override public UIFrame getSmallestContainingFrame(float uiX, float uiY) { return mFrameLayer != null ? mFrameLayer.getSmallestContainingFrame(uiX, uiY) : null; }
    @Override public Iterable<UIFrame> getAllFrames() { return mFrameLayer != null ? mFrameLayer.getFrameViews().values() : new ArrayList<>(); }

    @Override public void updateBoxSelection(float uiX, float uiY, float uiW, float uiH) { if (mNodeLayer != null) mNodeLayer.updateBoxSelection(uiX, uiY, uiW, uiH); }
    @Override public void moveSelectedNodes(float uiDx, float uiDy) { if (mNodeLayer != null) mNodeLayer.moveSelectedNodes(uiDx, uiDy); }
    @Override public List<UINode> getSelectedNodes() { return mNodeLayer != null ? mNodeLayer.getSelectedNodes() : new ArrayList<>(); }
    @Override public void moveFrameAndChildren(String frameId, float dx, float dy) { if (mFrameLayer != null) mFrameLayer.moveFrameAndChildren(frameId, dx, dy); }

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
    public void addToSelection(UINode node) {
        if (mNodeLayer != null) mNodeLayer.addToSelection(node);
    }
    @Override
    public void addToSelection(UIFrame frame) {
        if (mFrameLayer != null) mFrameLayer.addToSelection(frame);
    }
    @Override
    public List<UIFrame> getSelectedFrames() {
        return mFrameLayer != null ? mFrameLayer.getSelectedFrames() : new ArrayList<>();
    }
    public boolean isNodeSelected(String nodeId) { return mNodeLayer != null && mNodeLayer.isNodeSelected(nodeId); }
    public void updateSelectionState(List<String> selectedNodeIds) { if (mNodeLayer != null) { mNodeLayer.updateSelectionState(selectedNodeIds); invalidate(); } }

    @Override
    public boolean hasConnection(UINode outN, String outId, UINode inN, String inId) {
        List<com.mine.geometry_node.core.node.Connection> links = outN.getNodeData().getConnections(outId);
        if (links == null) return false;
        for (com.mine.geometry_node.core.node.Connection link : links) {
            if (link.targetNodeId().equals(inN.getNodeData().id) && link.targetPortName().equals(inId)) return true;
        }
        return false;
    }

    @Override
    public void showMenu(float screenX, float screenY) {
        closeMenu();
        mActiveMenu = new ViewportMenu(getContext());
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
    @Override public void requestRenamePort(String nodeId, String category, String portId, String oldName, String newName) { if (mController != null) mController.executeRenamePort(nodeId, category, portId, oldName, newName); }
    @Override public void requestSave() { if (mController != null) mController.onSaveRequested(); }
    @Override public void requestViewportFocus() { requestFocus(); }
    @Override public void addNodeToScene(UINode node) { if (mNodeLayer != null) mNodeLayer.addView(node); }
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
        if (event.isCtrlPressed() && event.getAction() == icyllis.modernui.view.KeyEvent.ACTION_DOWN) {
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
        public UINode node;
        public String portId;
        public boolean isInput;
        public PortInfo(UINode n, String id, boolean in) { this.node = n; this.portId = id; this.isInput = in; }
    }
}
// --- END OF FILE Viewport.java ---