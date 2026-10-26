package com.mine.geometry_node.client.ui.components.integration.entity.editor;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.integration.entity.appearance.EntityAppearancePicker;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorAppearance;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.mine.geometry_node.client.ui.components.integration.entity.editor.EntityEditorViews.*;

/** Binds the shared picker to one editor draft; only a completed selection publishes an edit. */
final class EntityAppearanceFieldView extends LinearLayout {
    @FunctionalInterface
    interface Picker {
        void show(View anchor, EntityAppearancePicker.Source source, EntityAppearanceSpec current,
                  Consumer<EntityAppearanceSpec> selected);
    }

    private final EntityEditDraft draft;
    private final BooleanSupplier editable;
    private final Picker picker;
    private final UiDisplayText name, registry, state;
    private final UiActionButton choose;
    private boolean disposed;
    private long selectionGeneration;

    EntityAppearanceFieldView(Context context, UiTheme theme, EntityEditDraft draft, BooleanSupplier editable) {
        this(context, theme, draft, editable, (anchor, source, current, selected) ->
                EntityAppearancePicker.show(context, anchor, theme, source, current, selected));
    }

    EntityAppearanceFieldView(Context context, UiTheme theme, EntityEditDraft draft, BooleanSupplier editable,
                              Picker picker) {
        super(context);
        this.draft = draft; this.editable = editable; this.picker = picker;
        setOrientation(VERTICAL);
        LinearLayout valueRow = row(context);
        valueRow.addView(text(context, tr("appearance.current_appearance"), theme.text().normal()),
                new LinearLayout.LayoutParams(0, px(82), 1));
        LinearLayout value = column(context);
        name = text(context, "", theme.text().normal());
        registry = text(context, "", theme.text().secondary());
        state = text(context, "", theme.text().muted());
        for (UiDisplayText text : new UiDisplayText[]{name, registry, state}) {
            text.setSingleLine(true); text.setEllipsize(TextUtils.TruncateAt.END);
            value.addView(text, size(-1, 24));
        }
        valueRow.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        addView(valueRow, size(-1, -2));
        LinearLayout actions = row(context); actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        choose = button(context, theme, "appearance.choose", this::openPicker);
        choose.setSingleLine(true);
        choose.setMinWidth(px(88)); choose.setMinimumWidth(px(88));
        choose.setPadding(px(16), 0, px(16), 0);
        actions.addView(choose, size(88, 36));
        LinearLayout.LayoutParams actionParams = size(-1, 48); actionParams.bottomMargin = px(8);
        addView(actions, actionParams);
        LinearLayout.LayoutParams line = size(-1, 1); line.bottomMargin = px(8);
        addView(divider(context, theme), line);
        refreshConfirmed();
    }

    boolean supported() {
        return draft.flag("appearanceSupported") && draft.snapshot().containsKey("appearance");
    }

    void refreshConfirmed() {
        if (disposed) return;
        setVisibility(supported() ? VISIBLE : GONE);
        if (supported()) {
            EntityAppearanceSpec spec = EntityEditorAppearance.read(draft.dictionary("appearance"));
            String id = spec.isNone() ? sourceType() : spec.targetTypeId();
            Identifier key = Identifier.tryParse(id);
            var type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
            name.setText(type == null ? id : Component.translatable(type.getDescriptionId()).getString());
            registry.setText(id);
            state.setText(tr(spec.isNone() ? "appearance.state_default" : "appearance.state_custom"));
        }
        refreshInputState();
    }

    void refreshInputState() {
        choose.setEnabled(!disposed && supported() && editable.getAsBoolean());
    }

    private String sourceType() { return String.valueOf(draft.snapshot().getOrDefault("registryType", "")); }

    private void openPicker() {
        if (disposed || !supported() || !editable.getAsBoolean()) return;
        Map<String, Object> expected = draft.dictionary("appearance");
        EntityAppearanceSpec current = EntityEditorAppearance.read(expected);
        long generation = ++selectionGeneration;
        var snapshot = draft.snapshot();
        var source = new EntityAppearancePicker.Source(sourceType(),
                String.valueOf(snapshot.getOrDefault("appearanceVariant", "")),
                EntityTemplateValue.from(snapshot.get("preview")));
        picker.show(this, source, current, selected -> {
            if (disposed || generation != selectionGeneration || !supported() || !editable.getAsBoolean()) return;
            // A stale child cannot overwrite a newer confirmed/local choice in its parent.
            if (!com.mine.geometry_node.core.engine.system.entity.edit.EntityEditorChanges.same(
                    expected, draft.dictionary("appearance"))) return;
            draft.set("appearance", EntityEditorAppearance.values(selected));
            refreshConfirmed();
        });
    }

    void dispose() { disposed = true; selectionGeneration++; refreshInputState(); }
}
