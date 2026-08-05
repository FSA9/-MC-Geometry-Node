package com.mine.geometry_node.client.quest.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.RectF;
import icyllis.modernui.view.View;

final class QuestProgressBar extends View {
    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    private float progress;

    QuestProgressBar(Context context, double current, double target) {
        super(context);
        setWillNotDraw(false);
        progress = target > 0.0 ? (float) Math.max(0.0, Math.min(1.0, current / target)) : 0.0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.max(1.0f, getHeight() * 0.5f);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF24282E);
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, radius, radius, radius, radius, paint);
        if (progress > 0.0f) {
            paint.setColor(progress >= 1.0f ? 0xFF55B96B : 0xFF4DA3FF);
            rect.set(0, 0, getWidth() * progress, getHeight());
            canvas.drawRoundRect(rect, radius, radius, radius, radius, paint);
        }
    }
}
