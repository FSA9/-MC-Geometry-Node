package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import com.mine.geometry_node.client.ui.bottom_window.IToolWindow;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.left.LeftQuickAccessPanel;
import com.mine.geometry_node.client.ui.bottom_window.asset_library.right.RightFileBrowserPanel;
import com.mine.geometry_node.client.ui.utils.PanelSplitter;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.persistence.PathUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.io.File;

public class AssetBrowserPanel extends FrameLayout implements IToolWindow {

    private final LinearLayout mMainLayout;
    private final LeftQuickAccessPanel mLeftPanel;
    private final RightFileBrowserPanel mRightPanel;

    public AssetBrowserPanel(Context context) {
        super(context);

        mMainLayout = new LinearLayout(context);
        mMainLayout.setOrientation(LinearLayout.HORIZONTAL);
        mMainLayout.setBackground(createColorDrawable(UIConstants.MainUI.BG_TIMELINE));
        addView(mMainLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mLeftPanel = new LeftQuickAccessPanel(context, this);
        mMainLayout.addView(mLeftPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.2f));

        mMainLayout.addView(PanelSplitter.create(context, true));

        mRightPanel = new RightFileBrowserPanel(context, this);
        mMainLayout.addView(mRightPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.8f));

        dispatchNavigateTo(PathUtils.getLocalDraftsDir());
    }

    /**
     * 跨区协调总线：将来自左侧边栏选中的目录精准分发给右侧文件浏览器
     */
    public void dispatchNavigateTo(File directory) {
        if (mRightPanel != null) {
            mRightPanel.navigateTo(directory);
        }
    }

    /**
     * 跨区协调总线：当右侧通过 NavBar 的 "+" 添加了新的快速路径后，驱动左侧状态重塑
     */
    public void notifySidebarChanged() {
        if (mLeftPanel != null) {
            mLeftPanel.buildSidebar();
        }
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void onShow() {
        if (mRightPanel != null) {
            mRightPanel.refreshFileList();
        }
        if (mLeftPanel != null) {
            mLeftPanel.buildSidebar();
        }
    }

    @Override
    public void onHide() {
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }
}