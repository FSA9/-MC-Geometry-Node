package com.mine.geometry_node.client.ui.components.integration.asset.browser;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;

/** Listing summary and view controls; navigation and persistence stay in the browser owner. */
final class AssetBrowserStatusBar extends LinearLayout {
    private final TextView summary;
    private final UiIconButton grid;
    private final UiIconButton list;

    AssetBrowserStatusBar(Context context, Consumer<AssetViewMode> onModeSelected) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(6), 0);
        setBackground(solid(UiThemes.EDITOR.surface().sidebar()));
        summary = UIUtils.createLockedTextView(context, "", 12, UiThemes.EDITOR.text().secondary());
        summary.setSingleLine(true);
        addView(summary, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        summary.setGravity(Gravity.CENTER_VERTICAL);
        grid = button(context, SvgIconView.Icon.GRID_VIEW, "icon_medium");
        list = button(context, SvgIconView.Icon.LIST_VIEW, "list");
        grid.setOnClickListener(view -> onModeSelected.accept(AssetViewMode.ICON_MEDIUM));
        list.setOnClickListener(view -> onModeSelected.accept(AssetViewMode.LIST));
        addView(grid, new LayoutParams(UIUtils.dp2pxInt(28), UIUtils.dp2pxInt(24)));
        addView(list, new LayoutParams(UIUtils.dp2pxInt(28), UIUtils.dp2pxInt(24)));
    }

    void update(int count, int selected, AssetViewMode mode, boolean loading, boolean success) {
        summary.setText(loading ? Component.translatable("geometry_node.asset_library.status.loading").getString()
                : !success ? Component.translatable("geometry_node.asset_library.status.failed").getString()
                : Component.translatable("geometry_node.asset_library.status.summary", count, selected).getString());
        summary.setTextColor(!loading && !success ? UiThemes.EDITOR.status().error() : UiThemes.EDITOR.text().secondary());
        grid.setSelected(mode != AssetViewMode.LIST);
        list.setSelected(mode == AssetViewMode.LIST);
        grid.setEnabled(mode == AssetViewMode.LIST);
        list.setEnabled(mode != AssetViewMode.LIST);
    }

    private static UiIconButton button(Context context, SvgIconView.Icon icon, String mode) {
        return UiIconButton.createThemed(context, UiThemes.EDITOR, icon,
                Component.translatable("geometry_node.settings.choice.asset_view." + mode).getString());
    }
}
