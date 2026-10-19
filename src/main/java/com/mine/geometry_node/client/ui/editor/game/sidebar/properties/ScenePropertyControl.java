package com.mine.geometry_node.client.ui.editor.game.sidebar.properties;

import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.input.UiSelectButton;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas.Field;
import com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas.Kind;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.TextView;

import java.util.function.Consumer;

/** One schema field; synchronization never invokes the user-change callback. */
public final class ScenePropertyControl {
    private final Field field;
    private final View view;
    private final Runnable changed;
    private String choiceValue = "";
    private String choiceLabel;

    public ScenePropertyControl(Context context, Field field, Runnable changed, Consumer<ScenePropertyControl> showChoices) {
        this.field = field;
        this.changed = changed;
        if (!field.editable()) {
            TextView text = UIUtils.createLockedTextView(context, "", 11, UiTheme.Text.MUTED);
            text.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            view = text;
            return;
        }
        view = switch (field.kind()) {
            case TEXT, NUMBER -> {
                UiTextInput input = new UiTextInput(context);
                input.setSingleLine(true);
                yield input;
            }
            case BOOLEAN -> {
                UiCheckBox checkbox = new UiCheckBox(context);
                checkbox.setOnCheckedChangeListener((v, checked) -> changed.run());
                yield checkbox;
            }
            case CHOICE -> {
                UiSelectButton button = new UiSelectButton(context, "", 11, () -> UiTheme.Metrics.RADIUS_SMALL);
                button.setOnClickListener(v -> showChoices.accept(this));
                yield button;
            }
        };
    }

    public Field field() { return field; }
    public View view() { return view; }
    public UiTextInput textInput() { return view instanceof UiTextInput input ? input : null; }

    public String value() {
        if (view instanceof UiCheckBox checkbox) return Boolean.toString(checkbox.isChecked());
        if (view instanceof UiSelectButton) return choiceValue;
        return ((TextView) view).getText().toString();
    }

    public void setValue(String value) {
        if (view instanceof UiCheckBox checkbox) {
            boolean checked = Boolean.parseBoolean(value);
            if (checkbox.isChecked() != checked) checkbox.setChecked(checked);
        } else if (view instanceof UiSelectButton button) {
            choiceValue = value;
            String label = choiceLabel(value);
            if (!label.equals(choiceLabel)) {
                choiceLabel = label;
                button.setValue(label);
            }
        } else {
            TextView text = (TextView) view;
            String label = field.kind() == Kind.CHOICE ? choiceLabel(value) : value;
            if (!text.getText().toString().equals(label)) text.setText(label);
            if (!field.editable()) text.setTooltipText(label);
        }
    }

    private String choiceLabel(String value) {
        return field.options().stream().filter(choice -> choice.value().equals(value))
                .findFirst().map(choice -> choice.label().getString()).orElse(value);
    }

    public void setEditable(boolean editable) {
        view.setEnabled(editable && field.editable() && (field.kind() != Kind.CHOICE || !field.options().isEmpty()));
    }

    public void choose(String value) {
        if (!view.isEnabled() || field.options().stream().noneMatch(choice -> choice.value().equals(value))
                || choiceValue.equals(value)) return;
        setValue(value);
        changed.run();
    }
}
