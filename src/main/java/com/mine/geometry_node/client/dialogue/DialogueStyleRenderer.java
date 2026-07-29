package com.mine.geometry_node.client.dialogue;

import com.mine.geometry_node.client.dialogue.ui.RpgDialogueFragment;
import com.mine.geometry_node.client.dialogue.ui.ShopMenuFragment;
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
    public static final String STYLE_SHOP = "shop";
    public static final String STYLE_MENU = "menu";

    @Nullable
    private static Screen activeScreen;
    @Nullable
    private static UUID activeSessionId;

    private DialogueStyleRenderer() {
    }

    public static boolean supports(String styleId) {
        return STYLE_RPG.equals(styleId)
                || STYLE_SHOP.equals(styleId)
                || STYLE_MENU.equals(styleId);
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

        if (isActive(packet) && activeScreen instanceof MuiScreen muiScreen) {
            Fragment fragment = muiScreen.getFragment();
            refreshOnUiThread(fragment, packet);
            return;
        }

        Screen previousScreen = minecraft.screen;
        if (previousScreen == activeScreen && activeScreen instanceof MuiScreen muiScreen) {
            previousScreen = muiScreen.getPreviousScreen();
        }

        Fragment fragment = createFragment(packet.styleId());
        String title = STYLE_SHOP.equals(packet.styleId()) || STYLE_MENU.equals(packet.styleId()) ? "Shop" : "Dialogue";
        Screen screen = MuiModApi.get().createScreen(fragment, new DialogueScreenCallback(), previousScreen, title);
        activeScreen = screen;
        activeSessionId = packet.sessionId();
        minecraft.setScreen(screen);
    }

    private static Fragment createFragment(String styleId) {
        if (STYLE_SHOP.equals(styleId) || STYLE_MENU.equals(styleId)) {
            return new ShopMenuFragment();
        }
        return new RpgDialogueFragment();
    }

    private static void refreshOnUiThread(Fragment fragment, PacketOpenDialogue packet) {
        MuiModApi.postToUiThread(() -> refresh(fragment, packet));
    }

    private static void refresh(Fragment fragment, PacketOpenDialogue packet) {
        if (fragment instanceof RpgDialogueFragment rpgDialogueFragment && STYLE_RPG.equals(packet.styleId())) {
            rpgDialogueFragment.refresh(packet);
            return;
        }
        if (fragment instanceof ShopMenuFragment shopMenuFragment
                && (STYLE_SHOP.equals(packet.styleId()) || STYLE_MENU.equals(packet.styleId()))) {
            shopMenuFragment.refresh(packet);
        }
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

        if (minecraft.screen == activeScreen) {
            Screen previousScreen = activeScreen instanceof MuiScreen muiScreen ? muiScreen.getPreviousScreen() : null;
            minecraft.setScreen(previousScreen);
        }
        activeScreen = null;
        activeSessionId = null;
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
        restoreGameInputIfNeeded();
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
