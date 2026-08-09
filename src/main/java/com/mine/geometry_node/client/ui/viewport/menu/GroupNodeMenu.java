package com.mine.geometry_node.client.ui.viewport.menu;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.common.UiActionButton;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionId;
import com.mine.geometry_node.client.ui.viewport.action.ViewportActionRequest;
import com.mine.geometry_node.client.ui.viewport.interaction.InteractionContext;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;
import com.mine.geometry_node.core.node.document.NodeData;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

public final class GroupNodeMenu {
    private static final int PANEL_W_DP = 230;
    private static final int PANEL_H_DP = 270;
    private static final int PANEL_PADDING_DP = 10;
    private static final int EDGE_MARGIN_DP = 6;
    private static final int INPUT_H_DP = 30;
    private static final int COMMENT_H_DP = 56;
    private static final int ACTION_H_DP = 28;
    private static final int SWATCH_SIZE_DP = 20;
    private static final int SWATCH_GAP_DP = 3;

    private static final int COLOR_PANEL_BG = 0xF02B2D33;
    private static final int COLOR_PANEL_BORDER = 0xFF15171B;
    private static final int COLOR_INPUT_BG = 0xFF181B20;
    private static final int COLOR_INPUT_BORDER = 0xFF3A404A;
    private static final int COLOR_LABEL = 0xFF8F98A6;
    private static final int COLOR_TEXT = 0xFFE7EAF0;

    private static final int[] PRESET_COLORS = {
            NodeData.DEFAULT_GROUP_COLOR,
            0xFF4A90E2,
            0xFF4FA36C,
            0xFFD2A040,
            0xFFC66B5D,
            0xFF8C6ED8,
            0xFF42A7B8,
            0xFFB86AA8
    };

    private GroupNodeMenu() {}

    public static void show(InteractionContext context, NodeVisualAdapter node, float screenX, float screenY) {
        if (context == null || node == null || !(context instanceof ViewGroup parent)) return;

        NodeData nodeData = node.getNodeData();
        if (nodeData == null || !nodeData.isGroupNode()) return;

        Context uiContext = context.getUIContext();
        String defaultTitle = Component.translatable("geometry_node.node.node_group").getString();
        String oldTitle = nodeData.customName != null && !nodeData.customName.trim().isEmpty()
                ? nodeData.customName.trim()
                : defaultTitle;
        String oldComment = nodeData.comment != null ? nodeData.comment.trim() : "";
        int initialColor = ensureOpaque(nodeData.getHeaderColor(NodeData.DEFAULT_GROUP_COLOR));
        final int[] selectedColor = {initialColor};

        FrameLayout popupOverlay = new FrameLayout(uiContext);
        popupOverlay.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        popupOverlay.setOnClickListener(v -> close(context, popupOverlay));

        LinearLayout panel = new LinearLayout(uiContext);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP));
        panel.setBackground(rect(COLOR_PANEL_BG, 6.0f, 1, COLOR_PANEL_BORDER));
        panel.setOnClickListener(v -> {});

        TextView header = label(uiContext, "图组设置", 13f, COLOR_TEXT, Gravity.CENTER_VERTICAL);
        panel.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        TextView titleLabel = label(uiContext, "标题", 10f, COLOR_LABEL, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        titleLabelLp.topMargin = dp(4);
        panel.addView(titleLabel, titleLabelLp);

        EditText titleInput = new EditText(uiContext);
        titleInput.setText(oldTitle);
        titleInput.setSingleLine(true);
        titleInput.setTextColor(UIConstants.CLR_WHITE);
        titleInput.setTextSize(0, UIUtils.dp2px(12));
        titleInput.setGravity(Gravity.CENTER_VERTICAL);
        titleInput.setPadding(dp(9), 0, dp(9), 0);
        titleInput.setBackground(rect(COLOR_INPUT_BG, 4.0f, 1, COLOR_INPUT_BORDER));
        panel.addView(titleInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(INPUT_H_DP)));

        TextView commentLabel = label(uiContext, "Comment", 10f, COLOR_LABEL, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams commentLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        commentLabelLp.topMargin = dp(8);
        panel.addView(commentLabel, commentLabelLp);

        EditText commentInput = new EditText(uiContext);
        commentInput.setText(oldComment);
        commentInput.setSingleLine(false);
        commentInput.setMinLines(2);
        commentInput.setTextColor(UIConstants.CLR_WHITE);
        commentInput.setTextSize(0, UIUtils.dp2px(11));
        commentInput.setGravity(Gravity.LEFT | Gravity.TOP);
        commentInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        commentInput.setBackground(rect(COLOR_INPUT_BG, 4.0f, 1, COLOR_INPUT_BORDER));
        panel.addView(commentInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(COMMENT_H_DP)));

        TextView colorLabel = label(uiContext, "颜色", 10f, COLOR_LABEL, Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colorLabelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
        colorLabelLp.topMargin = dp(10);
        panel.addView(colorLabel, colorLabelLp);

        LinearLayout swatches = new LinearLayout(uiContext);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(swatches, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SWATCH_SIZE_DP + 4)));

        addColorSwatch(uiContext, swatches, initialColor, selectedColor, true);
        for (int color : PRESET_COLORS) {
            if (ensureOpaque(color) != initialColor) {
                addColorSwatch(uiContext, swatches, color, selectedColor, false);
            }
        }

        LinearLayout actions = new LinearLayout(uiContext);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ACTION_H_DP));
        actionsLp.topMargin = dp(12);
        panel.addView(actions, actionsLp);

        UiActionButton dissolve = UiActionButton.create(uiContext, "解散", UiActionButton.Role.DANGER, v -> {
            close(context, popupOverlay);
            context.getActionSink().performAction(
                    ViewportActionId.DISSOLVE_NODE_GROUP,
                    ViewportActionRequest.builder().nodeId(nodeData.id).build()
            );
        });
        UiActionButton cancel = UiActionButton.create(uiContext, "取消", UiActionButton.Role.SECONDARY,
                v -> close(context, popupOverlay));
        UiActionButton apply = UiActionButton.create(uiContext, "应用", UiActionButton.Role.PRIMARY, v -> {
            String newTitle = titleInput.getText().toString().trim();
            if (newTitle.isEmpty()) newTitle = defaultTitle;
            String newComment = commentInput.getText().toString().trim();
            if (!newTitle.equals(oldTitle) || selectedColor[0] != initialColor || !newComment.equals(oldComment)) {
                String customTitle = newTitle.equals(defaultTitle) ? null : newTitle;
                String customComment = newComment.isEmpty() ? null : newComment;
                context.getActionSink().performAction(
                        ViewportActionId.SET_GROUP_NODE_PROPERTY,
                        ViewportActionRequest.builder()
                                .nodeId(nodeData.id)
                                .title(customTitle)
                                .color(selectedColor[0])
                                .comment(customComment)
                                .build()
                );
            }
            close(context, popupOverlay);
        });

        LinearLayout.LayoutParams dissolveLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        dissolveLp.rightMargin = dp(6);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        cancelLp.rightMargin = dp(6);
        actions.addView(dissolve, dissolveLp);
        actions.addView(cancel, cancelLp);
        actions.addView(apply, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        popupOverlay.addView(panel, createPanelLayout(parent, screenX, screenY));
        parent.addView(popupOverlay);
        titleInput.requestFocus();
        titleInput.setSelection(0, oldTitle.length());
    }

    private static void addColorSwatch(Context context, LinearLayout swatches, int color, int[] selectedColor, boolean selected) {
        int normalizedColor = ensureOpaque(color);
        ColorSwatch swatch = new ColorSwatch(context, normalizedColor);
        swatch.setSelectedColor(selected);
        swatch.setOnClickListener(v -> {
            selectedColor[0] = normalizedColor;
            for (int i = 0; i < swatches.getChildCount(); i++) {
                View child = swatches.getChildAt(i);
                if (child instanceof ColorSwatch colorSwatch) {
                    colorSwatch.setSelectedColor(colorSwatch.getColor() == selectedColor[0]);
                }
            }
        });

        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dp(SWATCH_SIZE_DP), dp(SWATCH_SIZE_DP));
        swatchLp.rightMargin = dp(SWATCH_GAP_DP);
        swatches.addView(swatch, swatchLp);
    }

    private static FrameLayout.LayoutParams createPanelLayout(ViewGroup parent, float screenX, float screenY) {
        int panelW = dp(PANEL_W_DP);
        int panelH = dp(PANEL_H_DP);
        int edge = dp(EDGE_MARGIN_DP);

        int targetX = (int) screenX;
        int targetY = (int) screenY;
        if (parent.getWidth() > 0 && targetX + panelW + edge > parent.getWidth()) {
            targetX = Math.max(edge, parent.getWidth() - panelW - edge);
        }
        if (parent.getHeight() > 0 && targetY + panelH + edge > parent.getHeight()) {
            int aboveY = (int) screenY - panelH;
            targetY = aboveY >= edge ? aboveY : Math.max(edge, parent.getHeight() - panelH - edge);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(panelW, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.max(edge, targetX);
        lp.topMargin = Math.max(edge, targetY);
        return lp;
    }

    private static TextView label(Context context, String text, float sizeDp, int color, int gravity) {
        TextView view = UIUtils.createLockedTextView(context, text, sizeDp, color);
        view.setGravity(gravity);
        view.setSingleLine(true);
        return view;
    }

    private static void removeOverlay(FrameLayout overlay) {
        if (overlay.getParent() instanceof ViewGroup parent) {
            parent.removeView(overlay);
        }
    }

    private static void close(InteractionContext context, FrameLayout overlay) {
        removeOverlay(overlay);
        context.requestViewportFocus();
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private static int dp(float value) {
        return UIUtils.dp2pxInt(value);
    }

    private static int ensureOpaque(int color) {
        return color | 0xFF000000;
    }

    private static int lighten(int color, float amount) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        r = Math.min(255, Math.round(r + (255 - r) * amount));
        g = Math.min(255, Math.round(g + (255 - g) * amount));
        b = Math.min(255, Math.round(b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class ColorSwatch extends View {
        private final int mColor;
        private boolean mSelectedColor;
        private boolean mHovered;
        private final Paint mPaint = new Paint();

        ColorSwatch(Context context, int color) {
            super(context);
            mColor = ensureOpaque(color);
            mPaint.setAntiAlias(true);
            setWillNotDraw(false);
            setOnHoverListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    mHovered = true;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    mHovered = false;
                    invalidate();
                }
                return false;
            });
        }

        int getColor() {
            return mColor;
        }

        void setSelectedColor(boolean selectedColor) {
            if (mSelectedColor == selectedColor) return;
            mSelectedColor = selectedColor;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = UIUtils.dp2px(2f);
            float radius = UIUtils.dp2px(4f);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mColor);
            canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset, radius, radius, radius, radius, mPaint);

            if (mSelectedColor || mHovered) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(UIUtils.dp2px(mSelectedColor ? 2.0f : 1.0f));
                mPaint.setColor(mSelectedColor ? UIConstants.CLR_WHITE : 0x99FFFFFF);
                canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset, radius, radius, radius, radius, mPaint);
            }
        }
    }
}
