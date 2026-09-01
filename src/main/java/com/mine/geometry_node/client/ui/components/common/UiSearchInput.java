package com.mine.geometry_node.client.ui.components.common;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.EditText;

import java.util.function.Consumer;

/** Shared visual style for search inputs; search execution remains owned by the caller. */
public final class UiSearchInput extends EditText {
    public static final Style DEFAULT_STYLE = new Style(
            14.0f,
            10.0f,
            3.0f,
            1.0f,
            0xFFE6E6E6,
            0xFF737B86,
            0xFF202020,
            0xFF484848
    );

    public UiSearchInput(Context context) {
        this(context, DEFAULT_STYLE);
    }

    public UiSearchInput(Context context, Style style) {
        super(context);
        Style resolved = style != null ? style : DEFAULT_STYLE;
        setSingleLine(true);
        setGravity(Gravity.CENTER_VERTICAL);
        setTextColor(resolved.textColor());
        setHintTextColor(resolved.hintColor());
        setPadding(UIUtils.dp2pxInt(resolved.horizontalPaddingDp()), 0,
                UIUtils.dp2pxInt(resolved.horizontalPaddingDp()), 0);
        UIUtils.setLockedTextSize(this, resolved.textSizeDp());
        setBackground(background(resolved));
    }

    public void setOnQueryChanged(Consumer<String> listener) {
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (listener != null) listener.accept(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private static ShapeDrawable background(Style style) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(style.backgroundColor());
        drawable.setCornerRadius(UIUtils.dp2px(style.cornerRadiusDp()));
        if (style.strokeWidthDp() > 0.0f) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(style.strokeWidthDp())), style.borderColor());
        }
        return drawable;
    }

    /** Visual parameters in density-independent units. */
    public record Style(
            float textSizeDp,
            float horizontalPaddingDp,
            float cornerRadiusDp,
            float strokeWidthDp,
            int textColor,
            int hintColor,
            int backgroundColor,
            int borderColor
    ) {
    }
}
