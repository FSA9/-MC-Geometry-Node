package com.mine.geometry_node.client.ui.workspace.state;

import com.mine.geometry_node.client.platform.ClientServerIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Publishes connection identity from the Minecraft thread for UI-thread state bindings. */
public final class UiStateScopes {
    public static final String GLOBAL = "global";
    private static volatile String current = "disconnected";
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private UiStateScopes() {}

    public static String current() { return current; }

    /** Call on the Minecraft client thread after login; no world state is accessed by readers. */
    public static void connected(UUID player) {
        String server;
        try {
            server = ClientServerIdentity.current();
        } catch (IllegalStateException unavailable) {
            // An unidentified connection must not reuse another server's filter state.
            server = "connection:" + UUID.randomUUID();
        }
        publish(worldScope(server, player));
    }

    public static void disconnected() { publish("disconnected"); }

    public static void addChangeListener(Runnable listener) {
        listeners.addIfAbsent(Objects.requireNonNull(listener));
    }

    public static void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    static String worldScope(String server, UUID player) {
        Objects.requireNonNull(server);
        Objects.requireNonNull(player);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((server + "\n" + player).getBytes(StandardCharsets.UTF_8));
            return "world:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static void publish(String scope) {
        if (current.equals(scope)) return;
        current = scope;
        for (Runnable listener : listeners) {
            try { listener.run(); }
            catch (RuntimeException failure) {
                System.getLogger(UiStateScopes.class.getName()).log(System.Logger.Level.WARNING,
                        "UI state scope listener failed", failure);
            }
        }
    }
}
