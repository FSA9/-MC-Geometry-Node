package com.mine.geometry_node.client.ui.shell.menu;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
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
    private static final int COLOR_BAR = UiThemes.EDITOR.surface().header();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().secondary();

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
        ShapeDrawable background = new ShapeDrawable();
        background.setColor(COLOR_BAR);
        setBackground(background);
    }

    void rebuild(List<MainMenuDefinition> definitions) {
        removeAllViews();
        buttons.clear();
        for (MainMenuDefinition definition : definitions) {
            TextView button = UiMenuItems.row(getContext(),
                    Component.translatable(definition.labelTranslationKey()).getString(),
                    false, true, () -> controller.toggleMenu(definition.id(), button(definition.id())),
                    UiThemes.EDITOR);
            ((ShapeDrawable) button.getBackground()).setCornerRadius(0);
            button.setTextColor(UiMenuItems.textColors(COLOR_TEXT, UiThemes.EDITOR));
            UIUtils.setLockedTextSize(button, 12.0f);
            button.setGravity(Gravity.CENTER);
            button.setPadding(UIUtils.dp2pxInt(10.0f), 0, UIUtils.dp2pxInt(10.0f), 0);
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
            if (!button.isEnabled() || button.getVisibility() != View.VISIBLE) continue;
            button.getLocationOnScreen(location);
            if (x >= location[0] && x < location[0] + button.getWidth()
                    && y >= location[1] && y < location[1] + button.getHeight()) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean onButtonHover(String menuId, MotionEvent event) {
        if (!buttons.get(menuId).isEnabled()) return false;
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
            button.setSelected(active);
            button.setHovered(hovered);
        }
    }
}
