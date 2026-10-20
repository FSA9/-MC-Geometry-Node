package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.overlay.OverlayEscapeCapture;

import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.config.ConfigDraft;
import com.mine.geometry_node.client.config.ConfigEntry;
import com.mine.geometry_node.client.input.binding.InputBinding;
import com.mine.geometry_node.client.ui.shortcut.ControlKeyRecorder;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.shortcut.UiInputBindings;
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
    private final ControlKeyRecorder mControlRecorder = new ControlKeyRecorder();
    private boolean mConsumedMouseGesture;
    private int mConsumedKey = -1;

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
        clear.setEnabled(entry.accepts(""));
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
        mConsumedKey = -1;
        mConsumedMouseGesture = false;
        mControlRecorder.reset();
        String binding = mDraft.get(mEntry);
        mCapture.setText(binding == null || binding.isBlank()
                ? tr("geometry_node.settings.value.unassigned") : binding);
        updateStatus(binding);
    }

    @Override
    public void revalidate() {
        updateStatus(mDraft.get(mEntry));
    }

    @Override public void focus() { mCapture.requestFocus(); }

    private void beginCapture() {
        mConsumedKey = -1;
        mConsumedMouseGesture = false;
        mControlRecorder.reset();
        mCapturing = true;
        mCapture.requestFocus();
        mCapture.setText(tr(mEntry.captureControlKeys()
                ? "geometry_node.settings.value.press_control_binding" : "geometry_node.settings.value.press_binding"));
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

    private final class CaptureView extends TextView implements OverlayEscapeCapture {
        CaptureView(Context context) { super(context); }

        @Override public boolean captureEscape(KeyEvent event) {
            return (mCapturing || mConsumedKey == event.getKeyCode()) && dispatchKeyEvent(event);
        }

        @Override protected void onFocusChanged(boolean focused, int direction, icyllis.modernui.graphics.Rect previouslyFocusedRect) {
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            // The release of a completed capture may be delivered to another view.
            if (!focused) refresh();
        }

        @Override protected void onDetachedFromWindow() {
            refresh();
            super.onDetachedFromWindow();
        }

        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            if (mConsumedKey == event.getKeyCode()) {
                if (event.getAction() == KeyEvent.ACTION_UP) mConsumedKey = -1;
                return true;
            }
            if (!mCapturing) return super.dispatchKeyEvent(event);
            if (mEntry.captureControlKeys()) {
                InputBinding binding = UiInputBindings.fromEvent(event);
                String result = mControlRecorder.accept(event.getKeyCode(), event.getAction() == KeyEvent.ACTION_DOWN,
                        binding != null ? binding.text() : null);
                if (result != null) {
                    setBinding(result);
                    if (event.getAction() == KeyEvent.ACTION_DOWN) mConsumedKey = event.getKeyCode();
                }
                return true;
            }
            if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
            if (event.getKeyCode() == KeyEvent.KEY_ESCAPE) { cancelCapture(); mConsumedKey = event.getKeyCode(); return true; }
            InputBinding binding = UiInputBindings.fromEvent(event);
            if (binding != null) { setBinding(binding.text()); mConsumedKey = event.getKeyCode(); }
            return true;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                // A new gesture also recovers from a release lost outside this view.
                mConsumedMouseGesture = false;
            } else if (mConsumedMouseGesture) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    mConsumedMouseGesture = false;
                }
                return true;
            }
            if (mCapturing && mEntry.editorType() == ConfigEntry.EditorType.SHORTCUT
                    && action == MotionEvent.ACTION_DOWN) {
                InputBinding binding = UiInputBindings.fromEvent(event);
                if (binding != null) {
                    setBinding(binding.text());
                    // Consume the matching release here, so TextView cannot turn it
                    // into another capture click or leave suppression for a later click.
                    mConsumedMouseGesture = true;
                    return true;
                }
            }
            return super.onTouchEvent(event);
        }
    }
}
