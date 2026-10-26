package com.mine.geometry_node.client.ui.components.integration.asset.dialog;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.overlay.OverlayHost;
import com.mine.geometry_node.client.ui.overlay.OverlayHandle;
import com.mine.geometry_node.client.ui.overlay.modal.ModalOptions;
import com.mine.geometry_node.client.ui.components.composite.overlay.ModalWindowView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

abstract class AssetDialogBase extends ModalWindowView {
    private static final int ASSET_SCRIM_COLOR = UiThemes.EDITOR.surface().modalScrimLight();

    protected final LinearLayout mPanel;

    AssetDialogBase(Context context, String title) {
        super(context, title, MovementMode.DRAGGABLE);
        mPanel = new LinearLayout(context);
        mPanel.setOrientation(LinearLayout.VERTICAL);
        setContent(mPanel);
    }

    public final OverlayHandle show(View anchor) {
        return java.util.Objects.requireNonNull(OverlayHost.find(anchor), "No overlay host for asset dialog").showModal(
                this,
                new ModalOptions(false, true, anchor, ASSET_SCRIM_COLOR)
        );
    }

    protected TextView label(Context context, String text, float size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        UIUtils.setLockedTextSize(view, size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    protected UiActionButton actionButton(Context context, String text, UiActionButton.Role role) {
        return new UiActionButton(context, text, role, UiActionButton.Density.NORMAL);
    }

    protected ShapeDrawable rect(int color, float radiusDp) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        return drawable;
    }

    protected final void setWindowSizeDp(float widthDp, float heightDp) {
        setPreferredSizeDp(Math.max(0.0f, widthDp), Math.max(0.0f, heightDp));
    }

    @Override
    protected final void onWindowShown() {
        onAssetDialogShown();
    }

    @Override
    protected final void onWindowDestroyed() {
        onAssetDialogDestroyed();
    }

    protected void onAssetDialogShown() {
    }

    protected void onAssetDialogDestroyed() {
    }
}
