package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.settings.page.api.SettingsPage;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

final class SettingsContentHost extends FrameLayout {
    private SettingsPage currentPage;
    private final TextView emptyView;

    SettingsContentHost(Context context) {
        super(context);
        emptyView = UIUtils.createLockedTextView(context,
                Component.translatable("geometry_node.settings.no_results").getString(), 11.0f, 0xFF929292);
        emptyView.setGravity(Gravity.CENTER);
    }

    void show(SettingsPage page) {
        if (currentPage == page && getChildCount() != 0) {
            return;
        }
        removeAllViews();
        currentPage = page;
        addView(page != null ? page.getView() : emptyView, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }
}
