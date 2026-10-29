package com.mine.geometry_node.client.ui.components.composite.navigation;

import static com.mine.geometry_node.client.ui.components.base.drawable.UiDrawables.solid;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.scroll.UiHorizontalScrollView;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fixed-width page tabs. Owners retain navigation and page lifecycle callbacks. */
public final class UiSubTabs extends UiHorizontalScrollView {
    public static final int HEIGHT = 36;
    public static final int WIDTH = 124;
    private final UiTheme theme;
    private final LinearLayout strip;
    private final Map<String, Tab> tabs = new LinkedHashMap<>();

    public UiSubTabs(Context context, UiTheme theme) {
        super(context, Objects.requireNonNull(theme, "theme").scroll());
        this.theme = Objects.requireNonNull(theme, "theme");
        // Tabs occupy the full bar height. An inset scrollbar would reserve a blank strip
        // below every underline even when all tabs fit and the scrollbar is invisible.
        setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        setFillViewport(true);
        setMinimumHeight(UIUtils.dp2pxInt(HEIGHT));
        setBackground(solid(theme.surface().panel()));
        strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setBaselineAligned(false);
        addView(strip, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }

    /** Returns the actual clickable caption, also discoverable as a UiActionButton descendant. */
    public UiActionButton addTab(String key, CharSequence title, Runnable action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        if (tabs.containsKey(key)) throw new IllegalArgumentException("Duplicate subtab: " + key);
        UiActionButton button = new UiActionButton(getContext(), theme, title,
                UiActionButton.Role.INLINE, UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, UiMetrics.DISABLED_ALPHA, theme.border().focus()));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(UIUtils.dp2pxInt(16), 0, UIUtils.dp2pxInt(16), 0);
        button.setSingleLine(true);
        UIUtils.setLockedTextSize(button, UiMetrics.TEXT_CAPTION);
        button.setPalette(palette(false));
        button.setOnClickListener(view -> action.run());
        FrameLayout host = new FrameLayout(getContext());
        host.addView(button, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        // A later sibling stays above the button's text and state background.
        View underline = new View(getContext());
        underline.setBackground(solid(theme.selection().outline()));
        underline.setVisibility(INVISIBLE);
        host.addView(underline, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(2), Gravity.BOTTOM));
        tabs.put(key, new Tab(button, underline));
        strip.addView(host, new LinearLayout.LayoutParams(UIUtils.dp2pxInt(WIDTH), LayoutParams.MATCH_PARENT));
        return button;
    }

    /** Presentation only: restoring a selection must not execute navigation callbacks. */
    public void setSelectedTab(String key) {
        if (!tabs.containsKey(key)) throw new IllegalArgumentException("Unknown subtab: " + key);
        tabs.forEach((id, tab) -> {
            boolean selected = id.equals(key);
            tab.button().setSelected(selected);
            tab.button().setPalette(palette(selected));
            tab.underline().setVisibility(selected ? VISIBLE : INVISIBLE);
        });
    }

    private UiActionButton.Palette palette(boolean selected) {
        return new UiActionButton.Palette(selected ? theme.surface().header() : theme.surface().panel(),
                theme.control().iconHover(), theme.control().iconPressed(),
                selected ? theme.text().strong() : theme.text().muted());
    }

    private record Tab(UiActionButton button, View underline) {}
}
