package com.mine.geometry_node.client.ui.components.composite.menu;

import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/** Shared search/list geometry; callers own filtering, navigation and popup lifecycle. */
public final class UiSearchMenuLayout {
    private static final int TITLE_HEIGHT_DP = 22;
    private static final int TITLE_GAP_DP = 6;
    private static final int SEARCH_HEIGHT_DP = 28;
    private static final int SEARCH_LIST_GAP_DP = 8;

    private UiSearchMenuLayout() {}

    /** Adds only menu chrome. Filtering, category navigation and scrolling belong to the caller.
     * @return the search input, or null when search is disabled
     */
    public static UiSearchInput addHeader(LinearLayout content, String title, boolean searchable) {
        if (title != null && !title.isBlank()) {
            TextView heading = UIUtils.createLockedTextView(content.getContext(), title, 12, UiTheme.INLINE.text());
            heading.setSingleLine(true);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
            ShapeDrawable background = new ShapeDrawable();
            background.setColor(UiTheme.Control.INPUT);
            background.setCornerRadius(UIUtils.dp2px(4));
            heading.setBackground(background);
            content.addView(heading, titleLayoutParams());
        }
        if (!searchable) return null;
        UiSearchInput search = new UiSearchInput(content.getContext());
        search.setHint(Component.translatable("geometry_node.asset_library.toolbar.search").getString());
        content.addView(search, searchLayoutParams());
        return search;
    }

    public static LinearLayout.LayoutParams titleLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(TITLE_HEIGHT_DP));
        params.bottomMargin = UIUtils.dp2pxInt(TITLE_GAP_DP);
        return params;
    }

    /** Fresh params keep the gap outside list contents rebuilt during filtering. */
    public static LinearLayout.LayoutParams searchLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(SEARCH_HEIGHT_DP));
        params.bottomMargin = UIUtils.dp2pxInt(SEARCH_LIST_GAP_DP);
        return params;
    }
}
