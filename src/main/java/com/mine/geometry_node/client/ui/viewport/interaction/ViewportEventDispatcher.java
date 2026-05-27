package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.Viewport;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.client.ui.viewport.visual.NodeVisualAdapter;

import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;

/**
 * 视口事件调度器
 * 专门负责处理底层的坐标系转换、节点交互元素的射线检测(Hit-Test)以及事件的拦截分发。
 */
public class ViewportEventDispatcher {
    private static final int TOOL_TYPE_MOUSE = 1;

    private final Viewport mViewport;

    // 状态与缓存 (从原 Viewport 中剥离)
    private View mCapturedHintView;
    private boolean mHintCaptureUsesLogical;
    private float mLastMouseScreenX = 0;
    private float mLastMouseScreenY = 0;

    private final float[] mTmpEventScreen = new float[2];
    private final int[] mTmpTargetLoc = new int[2];

    private record HintHitResult(View view, boolean isLogical, NodeVisualAdapter node) {}

    public ViewportEventDispatcher(Viewport viewport) {
        this.mViewport = viewport;
    }

    public float getLastMouseScreenX() { return mLastMouseScreenX; }
    public float getLastMouseScreenY() { return mLastMouseScreenY; }

    /**
     * 处理主要的触摸事件
     * @return 如果事件被拦截器消化了，返回 true；如果需要 Viewport 继续跑 super 逻辑，返回 false。
     */
    public boolean handleTouchEvent(MotionEvent ev, boolean isHitOverlay) {
        mLastMouseScreenX = ev.getX();
        mLastMouseScreenY = ev.getY();
        int action = ev.getActionMasked();

        // 1. 如果当前有捕获的交互控件（例如滑动输入框），优先将事件灌给它
        if (mCapturedHintView != null) {
            boolean r = dispatchTransformedEvent(ev, mCapturedHintView, mHintCaptureUsesLogical, !mHintCaptureUsesLogical);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mCapturedHintView = null;
                mHintCaptureUsesLogical = false;
            }
            return r;
        }

        // 2. 如果点到了悬浮菜单或非图元UI，直接放行给 super
        if (isHitOverlay) return false;

        // 3. 探测是否点到了节点内部的 UI 交互件（比如 Add/Remove 按钮、输入框）
        if (ev.getPointerCount() == 1) {
            boolean isMouseHoverMove = (action == MotionEvent.ACTION_MOVE && ev.getButtonState() == 0 && ev.getToolType(0) == TOOL_TYPE_MOUSE);
            boolean isActionDown = (action == MotionEvent.ACTION_DOWN);

            if (isMouseHoverMove || isActionDown) {
                HintHitResult hitResult = findInteractiveHint(ev);
                if (hitResult != null) {
                    View targetView = hitResult.view();
                    if (isActionDown) {
                        if (!mViewport.getSelectedNodeVisuals().contains(hitResult.node())) {
                            mViewport.clearSelection();
                        }
                        mViewport.addToSelection(hitResult.node());
                        mViewport.invalidate();
                    }

                    boolean handled = dispatchTransformedEvent(ev, targetView, hitResult.isLogical(), !hitResult.isLogical());
                    if (handled) {
                        if (isActionDown) {
                            mCapturedHintView = targetView;
                            mHintCaptureUsesLogical = hitResult.isLogical();
                        }
                        return true;
                    }
                } else if (isActionDown) {
                    // 点到了空白区域或节点背景，请求焦点
                    mViewport.requestViewportFocus();
                }
            }
        }
        return false;
    }

    public boolean handleGenericMotionEvent(MotionEvent ev, boolean isHitOverlay) {
        mLastMouseScreenX = ev.getX();
        mLastMouseScreenY = ev.getY();

        if (isHitOverlay) return false;

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_EXIT) {
            HintHitResult hitResult = findInteractiveHint(ev);
            if (hitResult != null) {
                if (dispatchTransformedEvent(ev, hitResult.view(), hitResult.isLogical(), !hitResult.isLogical())) return true;
            }
        }
        return false;
    }

    public PointerIcon resolvePointerIcon(MotionEvent event) {
        HintHitResult hitResult = findInteractiveHint(event);
        if (hitResult != null) {
            return (hitResult.view() instanceof EditText) ? PointerIcon.getSystemIcon(PointerIcon.TYPE_TEXT) : PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
        }
        return null;
    }

    private void eventToScreen(MotionEvent ev) {
        mTmpEventScreen[0] = ev.getRawX();
        mTmpEventScreen[1] = ev.getRawY();
    }

    private HintHitResult findInteractiveHint(MotionEvent ev) {
        ViewportCamera camera = mViewport.getCamera();
        float uiX = camera.screenToUIX(ev.getX());
        float uiY = camera.screenToUIY(ev.getY());

        NodeVisualAdapter topNode = mViewport.findNodeAt(uiX, uiY);
        if (topNode != null) {
            float localXpx = UIUtils.dp2px(uiX - topNode.getUiX());
            float localYpx = UIUtils.dp2px(uiY - topNode.getUiY());

            View interactiveView = topNode.findInteractiveViewAt(localXpx, localYpx);
            if (interactiveView != null) {
                eventToScreen(ev);
                return new HintHitResult(interactiveView, true, topNode);
            }
        }
        return null;
    }

    private boolean dispatchTransformedEvent(MotionEvent ev, View target, boolean isLogical, boolean skipEventToScreen) {
        float ox = ev.getX();
        float oy = ev.getY();
        float lx, ly;
        ViewportCamera camera = mViewport.getCamera();

        if (isLogical) {
            NodeVisualAdapter node = findNodeVisualForOverlay(target);
            if (node == null) return false;

            float uiX = camera.screenToUIX(ox);
            float uiY = camera.screenToUIY(oy);

            // 初始化相对于节点 overlay host 的局部像素坐标
            lx = UIUtils.dp2px(uiX - node.getUiX());
            ly = UIUtils.dp2px(uiY - node.getUiY());

            // 逐层减去所有父容器的相对偏移量
            View overlayHost = node.getOverlayHostView();
            View current = target;
            while (current != overlayHost && current != null) {
                lx -= current.getLeft();
                ly -= current.getTop();
                current = (View) current.getParent();
            }
        } else {
            if (!skipEventToScreen) eventToScreen(ev);
            target.getLocationOnScreen(mTmpTargetLoc);
            lx = (mTmpEventScreen[0] - mTmpTargetLoc[0]) / camera.getScale();
            ly = (mTmpEventScreen[1] - mTmpTargetLoc[1]) / camera.getScale();
        }

        ev.setLocation(lx, ly);
        boolean handled = target.dispatchTouchEvent(ev);
        ev.setLocation(ox, oy);
        return handled;
    }

    private NodeVisualAdapter findNodeVisualForOverlay(View target) {
        for (NodeVisualAdapter node : mViewport.getNodeVisuals().values()) {
            View overlayHost = node.getOverlayHostView();
            View current = target;
            while (current != null) {
                if (current == overlayHost) {
                    return node;
                }
                if (!(current.getParent() instanceof View parentView)) {
                    break;
                }
                current = parentView;
            }
        }
        return null;
    }
}
