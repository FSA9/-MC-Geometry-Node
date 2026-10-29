package com.mine.geometry_node.client.ui.components.integration.overlay;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.composite.overlay.UiOverlayHosts;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.overlay.OverlayHandle;
import com.mine.geometry_node.client.ui.overlay.OverlayMount;
import icyllis.modernui.core.Context;
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
import java.util.Objects;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.rect;

public final class ItemStackTooltipOverlay {
    private static final int TOOLTIP_MAX_WIDTH_DP = 220;

    private static ViewGroup sHost;
    private static View sAnchor;
    private static OverlayHandle sOverlayHandle;
    private static LinearLayout sTooltipView;
    // Presentation state of the currently owned tooltip, never a default for another caller.
    private static UiTheme sTooltipTheme;
    private static final List<TextView> sTooltipLines = new ArrayList<>();
    private static final View.OnAttachStateChangeListener ANCHOR_LIFETIME = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {
            hide(view);
        }
    };

    private ItemStackTooltipOverlay() {
    }

    public static void showForEvent(View anchor, ItemStack stack, MotionEvent event) {
        showForEvent(anchor, stack, event, UiThemes.EDITOR);
    }

    public static void showForEvent(View anchor, ItemStack stack, MotionEvent event, UiTheme theme) {
        if (anchor == null || event == null) {
            hide(anchor);
            return;
        }
        show(anchor, stack, event.getRawX(), event.getRawY(), theme);
    }

    public static void show(View anchor, ItemStack stack, float rawX, float rawY) {
        show(anchor, stack, rawX, rawY, UiThemes.EDITOR);
    }

    public static void show(View anchor, ItemStack stack, float rawX, float rawY, UiTheme theme) {
        if (anchor == null || stack == null || stack.isEmpty()) {
            hide(anchor);
            return;
        }

        showLines(anchor, tooltipComponents(stack).stream().map(Component::getString).toList(), rawX, rawY, theme);
    }

    /** Pure UI presentation, separate from Minecraft tooltip extraction. */
    static void showLines(View anchor, List<String> lines, float rawX, float rawY, UiTheme theme) {
        Objects.requireNonNull(theme, "theme");
        if (anchor == null || lines == null || lines.isEmpty()) {
            hide(anchor);
            return;
        }

        ViewGroup host = OverlayMount.host(anchor);
        if (host == null) host = UiOverlayHosts.findWindowHost(anchor);
        if (host == null) {
            hide(anchor);
            return;
        }

        ensureTooltipView(anchor, host, theme);
        if (sTooltipView == null) return;

        ensureTooltipLineCount(host.getContext(), lines.size(), theme);
        sTooltipView.removeAllViews();
        for (int i = 0; i < lines.size(); i++) {
            TextView line = sTooltipLines.get(i);
            line.setText(lines.get(i));
            line.setTextColor(i == 0 ? theme.text().normal() : theme.text().muted());
            line.setVisibility(View.VISIBLE);
            sTooltipView.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sTooltipView.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int[] hostLoc = new int[2];
        host.getLocationOnScreen(hostLoc);
        int pointerX = Math.round(rawX - hostLoc[0]);
        int pointerY = Math.round(rawY - hostLoc[1]);
        int pointerOffset = dp(12);
        int left = pointerX + pointerOffset;
        int top = pointerY + pointerOffset;
        int estimatedWidth = Math.min(dp(TOOLTIP_MAX_WIDTH_DP), estimateTooltipWidth(lines));
        int estimatedHeight = Math.max(dp(20), lines.size() * dp(15) + dp(10));
        if (left + estimatedWidth > host.getWidth() - dp(6)) {
            left = Math.max(dp(6), pointerX - estimatedWidth - pointerOffset);
        }
        if (top + estimatedHeight > host.getHeight() - dp(6)) {
            top = Math.max(dp(6), pointerY - estimatedHeight - pointerOffset);
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

    /** Dismisses only the tooltip owned by this exact view. Null owns nothing. */
    public static void hide(View owner) {
        if (owner != null && sAnchor == owner) hide();
    }

    /** Dismisses this container's tooltip or one owned by a descendant, never a sibling's. */
    public static void hideWithin(View owner) {
        if (owner == null) return;
        for (View anchor = sAnchor; anchor != null;
             anchor = anchor.getParent() instanceof View parent ? parent : null) {
            if (anchor == owner) {
                hide();
                return;
            }
        }
    }

    /** Global dismissal, also used when replacing the currently displayed tooltip. */
    public static void hide() {
        OverlayHandle handle = sOverlayHandle;
        LinearLayout tooltip = sTooltipView;
        clearReferences(tooltip);
        if (handle != null) handle.close();
        else if (tooltip != null && tooltip.getParent() instanceof ViewGroup parent) parent.removeView(tooltip);
    }

    private static void clearReferences(LinearLayout tooltip) {
        if (sTooltipView != tooltip) return;
        if (sAnchor != null) sAnchor.removeOnAttachStateChangeListener(ANCHOR_LIFETIME);
        sHost = null;
        sAnchor = null;
        sOverlayHandle = null;
        sTooltipView = null;
        sTooltipTheme = null;
        sTooltipLines.clear();
    }

    private static void ensureTooltipView(View anchor, ViewGroup host, UiTheme theme) {
        if (sHost == host && sAnchor == anchor && sTooltipView != null) {
            if (!theme.equals(sTooltipTheme)) {
                sTooltipView.setBackground(rect(theme.surface().tooltip(), 3.0f, 1, theme.border().subtle()));
                sTooltipTheme = theme;
            }
            return;
        }
        hide();
        sHost = host;
        sAnchor = anchor;
        anchor.addOnAttachStateChangeListener(ANCHOR_LIFETIME);
        LinearLayout tooltip = new LinearLayout(host.getContext());
        sTooltipView = tooltip;
        sTooltipTheme = theme;
        tooltip.setOrientation(LinearLayout.VERTICAL);
        tooltip.setPadding(dp(7), dp(5), dp(7), dp(5));
        tooltip.setBackground(rect(theme.surface().tooltip(), 3.0f, 1, theme.border().subtle()));
        tooltip.setVisibility(View.GONE);
        tooltip.setEnabled(false);
        tooltip.setClickable(false);
        tooltip.setFocusable(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sOverlayHandle = OverlayMount.tooltip(anchor, tooltip, params, reason -> clearReferences(tooltip));
        if (sOverlayHandle == null && sTooltipView == tooltip) {
            host.addView(tooltip, params);
        }
    }

    private static List<Component> tooltipComponents(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        Item.TooltipContext context = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
        return stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL);
    }

    private static void ensureTooltipLineCount(Context context, int count, UiTheme theme) {
        while (sTooltipLines.size() < count) {
            TextView line = UIUtils.createLockedTextView(context, "", 11.0f, theme.text().normal());
            line.setSingleLine(false);
            line.setMaxWidth(dp(TOOLTIP_MAX_WIDTH_DP));
            line.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            sTooltipLines.add(line);
        }
    }

    private static int estimateTooltipWidth(List<String> lines) {
        int maxUnits = 0;
        for (String line : lines) {
            maxUnits = Math.max(maxUnits, estimateTextUnits(line));
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



    private static int dp(int value) {
        return UIUtils.dp2pxInt(value);
    }
}
