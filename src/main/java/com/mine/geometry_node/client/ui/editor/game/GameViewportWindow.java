package com.mine.geometry_node.client.ui.editor.game;

import com.mine.geometry_node.client.runtime.game.GameFrameImage;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.GameViewTiming;
import com.mine.geometry_node.client.runtime.game.SharedGameFrame;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.client.ui.workspace.area.AreaEditorWindow;
import com.mine.geometry_node.client.ui.workspace.area.SurfaceRegistrationAware;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

/** Workspace chrome owns visibility, while game state and the captured frame are shared. */
public final class GameViewportWindow extends LinearLayout implements AreaEditorWindow, SurfaceRegistrationAware {
    private final GameViewportView viewport;
    private final TextView status;
    private final TextView waiting;
    private UiSurfaceRegistry.Registration registration;
    private boolean shown;
    private boolean registered;
    private boolean disposed;
    private String statusKey;
    private final Runnable refresh = this::refresh;

    public GameViewportWindow(Context context) {
        super(context);
        long constructionStart = GameViewTiming.start();
        setOrientation(VERTICAL);
        status = UIUtils.createLockedTextView(context, "", 10.0f, UiTheme.Text.MUTED);
        status.setSingleLine(true);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);
        ShapeDrawable bar = new ShapeDrawable();
        bar.setColor(UiTheme.Surface.HEADER);
        status.setBackground(bar);
        addView(status, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(26)));

        FrameLayout content = new FrameLayout(context);
        viewport = new GameViewportView(context, this, () -> {
            if (registration != null) registration.markInteracted();
        });
        content.addView(viewport, new FrameLayout.LayoutParams(-1, -1));
        waiting = UIUtils.createLockedTextView(context, tr("waiting"), 12.0f, UiTheme.Text.MUTED);
        waiting.setGravity(Gravity.CENTER);
        content.addView(waiting, new FrameLayout.LayoutParams(-1, -1));
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        updateStatus(false);
        GameViewTiming.finish("window construction", constructionStart, true);
    }

    public ViewportMode mode() { return ViewportMode.GAME; }
    @Override public View getView() { return this; }
    @Override public void bindSurfaceRegistration(UiSurfaceRegistry.Registration registration) { this.registration = registration; }
    @Override public void onShow() { shown = true; updateVisibility(); }
    @Override public void onHide() { shown = false; updateVisibility(); }
    @Override public void onDispose() { disposed = true; onHide(); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateVisibility(); }
    @Override protected void onDetachedFromWindow() { deactivate(); super.onDetachedFromWindow(); }
    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (viewport != null) updateVisibility();
    }

    private void updateVisibility() {
        boolean visible = shown && !disposed && isAttachedToWindow() && isShown();
        if (!visible) {
            deactivate();
            return;
        }
        if (registered) return;
        registered = true;
        if (!Boolean.getBoolean("gn.standalone")) {
            SharedGameFrame.setVisible(this, true);
            GameViewportInput.setVisible(this, true);
        }
        viewport.setActive(true);
        refresh();
    }

    private void deactivate() {
        removeCallbacks(refresh);
        if (viewport != null) viewport.setActive(false);
        if (!registered) return;
        registered = false;
        if (!Boolean.getBoolean("gn.standalone")) {
            GameViewportInput.setVisible(this, false);
            SharedGameFrame.setVisible(this, false);
        }
    }

    private void refresh() {
        if (!registered) return;
        boolean available = !Boolean.getBoolean("gn.standalone") && GameFrameImage.isAvailable();
        waiting.setVisibility(available ? View.GONE : View.VISIBLE);
        updateStatus(available);
        viewport.invalidate();
        postDelayed(refresh, 16);
    }

    private void updateStatus(boolean available) {
        String key = !available ? "waiting" : GameViewportInput.isCaptured(this) ? "controlling" : "viewing";
        if (key.equals(statusKey)) return;
        statusKey = key;
        status.setText(tr(key));
        status.setTextColor("controlling".equals(key) ? UiTheme.Control.FOCUS : UiTheme.Text.MUTED);
    }

    private static String tr(String key) {
        return Component.translatable("geometry_node.game_view." + key).getString();
    }
}
