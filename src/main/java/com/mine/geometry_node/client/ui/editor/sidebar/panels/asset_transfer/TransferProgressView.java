package com.mine.geometry_node.client.ui.editor.sidebar.panels.asset_transfer;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;

final class TransferProgressView extends View {
    private static final int COLOR_TRACK = 0xFF202020;
    private static final int COLOR_FILL = 0xFF4C88B8;
    private static final int COLOR_COMPLETE = 0xFF5B9B62;

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private float progress;

    TransferProgressView(Context context, double progress) {
        super(context);
        this.progress = (float) Math.clamp(progress, 0.0, 1.0);
        paint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = UIUtils.dp2px(1.0f);
        float radius = UIUtils.dp2px(1.5f);
        rect.set(0, 0, getWidth(), getHeight());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_TRACK);
        canvas.drawRoundRect(rect, radius, radius, radius, radius, paint);
        if (progress <= 0.0f) return;
        rect.set(inset, inset, Math.max(inset, getWidth() * progress - inset), getHeight() - inset);
        paint.setColor(progress >= 1.0f ? COLOR_COMPLETE : COLOR_FILL);
        canvas.drawRoundRect(rect, radius, radius, radius, radius, paint);
    }
}
