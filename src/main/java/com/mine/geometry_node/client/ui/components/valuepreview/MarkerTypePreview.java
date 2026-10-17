package com.mine.geometry_node.client.ui.components.valuepreview;

import com.mine.geometry_node.core.engine.system.marker.MarkerType;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

/** Passive marker swatch using registered color metadata, without creating a world marker. */
public final class MarkerTypePreview extends View {
    private final int color;
    private final Paint paint = new Paint();
    public MarkerTypePreview(Context context, String typeId) {
        super(context);
        MarkerType type = MarkerTypeRegistry.INSTANCE.get(typeId);
        color = type == null ? 0xFFFFFFFF : type.color();
        setWillNotDraw(false);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = size * 0.22f;
        paint.setColor(color);
        canvas.drawRect(cx-radius, cy-radius, cx+radius, cy+radius, paint);
        paint.setColor(0xFF101214);
        float inset = size * 0.025f;
        canvas.drawRect(cx-radius+inset, cy-radius+inset, cx+radius-inset, cy+radius-inset, paint);
        paint.setColor(color);
        radius *= 0.54f;
        canvas.drawRect(cx-radius, cy-radius, cx+radius, cy+radius, paint);
        paint.setColor(0xFFFFFFFF);
        radius *= 0.4f;
        canvas.drawRect(cx-radius, cy-radius, cx+radius, cy+radius, paint);
    }
}
