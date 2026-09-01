package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.VectorIconView;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;

final class AreaIconButton extends UiIconButton {
    interface HintSink {
        void showButtonHint(AreaIconButton button, String hint);

        void clearButtonHint(AreaIconButton button);
    }

    private final View mIconView;
    private final String mHint;
    private final HintSink mHintSink;
    private boolean mHovered;

    AreaIconButton(Context context, SvgIconView.Icon icon, String hint, HintSink hintSink) {
        this(context, new SvgIconView(context, icon, AreaStyle.COLOR_ICON), hint, hintSink);
    }

    AreaIconButton(Context context, VectorIconView.Kind kind, String hint, HintSink hintSink) {
        this(context, new VectorIconView(context, kind, AreaStyle.COLOR_ICON), hint, hintSink);
    }

    private AreaIconButton(Context context, View iconView, String hint, HintSink hintSink) {
        super(context, iconView, new UiIconButton.Style(2.0f, 0.0f,
                0x00000000, 0x00000000, 0x00000000, 0x00000000,
                0, 0.0f, 0.5f));
        mIconView = iconView;
        mHint = hint;
        mHintSink = hintSink;
        setOnHoverListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                updateHovered(true);
                if (mHintSink != null) {
                    mHintSink.showButtonHint(this, mHint);
                }
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                updateHovered(false);
                if (mHintSink != null) {
                    mHintSink.clearButtonHint(this);
                }
            }
            return true;
        });
    }

    void setIcon(SvgIconView.Icon icon) {
        if (mIconView instanceof SvgIconView svgIconView) {
            svgIconView.setIcon(icon);
        }
    }

    private void updateHovered(boolean hovered) {
        if (mHovered == hovered) {
            return;
        }
        mHovered = hovered;
        setIconColor(mHovered ? AreaStyle.COLOR_ICON_SELECTED : AreaStyle.COLOR_ICON);
        invalidate();
    }

    private void setIconColor(int color) {
        if (mIconView instanceof SvgIconView svgIconView) {
            svgIconView.setIconColor(color);
        } else if (mIconView instanceof VectorIconView vectorIconView) {
            vectorIconView.setIconColor(color);
        }
    }

}
