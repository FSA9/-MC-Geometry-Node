package com.mine.geometry_node.client.runtime.dialogue;

import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client-side dispatcher for non-vanilla dialogue styles.
 */
public final class DialogueStyleRenderer {
    private static final int CLOSE_GRACE_TICKS = 2;

    @Nullable
    private static Screen activeScreen;
    @Nullable
    private static UUID activeSessionId;
    @Nullable
    private static String activeStyleId;
    @Nullable
    private static UUID pendingCloseSessionId;
    private static int pendingCloseTicks;

    private DialogueStyleRenderer() {
    }

    public static boolean supports(String styleId) {
        return ClientDialogueStyleRegistry.supports(styleId);
    }

    public static void open(PacketOpenDialogue packet) {
        if (!supports(packet.styleId())) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> open(packet));
            return;
        }

        ClientDialogueStyleRegistry.Renderer renderer = ClientDialogueStyleRegistry.find(packet.styleId());
        if (renderer == null) {
            return;
        }

        cancelPendingClose();
        if (canRefreshActiveScreen(packet.styleId()) && activeScreen instanceof MuiScreen muiScreen) {
            activeSessionId = packet.sessionId();
            activeStyleId = packet.styleId();
            Fragment fragment = muiScreen.getFragment();
            refreshOnUiThread(renderer, fragment, packet);
            return;
        }

        Screen previousScreen = minecraft.screen;
        if (previousScreen == activeScreen && activeScreen instanceof MuiScreen muiScreen) {
            previousScreen = muiScreen.getPreviousScreen();
        }

        Fragment fragment = renderer.create();
        Screen screen = MuiModApi.get().createScreen(
                fragment,
                new DialogueScreenCallback(),
                previousScreen,
                renderer.title(packet)
        );
        activeScreen = screen;
        activeSessionId = packet.sessionId();
        activeStyleId = packet.styleId();
        minecraft.setScreen(screen);
    }

    private static void refreshOnUiThread(ClientDialogueStyleRegistry.Renderer renderer,
                                          Fragment fragment,
                                          PacketOpenDialogue packet) {
        MuiModApi.postToUiThread(() -> renderer.refresh(fragment, packet));
    }

    public static void close(UUID sessionId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> close(sessionId));
            return;
        }
        if (activeSessionId == null || !activeSessionId.equals(sessionId)) {
            return;
        }

        pendingCloseSessionId = sessionId;
        pendingCloseTicks = CLOSE_GRACE_TICKS;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(DialogueStyleRenderer::tick);
            return;
        }

        UUID sessionId = pendingCloseSessionId;
        if (sessionId == null) {
            return;
        }
        if (!sessionId.equals(activeSessionId)) {
            cancelPendingClose();
            return;
        }
        if (--pendingCloseTicks > 0) {
            return;
        }

        closeNow(sessionId);
    }

    private static void closeNow(UUID sessionId) {
        if (activeSessionId == null || !activeSessionId.equals(sessionId)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == activeScreen) {
            Screen previousScreen = activeScreen instanceof MuiScreen muiScreen ? muiScreen.getPreviousScreen() : null;
            minecraft.setScreen(previousScreen);
        }
        activeScreen = null;
        activeSessionId = null;
        activeStyleId = null;
        cancelPendingClose();
        restoreGameInputIfNeeded();
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(DialogueStyleRenderer::clear);
            return;
        }

        if (minecraft.screen == activeScreen) {
            Screen previousScreen = activeScreen instanceof MuiScreen muiScreen ? muiScreen.getPreviousScreen() : null;
            minecraft.setScreen(previousScreen);
        }
        activeScreen = null;
        activeSessionId = null;
        activeStyleId = null;
        cancelPendingClose();
        restoreGameInputIfNeeded();
    }

    private static boolean canRefreshActiveScreen(String styleId) {
        return activeScreen != null
                && Minecraft.getInstance().screen == activeScreen
                && styleId.equals(activeStyleId);
    }

    private static void cancelPendingClose() {
        pendingCloseSessionId = null;
        pendingCloseTicks = 0;
    }

    private static void restoreGameInputIfNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
            minecraft.execute(() -> {
                if (minecraft.screen == null) {
                    minecraft.mouseHandler.grabMouse();
                }
            });
        }
    }

    public static boolean isActive(PacketOpenDialogue packet) {
        return activeSessionId != null
                && activeSessionId.equals(packet.sessionId())
                && activeScreen != null
                && Minecraft.getInstance().screen == activeScreen;
    }

    private static final class DialogueScreenCallback implements ScreenCallback {
        @Override
        public boolean isBackKey(int keyCode, KeyEvent event) {
            return keyCode == KeyEvent.KEY_ESCAPE;
        }

        @Override
        public boolean shouldClose() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean hasDefaultBackground() {
            return false;
        }

        @Override
        public boolean shouldBlurBackground() {
            return false;
        }
    }
}
