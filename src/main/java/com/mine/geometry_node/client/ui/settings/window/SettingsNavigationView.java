package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageDefinition;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
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

    private final Map<String, UiActionButton> rows = new LinkedHashMap<>();
    private final Consumer<String> onSelect;
    private String selectedId;

    SettingsNavigationView(Context context, List<SettingsPageDefinition> definitions, Consumer<String> onSelect) {
        super(context);
        this.onSelect = onSelect;
        setOrientation(VERTICAL);
        for (SettingsPageDefinition definition : definitions) {
            UiActionButton row = createRow(context, definition);
            rows.put(definition.id(), row);
            addView(row, new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    UIUtils.dp2pxInt(34.0f)
            ));
        }
        updateStyles();
    }

    void setSelected(String pageId) {
        selectedId = pageId;
        updateStyles();
    }

    void applyFilter(Predicate<String> visiblePage) {
        for (Map.Entry<String, UiActionButton> entry : rows.entrySet()) {
            entry.getValue().setVisibility(visiblePage.test(entry.getKey()) ? View.VISIBLE : View.GONE);
        }
    }

    private UiActionButton createRow(Context context, SettingsPageDefinition definition) {
        UiActionButton row = new UiActionButton(context,
                Component.translatable(definition.titleTranslationKey()).getString(),
                UiActionButton.Role.QUIET, UiActionButton.Density.COMPACT,
                new UiActionButton.Style(0, 0, 0, UiTheme.Metrics.DISABLED_ALPHA));
        row.setMinWidth(0);
        row.setMinHeight(0);
        row.setMinimumWidth(0);
        row.setMinimumHeight(0);
        row.setSingleLine(true);
        UIUtils.setLockedTextSize(row, 11.5f);
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(14.0f), 0, UIUtils.dp2pxInt(8.0f), 0);
        row.setClickable(true);
        row.setOnClickListener(view -> onSelect.accept(definition.id()));
        return row;
    }

    private void updateStyles() {
        for (Map.Entry<String, UiActionButton> entry : rows.entrySet()) {
            boolean selected = entry.getKey().equals(selectedId);
            UiActionButton row = entry.getValue();
            row.setPalette(new UiActionButton.Palette(selected ? COLOR_ACTIVE : 0,
                    selected ? COLOR_ACTIVE : COLOR_HOVER, COLOR_ACTIVE,
                    selected ? COLOR_TEXT_ACTIVE : COLOR_TEXT));
            row.setStyle(new UiActionButton.Style(0, selected ? 1 : 0, COLOR_ACCENT,
                    UiTheme.Metrics.DISABLED_ALPHA));
        }
    }

}
