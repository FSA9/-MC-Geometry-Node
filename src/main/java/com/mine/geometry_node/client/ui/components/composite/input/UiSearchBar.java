package com.mine.geometry_node.client.ui.components.composite.input;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchSubmit;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;

/** Explicit search submission. Typing remains a draft; the square action and Enter share a callback. */
public final class UiSearchBar extends LinearLayout {
    public static final int CONTROL_SIZE_DP = 32;
    private final UiSearchInput input;
    private final UiIconButton submit;

    public UiSearchBar(Context context, CharSequence hint, CharSequence actionName, Runnable action) {
        this(context, hint, actionName, action, UiThemes.EDITOR);
    }

    public UiSearchBar(Context context, CharSequence hint, CharSequence actionName, Runnable action, UiTheme theme) {
        super(context);
        java.util.Objects.requireNonNull(theme, "theme");
        setOrientation(HORIZONTAL);
        setBaselineAligned(false);
        setGravity(Gravity.CENTER_VERTICAL);
        int size = UIUtils.dp2pxInt(CONTROL_SIZE_DP);
        input = UiSearchInput.createThemed(context, theme);
        input.setHint(hint);
        addView(input, new LayoutParams(0, size, 1));
        submit = UiIconButton.createThemed(context, theme, SvgIconView.Icon.SEARCH, actionName);
        UiSearchSubmit.bind(submit, action, input);
        LayoutParams params = new LayoutParams(size, size);
        params.leftMargin = UIUtils.dp2pxInt(6);
        addView(submit, params);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (input != null) input.setEnabled(enabled);
        if (submit != null) submit.setEnabled(enabled);
    }

    public UiSearchInput input() { return input; }
    public UiIconButton submitButton() { return submit; }
}
