package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;

/** Shared command button for dialog and popup action rows. */
public final class UiActionButton extends Button {
    public static final Style DEFAULT_STYLE = new Style(2.0f, 1.0f, 0xFF5C5C5C, 0.48f);

    public enum Role {
        PRIMARY(0xFF2F7DDE, 0xFF3D8BEA, 0xFF2768BA, 0xFFFFFFFF),
        SECONDARY(0xFF454545, 0xFF525252, 0xFF383838, 0xFFD8D8D8),
        INLINE(0xFF30343B, 0xFF3A4049, 0xFF292D33, 0xFFBFC7D5),
        DANGER(0xFF9A3D3D, 0xFFAC4A4A, 0xFF7C3030, 0xFFFFFFFF),
        QUIET(0xFF4C6B4C, 0xFF5B7B5B, 0xFF3D583D, 0xFFE8F0E8);

        private final int normal;
        private final int hover;
        private final int pressed;
        private final int text;

        Role(int normal, int hover, int pressed, int text) {
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
            this.text = text;
        }
    }

    public enum Density {
        COMPACT(10.0f),
        NORMAL(10.5f);

        private final float textSize;

        Density(float textSize) {
            this.textSize = textSize;
        }
    }

    private final Role role;
    private final Style style;
    private boolean hovered;

    public UiActionButton(Context context, CharSequence text, Role role, Density density) {
        this(context, text, role, density, DEFAULT_STYLE);
    }

    public UiActionButton(Context context, CharSequence text, Role role, Density density, Style style) {
        super(context);
        this.role = role != null ? role : Role.SECONDARY;
        this.style = style != null ? style : DEFAULT_STYLE;
        Density resolvedDensity = density != null ? density : Density.NORMAL;
        setText(text != null ? text : "");
        setGravity(Gravity.CENTER);
        setTextColor(this.role.text);
        UIUtils.setLockedTextSize(this, resolvedDensity.textSize);
        setBackground(background(this.role.normal));
        setOnHoverListener(this::handleHover);
        setOnTouchListener(this::handleTouch);
    }

    public static UiActionButton create(Context context, CharSequence text, Role role,
                                        View.OnClickListener listener) {
        return create(context, text, role, Density.NORMAL, DEFAULT_STYLE, listener);
    }

    public static UiActionButton create(Context context, CharSequence text, Role role,
                                        Density density, Style style, View.OnClickListener listener) {
        UiActionButton button = new UiActionButton(context, text, role, density, style);
        button.setOnClickListener(listener);
        return button;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        hovered = false;
        setAlpha(enabled ? 1.0f : style.disabledAlpha());
        Role resolvedRole = role != null ? role : Role.SECONDARY;
        setBackground(background(resolvedRole.normal));
    }

    private boolean handleHover(View view, MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
            hovered = true;
            setBackground(background(role.hover));
        } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            hovered = false;
            setBackground(background(role.normal));
        }
        return false;
    }

    private boolean handleTouch(View view, MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            setBackground(background(role.pressed));
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            setBackground(background(hovered ? role.hover : role.normal));
        }
        return false;
    }

    private ShapeDrawable background(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        if (style.strokeWidthDp() > 0.0f) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), style.strokeColor());
        }
        return drawable;
    }

    /** Visual parameters in density-independent units; role and density remain semantic choices. */
    public record Style(float cornerRadiusDp, float strokeWidthDp, int strokeColor, float disabledAlpha) {
    }
}
