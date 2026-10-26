package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.composite.table.UiTableStyle;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.entity.classification.EntityTypeTraits;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static com.mine.geometry_node.client.ui.components.integration.entity.editor.EntityEditorViews.*;

/** Native-tag controls embedded in the base-properties category; the owner supplies scrolling. */
final class EntityTypesView extends LinearLayout {
    private record Controls(UiCheckBox enabled, UiDisplayText origin, UiActionButton reset) {}
    private final Map<String, Controls> controls = new LinkedHashMap<>();
    private final EntityEditDraft draft;
    private final BooleanSupplier editable;

    EntityTypesView(Context context, UiTheme theme, EntityEditDraft draft, BooleanSupplier editable) {
        super(context);
        this.draft = draft; this.editable = editable;
        setOrientation(VERTICAL);
        for (String id : EntityTypeTraits.IDS) {
            LinearLayout item = row(context);
            item.setPadding(px(12), px(8), px(12), px(8));
            LinearLayout names = column(context);
            names.addView(text(context, Component.translatable(EntityTypeTraits.labelKey(id)).getString(), theme.text().normal()), size(-1, 25));
            UiDisplayText identifier = text(context, id, theme.text().muted());
            UIUtils.setLockedTextSize(identifier, UiMetrics.TEXT_CAPTION);
            names.addView(identifier, size(-1, 22));
            item.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            UiCheckBox enabled = UiCheckBox.createThemed(context, theme);
            enabled.setContentDescription(Component.translatable(EntityTypeTraits.labelKey(id)).getString());
            item.addView(enabled, size(18, 18));
            UiDisplayText origin = text(context, "", theme.text().muted());
            origin.setGravity(Gravity.CENTER);
            item.addView(origin, size(64, 32));
            UiActionButton reset = UiTableStyle.rowButton(context, theme);
            reset.setText(tr("types.reset")); UIUtils.setLockedTextSize(reset, UiMetrics.TEXT_BODY);
            item.addView(reset, size(76, 32));
            enabled.setOnCheckedChangeListener((view, checked) -> {
                if (!editable.getAsBoolean()) { refreshConfirmed(); return; }
                var values = new LinkedHashMap<>(draft.dictionary("biologicalTypes"));
                values.put(id, checked); draft.set("biologicalTypes", values); refreshConfirmed();
            });
            reset.setOnClickListener(view -> {
                if (!editable.getAsBoolean()) return;
                var values = new LinkedHashMap<>(draft.dictionary("biologicalTypes"));
                values.remove(id); draft.set("biologicalTypes", values); refreshConfirmed();
            });
            controls.put(id, new Controls(enabled, origin, reset));
            if (getChildCount() > 0) addView(divider(context, theme), size(-1, 1));
            addView(item, size(-1, -2));
        }
        refreshConfirmed();
    }
    void refreshConfirmed() {
        Map<String, Object> overrides = draft.dictionary("biologicalTypes");
        Map<String, Object> defaults = draft.dictionary("biologicalTypeDefaults");
        controls.forEach((id, row) -> {
            boolean explicit = overrides.containsKey(id);
            row.enabled.setChecked(Boolean.TRUE.equals(explicit ? overrides.get(id) : defaults.get(id)));
            row.enabled.setEnabled(editable.getAsBoolean() && defaults.containsKey(id));
            row.origin.setText(tr(explicit ? "types.overridden" : "types.inherited"));
            row.reset.setEnabled(editable.getAsBoolean() && explicit);
        });
    }
}
