package com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.InlineActionButton;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import net.minecraft.network.chat.Component;

/** Shared preview-and-edit layout for persistent template hints. */
final class TemplateEditorHintLayout extends FrameLayout implements ViewportScaledHint, ViewportTransformedHint {
    static final float BUTTON_GAP_DP = 4.0f;

    private final View preview;
    private float viewportScale = 1.0f;
    private float windowLeftPx;
    private float windowTopPx;
    private long previewOrder;
    private boolean hasViewportTransform;

    TemplateEditorHintLayout(Context context, View preview, float previewSizeDp, Runnable editAction) {
        super(context);
        this.preview = preview;
        setClipChildren(false);

        LayoutParams previewParams = new LayoutParams(
                UIUtils.dp2pxInt(previewSizeDp),
                UIUtils.dp2pxInt(previewSizeDp)
        );
        previewParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        addView(preview, previewParams);

        InlineActionButton editButton = new InlineActionButton(
                context,
                Component.translatable("geometry_node.ui.edit_template").getString()
        );
        editButton.setSingleLine(true);
        editButton.setOnClickListener(view -> editAction.run());

        LayoutParams buttonParams = new LayoutParams(
                LayoutParams.MATCH_PARENT,
                InlineActionButton.heightPx()
        );
        buttonParams.gravity = Gravity.TOP | Gravity.LEFT;
        buttonParams.topMargin = UIUtils.dp2pxInt(previewSizeDp + BUTTON_GAP_DP);
        addView(editButton, buttonParams);
    }

    static float contentHeightDp(float previewSizeDp) {
        return previewSizeDp + BUTTON_GAP_DP + InlineActionButton.heightDp();
    }

    @Override
    public void setViewportScale(float scale) {
        viewportScale = scale > 0.0f ? scale : 1.0f;
        if (preview instanceof ViewportScaledHint scaledHint) {
            scaledHint.setViewportScale(viewportScale);
        }
        applyPreviewTransform();
    }

    @Override
    public void setViewportTransform(float scale, float windowLeftPx, float windowTopPx) {
        setViewportTransform(scale, windowLeftPx, windowTopPx, 0L);
    }

    @Override
    public void setViewportTransform(float scale, float windowLeftPx, float windowTopPx, long previewOrder) {
        viewportScale = scale > 0.0f ? scale : 1.0f;
        this.windowLeftPx = windowLeftPx;
        this.windowTopPx = windowTopPx;
        this.previewOrder = previewOrder;
        hasViewportTransform = true;
        applyPreviewTransform();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        applyPreviewTransform();
    }

    private void applyPreviewTransform() {
        if (!hasViewportTransform || !(preview instanceof ViewportTransformedHint transformedHint)) {
            return;
        }
        LayoutParams rootParams = getLayoutParams() instanceof LayoutParams params ? params : null;
        LayoutParams previewParams = preview.getLayoutParams() instanceof LayoutParams params ? params : null;
        int rootWidth = getWidth() > 0 ? getWidth() : rootParams != null ? rootParams.width : 0;
        int previewWidth = preview.getWidth() > 0 ? preview.getWidth() : previewParams != null ? previewParams.width : 0;
        float previewLeftPx = Math.max(0.0f, (rootWidth - previewWidth) * 0.5f);
        transformedHint.setViewportTransform(
                viewportScale,
                windowLeftPx + previewLeftPx * viewportScale,
                windowTopPx,
                previewOrder
        );
    }
}
