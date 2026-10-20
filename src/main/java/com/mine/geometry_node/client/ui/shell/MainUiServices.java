package com.mine.geometry_node.client.ui.shell;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.shell.layer.MainUiLayerManager;
import com.mine.geometry_node.client.ui.shell.menu.api.MainMenuRegistry;
import com.mine.geometry_node.client.ui.settings.page.api.SettingsPageRegistry;
import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns services whose lifetime is bound to one MainUI instance.
 */
public final class MainUiServices implements AutoCloseable {
    private final MainUiLayerManager layerManager;
    private final MainMenuRegistry menuRegistry;
    private final SettingsPageRegistry settingsPageRegistry;
    private final List<Runnable> closeActions = new ArrayList<>();
    private boolean closed;

    private Runnable openSettingsAction;

    MainUiServices(MainUiLayerManager layerManager, MainMenuRegistry menuRegistry,
                   SettingsPageRegistry settingsPageRegistry) {
        this.layerManager = Objects.requireNonNull(layerManager, "layerManager");
        this.menuRegistry = Objects.requireNonNull(menuRegistry, "menuRegistry");
        this.settingsPageRegistry = Objects.requireNonNull(settingsPageRegistry, "settingsPageRegistry");
        closeActions.add(layerManager::close);
    }

    public static MainUiServices require(View anchor) {
        Objects.requireNonNull(anchor, "anchor");
        MainUiServices services = find(anchor);
        if (services != null) return services;
        throw new IllegalStateException("View is not attached to a MainUiShell");
    }

    public static MainUiServices find(View anchor) {
        View current = anchor;
        while (current != null) {
            if (current instanceof MainUiShell shell) {
                return shell.services();
            }
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return null;
    }

    public MainUiLayerManager layerManager() {
        return layerManager;
    }

    public MainMenuRegistry menuRegistry() {
        return menuRegistry;
    }

    public SettingsPageRegistry settingsPageRegistry() {
        return settingsPageRegistry;
    }

    public Registration registerOpenSettingsAction(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed) throw new IllegalStateException("The MainUI services are closed");
        if (openSettingsAction != null) {
            throw new IllegalStateException("The MainUI settings action is already registered");
        }
        openSettingsAction = action;
        return () -> {
            if (openSettingsAction == action) {
                openSettingsAction = null;
            }
        };
    }

    public boolean canOpenSettings() {
        return !closed && openSettingsAction != null;
    }

    public void openSettings() {
        if (canOpenSettings()) {
            openSettingsAction.run();
        }
    }

    public Registration addCloseAction(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed) {
            runCloseAction(action);
            return Registration.NO_OP;
        }

        closeActions.add(action);
        return () -> closeActions.remove(action);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        openSettingsAction = null;
        // Snapshot the iteration order; registrations may still withdraw a pending cleanup.
        // Remove each action before invoking it so reentry cannot run the same registration twice.
        List<Runnable> pending = List.copyOf(closeActions);
        try {
            for (int index = pending.size() - 1; index >= 0; index--) {
                Runnable action = pending.get(index);
                if (closeActions.remove(action)) runCloseAction(action);
            }
        } finally {
            closeActions.clear();
        }
    }

    private static void runCloseAction(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            GeometryNode.LOGGER.error("Failed to close a MainUI service", exception);
        }
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        Registration NO_OP = () -> {
        };

        @Override
        void close();
    }
}
