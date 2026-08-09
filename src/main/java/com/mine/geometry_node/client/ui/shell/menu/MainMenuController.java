package com.mine.geometry_node.client.ui.shell.menu;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.shell.layer.MainUiLayerManager;
import com.mine.geometry_node.client.ui.shell.layer.OverlayCloseReason;
import com.mine.geometry_node.client.ui.shell.layer.OverlayHandle;
import com.mine.geometry_node.client.ui.shell.layer.ephemeral.TransientOptions;
import com.mine.geometry_node.client.ui.shell.layer.ephemeral.TransientOverlay;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuDefinition;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuItemDefinition;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuRegistry;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.FrameLayout;

import java.util.List;
import java.util.Objects;

public final class MainMenuController implements AutoCloseable {
    private static final float EDGE_MARGIN_DP = 4.0f;

    private final MainMenuRegistry registry;
    private final MainUiLayerManager layerManager;
    private final MainMenuBar menuBar;
    private final MainMenuRegistry.Registration registryRegistration;

    private MenuOverlay activeOverlay;
    private OverlayHandle activeHandle;
    private String activeMenuId;
    private boolean closed;

    public MainMenuController(Context context, MainMenuRegistry registry, MainUiLayerManager layerManager) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.layerManager = Objects.requireNonNull(layerManager, "layerManager");
        menuBar = new MainMenuBar(Objects.requireNonNull(context, "context"), this);
        registryRegistration = registry.addChangeListener(this::onRegistryChanged);
        menuBar.rebuild(registry.menus());
    }

    public MainMenuBar menuBar() {
        return menuBar;
    }

    void toggleMenu(String menuId, View anchor) {
        if (menuId != null && menuId.equals(activeMenuId)) {
            closeMenu();
        } else {
            openMenu(menuId, anchor);
        }
    }

    void openMenu(String menuId, View anchor) {
        if (closed || menuId == null) {
            return;
        }
        MainMenuDefinition definition = registry.menu(menuId);
        List<MainMenuItemDefinition> items = registry.items(menuId);
        if (definition == null || items.isEmpty()) {
            closeMenu();
            return;
        }
        View resolvedAnchor = anchor != null ? anchor : menuBar.button(menuId);
        if (resolvedAnchor == null || resolvedAnchor.getWidth() <= 0) {
            return;
        }

        MenuOverlay overlay = new MenuOverlay(resolvedAnchor, items);
        activeOverlay = overlay;
        activeMenuId = menuId;
        menuBar.setActiveMenuId(menuId);
        try {
            activeHandle = layerManager.showTransient(
                    overlay,
                    new TransientOptions(true, true, resolvedAnchor)
            );
        } catch (RuntimeException exception) {
            if (activeOverlay == overlay) {
                clearActiveMenu();
            }
            throw exception;
        }
    }

    public void closeMenu() {
        OverlayHandle handle = activeHandle;
        if (handle != null && handle.isOpen()) {
            handle.requestClose(OverlayCloseReason.PROGRAMMATIC);
        } else {
            clearActiveMenu();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeMenu();
        registryRegistration.close();
    }

    private void execute(MainMenuItemDefinition item) {
        if (!safeEnabled(item)) {
            return;
        }
        closeMenu();
        try {
            item.action().run();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Main menu action failed: {}", item.id(), exception);
        }
    }

    private boolean onOverlayHover(MenuOverlay source, float screenX, float screenY) {
        if (source != activeOverlay) {
            return false;
        }
        String hoveredMenuId = menuBar.menuAtScreen(screenX, screenY);
        menuBar.setHoveredMenuId(hoveredMenuId);
        if (hoveredMenuId == null) {
            return false;
        }
        if (!hoveredMenuId.equals(activeMenuId)) {
            openMenu(hoveredMenuId, menuBar.button(hoveredMenuId));
        }
        return true;
    }

    private void onOverlayClosed(MenuOverlay overlay) {
        if (activeOverlay == overlay) {
            clearActiveMenu();
        }
    }

    private void onRegistryChanged() {
        closeMenu();
        menuBar.rebuild(registry.menus());
    }

    private void clearActiveMenu() {
        activeOverlay = null;
        activeHandle = null;
        activeMenuId = null;
        menuBar.setActiveMenuId(null);
    }

    private static boolean safeEnabled(MainMenuItemDefinition item) {
        try {
            return item.isEnabled();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Main menu enabled-state evaluation failed: {}", item.id(), exception);
            return false;
        }
    }

    private final class MenuOverlay implements TransientOverlay {
        private final View anchor;
        private final List<MainMenuItemDefinition> items;

        private MenuOverlay(View anchor, List<MainMenuItemDefinition> items) {
            this.anchor = anchor;
            this.items = List.copyOf(items);
        }

        @Override
        public View createView(Context context) {
            return new MainMenuPopup(context, items, MainMenuController.this::execute);
        }

        @Override
        public FrameLayout.LayoutParams createLayoutParams(ViewGroup host) {
            int width = UIUtils.dp2pxInt(MainMenuPopup.WIDTH_DP);
            int edge = UIUtils.dp2pxInt(EDGE_MARGIN_DP);
            int[] anchorLocation = new int[2];
            int[] hostLocation = new int[2];
            anchor.getLocationOnScreen(anchorLocation);
            host.getLocationOnScreen(hostLocation);

            int maxLeft = Math.max(edge, host.getWidth() - width - edge);
            int left = clamp(anchorLocation[0] - hostLocation[0], edge, maxLeft);
            int top = Math.max(edge, anchorLocation[1] - hostLocation[1] + anchor.getHeight());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.setMargins(left, top, 0, 0);
            return params;
        }

        @Override
        public boolean onPointerHover(float screenX, float screenY) {
            return onOverlayHover(this, screenX, screenY);
        }

        @Override
        public void onClosed(OverlayCloseReason reason) {
            onOverlayClosed(this);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
