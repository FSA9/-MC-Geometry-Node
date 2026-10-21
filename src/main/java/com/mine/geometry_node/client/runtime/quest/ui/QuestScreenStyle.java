package com.mine.geometry_node.client.runtime.quest.ui;

import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiThemes;

/** Quest-specific translucent layers and preview decoration derived from the HUD preset. */
final class QuestScreenStyle {
    static final int OVERLAY_DIM = UiTheme.withAlpha(UiThemes.HUD.surface().overlayDim(), 0x52);
    static final int SURFACE_HOVER = UiTheme.withAlpha(UiThemes.HUD.surface().hover(), 0xA8);
    static final int DIVIDER = UiTheme.withAlpha(UiThemes.HUD.border().divider(), 0x30);
    static final int BUTTON = UiTheme.withAlpha(UiThemes.HUD.buttons().secondary().normal(), 0xA0);
    static final int BUTTON_HOVER = UiTheme.withAlpha(UiThemes.HUD.buttons().secondary().hover(), 0xC0);
    static final ValuePreviewStyle PREVIEW_STYLE = new ValuePreviewStyle(
            1.0f, 1.0f, UiTheme.withAlpha(UiThemes.HUD.surface().panel(), 0x48),
            DIVIDER, UiThemes.HUD.border().focus());

    private QuestScreenStyle() {}
}
