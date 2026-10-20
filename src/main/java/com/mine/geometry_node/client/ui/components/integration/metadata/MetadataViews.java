package com.mine.geometry_node.client.ui.components.integration.metadata;

import com.mine.geometry_node.client.ui.components.base.button.UiActionButton;
import com.mine.geometry_node.client.ui.components.base.display.UiDisplayText;
import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

/** Package-local presentation helpers and generation-guarded capability subscriptions. */
final class MetadataViews {
    private MetadataViews() {}
    static int px(float dp) { return UIUtils.dp2pxInt(dp); }
    static String tr(String key, Object... args) {
        return Component.translatable("geometry_node.database.data." + key, args).getString();
    }
    static UiDisplayText text(Context context, String value, int color) {
        UiDisplayText result = new UiDisplayText(context, value, 11, color);
        result.setGravity(Gravity.CENTER_VERTICAL);
        return result;
    }
    static UiActionButton button(Context context, String key, Runnable action) {
        UiActionButton button = new UiActionButton(context, tr(key), UiActionButton.Role.SECONDARY,
                UiActionButton.Density.NORMAL);
        button.setOnClickListener(view -> action.run());
        button.setPadding(px(10), 0, px(10), 0);
        return button;
    }
    static UiTextInput input(Context context, String hint, boolean singleLine) {
        UiTextInput input = new UiTextInput(context);
        input.setSingleLine(singleLine);
        input.setHint(tr(hint));
        input.setGravity(Gravity.LEFT | (singleLine ? Gravity.CENTER_VERTICAL : Gravity.TOP));
        if (!singleLine) input.setPadding(px(8), px(8), px(8), px(8));
        return input;
    }
    static void watch(UiTextInput input, Runnable action) {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable editable) { action.run(); }
        });
    }
    static LinearLayout column(Context context) {
        LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.VERTICAL); return result;
    }
    static LinearLayout row(Context context) {
        LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.HORIZONTAL);
        result.setGravity(Gravity.CENTER_VERTICAL); return result;
    }
    static LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : px(width), height < 0 ? height : px(height));
    }
    static String status(DataDictionaryBinding.State state) {
        if (!state.message().isEmpty()) return state.message();
        if (state.loading()) return tr("loading");
        if (!state.available()) return tr("unavailable");
        if (state.saving()) return tr("saving");
        return "";
    }

    abstract static class BoundView extends LinearLayout {
        DataDictionaryBinding binding;
        private Runnable listener;
        private long generation;
        private boolean subscribed;
        private boolean refreshedVisible;

        BoundView(Context context) { super(context); }
        public final void setBinding(DataDictionaryBinding next) {
            unsubscribe(); binding = next;
            if (isAttachedToWindow()) subscribe();
            render(next == null ? null : next.snapshot());
            refreshIfVisible();
        }
        protected abstract void render(DataDictionaryBinding.State state);
        protected boolean refreshOnAttach() { return false; }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow(); subscribe();
            if (binding != null) {
                render(binding.snapshot());
                refreshIfVisible();
            }
        }
        @Override protected void onDetachedFromWindow() { unsubscribe(); refreshedVisible = false; super.onDetachedFromWindow(); }
        @Override protected void onVisibilityChanged(View changed, int visibility) {
            super.onVisibilityChanged(changed, visibility);
            if (!isShown()) refreshedVisible = false;
            else refreshIfVisible();
        }
        private void refreshIfVisible() {
            if (binding != null && refreshOnAttach() && isAttachedToWindow() && isShown() && !refreshedVisible) {
                refreshedVisible = true;
                render(binding.snapshot());
                binding.refresh();
            }
        }
        private void subscribe() {
            if (binding == null || subscribed) return;
            subscribed = true;
            DataDictionaryBinding source = binding;
            long token = ++generation;
            listener = () -> post(() -> {
                if (subscribed && generation == token && source == binding && isShown()) render(source.snapshot());
            });
            source.addChangeListener(listener);
        }
        private void unsubscribe() {
            generation++; refreshedVisible = false;
            if (binding != null && listener != null) binding.removeChangeListener(listener);
            listener = null; subscribed = false;
        }
    }
}
