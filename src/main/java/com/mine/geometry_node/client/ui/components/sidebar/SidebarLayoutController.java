package com.mine.geometry_node.client.ui.components.sidebar;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.components.layout.UiFixedSplitLayout;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

/**
 * Connects an editor's main content to an optional right registration without imposing inheritance on the editor window.
 */
public final class SidebarLayoutController {
    public interface StateListener {
        void onStateChanged(boolean visible, float expandedWeight);
    }

    private final LinearLayout mParent;
    private final View mMainContent;
    private final View mDivider;
    private final EditorSidebar mSidebar;
    private final StateListener mStateListener;
    private float mExpandedWeight;
    private boolean mOwnerVisible = true;
    private UiFixedSplitLayout mFixedSplit;
    private java.util.function.Consumer<Boolean> mVisibilityListener;

    /** Fixed-width variant; the middle content absorbs resize and collapse changes. */
    public SidebarLayoutController(UiFixedSplitLayout split, EditorSidebar sidebar) {
        this(split, split.flexiblePane(), split.divider(), sidebar, 0, null);
        if (split.fixedPane() != sidebar) throw new IllegalArgumentException("Sidebar must be the fixed pane");
        mFixedSplit = split;
    }

    /** Fixed-width binding without weight semantics. initialize() and setVisible(..., false)
     * restore silently; notified actions and persistState() publish visibility to the owner. */
    public SidebarLayoutController(UiFixedSplitLayout split, EditorSidebar sidebar,
                                   java.util.function.Consumer<Boolean> visibilityListener) {
        this(split, sidebar);
        mVisibilityListener = visibilityListener;
    }

    public SidebarLayoutController(
            LinearLayout parent,
            View mainContent,
            View divider,
            EditorSidebar sidebar,
            float expandedWeight,
            StateListener stateListener) {
        mParent = parent;
        mMainContent = mainContent;
        mDivider = divider;
        mSidebar = sidebar;
        mExpandedWeight = expandedWeight;
        mStateListener = stateListener;
        mSidebar.setOnExpandRequested(() -> setVisible(true, true));
    }

    public void initialize(boolean visible) {
        setVisible(visible, false);
    }

    public void toggle() {
        setVisible(!isVisible(), true);
    }

    public void onOwnerShown() {
        mOwnerVisible = true;
        mSidebar.setPanelsActive(isVisible());
    }

    public void onOwnerHidden() {
        mOwnerVisible = false;
        mSidebar.setPanelsActive(false);
    }

    public void setVisible(boolean visible, boolean notify) {
        boolean currentlyVisible = isVisible();
        if (currentlyVisible == visible) {
            if (notify) notifyStateChanged();
            return;
        }

        if (mFixedSplit != null) {
            mFixedSplit.setCollapsed(!visible, mSidebar.getCollapsedWidth());
        } else if (visible) {
            transferWeight(-mExpandedWeight);
            setSidebarLayout(0, mExpandedWeight);
        } else {
            rememberExpandedWeight();
            transferWeight(mExpandedWeight);
            setSidebarLayout(mSidebar.getCollapsedWidth(), 0.0f);
        }
        mDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
        mSidebar.setContentVisible(visible);
        mSidebar.setPanelsActive(mOwnerVisible && visible);
        mParent.requestLayout();
        if (notify) notifyStateChanged();
    }

    public boolean isVisible() {
        return mSidebar.isContentVisible();
    }

    public void persistState() {
        notifyStateChanged();
    }

    private void transferWeight(float sidebarWeightDelta) {
        if (!(mMainContent.getLayoutParams() instanceof LinearLayout.LayoutParams contentParams)) return;
        contentParams.weight = Math.max(
                UIConstants.MainUI.WEIGHT_MIN,
                contentParams.weight + sidebarWeightDelta);
        mMainContent.setLayoutParams(contentParams);
    }

    private void setSidebarLayout(int width, float weight) {
        if (!(mSidebar.getLayoutParams() instanceof LinearLayout.LayoutParams params)) return;
        params.width = width;
        params.weight = weight;
        mSidebar.setLayoutParams(params);
    }

    private void rememberExpandedWeight() {
        if (mSidebar.getLayoutParams() instanceof LinearLayout.LayoutParams params && params.weight > 0.0f) {
            mExpandedWeight = params.weight;
        }
    }

    private void notifyStateChanged() {
        if (mFixedSplit != null) {
            if (mVisibilityListener != null) mVisibilityListener.accept(isVisible());
            return;
        }
        rememberExpandedWeight();
        if (mStateListener != null) mStateListener.onStateChanged(isVisible(), mExpandedWeight);
    }
}
