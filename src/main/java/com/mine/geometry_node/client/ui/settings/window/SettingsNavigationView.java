package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageDefinition;
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
import java.util.function.Consumer;
import java.util.function.Predicate;

final class SettingsNavigationView extends LinearLayout {
    private static final int COLOR_TEXT = 0xFFB8B8B8;
    private static final int COLOR_TEXT_ACTIVE = 0xFFE8E8E8;
    private static final int COLOR_HOVER = 0xFF383838;
    private static final int COLOR_ACTIVE = 0xFF424242;
    private static final int COLOR_ACCENT = 0xFF777777;

    private final Map<String, TextView> rows = new LinkedHashMap<>();
    private final Consumer<String> onSelect;
    private String selectedId;

    SettingsNavigationView(Context context, List<SettingsPageDefinition> definitions, Consumer<String> onSelect) {
        super(context);
        this.onSelect = onSelect;
        setOrientation(VERTICAL);
        for (SettingsPageDefinition definition : definitions) {
            TextView row = createRow(context, definition);
            rows.put(definition.id(), row);
            addView(row, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(34.0f)
            ));
        }
    }

    void setSelected(String pageId) {
        selectedId = pageId;
        updateStyles();
    }

    void applyFilter(Predicate<String> visiblePage) {
        for (Map.Entry<String, TextView> entry : rows.entrySet()) {
            entry.getValue().setVisibility(visiblePage.test(entry.getKey()) ? View.VISIBLE : View.GONE);
        }
    }

    private TextView createRow(Context context, SettingsPageDefinition definition) {
        TextView row = UIUtils.createLockedTextView(
                context,
                Component.translatable(definition.titleTranslationKey()).getString(),
                11.5f,
                COLOR_TEXT
        );
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(14.0f), 0, UIUtils.dp2pxInt(8.0f), 0);
        row.setClickable(true);
        row.setOnClickListener(view -> onSelect.accept(definition.id()));
        row.setOnHoverListener((view, event) -> {
            if (!definition.id().equals(selectedId)) {
                if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                    row.setBackground(rect(COLOR_HOVER, 0));
                } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                    row.setBackground(null);
                }
            }
            return true;
        });
        return row;
    }

    private void updateStyles() {
        for (Map.Entry<String, TextView> entry : rows.entrySet()) {
            boolean selected = entry.getKey().equals(selectedId);
            TextView row = entry.getValue();
            row.setTextColor(selected ? COLOR_TEXT_ACTIVE : COLOR_TEXT);
            row.setBackground(selected ? rect(COLOR_ACTIVE, COLOR_ACCENT) : null);
        }
    }

    private static ShapeDrawable rect(int color, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        if (strokeColor != 0) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1.0f)), strokeColor);
        }
        return drawable;
    }
}
