package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.common.SvgIconView;
import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.persistence.config.InputBinding;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;

import java.util.List;

final class ShortcutConfigEntryEditor extends AbstractConfigEntryEditor<String> {
    private final CaptureView mCapture;
    private boolean mCapturing;
    private boolean mSuppressClick;

    private ShortcutConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<String> entry,
                                      SettingsEditorEnvironment environment) {
        super(context, draft, entry, environment, true);
        mCapture = new CaptureView(context);
        mCapture.setTextSize(0, UIUtils.dp2px(10.5f));
        mCapture.setTextColor(SettingsEditorStyle.TEXT);
        mCapture.setGravity(Gravity.CENTER);
        mCapture.setFocusable(true);
        mCapture.setFocusableInTouchMode(true);
        mCapture.setOnClickListener(view -> beginCapture());
        mControlHost.addView(mCapture, new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(26), 1.0f));
        FrameLayout clear = SettingsEditorStyle.iconButton(context, SvgIconView.Icon.CLEAR,
                tr("geometry_node.settings.action.clear"));
        clear.setOnClickListener(view -> setBinding(""));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(26));
        clearParams.leftMargin = UIUtils.dp2pxInt(4);
        mControlHost.addView(clear, clearParams);
    }

    @SuppressWarnings("unchecked")
    static ConfigEntryEditor<?> create(Context context, ConfigDraft draft, ConfigEntry<?> entry,
                                       SettingsEditorEnvironment environment) {
        return new ShortcutConfigEntryEditor(context, draft, (ConfigEntry<String>) entry, environment);
    }

    @Override public void refresh() {
        mCapturing = false;
        String binding = mDraft.get(mEntry);
        mCapture.setText(binding == null || binding.isBlank()
                ? tr("geometry_node.settings.value.unassigned") : binding);
        updateStatus(binding);
    }

    @Override
    public void revalidate() {
        updateStatus(mDraft.get(mEntry));
    }

    private void beginCapture() {
        if (mSuppressClick) {
            mSuppressClick = false;
            return;
        }
        mCapturing = true;
        mCapture.requestFocus();
        mCapture.setText(tr("geometry_node.settings.value.press_binding"));
        mCapture.setBackground(SettingsEditorStyle.rect(SettingsEditorStyle.CONTROL_HOVER, 2.0f,
                SettingsEditorStyle.ACCENT));
    }

    private void cancelCapture() {
        mCapturing = false;
        refresh();
    }

    private void setBinding(String value) {
        mDraft.set(mEntry, value);
        refresh();
        notifyStateChanged();
    }

    private void updateStatus(String value) {
        if (value == null || value.isBlank()) {
            showValidation(true, "");
            return;
        }
        List<ConfigEntry<String>> conflicts = mDraft.shortcutConflicts(mEntry, value);
        if (conflicts.isEmpty()) {
            showValidation(true, "");
        } else {
            String names = conflicts.stream().map(conflict -> tr(conflict.labelTranslationKey()))
                    .reduce((left, right) -> left + ", " + right).orElse("");
            String message = tr("geometry_node.settings.validation.shortcut_conflict") + ": " + names;
            showValidation(false, message);
        }
    }

    private void showValidation(boolean valid, String message) {
        mCapture.setBackground(SettingsEditorStyle.rect(
                valid ? SettingsEditorStyle.CONTROL : SettingsEditorStyle.CONTROL_ERROR,
                2.0f,
                valid ? SettingsEditorStyle.BORDER : SettingsEditorStyle.BORDER_ERROR));
        setValidationSilently(valid, message);
    }

    private final class CaptureView extends TextView {
        CaptureView(Context context) { super(context); }

        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if (!mCapturing || event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
            if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) { cancelCapture(); return true; }
            InputBinding binding = InputBinding.fromEvent(event);
            if (binding != null) setBinding(binding.text());
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (mCapturing && mEntry.editorType() == ConfigEntry.EditorType.SHORTCUT
                    && event.getAction() == MotionEvent.ACTION_DOWN) {
                InputBinding binding = InputBinding.fromEvent(event);
                if (binding != null) {
                    mSuppressClick = true;
                    setBinding(binding.text());
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }
    }
}
