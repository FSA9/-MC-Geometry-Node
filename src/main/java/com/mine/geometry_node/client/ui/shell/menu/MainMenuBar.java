package com.mine.geometry_node.client.ui.shell.menu;

import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuDefinition;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainMenuBar extends LinearLayout {
    private static final int COLOR_BAR = 0xFF2D2D2D;
    private static final int COLOR_TEXT = 0xFFB8B8B8;
    private static final int COLOR_TEXT_ACTIVE = 0xFFF0F0F0;
    private static final int COLOR_HOVER = 0xFF3A3A3A;
    private static final int COLOR_ACTIVE = 0xFF424242;

    private final MainMenuController controller;
    private final Map<String, TextView> buttons = new LinkedHashMap<>();
    private String activeMenuId;
    private String hoveredMenuId;

    MainMenuBar(Context context, MainMenuController controller) {
        super(context);
        this.controller = controller;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        setPadding(UIUtils.dp2pxInt(4.0f), 0, UIUtils.dp2pxInt(4.0f), 0);
        setBackground(rect(COLOR_BAR));
    }

    void rebuild(List<MainMenuDefinition> definitions) {
        removeAllViews();
        buttons.clear();
        for (MainMenuDefinition definition : definitions) {
            TextView button = new TextView(getContext());
            button.setText(Component.translatable(definition.labelTranslationKey()).getString());
            UIUtils.setLockedTextSize(button, 12.0f);
            button.setGravity(Gravity.CENTER);
            button.setPadding(UIUtils.dp2pxInt(10.0f), 0, UIUtils.dp2pxInt(10.0f), 0);
            button.setClickable(true);
            button.setOnClickListener(view -> controller.toggleMenu(definition.id(), button));
            button.setOnHoverListener((view, event) -> onButtonHover(definition.id(), event));
            buttons.put(definition.id(), button);
            addView(button, new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
        updateButtonStyles();
    }

    void setActiveMenuId(String menuId) {
        activeMenuId = menuId;
        if (menuId == null) {
            hoveredMenuId = null;
        }
        updateButtonStyles();
    }

    void setHoveredMenuId(String menuId) {
        hoveredMenuId = menuId;
        updateButtonStyles();
    }

    View button(String menuId) {
        return buttons.get(menuId);
    }

    String menuAtScreen(float screenX, float screenY) {
        int x = Math.round(screenX);
        int y = Math.round(screenY);
        int[] location = new int[2];
        for (Map.Entry<String, TextView> entry : buttons.entrySet()) {
            TextView button = entry.getValue();
            button.getLocationOnScreen(location);
            if (x >= location[0] && x < location[0] + button.getWidth()
                    && y >= location[1] && y < location[1] + button.getHeight()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean onButtonHover(String menuId, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER
                || event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
            hoveredMenuId = menuId;
            if (activeMenuId != null && !activeMenuId.equals(menuId)) {
                controller.openMenu(menuId, buttons.get(menuId));
            }
        } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
            hoveredMenuId = null;
        }
        updateButtonStyles();
        return true;
    }

    private void updateButtonStyles() {
        for (Map.Entry<String, TextView> entry : buttons.entrySet()) {
            boolean active = entry.getKey().equals(activeMenuId);
            boolean hovered = entry.getKey().equals(hoveredMenuId);
            TextView button = entry.getValue();
            button.setTextColor(active || hovered ? COLOR_TEXT_ACTIVE : COLOR_TEXT);
            button.setBackground(active ? rect(COLOR_ACTIVE) : hovered ? rect(COLOR_HOVER) : null);
        }
    }

    private static ShapeDrawable rect(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }
}
