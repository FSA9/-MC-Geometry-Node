package com.mine.geometry_node.client.ui.editor.graph.menu;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

/** Opaque document-color choices shared by frame and group property menus. */
final class GraphColorPalette extends LinearLayout {
    private int selectedColor;

    GraphColorPalette(Context context, int initialColor, int[] presets) {
        super(context);
        setOrientation(HORIZONTAL);
        selectedColor = initialColor | 0xFF000000;
        addColor(selectedColor);
        for (int color : presets) {
            if ((color | 0xFF000000) != selectedColor) addColor(color | 0xFF000000);
        }
    }

    int selectedColor() {
        return selectedColor;
    }

    private void addColor(int color) {
        Swatch swatch = new Swatch(getContext(), color);
        swatch.setSelected(color == selectedColor);
        swatch.setOnClickListener(view -> {
            selectedColor = color;
            for (int i = 0; i < getChildCount(); i++) {
                Swatch child = (Swatch) getChildAt(i);
                child.setSelected(child.color == selectedColor);
            }
        });
        LayoutParams params = new LayoutParams(UIUtils.dp2pxInt(20), UIUtils.dp2pxInt(20));
        params.rightMargin = UIUtils.dp2pxInt(3);
        addView(swatch, params);
    }

    private static final class Swatch extends View {
        private final int color;
        private final Paint paint = new Paint();

        Swatch(Context context, int color) {
            super(context);
            this.color = color;
            paint.setAntiAlias(true);
            setWillNotDraw(false);
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = UIUtils.dp2px(2);
            float radius = UIUtils.dp2px(4);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                    radius, radius, radius, radius, paint);
            if (isSelected() || isEnabled() && isHovered()) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(UIUtils.dp2px(isSelected() ? 2 : 1));
                paint.setColor(isSelected() ? UiThemes.EDITOR.selection().outline()
                        : (UiThemes.EDITOR.text().strong() & 0x00FFFFFF) | 0x99000000);
                canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset,
                        radius, radius, radius, radius, paint);
            }
        }
    }
}
