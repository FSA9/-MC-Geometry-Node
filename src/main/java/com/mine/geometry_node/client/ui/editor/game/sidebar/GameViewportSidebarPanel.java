package com.mine.geometry_node.client.ui.editor.game.sidebar;

import com.mine.geometry_node.client.ui.components.scroll.UiScrollView;

import com.mine.geometry_node.client.ui.editor.game.GameViewportWindow;
import com.mine.geometry_node.client.ui.editor.game.ViewportMode;
import com.mine.geometry_node.client.ui.editor.game.input.GameViewportShortcuts;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanel;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelDefinition;
import com.mine.geometry_node.client.ui.components.sidebar.api.SidebarPanelScope;
import com.mine.geometry_node.client.config.BuiltinConfigEntries;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.network.chat.Component;

import java.util.Set;

/** Settings for the owning viewport; it never owns another frame or camera. */
public final class GameViewportSidebarPanel extends UiScrollView implements SidebarPanel {
    public static final String PANEL_ID = "game_view";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(
            PANEL_ID, "geometry_node.game_view.sidebar.title", 100,
            Set.of(SidebarPanelScope.GAME_VIEWPORT),
            context -> new GameViewportSidebarPanel(context.uiContext(), context.requireOwner(GameViewportWindow.class)),
            SvgIconView.Icon.GAME);

    private final GameViewportWindow owner;
    private final TextView projection;
    private final TextView reset;
    private final TextView help;
    private String lastHelp;
    private String lastProjection;
    private String lastProjectionShortcut;

    private GameViewportSidebarPanel(Context context, GameViewportWindow owner) {
        super(context);
        this.owner = owner;
        setHorizontalScrollBarEnabled(false);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int padding = UIUtils.dp2pxInt(12);
        column.setPadding(padding, padding, padding, padding);
        addView(column, new ViewGroup.LayoutParams(-1, -2));
        projection = button(context, "toggle_projection", owner::toggleProjection);
        reset = button(context, "reset_camera", owner::resetCamera);
        for (TextView button : new TextView[]{projection, reset}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(30));
            params.bottomMargin = UIUtils.dp2pxInt(8);
            column.addView(button, params);
        }
        help = UIUtils.createLockedTextView(context, "", 11, UiTheme.Text.SECONDARY);
        column.addView(help, new LinearLayout.LayoutParams(-1, -2));
        refresh();
    }

    @Override public View getView() { return this; }
    @Override public void onSelected() { refresh(); }

    public void refresh() {
        boolean editing = owner.mode() == ViewportMode.EDIT;
        projection.setVisibility(editing ? View.VISIBLE : View.GONE);
        reset.setVisibility(owner.mode().usesEditorCamera() ? View.VISIBLE : View.GONE);
        String projectionName = tr(owner.isOrthographic() ? "projection.orthographic" : "projection.perspective");
        if (!projectionName.equals(lastProjection)) {
            lastProjection = projectionName;
            projection.setText(projectionName);
        }
        String shortcut = GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_PROJECTION);
        if (!shortcut.equals(lastProjectionShortcut)) {
            lastProjectionShortcut = shortcut;
            projection.setTooltipText(shortcut);
        }
        String text = owner.mode().usesEditorCamera()
                ? Component.translatable(owner.mode() == ViewportMode.FLY
                    ? "geometry_node.game_view.sidebar.fly_help" : "geometry_node.game_view.sidebar.edit_help",
                    tr("mode.fly"),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_PROJECTION),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_FORWARD),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_BACKWARD),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_LEFT),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_RIGHT),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_UP),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_DOWN),
                    GameViewportShortcuts.display(BuiltinConfigEntries.GAME_FLIGHT_EXIT)).getString()
                : tr("sidebar.game_help");
        if (owner.mode().usesEditorCamera()) text += "\n\n" + Component.translatable(
                "geometry_node.game_view.view_menu.help", GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_MENU)).getString()
                + "\n\n" + tr("sidebar.multi_view_help");
        else if (!Boolean.getBoolean("gn.standalone") && EditViewportRuntime.hasVisibleViews()) {
            text += "\n\n" + tr("sidebar.shared_basic_help");
        }
        if (editing) text += "\n\n" + Component.translatable("geometry_node.game_view.scene.help",
                GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_ADD),
                GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_FOCUS),
                GameViewportShortcuts.display(BuiltinConfigEntries.GAME_VIEW_CANCEL)).getString();
        if (!text.equals(lastHelp)) { lastHelp = text; help.setText(text); }
    }

    private TextView button(Context context, String key, Runnable action) {
        TextView view = new TextView(context) {
            @Override protected void drawableStateChanged() {
                super.drawableStateChanged();
                ShapeDrawable background = new ShapeDrawable();
                background.setColor(isPressed() ? UiTheme.Control.ICON_PRESSED
                        : isHovered() ? UiTheme.Control.ICON_HOVER : UiTheme.Control.ICON);
                background.setCornerRadius(UIUtils.dp2px(3));
                if (isFocused()) background.setStroke(UIUtils.dp2pxInt(1), UiTheme.Control.FOCUS);
                setBackground(background);
            }
        };
        view.setText(tr(key));
        UIUtils.setLockedTextSize(view, 11);
        view.setTextColor(UiTheme.Text.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static String tr(String key) {
        return Component.translatable("geometry_node.game_view." + key).getString();
    }
}
