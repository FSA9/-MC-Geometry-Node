package com.mine.geometry_node.client.ui.components.sidebar;

import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelRegistry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Editor-owned, Blender-style registration with a content host and a vertical tab rail.
 */
public final class EditorSidebar extends LinearLayout {
    private static final int COLOR_BACKGROUND = UiTheme.Surface.SIDEBAR;
    private static final int COLOR_HEADER = UiTheme.Surface.HEADER;
    private static final int COLOR_BORDER = UiTheme.Surface.CANVAS;
    private static final int COLOR_TEXT = UiTheme.Text.NORMAL;
    private static final int COLOR_MUTED = UiTheme.Text.MUTED;
    private static final int COLOR_HOVER = UiTheme.Control.ICON_HOVER;
    private static final int COLOR_TAB_RAIL = UiTheme.Control.INPUT;
    private static final int COLOR_TAB_SELECTED = UiTheme.Control.SELECTED;
    private static final int TAB_RAIL_WIDTH_DP = 36;

    private final Map<String, PanelEntry> mPanels = new LinkedHashMap<>();
    private final LinearLayout mPanelColumn;
    private final TextView mTitleView;
    private final FrameLayout mContentHost;
    private final ScrollView mTabScroll;
    private final LinearLayout mTabStrip;
    private PanelEntry mSelected;
    private boolean mPanelsActive = true;
    private boolean mContentVisible = true;
    private Runnable mOnCollapseRequested;
    private Runnable mOnExpandRequested;
    private Consumer<String> mOnSelectedPanelChanged;

    public EditorSidebar(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackground(rect(COLOR_BACKGROUND, 0.0f, 0, 0));

        mPanelColumn = new LinearLayout(context);
        mPanelColumn.setOrientation(VERTICAL);
        addView(mPanelColumn, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(5), 0);
        header.setBackground(rect(COLOR_HEADER, 0.0f, 1, COLOR_BORDER));

        mTitleView = label(context, "", 12.0f, COLOR_TEXT);
        header.addView(mTitleView, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView collapseIcon = label(context, "›", 18.0f, COLOR_MUTED);
        collapseIcon.setGravity(Gravity.CENTER);
        UiIconButton collapse = new UiIconButton(context, collapseIcon).tooltip(tr("geometry_node.sidebar.collapse"));
        collapse.setOnClickListener(v -> {
            if (mOnCollapseRequested != null) mOnCollapseRequested.run();
        });
        header.addView(collapse, new LayoutParams(UIUtils.dp2pxInt(24), UIUtils.dp2pxInt(24)));

        mPanelColumn.addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        mContentHost = new FrameLayout(context);
        mPanelColumn.addView(mContentHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        mTabScroll = new ScrollView(context);
        mTabScroll.setVerticalScrollBarEnabled(false);
        mTabScroll.setBackground(rect(COLOR_TAB_RAIL, 0.0f, 0, 0));
        mTabStrip = new LinearLayout(context);
        mTabStrip.setOrientation(VERTICAL);
        mTabStrip.setPadding(UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(3), UIUtils.dp2pxInt(4));
        mTabScroll.addView(mTabStrip, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mTabScroll, new LayoutParams(
                UIUtils.dp2pxInt(TAB_RAIL_WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setOnCollapseRequested(Runnable listener) {
        mOnCollapseRequested = listener;
    }

    public void setOnExpandRequested(Runnable listener) {
        mOnExpandRequested = listener;
    }

    public void setOnSelectedPanelChanged(Consumer<String> listener) {
        mOnSelectedPanelChanged = listener;
    }

    public void installRegisteredPanels(SidebarPanelContext context) {
        for (SidebarPanelDefinition definition
                : SidebarPanelRegistry.INSTANCE.definitionsFor(context.scope())) {
            registerPanel(
                    definition.id(),
                    tr(definition.titleTranslationKey()),
                    definition.create(context), definition.icon());
        }
    }

    public void registerPanel(String id, String title, View view) {
        if (view == null) throw new IllegalArgumentException("Sidebar panel view cannot be null");
        registerPanel(id, title, new SidebarPanel() {
            @Override
            public View getView() {
                return view;
            }
        });
    }

    public void registerPanel(String id, String title, SidebarPanel panel) {
        registerPanel(id, title, panel, null);
    }

    public void registerPanel(String id, String title, SidebarPanel panel, SvgIconView.Icon icon) {
        String normalizedId = normalizeId(id);
        if (panel == null || panel.getView() == null) {
            throw new IllegalArgumentException("Sidebar panel cannot be null");
        }
        if (mPanels.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Duplicate registration panel id: " + normalizedId);
        }

        String resolvedTitle = title != null && !title.isBlank() ? title : normalizedId;
        SidebarTabView tab = new SidebarTabView(getContext(), resolvedTitle, icon);
        PanelEntry entry = new PanelEntry(normalizedId, resolvedTitle, panel, tab);
        tab.setOnClickListener(v -> {
            selectPanel(normalizedId);
            if (!mContentVisible && mOnExpandRequested != null) mOnExpandRequested.run();
        });
        mPanels.put(normalizedId, entry);
        LayoutParams tabParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30));
        tabParams.bottomMargin = UIUtils.dp2pxInt(4);
        mTabStrip.addView(tab, tabParams);

        if (mSelected == null) selectPanel(normalizedId, false);
    }

    public <T extends SidebarPanel> T requirePanel(String id, Class<T> panelType) {
        PanelEntry entry = mPanels.get(normalizeId(id));
        if (entry == null) throw new IllegalStateException("Sidebar panel is not installed: " + id);
        if (!panelType.isInstance(entry.panel())) {
            throw new IllegalStateException("Sidebar panel has unexpected type: " + id);
        }
        return panelType.cast(entry.panel());
    }

    public boolean unregisterPanel(String id) {
        PanelEntry entry = mPanels.remove(normalizeId(id));
        if (entry == null) return false;

        mTabStrip.removeView(entry.tab());
        if (entry != mSelected) return true;

        if (mPanelsActive) entry.panel().onDeselected();
        mSelected = null;
        mContentHost.removeAllViews();
        mTitleView.setText("");
        if (!mPanels.isEmpty()) selectPanel(mPanels.keySet().iterator().next(), true);
        return true;
    }

    public boolean selectPanel(String id) {
        return selectPanel(id, true);
    }

    public boolean restoreSelectedPanel(String id) {
        if (id != null && selectPanel(id, false)) return true;
        if (mPanels.isEmpty()) return false;
        return selectPanel(mPanels.keySet().iterator().next(), false);
    }

    public String getSelectedPanelId() {
        return mSelected != null ? mSelected.id() : null;
    }

    public boolean hasPanels() {
        return !mPanels.isEmpty();
    }

    public int getCollapsedWidth() {
        return UIUtils.dp2pxInt(TAB_RAIL_WIDTH_DP);
    }

    public boolean isContentVisible() {
        return mContentVisible;
    }

    public void setContentVisible(boolean visible) {
        if (mContentVisible == visible) return;
        mContentVisible = visible;
        mPanelColumn.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setPanelsActive(boolean active) {
        if (mPanelsActive == active) return;
        mPanelsActive = active;
        if (mSelected == null) return;
        if (active) {
            mSelected.panel().onSelected();
        } else {
            mSelected.panel().onDeselected();
        }
    }

    private boolean selectPanel(String id, boolean notify) {
        PanelEntry next = mPanels.get(id);
        if (next == null) return false;
        if (next == mSelected) return true;

        PanelEntry previous = mSelected;
        if (previous != null && mPanelsActive) previous.panel().onDeselected();

        mSelected = next;
        mContentHost.removeAllViews();
        View content = next.panel().getView();
        if (content.getParent() instanceof ViewGroup parent) parent.removeView(content);
        mContentHost.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mTitleView.setText(next.title());
        if (mPanelsActive) next.panel().onSelected();

        if (previous != null) previous.tab().setSelected(false);
        next.tab().setSelected(true);
        if (isAttachedToWindow()) post(() -> mTabScroll.scrollToDescendant(next.tab()));
        if (notify && mOnSelectedPanelChanged != null) mOnSelectedPanelChanged.accept(next.id());
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSelected != null) post(() -> mTabScroll.scrollToDescendant(mSelected.tab()));
    }

    private static String normalizeId(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Sidebar panel id cannot be blank");
        return id.trim();
    }

    private static TextView label(Context context, String text, float sizeDp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(0, UIUtils.dp2px(sizeDp));
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        return view;
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        return drawable;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private record PanelEntry(String id, String title, SidebarPanel panel, SidebarTabView tab) {
    }

    private static final class SidebarTabView extends FrameLayout {
        private final View mIcon;
        private final View mIndicator;

        private SidebarTabView(Context context, String title, SvgIconView.Icon icon) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setTooltipText(title);
            setContentDescription(title);
            if (icon != null) {
                mIcon = new SvgIconView(context, icon, COLOR_MUTED);
            } else {
                // Third-party panels without an icon keep a readable, unrotated initial.
                TextView initial = label(context, title.substring(0, title.offsetByCodePoints(0, 1)), 12, COLOR_MUTED);
                initial.setGravity(Gravity.CENTER);
                mIcon = initial;
            }
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(16), UIUtils.dp2pxInt(16));
            iconParams.gravity = Gravity.CENTER;
            addView(mIcon, iconParams);
            mIndicator = new View(context);
            mIndicator.setBackground(rect(UiTheme.Control.FOCUS, 1, 0, 0));
            FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(UIUtils.dp2pxInt(2), UIUtils.dp2pxInt(16));
            indicatorParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            addView(mIndicator, indicatorParams);
            updateStyle();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (mIcon != null) updateStyle();
        }

        private void updateStyle() {
            int color = isSelected() ? UiTheme.Control.FOCUS : isHovered() ? COLOR_TEXT : COLOR_MUTED;
            if (mIcon instanceof SvgIconView svg) svg.setIconColor(color);
            if (mIcon instanceof TextView text) text.setTextColor(color);
            if (mIndicator != null) mIndicator.setVisibility(isSelected() ? View.VISIBLE : View.INVISIBLE);
            setAlpha(isEnabled() ? 1.0f : UiTheme.Metrics.DISABLED_ALPHA);
            setBackground(rect(isPressed() ? UiTheme.Control.ICON_PRESSED
                            : isSelected() ? COLOR_TAB_SELECTED : isHovered() ? COLOR_HOVER : 0x00000000,
                    3.0f, isFocused() ? 1 : 0, UiTheme.Control.FOCUS));
        }
    }
}
