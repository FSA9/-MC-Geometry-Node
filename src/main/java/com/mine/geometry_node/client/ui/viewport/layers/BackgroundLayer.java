package com.mine.geometry_node.client.ui.viewport.layers;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.ViewportCamera;
import com.mine.geometry_node.client.ui.persistence.ConfigManager;

public class BackgroundLayer {
    private final Paint mBackgroundPaint = new Paint();
    private final Paint mGridPaint = new Paint();

    public BackgroundLayer() {
        mBackgroundPaint.setColor(UIConstants.ViewPort.BG_COLOR);
        mGridPaint.setAntiAlias(true);
        mGridPaint.setStyle(Paint.Style.STROKE);
    }

    public void draw(Canvas canvas, ViewportCamera camera, int width, int height) {
        // 1. 绘制底色
        canvas.drawRect(0, 0, width, height, mBackgroundPaint);

        if (camera == null) return;

        float scale = camera.getScale();
        float cx = camera.getX();
        float cy = camera.getY();
        float scaledGrid = UIUtils.dp2px(ConfigManager.INSTANCE.getConfig().viewport.gridSize) * scale;

        if (scaledGrid < 5f) return; // 缩放太小时不绘制网格以保证性能

        float startX = cx % scaledGrid;
        if (startX > 0) startX -= scaledGrid;
        float startY = cy % scaledGrid;
        if (startY > 0) startY -= scaledGrid;

        // 2. 绘制普通网格线
        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_LINE);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_NORMAL);
        for (float x = startX; x < width; x += scaledGrid) {
            canvas.drawLine(x, 0, x, height, mGridPaint);
        }
        for (float y = startY; y < height; y += scaledGrid) {
            canvas.drawLine(0, y, width, y, mGridPaint);
        }

        // 3. 绘制世界坐标原点轴线
        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_AXIS);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_AXIS);
        if (cx >= 0 && cx <= width) canvas.drawLine(cx, 0, cx, height, mGridPaint);
        if (cy >= 0 && cy <= height) canvas.drawLine(0, cy, width, cy, mGridPaint);
    }
}