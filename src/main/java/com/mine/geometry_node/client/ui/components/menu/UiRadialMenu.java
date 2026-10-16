package com.mine.geometry_node.client.ui.components.menu;

import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaLifecycle;
import com.mine.geometry_node.client.ui.workspace.area.AreaTransientUi;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Matrix;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Reusable Blender-style pie menu. Mount as the last MATCH_PARENT child of a local overlay host.
 * Callers choose any show/dismiss trigger. Selection invokes the action without implicit dismissal.
 */
public final class UiRadialMenu extends FrameLayout {
    public record Item(RadialMenuLayout.Slot slot, String text, Function<Context, View> icon,
                       Runnable action) {
        public Item {
            Objects.requireNonNull(slot);
            Objects.requireNonNull(action);
            if ((text == null || text.isBlank()) && icon == null)
                throw new IllegalArgumentException("A radial item needs text or an icon");
        }
    }

    private final FrameLayout panel;
    private final float layoutDensity;
    private final java.util.ArrayList<View> buttons = new java.util.ArrayList<>();
    private View pressed, hovered, lastActivated;
    private final java.util.Map<View, Runnable> actions = new java.util.IdentityHashMap<>();
    private final Matrix rootToLocal = new Matrix();
    private final float[] pointer = new float[2];
    private AreaLifecycle.Subscription areaBinding = AreaLifecycle.Subscription.NONE;

    public UiRadialMenu(Context context, String title, List<Item> items) {
        super(context);
        layoutDensity = UIUtils.dp2px(1);
        var slots = EnumSet.noneOf(RadialMenuLayout.Slot.class);
        for (Item item : items) if (!slots.add(item.slot()))
            throw new IllegalArgumentException("Duplicate radial slot: " + item.slot());
        if (items.isEmpty()) throw new IllegalArgumentException("Empty radial menu");
        setVisibility(INVISIBLE);
        setClickable(true);
        setFocusable(false);
        panel = new FrameLayout(context) {
            private final Paint ring = new Paint();
            { setWillNotDraw(false); ring.setAntiAlias(true); }
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                ring.setColor(0xDD171717);
                ring.setStyle(Paint.STROKE);
                ring.setStrokeWidth(5 * layoutDensity);
                canvas.drawCircle(208 * layoutDensity, 128 * layoutDensity, 13 * layoutDensity, ring);
            }
        };
        panel.setPivotX(0);
        panel.setPivotY(0);
        addView(panel, new LayoutParams(UIUtils.dp2pxInt(RadialMenuLayout.WIDTH), UIUtils.dp2pxInt(RadialMenuLayout.HEIGHT)));
        if (title != null && !title.isBlank()) {
            TextView label = UIUtils.createLockedTextView(context, title, 11, UiTheme.Text.MUTED);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            panel.addView(label, position(108, 88, 200, 22));
        }
        for (Item item : items) {
            LinearLayout button = new LinearLayout(context) {
                @Override protected void drawableStateChanged() {
                    super.drawableStateChanged();
                    ShapeDrawable background = new ShapeDrawable();
                    background.setCornerRadius(3 * layoutDensity);
                    background.setColor(isPressed() ? 0xFF35658B : isHovered() ? 0xFF477EA7 : 0xEE191919);
                    setBackground(background);
                }
            };
            button.setOrientation(LinearLayout.HORIZONTAL);
            button.setGravity(Gravity.CENTER);
            button.setPadding(UIUtils.dp2pxInt(9), 0, UIUtils.dp2pxInt(9), 0);
            button.setClickable(true);
            // Keep keyboard focus in the owning viewport for the trigger's key-up.
            button.setFocusable(false);
            ShapeDrawable background = new ShapeDrawable();
            background.setColor(0xEE191919);
            background.setCornerRadius(3 * layoutDensity);
            button.setBackground(background);
            if (item.icon() != null) {
                View icon = Objects.requireNonNull(item.icon().apply(context));
                icon.setClickable(false);
                icon.setFocusable(false);
                var params = new LinearLayout.LayoutParams(UIUtils.dp2pxInt(15), UIUtils.dp2pxInt(15));
                if (item.text() != null && !item.text().isBlank()) params.rightMargin = UIUtils.dp2pxInt(6);
                button.addView(icon, params);
            }
            if (item.text() != null && !item.text().isBlank()) {
                TextView label = UIUtils.createLockedTextView(context, item.text(), 12, 0xFFE0E0E0);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                button.addView(label, new LinearLayout.LayoutParams(-2, -2));
                button.setContentDescription(item.text());
            }
            actions.put(button, item.action());
            button.setOnClickListener(ignored -> {
                if (isOpen() && button.isEnabled()) {
                    lastActivated = button;
                    item.action().run();
                }
            });
            buttons.add(button);
            panel.addView(button, position(item.slot().x - 60, item.slot().y - 15, 120, 30));
        }
    }

    private static LayoutParams position(float x, float y, float width, float height) {
        var params = new LayoutParams(UIUtils.dp2pxInt(width), UIUtils.dp2pxInt(height));
        params.leftMargin = UIUtils.dp2pxInt(x);
        params.topMargin = UIUtils.dp2pxInt(y);
        return params;
    }

    /** x/y are in this overlay's local coordinates, never screen pixels. */
    public boolean showAt(View anchor, float x, float y) {
        dismiss();
        lastActivated = null;
        var placement = RadialMenuLayout.fit(getWidth(), getHeight(), x, y, UIUtils.dp2px(1));
        if (placement == null) return false;
        // Layout dimensions were converted when this reusable component was created.
        // Shared UI density may have changed since then; scale the whole layout together.
        float scale = placement.scale() * UIUtils.dp2px(1) / layoutDensity;
        panel.setScaleX(scale);
        panel.setScaleY(scale);
        // ModernUI 3.13 RenderProperties includes translation twice in its matrix.
        // Place via layout offsets; reserve the view matrix for scaling only.
        var params = (LayoutParams) panel.getLayoutParams();
        params.leftMargin = Math.round(placement.left());
        params.topMargin = Math.round(placement.top());
        panel.setLayoutParams(params);
        // Be ready for paint/input immediately, before the requested layout traversal.
        panel.offsetLeftAndRight(params.leftMargin - panel.getLeft());
        panel.offsetTopAndBottom(params.topMargin - panel.getTop());
        setVisibility(VISIBLE);
        areaBinding = AreaTransientUi.bind(anchor, this, this::dismiss);
        return true;
    }

    /** Root pixels from MotionEvent.getRawX/Y, not GLFW coordinates or dp. */
    public boolean showAtRoot(View anchor, float rootX, float rootY) {
        if (!UIUtils.rootToLocal(this, rootX, rootY, rootToLocal, pointer)) {
            dismiss();
            return false;
        }
        return showAt(anchor, pointer[0], pointer[1]);
    }

    /** Centers on an anchor with its own offset/scale, rather than assuming shared origins. */
    public boolean showAtCenter(View anchor) {
        rootToLocal.setIdentity();
        anchor.transformMatrixToGlobal(rootToLocal);
        pointer[0] = anchor.getWidth() / 2f;
        pointer[1] = anchor.getHeight() / 2f;
        rootToLocal.mapPoint(pointer);
        return showAtRoot(anchor, pointer[0], pointer[1]);
    }

    public boolean isOpen() { return getVisibility() == VISIBLE; }

    /** Apply the hovered item once and close. Cancellation must call dismiss instead. */
    public boolean confirmHoveredAndDismiss() {
        View selected = isOpen() && hovered != null && hovered.isEnabled()
                && hovered.getVisibility() == VISIBLE && hovered != lastActivated ? hovered : null;
        Runnable action = actions.get(selected);
        // Clear hover, tooltip and lifecycle binding before invoking arbitrary caller code.
        dismiss();
        if (action == null) return false;
        action.run();
        return true;
    }

    public void dismiss() {
        clearPress();
        setHoveredButton(null);
        // These labels are already visible. Do not leave a detached hover popup behind.
        for (View button : buttons) button.setTooltipText(null);
        areaBinding.close();
        areaBinding = AreaLifecycle.Subscription.NONE;
        setVisibility(INVISIBLE);
    }

    private View hit(MotionEvent event) {
        if (!isOpen()) return null;
        // Use the same conversion as placement. Raw coordinates already include DPI.
        if (!UIUtils.rootToLocal(panel, event.getRawX(), event.getRawY(), rootToLocal, pointer)) return null;
        float localX = pointer[0], localY = pointer[1];
        for (View button : buttons) if (button.isEnabled() && localX >= button.getLeft()
                && localX < button.getRight() && localY >= button.getTop() && localY < button.getBottom()) return button;
        return null;
    }

    private void clearPress() {
        if (pressed != null) pressed.setPressed(false);
        pressed = null;
    }

    private void setHoveredButton(View button) {
        if (hovered == button) return;
        if (hovered != null) hovered.setHovered(false);
        hovered = button;
        if (hovered != null) hovered.setHovered(true);
    }

    // Dispatch at the overlay boundary: ModernUI's nested MotionEvent offsets after a
    // scaled child can scale the offset twice. Paint and hit use the same panel placement,
    // without dispatching transformed events down through the scaled button hierarchy.
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isOpen()) return false;
        View hit = hit(event);
        setHoveredButton(event.getActionMasked() == MotionEvent.ACTION_CANCEL ? null : hit);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                clearPress();
                if (event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) {
                    pressed = hit;
                    if (pressed != null) pressed.setPressed(true);
                }
            }
            case MotionEvent.ACTION_MOVE -> { if (pressed != hit) clearPress(); }
            case MotionEvent.ACTION_UP -> {
                View selected = pressed == hit ? pressed : null;
                clearPress();
                // Commit before the trigger's subsequent key-up dismisses the menu.
                if (selected != null) selected.performClick();
            }
            case MotionEvent.ACTION_CANCEL -> clearPress();
        }
        return true;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!isOpen()) return false;
        setHoveredButton(event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT
                ? null : hit(event));
        return true;
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) dismiss();
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w != oldW || h != oldH) dismiss();
    }
    @Override protected void onDetachedFromWindow() {
        dismiss();
        super.onDetachedFromWindow();
    }
}
