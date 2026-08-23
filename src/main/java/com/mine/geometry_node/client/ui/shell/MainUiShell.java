package com.mine.geometry_node.client.ui.shell;

import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.area.AreaLayoutRoot;
import com.mine.geometry_node.client.ui.shell.layer.MainUiLayerHost;
import com.mine.geometry_node.client.ui.shell.layer.MainUiLayerManager;
import com.mine.geometry_node.client.ui.shell.menu.MainMenuController;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuRegistry;
import com.mine.geometry_node.client.ui.shell.menu.builtin.BuiltinMainMenus;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageRegistry;
import com.mine.geometry_node.client.ui.settings.page.builtin.BuiltinSettingsPages;
import com.mine.geometry_node.client.ui.settings.window.SettingsWindowController;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

/**
 * Composition root for one MainUI instance.
 */
public final class MainUiShell extends FrameLayout {
    private final MainUiServices services;
    private final MainUiLayerHost transientLayer;
    private final MainUiLayerHost modalLayer;
    private final AreaLayoutRoot areaRoot;
    private boolean destroyed;

    public MainUiShell(Context context) {
        super(context);
        setBackground(createColorDrawable(UIConstants.MainUI.BG_ROOT));

        LinearLayout contentLayer = new LinearLayout(context);
        contentLayer.setOrientation(LinearLayout.VERTICAL);
        addView(contentLayer, matchParentLayoutParams());

        transientLayer = new MainUiLayerHost(context);
        modalLayer = new MainUiLayerHost(context);
        MainUiLayerManager layerManager = new MainUiLayerManager(context, transientLayer, modalLayer);
        MainMenuRegistry menuRegistry = new MainMenuRegistry();
        SettingsPageRegistry settingsPageRegistry = new SettingsPageRegistry();
        services = new MainUiServices(layerManager, menuRegistry, settingsPageRegistry);
        BuiltinMainMenus.register(menuRegistry, services);
        BuiltinSettingsPages.register(settingsPageRegistry);

        MainMenuController menuController = new MainMenuController(context, menuRegistry, layerManager);
        services.addCloseAction(menuController::close);
        SettingsWindowController settingsWindowController = new SettingsWindowController(context, services);
        services.addCloseAction(settingsWindowController::close);
        contentLayer.addView(menuController.menuBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UIUtils.dp2pxInt(UIConstants.MainUI.HEIGHT_HEADER)
        ));

        areaRoot = new AreaLayoutRoot(context);
        LinearLayout.LayoutParams areaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
        );
        areaParams.weight = 1.0f;
        contentLayer.addView(areaRoot, areaParams);

        addView(transientLayer, matchParentLayoutParams());
        addView(modalLayer, matchParentLayoutParams());
    }

    public MainUiServices services() {
        return services;
    }

    public void persistNow() {
        areaRoot.persistNow();
    }

    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;

        try {
            services.close();
            clearLayer(transientLayer);
            clearLayer(modalLayer);
        } finally {
            areaRoot.close();
        }
    }

    private static FrameLayout.LayoutParams matchParentLayoutParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private static void clearLayer(FrameLayout layer) {
        layer.removeAllViews();
        layer.setVisibility(View.GONE);
    }

    private static ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(ShapeDrawable.RECTANGLE);
        drawable.setColor(color);
        return drawable;
    }
}
