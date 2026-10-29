package com.mine.geometry_node.client.ui.components.composite.menu;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
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
        return addHeader(content, title, searchable, UiThemes.EDITOR);
    }

    public static UiSearchInput addHeader(LinearLayout content, String title, boolean searchable, UiTheme theme) {
        java.util.Objects.requireNonNull(theme, "theme");
        if (title != null && !title.isBlank()) {
            TextView heading = UIUtils.createLockedTextView(content.getContext(), title, 12, theme.buttons().inline().text());
            heading.setSingleLine(true);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
            heading.setBackground(rect(theme.control().input(), 4, 0, 0));
            content.addView(heading, titleLayoutParams());
        }
        if (!searchable) return null;
        UiSearchInput search = new UiSearchInput(content.getContext(), UiTextInput.Style.forTheme(theme,
                UiMetrics.TEXT_SEARCH, 10, UiMetrics.RADIUS_INPUT, UiMetrics.BORDER));
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
