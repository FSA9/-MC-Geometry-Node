package com.mine.geometry_node.core.engine.system.dialogue;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Common registry describing how each dialogue style is presented by the server.
 * Client-side renderer factories are registered separately to keep client classes off dedicated servers.
 */
public final class DialogueStyleRegistry {
    public static final String DEFAULT = "default";
    public static final String RPG = "rpg";
    public static final String SHOP = "shop";
    public static final String MENU = "menu";

    private static final ConcurrentMap<String, Definition> DEFINITIONS = new ConcurrentHashMap<>();

    static {
        register(DEFAULT, Presentation.CHAT);
        register(RPG, Presentation.PACKET);
        register(SHOP, Presentation.PACKET);
        register(MENU, Presentation.PACKET);
    }

    private DialogueStyleRegistry() {
    }

    public static void register(String styleId, Presentation presentation) {
        String normalizedId = requireStyleId(styleId);
        Definition definition = new Definition(normalizedId, Objects.requireNonNull(presentation, "presentation"));
        Definition previous = DEFINITIONS.putIfAbsent(normalizedId, definition);
        if (previous != null) {
            throw new IllegalStateException("Dialogue style already registered: " + normalizedId);
        }
    }

    @Nullable
    public static Definition find(@Nullable String styleId) {
        if (styleId == null || styleId.isBlank()) {
            return DEFINITIONS.get(DEFAULT);
        }
        return DEFINITIONS.get(styleId);
    }

    public static Definition defaultDefinition() {
        return Objects.requireNonNull(DEFINITIONS.get(DEFAULT), "default dialogue style");
    }

    private static String requireStyleId(String styleId) {
        if (styleId == null || styleId.isBlank()) {
            throw new IllegalArgumentException("styleId must not be blank");
        }
        return styleId;
    }

    public enum Presentation {
        CHAT,
        PACKET
    }

    public record Definition(String id, Presentation presentation) {
    }
}
