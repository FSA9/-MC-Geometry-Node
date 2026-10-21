package com.mine.geometry_node.client.ui.components.base.input;

import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import java.util.function.Consumer;

/** Shared visual style for search inputs; search execution remains owned by the caller. */
public final class UiSearchInput extends UiTextInput {
    private Consumer<String> onQueryChanged;
    public static final Style DEFAULT_STYLE = searchStyle(UiThemes.EDITOR);

    /** Uses search geometry while resolving every input state from the supplied theme. */
    public static UiSearchInput createThemed(Context context, UiTheme theme) {
        return new UiSearchInput(context, searchStyle(theme));
    }

    private static Style searchStyle(UiTheme theme) {
        return Style.forTheme(theme, UiMetrics.TEXT_SEARCH, 10.0f, UiMetrics.RADIUS_INPUT, UiMetrics.BORDER);
    }

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
