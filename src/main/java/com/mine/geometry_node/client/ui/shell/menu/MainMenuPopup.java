package com.mine.geometry_node.client.ui.shell.menu;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuItemDefinition;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuItemType;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class MainMenuPopup extends LinearLayout {
    static final float WIDTH_DP = 220.0f;

    private static final float ROW_HEIGHT_DP = 27.0f;
    private static final int COLOR_BACKGROUND = 0xFF252525;
    private static final int COLOR_BORDER = 0xFF484848;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_MUTED = 0xFF888888;
    private static final int COLOR_HOVER = 0xFF3D5F82;
    private static final int COLOR_DIVIDER = 0xFF414141;

    MainMenuPopup(Context context, List<MainMenuItemDefinition> definitions,
                  Consumer<MainMenuItemDefinition> onExecute) {
        super(context);
        setOrientation(VERTICAL);
        int padding = UIUtils.dp2pxInt(3.0f);
        setPadding(padding, padding, padding, padding);
        setBackground(background(COLOR_BACKGROUND, 3.0f, 1, COLOR_BORDER));
        setClickable(true);

        for (MainMenuItemDefinition definition : normalized(definitions)) {
            if (definition.type() == MainMenuItemType.SEPARATOR) {
                addView(divider(context), dividerParams());
            } else {
                addView(row(context, definition, onExecute), new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        UIUtils.dp2pxInt(ROW_HEIGHT_DP)
                ));
            }
        }
    }

    private LinearLayout row(Context context, MainMenuItemDefinition definition,
                             Consumer<MainMenuItemDefinition> onExecute) {
        boolean enabled = safeEnabled(definition);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(6.0f), 0, UIUtils.dp2pxInt(7.0f), 0);
        row.setClickable(enabled);

        TextView check = text(context,
                definition.type() == MainMenuItemType.CHECK && safeChecked(definition) ? "\u2713" : "",
                enabled ? COLOR_TEXT : COLOR_MUTED, Gravity.CENTER);
        row.addView(check, new LayoutParams(UIUtils.dp2pxInt(18.0f), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView label = text(context,
                Component.translatable(definition.labelTranslationKey()).getString(),
                enabled ? COLOR_TEXT : COLOR_MUTED,
                Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(label, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView shortcut = text(context, safeShortcut(definition), COLOR_MUTED,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(shortcut, new LayoutParams(
                UIUtils.dp2pxInt(76.0f),
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (enabled) {
            row.setOnClickListener(view -> onExecute.accept(definition));
            row.setOnHoverListener((view, event) -> {
                boolean hovered = event.getAction() == MotionEvent.ACTION_HOVER_ENTER
                        || event.getAction() == MotionEvent.ACTION_HOVER_MOVE;
                row.setBackground(hovered ? background(COLOR_HOVER, 2.0f, 0, 0) : null);
                return true;
            });
        }
        return row;
    }

    private static TextView text(Context context, String value, int color, int gravity) {
        TextView view = new TextView(context);
        view.setText(value);
        UIUtils.setLockedTextSize(view, 11.0f);
        view.setTextColor(color);
        view.setGravity(gravity);
        return view;
    }

    private static View divider(Context context) {
        View divider = new View(context);
        divider.setBackground(background(COLOR_DIVIDER, 0.0f, 0, 0));
        return divider;
    }

    private static LayoutParams dividerParams() {
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, UIUtils.dp2pxInt(1.0f))
        );
        params.setMargins(UIUtils.dp2pxInt(6.0f), UIUtils.dp2pxInt(3.0f),
                UIUtils.dp2pxInt(6.0f), UIUtils.dp2pxInt(3.0f));
        return params;
    }

    private static List<MainMenuItemDefinition> normalized(List<MainMenuItemDefinition> source) {
        List<MainMenuItemDefinition> result = new ArrayList<>();
        boolean previousSeparator = true;
        for (MainMenuItemDefinition definition : source) {
            boolean separator = definition.type() == MainMenuItemType.SEPARATOR;
            if (separator && previousSeparator) {
                continue;
            }
            result.add(definition);
            previousSeparator = separator;
        }
        if (!result.isEmpty() && result.get(result.size() - 1).type() == MainMenuItemType.SEPARATOR) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    private static boolean safeEnabled(MainMenuItemDefinition definition) {
        try {
            return definition.isEnabled();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Main menu enabled-state evaluation failed: {}", definition.id(), exception);
            return false;
        }
    }

    private static boolean safeChecked(MainMenuItemDefinition definition) {
        try {
            return definition.isChecked();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Main menu checked-state evaluation failed: {}", definition.id(), exception);
            return false;
        }
    }

    private static String safeShortcut(MainMenuItemDefinition definition) {
        try {
            return definition.resolvedShortcutText();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Main menu shortcut evaluation failed: {}", definition.id(), exception);
            return "";
        }
    }

    private static ShapeDrawable background(int color, float radiusDp, int strokeWidthDp, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(radiusDp));
        if (strokeWidthDp > 0) {
            drawable.setStroke(UIUtils.dp2pxInt(strokeWidthDp), strokeColor);
        }
        return drawable;
    }
}
