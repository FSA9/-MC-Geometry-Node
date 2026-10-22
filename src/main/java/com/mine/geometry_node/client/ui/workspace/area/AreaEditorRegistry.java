package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.workspace.persistence.EditorSessionState;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceId;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.client.ui.overlay.UiWindowLifetime;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Editor factories supplied by the host, with workspace-owned surface lifetimes. */
public final class AreaEditorRegistry {
    private final Map<AreaEditorType, Entry> entries = new EnumMap<>(AreaEditorType.class);

    public void register(AreaEditorType type, UiSurfaceType surfaceType, AreaEditorFactory factory) {
        Objects.requireNonNull(type, "type");
        Entry entry = new Entry(Objects.requireNonNull(surfaceType, "surfaceType"),
                Objects.requireNonNull(factory, "factory"));
        if (entries.putIfAbsent(type, entry) != null) {
            throw new IllegalArgumentException("Area editor already registered: " + type);
        }
    }

    AreaEditorWindow create(
            Context context,
            AreaEditorType type,
            EditorSessionState.AreaState sessionState,
            Runnable sessionChanged) {
        Entry entry = entries.get(type);
        if (entry == null) {
            throw new IllegalStateException("Area editor is not registered: " + type);
        }
        AreaEditorWindow window = Objects.requireNonNull(
                entry.factory().create(context, sessionState, sessionChanged),
                "Area editor factory returned no window: " + type);
        return new RegisteredWindow(window, entry.surfaceType());
    }

    private record Entry(UiSurfaceType surfaceType, AreaEditorFactory factory) {}

    /** Retained with the logical editor, even when area split views are reconstructed. */
    private static final class WindowHost extends FrameLayout implements UiWindowLifetime.Owner {
        private final UiWindowLifetime lifetime;
        private WindowHost(Context context, UiWindowLifetime lifetime) {
            super(context);
            this.lifetime = lifetime;
        }
        @Override public UiWindowLifetime windowLifetime() { return lifetime; }
    }

    private static final class RegisteredWindow implements AreaEditorWindow {
        private final AreaEditorWindow delegate;
        private final UiSurfaceRegistry.Registration registration;
        private final UiWindowLifetime lifetime = new UiWindowLifetime();
        private View host;

        private RegisteredWindow(AreaEditorWindow delegate, UiSurfaceType type) {
            this.delegate = delegate;
            this.registration = UiSurfaceRegistry.INSTANCE.register(type, delegate);
            if (delegate instanceof SurfaceRegistrationAware aware) aware.bindSurfaceRegistration(registration);
        }

        @Override public View getView() {
            if (host == null) {
                View content = delegate.getView();
                if (content == null) return null;
                WindowHost root = new WindowHost(content.getContext(), lifetime);
                root.addView(content, new FrameLayout.LayoutParams(-1, -1));
                host = root;
            }
            return host;
        }
        @Override public UiSurfaceId surfaceId() { return registration.id(); }
        @Override public void onShow() {
            if (lifetime.state() == UiWindowLifetime.State.CLOSED) return;
            registration.setVisible(true);
            delegate.onShow();
            lifetime.show();
        }
        @Override public void onHide() {
            if (lifetime.state() == UiWindowLifetime.State.CLOSED) return;
            lifetime.hide();
            registration.setVisible(false);
            delegate.onHide();
        }

        @Override
        public void onDispose() {
            if (lifetime.state() == UiWindowLifetime.State.CLOSED) return;
            lifetime.close();
            try {
                delegate.onDispose();
            } finally {
                registration.close();
            }
        }
    }
}
