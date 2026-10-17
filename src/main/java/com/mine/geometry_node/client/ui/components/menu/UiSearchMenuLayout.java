package com.mine.geometry_node.client.ui.components.menu;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;

/** Shared search/list geometry; callers own filtering, navigation and popup lifecycle. */
public final class UiSearchMenuLayout {
    private static final int SEARCH_HEIGHT_DP = 28;
    private static final int SEARCH_LIST_GAP_DP = 8;

    private UiSearchMenuLayout() {}

    /** Fresh params keep the gap outside list contents rebuilt during filtering. */
    public static LinearLayout.LayoutParams searchLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(SEARCH_HEIGHT_DP));
        params.bottomMargin = UIUtils.dp2pxInt(SEARCH_LIST_GAP_DP);
        return params;
    }
}
