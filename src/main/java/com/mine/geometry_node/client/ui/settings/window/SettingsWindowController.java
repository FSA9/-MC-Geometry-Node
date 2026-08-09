package com.mine.geometry_node.client.ui.settings.window;

import com.mine.geometry_node.client.ui.shell.MainUiServices;
import com.mine.geometry_node.client.ui.shell.layer.modal.ModalOptions;
import icyllis.modernui.core.Context;

import java.util.Objects;

/** Owns the single settings-window session for one MainUI instance. */
public final class SettingsWindowController implements AutoCloseable {
    private final Context context;
    private final MainUiServices services;
    private final MainUiServices.Registration actionRegistration;
    private SettingsWindow currentWindow;
    private boolean closed;

    public SettingsWindowController(Context context, MainUiServices services) {
        this.context = Objects.requireNonNull(context, "context");
        this.services = Objects.requireNonNull(services, "services");
        actionRegistration = services.registerOpenSettingsAction(this::open);
    }

    public void open() {
        if (closed) {
            return;
        }
        if (currentWindow != null) {
            currentWindow.requestFocus();
            return;
        }
        SettingsWindow window = new SettingsWindow(context, services, this::onDestroyed);
        currentWindow = window;
        try {
            services.layerManager().showModal(window, ModalOptions.defaults());
        } catch (RuntimeException exception) {
            currentWindow = null;
            window.disposeUnshown();
            throw exception;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        actionRegistration.close();
    }

    private void onDestroyed(SettingsWindow window) {
        if (currentWindow == window) {
            currentWindow = null;
        }
    }
}
