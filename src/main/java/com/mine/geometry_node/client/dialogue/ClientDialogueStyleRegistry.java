package com.mine.geometry_node.client.dialogue;

import com.mine.geometry_node.core.engine.system.dialogue.DialogueStyleRegistry;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.client.dialogue.ui.RpgDialogueFragment;
import com.mine.geometry_node.client.dialogue.ui.ShopMenuFragment;
import icyllis.modernui.fragment.Fragment;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-only registry for packet dialogue renderers.
 */
public final class ClientDialogueStyleRegistry {
    private static final ConcurrentMap<String, Renderer> RENDERERS = new ConcurrentHashMap<>();

    static {
        register(DialogueStyleRegistry.RPG, new Renderer() {
            @Override
            public Fragment create() {
                return new RpgDialogueFragment();
            }

            @Override
            public void refresh(Fragment fragment, PacketOpenDialogue packet) {
                if (fragment instanceof RpgDialogueFragment dialogueFragment) {
                    dialogueFragment.refresh(packet);
                }
            }
        });

        Renderer shopRenderer = new Renderer() {
            @Override
            public Fragment create() {
                return new ShopMenuFragment();
            }

            @Override
            public void refresh(Fragment fragment, PacketOpenDialogue packet) {
                if (fragment instanceof ShopMenuFragment shopFragment) {
                    shopFragment.refresh(packet);
                }
            }

            @Override
            public String title(PacketOpenDialogue packet) {
                return "Shop";
            }
        };
        register(DialogueStyleRegistry.SHOP, shopRenderer);
        register(DialogueStyleRegistry.MENU, shopRenderer);
    }

    private ClientDialogueStyleRegistry() {
    }

    public static void register(String styleId, Renderer renderer) {
        if (styleId == null || styleId.isBlank()) {
            throw new IllegalArgumentException("styleId must not be blank");
        }
        Objects.requireNonNull(renderer, "renderer");
        Renderer previous = RENDERERS.putIfAbsent(styleId, renderer);
        if (previous != null && previous != renderer) {
            throw new IllegalStateException("Client dialogue renderer already registered: " + styleId);
        }
    }

    @Nullable
    public static Renderer find(@Nullable String styleId) {
        return styleId == null ? null : RENDERERS.get(styleId);
    }

    public static boolean supports(@Nullable String styleId) {
        return find(styleId) != null;
    }

    public interface Renderer {
        Fragment create();

        void refresh(Fragment fragment, PacketOpenDialogue packet);

        default String title(PacketOpenDialogue packet) {
            return "Dialogue";
        }
    }
}
