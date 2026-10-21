package com.mine.geometry_node.client.ui.shell.menu;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuItemDefinition;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuItemType;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
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
    private static final int COLOR_BACKGROUND = UiThemes.EDITOR.surface().popup();
    private static final int COLOR_BORDER = UiThemes.EDITOR.border().strong();
    private static final int COLOR_TEXT = UiThemes.EDITOR.text().normal();
    private static final int COLOR_MUTED = UiThemes.EDITOR.text().muted();
    private static final int COLOR_DIVIDER = UiThemes.EDITOR.border().divider();

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
        boolean checked = definition.type() == MainMenuItemType.CHECK && safeChecked(definition);
        LinearLayout row = new LinearLayout(context) {
            @Override public boolean performClick() { return isEnabled() && super.performClick(); }
        };
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(6.0f), 0, UIUtils.dp2pxInt(7.0f), 0);
        UiMenuItems.decorate(row, 0, 2.0f, UiThemes.EDITOR);
        row.setEnabled(enabled);
        row.setSelected(checked);
        row.setClickable(enabled);

        TextView check = text(context,
                checked ? "\u2713" : "",
                COLOR_TEXT, Gravity.CENTER);
        row.addView(check, new LayoutParams(UIUtils.dp2pxInt(18.0f), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView label = text(context,
                Component.translatable(definition.labelTranslationKey()).getString(),
                COLOR_TEXT,
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
        }
        return row;
    }

    private static TextView text(Context context, String value, int color, int gravity) {
        TextView view = new TextView(context);
        view.setText(value);
        UIUtils.setLockedTextSize(view, 11.0f);
        view.setTextColor(UiMenuItems.textColors(color, UiThemes.EDITOR));
        view.setDuplicateParentStateEnabled(true);
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
