package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.settings.page.api.SettingsPage;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

final class SettingsContentHost extends FrameLayout {
    private SettingsPage currentPage;

    SettingsContentHost(Context context) {
        super(context);
    }

    void show(SettingsPage page) {
        if (currentPage == page) {
            return;
        }
        removeAllViews();
        currentPage = page;
        if (page != null) {
            addView(page.getView(), new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
    }
}
