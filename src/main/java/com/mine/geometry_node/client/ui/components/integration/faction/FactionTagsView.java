package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.components.base.layout.TagFlowLayout;
import com.mine.geometry_node.client.ui.components.composite.display.UiTagChip;
import com.mine.geometry_node.client.ui.theme.UiMetrics;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.metadata.TagPolicy;
import icyllis.modernui.core.Context;
import icyllis.modernui.core.Handler;
import icyllis.modernui.core.Looper;
import icyllis.modernui.widget.LinearLayout;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.*;

/** Thin tag composition; the repository owns completed operations, this view owns raw input. */
public final class FactionTagsView extends LinearLayout {
    private final UiTheme theme;
    private final Supplier<List<String>> values;
    private final BooleanSupplier ownerCurrent, editable;
    private final Function<String, CompletableFuture<Void>> addTag, removeTag;
    private final TagFlowLayout chips;
    private final UiTextInput input;
    private final UiIconButton add;
    private final UiDisplayText feedback;
    private final Looper looper = Looper.myLooper();
    private final Handler handler = new Handler(looper);
    private List<String> rendered = List.of();
    private boolean pending, suspended = true, disposed;

    public FactionTagsView(Context context, UiTheme theme, Supplier<List<String>> values,
                           BooleanSupplier ownerCurrent, BooleanSupplier editable,
                           Function<String, CompletableFuture<Void>> addTag,
                           Function<String, CompletableFuture<Void>> removeTag) {
        super(context);
        this.theme = Objects.requireNonNull(theme); this.values = Objects.requireNonNull(values);
        this.ownerCurrent = Objects.requireNonNull(ownerCurrent); this.editable = Objects.requireNonNull(editable);
        this.addTag = Objects.requireNonNull(addTag); this.removeTag = Objects.requireNonNull(removeTag);
        setOrientation(VERTICAL);
        chips = new TagFlowLayout(context, 6, 6); addView(chips, new LayoutParams(-1, -2));
        LinearLayout compose = new LinearLayout(context);
        input = UiTextInput.createThemed(context, theme); input.setSingleLine(true); input.setHint(tr("tag_input"));
        input.setEditActions(this::addInput, () -> { input.setText(""); showError(""); });
        input.setOnFocusChangeListener((view, focused) -> { if (!focused && !suspended) addInput(); });
        compose.addView(input, new LayoutParams(0, px(32), 1));
        add = UiIconButton.createThemed(context, theme, SvgIconView.Icon.PLUS, tr("add_tag"));
        add.setOnClickListener(view -> addInput());
        LayoutParams button = new LayoutParams(px(32), px(32)); button.leftMargin = px(6);
        compose.addView(add, button);
        LayoutParams row = new LayoutParams(-1, -2); row.topMargin = px(8); addView(compose, row);
        feedback = new UiDisplayText(context, "", UiMetrics.TEXT_BODY, theme.status().error());
        LayoutParams message = new LayoutParams(-1, -2); message.topMargin = px(6); addView(feedback, message);
        showError(""); refresh();
    }

    public void refresh() {
        if (disposed) return;
        List<String> tags = ownerCurrent.getAsBoolean() ? values.get() : List.of();
        boolean enabled = canEdit();
        input.setEnabled(enabled); add.setEnabled(enabled);
        if (!tags.equals(rendered)) {
            rendered = List.copyOf(tags); chips.removeAllViews();
            for (String tag : tags) {
                UiTagChip chip = new UiTagChip(getContext(), tag, theme, UiMetrics.TEXT_BODY);
                chip.setOnRemove(tr("remove_tag"), () -> {
                    if (canEdit()) submit(() -> removeTag.apply(tag), null);
                });
                chips.addView(chip, new TagFlowLayout.LayoutParams(-2, px(UiTagChip.HEIGHT_DP)));
            }
        }
        for (int i = 0; i < chips.getChildCount(); i++) chips.getChildAt(i).setEnabled(enabled);
    }

    public void resume() { if (!disposed) { suspended = false; refresh(); } }
    public void suspend() { suspended = true; input.clearFocus(); refresh(); }
    public void dispose() { suspended = true; disposed = true; input.clearFocus(); input.setEnabled(false); add.setEnabled(false); }

    private boolean canEdit() {
        return !disposed && !suspended && !pending && ownerCurrent.getAsBoolean() && editable.getAsBoolean();
    }

    private void addInput() {
        if (!canEdit() || input.getText().length() == 0) return;
        String raw = input.getText().toString();
        try {
            String tag = TagPolicy.EXACT.normalize(raw);
            if (values.get().contains(tag)) { input.setText(""); showError(""); return; }
            submit(() -> addTag.apply(tag), raw);
        } catch (IllegalArgumentException invalid) { showError(tr("error.invalid")); }
    }

    private void submit(Supplier<CompletableFuture<Void>> operation, String submittedInput) {
        pending = true; showError(""); refresh();
        try {
            operation.get().whenComplete((ignored, failure) -> dispatch(() -> {
                pending = false;
                if (disposed || !ownerCurrent.getAsBoolean()) return;
                if (failure == null && submittedInput != null && submittedInput.equals(input.getText().toString()))
                    input.setText("");
                showError(errorMessage(failure)); refresh();
            }));
        } catch (RuntimeException failure) { pending = false; showError(errorMessage(failure)); refresh(); }
    }

    private void dispatch(Runnable task) { if (Looper.myLooper() == looper) task.run(); else handler.post(task); }
    private void showError(String message) {
        input.setErrorMessage(message); feedback.setText(message);
        feedback.setVisibility(message.isEmpty() ? GONE : VISIBLE);
    }
}
