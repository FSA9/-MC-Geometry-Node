package com.mine.geometry_node.client.ui.editor.graph.layers;

import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.editor.graph.ViewportCamera;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;

public class BackgroundLayer {
    private static final float MIN_GRID_SPACING_PX = 12f;
    private static final float FULL_ALPHA_GRID_SPACING_PX = 28f;
    private static final int MAJOR_GRID_INTERVAL = 5;
    private static final int MINOR_GRID_MAX_ALPHA = 165;
    private static final int MAJOR_GRID_ALPHA = 235;
    private static final int AXIS_ALPHA = 255;
    private static final int COLOR_GRID_MAJOR = 0xFF303030;

    private final Paint mBackgroundPaint = new Paint();
    private final Paint mGridPaint = new Paint();
    private boolean mGridAndAxisVisible = true;

    public BackgroundLayer() {
        mBackgroundPaint.setColor(UIConstants.ViewPort.BG_COLOR);
        mGridPaint.setAntiAlias(true);
        mGridPaint.setStyle(Paint.Style.STROKE);
    }

    public void draw(Canvas canvas, ViewportCamera camera, int width, int height) {
        // 1. 绘制底色
        canvas.drawRect(0, 0, width, height, mBackgroundPaint);

        if (!mGridAndAxisVisible) return;
        if (camera == null || width <= 0 || height <= 0) return;

        float scale = camera.getScale();
        float cx = camera.getX();
        float cy = camera.getY();
        float baseGridPx = UIUtils.dp2px(Math.max(1, ConfigManager.INSTANCE.getConfig().viewport.gridSize));
        float gridSpacingPx = getAdaptiveGridSpacing(baseGridPx, scale);

        // 2. 绘制自适应网格：缩小时减少线密度，普通线低于可见阈值时不绘制。
        int minorAlpha = getMinorGridAlpha(gridSpacingPx);
        if (minorAlpha > 0) {
            mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_LINE);
            mGridPaint.setAlpha(minorAlpha);
            mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_NORMAL);
            drawGridLines(canvas, cx, cy, width, height, gridSpacingPx, true);
        }

        mGridPaint.setColor(COLOR_GRID_MAJOR);
        mGridPaint.setAlpha(MAJOR_GRID_ALPHA);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_NORMAL);
        drawGridLines(canvas, cx, cy, width, height, gridSpacingPx * MAJOR_GRID_INTERVAL, false);

        // 3. 绘制世界坐标原点轴线，保持屏幕线宽稳定。
        mGridPaint.setColor(UIConstants.ViewPort.COLOR_GRID_AXIS);
        mGridPaint.setAlpha(AXIS_ALPHA);
        mGridPaint.setStrokeWidth(UIConstants.ViewPort.LINE_WIDTH_AXIS);
        if (cx >= 0 && cx <= width) canvas.drawLine(cx, 0, cx, height, mGridPaint);
        if (cy >= 0 && cy <= height) canvas.drawLine(0, cy, width, cy, mGridPaint);
    }

    public void setGridAndAxisVisible(boolean visible) {
        mGridAndAxisVisible = visible;
    }

    public boolean isGridAndAxisVisible() {
        return mGridAndAxisVisible;
    }

    private float getAdaptiveGridSpacing(float baseGridPx, float scale) {
        float spacing = baseGridPx * scale;
        while (spacing < MIN_GRID_SPACING_PX) {
            spacing *= 2f;
        }
        return spacing;
    }

    private int getMinorGridAlpha(float gridSpacingPx) {
        float t = (gridSpacingPx - MIN_GRID_SPACING_PX) / (FULL_ALPHA_GRID_SPACING_PX - MIN_GRID_SPACING_PX);
        if (t <= 0f) return 0;
        if (t >= 1f) return MINOR_GRID_MAX_ALPHA;
        return Math.round(MINOR_GRID_MAX_ALPHA * t);
    }

    private void drawGridLines(Canvas canvas, float originX, float originY, int width, int height, float spacingPx, boolean skipMajorLines) {
        long firstX = (long) Math.floor(-originX / spacingPx);
        long lastX = (long) Math.ceil((width - originX) / spacingPx);
        for (long index = firstX; index <= lastX; index++) {
            if (skipMajorLines && index % MAJOR_GRID_INTERVAL == 0) continue;
            float x = originX + index * spacingPx;
            if (x < 0f || x > width) continue;
            canvas.drawLine(x, 0, x, height, mGridPaint);
        }

        long firstY = (long) Math.floor(-originY / spacingPx);
        long lastY = (long) Math.ceil((height - originY) / spacingPx);
        for (long index = firstY; index <= lastY; index++) {
            if (skipMajorLines && index % MAJOR_GRID_INTERVAL == 0) continue;
            float y = originY + index * spacingPx;
            if (y < 0f || y > height) continue;
            canvas.drawLine(0, y, width, y, mGridPaint);
        }
    }
}
