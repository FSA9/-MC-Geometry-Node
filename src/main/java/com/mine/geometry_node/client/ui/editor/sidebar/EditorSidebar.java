package com.mine.geometry_node.client.ui.editor.sidebar;

import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelContext;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.editor.sidebar.api.SidebarPanelRegistry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
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
 * Editor-owned, Blender-style sidebar with a content host and a vertical tab rail.
 */
public final class EditorSidebar extends LinearLayout {
    private static final int COLOR_BACKGROUND = 0xFF303030;
    private static final int COLOR_HEADER = 0xFF292929;
    private static final int COLOR_BORDER = 0xFF181818;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFFAAAAAA;
    private static final int COLOR_HOVER = 0xFF454545;
    private static final int COLOR_TAB_RAIL = 0xFF202020;
    private static final int COLOR_TAB_SELECTED = 0xFF3A3A3A;
    private static final int TAB_RAIL_WIDTH_DP = 30;

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

        TextView collapse = label(context, ">", 14.0f, 0xFFB8B8B8);
        collapse.setGravity(Gravity.CENTER);
        String collapseDescription = tr("geometry_node.sidebar.collapse");
        collapse.setContentDescription(collapseDescription);
        collapse.setTooltipText(collapseDescription);
        collapse.setBackground(rect(0x00000000, 3.0f, 0, 0));
        collapse.setOnHoverListener((v, event) -> {
            collapse.setBackground(rect(
                    event.getAction() == MotionEvent.ACTION_HOVER_ENTER ? COLOR_HOVER : 0x00000000,
                    3.0f, 0, 0));
            return false;
        });
        collapse.setOnClickListener(v -> {
            if (mOnCollapseRequested != null) mOnCollapseRequested.run();
        });
        header.addView(collapse, new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(24)));
        mPanelColumn.addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(30)));

        mContentHost = new FrameLayout(context);
        mPanelColumn.addView(mContentHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        mTabScroll = new ScrollView(context);
        mTabScroll.setVerticalScrollBarEnabled(false);
        mTabScroll.setBackground(rect(COLOR_TAB_RAIL, 0.0f, 1, COLOR_BORDER));
        mTabStrip = new LinearLayout(context);
        mTabStrip.setOrientation(VERTICAL);
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
                    definition.create(context));
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
        String normalizedId = normalizeId(id);
        if (panel == null || panel.getView() == null) {
            throw new IllegalArgumentException("Sidebar panel cannot be null");
        }
        if (mPanels.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Duplicate sidebar panel id: " + normalizedId);
        }

        String resolvedTitle = title != null && !title.isBlank() ? title : normalizedId;
        SidebarTabView tab = createTab(resolvedTitle);
        PanelEntry entry = new PanelEntry(normalizedId, resolvedTitle, panel, tab);
        tab.setOnClickListener(v -> {
            selectPanel(normalizedId);
            if (!mContentVisible && mOnExpandRequested != null) mOnExpandRequested.run();
        });
        tab.setOnHoverListener((v, event) -> {
            updateTabBackground(entry, event.getAction() == MotionEvent.ACTION_HOVER_ENTER);
            return false;
        });
        mPanels.put(normalizedId, entry);
        mTabStrip.addView(tab, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tab.getPreferredHeight()));

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

        if (previous != null) updateTabBackground(previous, false);
        updateTabBackground(next, false);
        if (isAttachedToWindow()) post(() -> mTabScroll.scrollToDescendant(next.tab()));
        if (notify && mOnSelectedPanelChanged != null) mOnSelectedPanelChanged.accept(next.id());
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSelected != null) post(() -> mTabScroll.scrollToDescendant(mSelected.tab()));
    }

    private SidebarTabView createTab(String title) {
        SidebarTabView tab = new SidebarTabView(getContext(), title);
        tab.setContentDescription(title);
        return tab;
    }

    private void updateTabBackground(PanelEntry entry, boolean hovered) {
        boolean selected = entry == mSelected;
        entry.tab().setTabState(selected, hovered);
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
        private final TextView mLabel;
        private final int mPreferredHeight;

        private SidebarTabView(Context context, String title) {
            super(context);
            setBackground(rect(0x00000000, 0.0f, 0, 0));

            mLabel = new TextView(context);
            mLabel.setText(title);
            mLabel.setTextSize(0, UIUtils.dp2px(10.5f));
            mLabel.setTextColor(COLOR_MUTED);
            mLabel.setGravity(Gravity.CENTER);
            mLabel.setSingleLine(true);

            float textWidth = mLabel.getPaint().measureTextRun(
                    title,
                    0,
                    title.length(),
                    false,
                    null);
            mPreferredHeight = Math.max(
                    UIUtils.dp2pxInt(TAB_RAIL_WIDTH_DP),
                    (int) Math.ceil(textWidth) + UIUtils.dp2pxInt(8));

            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    mPreferredHeight,
                    UIUtils.dp2pxInt(TAB_RAIL_WIDTH_DP));
            labelParams.gravity = Gravity.CENTER;
            addView(mLabel, labelParams);
            mLabel.setRotation(-90.0f);
        }

        private int getPreferredHeight() {
            return mPreferredHeight;
        }

        private void setTabState(boolean selected, boolean hovered) {
            setSelected(selected);
            mLabel.setTextColor(selected ? COLOR_TEXT : COLOR_MUTED);
            setBackground(rect(
                    selected ? COLOR_TAB_SELECTED : hovered ? COLOR_HOVER : 0x00000000,
                    0.0f, 0, 0));
        }
    }
}
