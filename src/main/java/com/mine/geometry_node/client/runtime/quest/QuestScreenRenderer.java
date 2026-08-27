package com.mine.geometry_node.client.runtime.quest;

import com.mine.geometry_node.client.runtime.quest.ui.QuestScreenFragment;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.MuiScreen;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.view.KeyEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class QuestScreenRenderer {
    @Nullable
    private static Screen activeScreen;

    private QuestScreenRenderer() {
    }

    public static void openOrRefresh(PacketQuestScreenSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> openOrRefresh(snapshot));
            return;
        }

        if (isActive() && activeScreen instanceof MuiScreen muiScreen) {
            Fragment fragment = muiScreen.getFragment();
            if (fragment instanceof QuestScreenFragment questFragment) {
                MuiModApi.postToUiThread(() -> questFragment.refresh(snapshot));
                return;
            }
        }

        Screen screen = MuiModApi.get().createScreen(
                new QuestScreenFragment(),
                new QuestScreenCallback(),
                minecraft.screen,
                Component.translatable("geometry_node.quest.screen.title").getString()
        );
        activeScreen = screen;
        minecraft.setScreen(screen);
    }

    public static boolean isActive() {
        return activeScreen != null && Minecraft.getInstance().screen == activeScreen;
    }

    public static void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(QuestScreenRenderer::close);
            return;
        }
        closeNow(minecraft);
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(QuestScreenRenderer::clear);
            return;
        }
        closeNow(minecraft);
    }

    private static void closeNow(Minecraft minecraft) {
        if (minecraft.screen == activeScreen && activeScreen instanceof MuiScreen muiScreen) {
            minecraft.setScreen(muiScreen.getPreviousScreen());
        }
        activeScreen = null;
        if (minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    private static final class QuestScreenCallback implements ScreenCallback {
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
