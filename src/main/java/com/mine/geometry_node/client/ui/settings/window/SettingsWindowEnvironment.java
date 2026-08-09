package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.editor.asset.dialog.FolderPickerDialog;
import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.settings.editor.SettingsChoice;
import com.mine.geometry_node.client.ui.settings.editor.SettingsEditorEnvironment;
import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import com.mine.geometry_node.client.ui.shell.layer.OverlayHandle;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalOptions;
import com.mine.geometry_node.client.ui.shell.layer.ephemeral.TransientOptions;
import com.mine.geometry_node.client.ui.shell.layer.ephemeral.TransientOverlay;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** MainUI services exposed to config editors without root-view discovery. */
final class SettingsWindowEnvironment implements SettingsEditorEnvironment, AutoCloseable {
    private static final int COLOR_BACKGROUND = 0xFF242424;
    private static final int COLOR_BORDER = 0xFF4A4A4A;
    private static final int COLOR_TEXT = 0xFFE3E3E3;
    private static final int COLOR_HOVER = 0xFF3A3A3A;
    private static final int COLOR_SELECTED = 0xFF4A4A4A;
    private static final float ROW_HEIGHT_DP = 28.0f;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final float EDGE_MARGIN_DP = 4.0f;

    private final Context context;
    private final MainUiServices services;
    private ChoiceOverlay activeChoice;
    private OverlayHandle choiceHandle;
    private boolean closed;

    SettingsWindowEnvironment(Context context, MainUiServices services) {
        this.context = Objects.requireNonNull(context, "context");
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public boolean showChoices(View anchor, List<SettingsChoice> values, String selected,
                               Consumer<String> onSelect) {
        if (closed || anchor == null || values == null || values.isEmpty() || onSelect == null) {
            return false;
        }
        closeTransient();
        ChoiceOverlay overlay = new ChoiceOverlay(anchor, List.copyOf(values), selected, onSelect);
        activeChoice = overlay;
        try {
            choiceHandle = services.layerManager().showTransient(
                    overlay, new TransientOptions(true, true, anchor));
            return true;
        } catch (RuntimeException exception) {
            if (activeChoice == overlay) {
                activeChoice = null;
                choiceHandle = null;
            }
            throw exception;
        }
    }

    @Override
    public boolean requestDirectory(View anchor, Consumer<String> onSelect) {
        if (closed || anchor == null || onSelect == null) {
            return false;
        }
        closeTransient();
        FolderPickerDialog dialog = FolderPickerDialog.local(
                context,
                tr("geometry_node.settings.directory.title"),
                AssetBrowserPathPolicy.getLocalDraftsDir(),
                file -> onSelect.accept(file.getAbsolutePath())
        );
        services.layerManager().showModal(dialog, new ModalOptions(false, true, anchor));
        return true;
    }

    void closeTransient() {
        OverlayHandle handle = choiceHandle;
        if (handle != null && handle.isOpen()) {
            handle.requestClose(OverlayCloseReason.PROGRAMMATIC);
        } else {
            activeChoice = null;
            choiceHandle = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeTransient();
    }

    private void select(ChoiceOverlay source, String value) {
        if (source != activeChoice) {
            return;
        }
        Consumer<String> callback = source.onSelect;
        closeTransient();
        callback.accept(value);
    }

    private void onChoiceClosed(ChoiceOverlay source) {
        if (activeChoice == source) {
            activeChoice = null;
            choiceHandle = null;
        }
    }

    private final class ChoiceOverlay implements TransientOverlay {
        private final View anchor;
        private final List<SettingsChoice> values;
        private final String selected;
        private final Consumer<String> onSelect;

        private ChoiceOverlay(View anchor, List<SettingsChoice> values, String selected,
                              Consumer<String> onSelect) {
            this.anchor = anchor;
            this.values = values;
            this.selected = selected;
            this.onSelect = onSelect;
        }

        @Override
        public View createView(Context context) {
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.setBackground(background(COLOR_BACKGROUND, COLOR_BORDER));
            LinearLayout rows = new LinearLayout(context);
            rows.setOrientation(LinearLayout.VERTICAL);
            int padding = UIUtils.dp2pxInt(3.0f);
            rows.setPadding(padding, padding, padding, padding);
            for (SettingsChoice choice : values) {
                rows.addView(choiceRow(context, this, choice), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        UIUtils.dp2pxInt(ROW_HEIGHT_DP)
                ));
            }
            scroll.addView(rows, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return scroll;
        }

        @Override
        public FrameLayout.LayoutParams createLayoutParams(ViewGroup host) {
            int edge = UIUtils.dp2pxInt(EDGE_MARGIN_DP);
            int width = Math.min(
                    Math.max(anchor.getWidth(), UIUtils.dp2pxInt(120.0f)),
                    Math.max(1, host.getWidth() - edge * 2)
            );
            int expectedHeight = Math.min(
                    UIUtils.dp2pxInt(Math.min(values.size(), MAX_VISIBLE_ROWS) * ROW_HEIGHT_DP + 6.0f),
                    Math.max(1, host.getHeight() - edge * 2)
            );
            int[] anchorLocation = new int[2];
            int[] hostLocation = new int[2];
            anchor.getLocationOnScreen(anchorLocation);
            host.getLocationOnScreen(hostLocation);

            int maxLeft = Math.max(edge, host.getWidth() - width - edge);
            int left = clamp(anchorLocation[0] - hostLocation[0], edge, maxLeft);
            int below = anchorLocation[1] - hostLocation[1] + anchor.getHeight();
            int above = anchorLocation[1] - hostLocation[1] - expectedHeight;
            int top = below + expectedHeight <= host.getHeight() - edge ? below : Math.max(edge, above);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, expectedHeight);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.setMargins(left, top, 0, 0);
            return params;
        }

        @Override
        public void onClosed(OverlayCloseReason reason) {
            onChoiceClosed(this);
        }
    }

    private TextView choiceRow(Context context, ChoiceOverlay owner, SettingsChoice choice) {
        boolean selected = Objects.equals(owner.selected, choice.value());
        TextView row = UIUtils.createLockedTextView(
                context,
                (selected ? "\u2713  " : "    ") + choice.label(),
                10.5f,
                selected ? 0xFFFFFFFF : COLOR_TEXT
        );
        row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.setPadding(UIUtils.dp2pxInt(7.0f), 0, UIUtils.dp2pxInt(7.0f), 0);
        row.setClickable(true);
        row.setBackground(selected ? background(COLOR_SELECTED, 0) : null);
        row.setOnClickListener(view -> select(owner, choice.value()));
        row.setOnHoverListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                row.setBackground(background(COLOR_HOVER, 0));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                row.setBackground(selected ? background(COLOR_SELECTED, 0) : null);
            }
            return true;
        });
        return row;
    }

    private static ShapeDrawable background(int color, int strokeColor) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UIUtils.dp2px(2.0f));
        if (strokeColor != 0) {
            drawable.setStroke(Math.max(1, UIUtils.dp2pxInt(1.0f)), strokeColor);
        }
        return drawable;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }
}
