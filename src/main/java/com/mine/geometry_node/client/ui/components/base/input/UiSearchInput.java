package com.mine.geometry_node.client.ui.components.base.input;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;

import java.util.function.Consumer;

/** Shared visual style for search inputs; search execution remains owned by the caller. */
public final class UiSearchInput extends UiTextInput {
    private Consumer<String> onQueryChanged;
    public static final Style DEFAULT_STYLE = new Style(
            UiTheme.Metrics.TEXT_SEARCH,
            10.0f,
            UiTheme.Metrics.RADIUS_INPUT,
            UiTheme.Metrics.BORDER,
            UiTheme.Text.NORMAL,
            UiTheme.Text.HINT,
            UiTheme.Control.INPUT,
            UiTheme.Control.INPUT_BORDER
    );

    public UiSearchInput(Context context) {
        this(context, DEFAULT_STYLE);
    }

    public UiSearchInput(Context context, Style style) {
        super(context, style != null ? style : DEFAULT_STYLE);
        setSingleLine(true);
        setGravity(Gravity.CENTER_VERTICAL);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Consumer<String> listener = onQueryChanged;
                if (listener != null) listener.accept(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** Replaces the query listener; null clears it. Programmatic text changes still notify. */
    public void setOnQueryChanged(Consumer<String> listener) {
        onQueryChanged = listener;
    }
}
