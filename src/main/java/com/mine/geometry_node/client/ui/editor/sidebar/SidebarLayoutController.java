package com.mine.geometry_node.client.ui.editor.sidebar;

import com.mine.geometry_node.client.ui.UIConstants;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;

/**
 * Connects an editor's main content to an optional right sidebar without imposing inheritance on the editor window.
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

        if (visible) {
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
        rememberExpandedWeight();
        if (mStateListener != null) mStateListener.onStateChanged(isVisible(), mExpandedWeight);
    }
}
