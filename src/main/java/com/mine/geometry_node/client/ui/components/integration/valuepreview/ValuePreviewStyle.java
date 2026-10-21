package com.mine.geometry_node.client.ui.components.integration.valuepreview;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;


/**
 * Instance-local decoration only; never changes preview content, input policy or rendering scale.
 * The focus border marks keyboard focus only; populated picker slots have a separate color.
 */
public record ValuePreviewStyle(float cornerRadiusDp, float strokeWidthDp,
                                int backgroundColor, int borderColor, int focusBorderColor) {
    public static final ValuePreviewStyle DEFAULT = forTheme(UiThemes.EDITOR);

    public static ValuePreviewStyle forTheme(UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        return new ValuePreviewStyle(1.0f, 1.0f, theme.surface().panel(), theme.border().subtle(), theme.border().focus());
    }

    public ValuePreviewStyle {
        if (!Float.isFinite(cornerRadiusDp) || cornerRadiusDp < 0
                || !Float.isFinite(strokeWidthDp) || strokeWidthDp < 0) {
            throw new IllegalArgumentException("Preview decoration dimensions must be finite and nonnegative");
        }
    }

    public ValuePreviewStyle withColors(int background, int border, int focusBorder) {
        return new ValuePreviewStyle(cornerRadiusDp, strokeWidthDp, background, border, focusBorder);
    }
}
