package com.mine.geometry_node.client.ui.settings.editor;

import com.mine.geometry_node.client.ui.persistence.config.ConfigDraft;
import com.mine.geometry_node.client.ui.persistence.config.ConfigEntry;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

abstract class AbstractConfigEntryEditor<T> extends LinearLayout implements ConfigEntryEditor<T> {
    protected final ConfigDraft mDraft;
    protected final ConfigEntry<T> mEntry;
    protected final SettingsEditorEnvironment mEnvironment;
    protected final LinearLayout mControlHost;
    private Runnable mStateChangedListener;
    private boolean mValid = true;
    private String mValidationMessage = "";

    AbstractConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<T> entry,
                              SettingsEditorEnvironment environment) {
        this(context, draft, entry, environment, false);
    }

    AbstractConfigEntryEditor(Context context, ConfigDraft draft, ConfigEntry<T> entry,
                              SettingsEditorEnvironment environment, boolean compact) {
        super(context);
        mDraft = draft;
        mEntry = entry;
        mEnvironment = environment != null ? environment : SettingsEditorEnvironment.NONE;
        setOrientation(compact ? HORIZONTAL : VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(0, compact ? UIUtils.dp2pxInt(3) : UIUtils.dp2pxInt(7),
                0, compact ? UIUtils.dp2pxInt(3) : UIUtils.dp2pxInt(7));

        if (compact) {
            TextView title = text(context, tr(entry.labelTranslationKey()), 10.5f, SettingsEditorStyle.TEXT);
            title.setSingleLine(true);
            addView(title, new LayoutParams(0, UIUtils.dp2pxInt(30), 0.24f));

            TextView description = text(context, tr(entry.descriptionTranslationKey()), 9.5f,
                    SettingsEditorStyle.MUTED);
            description.setSingleLine(true);
            LayoutParams descriptionParams = new LayoutParams(0, UIUtils.dp2pxInt(30), 0.36f);
            descriptionParams.leftMargin = UIUtils.dp2pxInt(8);
            addView(description, descriptionParams);

            mControlHost = new LinearLayout(context);
            mControlHost.setOrientation(HORIZONTAL);
            mControlHost.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LayoutParams controlParams = new LayoutParams(0, UIUtils.dp2pxInt(30), 0.40f);
            controlParams.leftMargin = UIUtils.dp2pxInt(8);
            addView(mControlHost, controlParams);

            FrameLayout reset = createResetButton(context);
            LayoutParams resetParams = new LayoutParams(UIUtils.dp2pxInt(26), UIUtils.dp2pxInt(26));
            resetParams.leftMargin = UIUtils.dp2pxInt(4);
            addView(reset, resetParams);
            return;
        }

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(context, tr(entry.labelTranslationKey()), 12.0f, SettingsEditorStyle.TEXT);
        header.addView(title, new LayoutParams(0, UIUtils.dp2pxInt(24), 1.0f));
        FrameLayout reset = createResetButton(context);
        header.addView(reset, new LayoutParams(UIUtils.dp2pxInt(24), UIUtils.dp2pxInt(24)));
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(24)));

        TextView description = text(context, tr(entry.descriptionTranslationKey()), 10.0f, SettingsEditorStyle.MUTED);
        description.setSingleLine(false);
        addView(description, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mControlHost = new LinearLayout(context);
        mControlHost.setOrientation(VERTICAL);
        LayoutParams controlParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.topMargin = UIUtils.dp2pxInt(6);
        addView(mControlHost, controlParams);
    }

    private FrameLayout createResetButton(Context context) {
        FrameLayout reset = SettingsEditorStyle.iconButton(context, SvgIconView.Icon.RESET,
                tr("geometry_node.settings.action.reset"));
        reset.setOnClickListener(view -> reset());
        return reset;
    }

    @Override public ConfigEntry<T> entry() { return mEntry; }
    @Override public View getView() { return this; }
    @Override public boolean isValid() { return mValid; }
    @Override public String validationMessage() { return mValidationMessage; }
    @Override public void setOnStateChangedListener(Runnable listener) { mStateChangedListener = listener; }

    @Override public void reset() {
        mDraft.reset(mEntry);
        refresh();
        notifyStateChanged();
    }

    protected void setValidation(boolean valid, String message) {
        setValidationSilently(valid, message);
        notifyStateChanged();
    }

    protected void setValidationSilently(boolean valid, String message) {
        mValid = valid;
        mValidationMessage = valid ? "" : (message != null ? message : "");
    }

    protected void notifyStateChanged() {
        if (mStateChangedListener != null) mStateChangedListener.run();
    }

    protected static TextView text(Context context, String value, float sizeDp, int color) {
        TextView view = UIUtils.createLockedTextView(context, value, sizeDp, color);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return view;
    }

    protected static String tr(String key) { return Component.translatable(key).getString(); }
}
