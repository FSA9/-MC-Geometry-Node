// --- START OF FILE ViewportCamera.java ---
package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.RectF;

/**
 * 视口摄像机管理器
 * 专门负责处理画布的平移(Pan)、缩放(Zoom)以及屏幕与逻辑坐标系的互相转换。
 */
public class ViewportCamera {
    private float mX = 0;
    private float mY = 0;
    private float mScale = 1.0f;

    // 当摄像机矩阵发生改变时，通知 UI 重绘/更新 LayoutParams
    private final Runnable mOnTransformChanged;

    public ViewportCamera(Runnable onTransformChanged) {
        this.mOnTransformChanged = onTransformChanged;
    }

    // --- 状态获取与设置 ---

    public float getX() { return mX; }
    public float getY() { return mY; }
    public float getScale() { return mScale; }

    public void setPosition(float x, float y) {
        mX = x;
        mY = y;
        notifyChange();
    }

    public void setScale(float scale) {
        mScale = scale;
        notifyChange();
    }

    // --- 交互操作 ---

    public void pan(float dx, float dy) {
        mX += dx;
        mY += dy;
        notifyChange();
    }

    public void zoom(boolean zoomIn, float pivotScreenX, float pivotScreenY) {
        float oldScale = mScale;
        float factor = zoomIn ? UIConstants.ViewPort.ZOOM_SENSITIVITY : -UIConstants.ViewPort.ZOOM_SENSITIVITY;

        mScale = Math.max(UIConstants.ViewPort.ZOOM_MIN,
                Math.min(UIConstants.ViewPort.ZOOM_MAX, oldScale + factor));

        if (mScale == oldScale) return;

        float ratio = mScale / oldScale;
        mX = pivotScreenX - (pivotScreenX - mX) * ratio;
        mY = pivotScreenY - (pivotScreenY - mY) * ratio;

        notifyChange();
    }

    // --- 坐标系转换 ---

    public float screenToUIX(float screenX) {
        return UIUtils.px2dp((screenX - mX) / mScale);
    }

    public float screenToUIY(float screenY) {
        return UIUtils.px2dp((screenY - mY) / mScale);
    }

    public float uiToScreenX(float uiX) {
        return UIUtils.dp2px(uiX) * mScale + mX;
    }

    public float uiToScreenY(float uiY) {
        return UIUtils.dp2px(uiY) * mScale + mY;
    }

    public void getVisibleUiRect(RectF outRect, int viewportWidthPx, int viewportHeightPx, float paddingDp) {
        float x1 = screenToUIX(0);
        float y1 = screenToUIY(0);
        float x2 = screenToUIX(viewportWidthPx);
        float y2 = screenToUIY(viewportHeightPx);

        outRect.set(
                Math.min(x1, x2) - paddingDp,
                Math.min(y1, y2) - paddingDp,
                Math.max(x1, x2) + paddingDp,
                Math.max(y1, y2) + paddingDp
        );
    }

    private void notifyChange() {
        if (mOnTransformChanged != null) {
            mOnTransformChanged.run();
        }
    }
}
// --- END OF FILE ViewportCamera.java ---
