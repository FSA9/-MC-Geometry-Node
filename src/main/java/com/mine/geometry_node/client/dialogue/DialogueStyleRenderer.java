package com.mine.geometry_node.client.dialogue;

import com.mine.geometry_node.client.dialogue.ui.RpgDialogueFragment;
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
    public static final String STYLE_RPG = "rpg";

    @Nullable
    private static Screen activeScreen;
    @Nullable
    private static UUID activeSessionId;

    private DialogueStyleRenderer() {
    }

    public static boolean supports(String styleId) {
        return STYLE_RPG.equals(styleId);
    }

    public static void open(PacketOpenDialogue packet) {
        if (!supports(packet.styleId())) {
            return;
        }

        if (isActive(packet) && activeScreen instanceof MuiScreen muiScreen) {
            Fragment fragment = muiScreen.getFragment();
            if (fragment instanceof RpgDialogueFragment rpgDialogueFragment) {
                rpgDialogueFragment.refresh(packet);
                return;
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen previousScreen = minecraft.screen;
        if (previousScreen == activeScreen && activeScreen instanceof MuiScreen muiScreen) {
            previousScreen = muiScreen.getPreviousScreen();
        }

        RpgDialogueFragment fragment = new RpgDialogueFragment();
        Screen screen = MuiModApi.get().createScreen(fragment, new DialogueScreenCallback(), previousScreen, "Dialogue");
        activeScreen = screen;
        activeSessionId = packet.sessionId();
        minecraft.setScreen(screen);
    }

    public static void close(UUID sessionId) {
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
        restoreGameInputIfNeeded();
    }

    private static void restoreGameInputIfNeeded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
            minecraft.tell(() -> {
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
