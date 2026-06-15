package com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

public final class ItemStackTooltipOverlay {
    private static final int COLOR_TEXT = 0xFFE8EDF6;
    private static final int COLOR_MUTED = 0xFF9AA5B5;
    private static final int COLOR_TOOLTIP_BG = 0xF0201428;
    private static final int COLOR_TOOLTIP_BORDER = 0xFF6B4FA3;
    private static final int TOOLTIP_MAX_WIDTH_DP = 220;

    private static ViewGroup sHost;
    private static LinearLayout sTooltipView;
    private static final List<TextView> sTooltipLines = new ArrayList<>();

    private ItemStackTooltipOverlay() {
    }

    public static void showForEvent(View anchor, ItemStack stack, MotionEvent event) {
        if (anchor == null || event == null) {
            hide();
            return;
        }
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        float rawX = loc[0] + event.getX();
        float rawY = loc[1] + event.getY();
        show(anchor, stack, rawX, rawY);
    }

    public static void show(View anchor, ItemStack stack, float rawX, float rawY) {
        if (anchor == null || stack == null || stack.isEmpty()) {
            hide();
            return;
        }

        ViewGroup host = findWindowHost(anchor);
        if (host == null) {
            hide();
            return;
        }

        ensureTooltipView(host);
        List<Component> lines = tooltipComponents(stack);
        if (lines.isEmpty()) {
            hide();
            return;
        }

        ensureTooltipLineCount(host.getContext(), lines.size());
        sTooltipView.removeAllViews();
        for (int i = 0; i < lines.size(); i++) {
            TextView line = sTooltipLines.get(i);
            line.setText(lines.get(i).getString());
            line.setTextColor(i == 0 ? COLOR_TEXT : COLOR_MUTED);
            line.setVisibility(View.VISIBLE);
            sTooltipView.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sTooltipView.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int[] hostLoc = new int[2];
        host.getLocationOnScreen(hostLoc);
        int left = Math.round(rawX - hostLoc[0]) + dp(12);
        int top = Math.round(rawY - hostLoc[1]) + dp(12);
        int estimatedWidth = Math.min(dp(TOOLTIP_MAX_WIDTH_DP), estimateTooltipWidth(lines));
        int estimatedHeight = Math.max(dp(20), lines.size() * dp(15) + dp(10));
        if (left + estimatedWidth > host.getWidth() - dp(6)) {
            left = Math.max(dp(6), Math.round(rawX - hostLoc[0]) - estimatedWidth - dp(12));
        }
        if (top + estimatedHeight > host.getHeight() - dp(6)) {
            top = Math.max(dp(6), host.getHeight() - estimatedHeight - dp(6));
        }
        lp.leftMargin = left;
        lp.topMargin = top;
        sTooltipView.setLayoutParams(lp);
        sTooltipView.setVisibility(View.VISIBLE);
        if (sTooltipView.getParent() == host && host.getChildAt(host.getChildCount() - 1) != sTooltipView) {
            host.removeView(sTooltipView);
            host.addView(sTooltipView, lp);
        }
    }

    public static void hide() {
        if (sTooltipView != null) {
            sTooltipView.setVisibility(View.GONE);
        }
    }

    private static void ensureTooltipView(ViewGroup host) {
        if (sHost != host && sTooltipView != null && sTooltipView.getParent() instanceof ViewGroup parent) {
            parent.removeView(sTooltipView);
        }
        sHost = host;
        if (sTooltipView == null) {
            Context context = host.getContext();
            sTooltipView = new LinearLayout(context);
            sTooltipView.setOrientation(LinearLayout.VERTICAL);
            sTooltipView.setPadding(dp(7), dp(5), dp(7), dp(5));
            sTooltipView.setBackground(rect(COLOR_TOOLTIP_BG, 3.0f, 1, COLOR_TOOLTIP_BORDER));
            sTooltipView.setVisibility(View.GONE);
            sTooltipView.setEnabled(false);
            sTooltipView.setClickable(false);
            sTooltipView.setFocusable(false);
        }
        if (sTooltipView.getParent() != host) {
            host.addView(sTooltipView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private static List<Component> tooltipComponents(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        Item.TooltipContext context = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
        return stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL);
    }

    private static void ensureTooltipLineCount(Context context, int count) {
        while (sTooltipLines.size() < count) {
            TextView line = UIUtils.createLockedTextView(context, "", 11.0f, COLOR_TEXT);
            line.setSingleLine(false);
            line.setMaxWidth(dp(TOOLTIP_MAX_WIDTH_DP));
            line.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            sTooltipLines.add(line);
        }
    }

    private static int estimateTooltipWidth(List<Component> lines) {
        int maxUnits = 0;
        for (Component line : lines) {
            maxUnits = Math.max(maxUnits, estimateTextUnits(line.getString()));
        }
        return Math.min(dp(TOOLTIP_MAX_WIDTH_DP), maxUnits * dp(6) + dp(14));
    }

    private static int estimateTextUnits(String text) {
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            units += text.charAt(i) <= 0x7F ? 1 : 2;
        }
        return Math.max(1, units);
    }

    private static ViewGroup findWindowHost(View anchor) {
        View current = anchor;
        ViewGroup best = anchor instanceof ViewGroup viewGroup ? viewGroup : null;
        while (current != null) {
            if (current instanceof FrameLayout frameLayout) {
                best = frameLayout;
            }
            if (!(current.getParent() instanceof View parentView)) {
                break;
            }
            current = parentView;
        }
        return best != null ? best : anchor.getParent() instanceof ViewGroup parent ? parent : null;
    }

    private static ShapeDrawable rect(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private static int dp(int value) {
        return UIUtils.dp2pxInt(value);
    }
}
