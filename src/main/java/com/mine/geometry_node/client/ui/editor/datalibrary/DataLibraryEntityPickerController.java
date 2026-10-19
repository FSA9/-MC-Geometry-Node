package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Temporarily returns to the world and captures an entity reference plus a preview snapshot. */
public final class DataLibraryEntityPickerController {
    private static Session active;
    private static volatile long connectionGeneration;

    private DataLibraryEntityPickerController() {}

    public static boolean open(BiConsumer<UUID, EntityTemplateValue> picked,
                               Runnable dismissed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            long generation = connectionGeneration;
            minecraft.execute(() -> {
                if (generation != connectionGeneration || !open(picked, dismissed)) {
                    if (dismissed != null) MuiModApi.postToUiThread(dismissed);
                }
            });
            return true;
        }
        if (active != null || minecraft.player == null || minecraft.level == null) return false;
        active = new Session(minecraft.screen, picked, dismissed);
        minecraft.setScreen(null);
        return true;
    }

    public static void handleInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Session session = active;
        if (session == null || !event.isAttack()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof EntityHitResult hit)) return;
        finish(session, hit.getEntity().getUUID(),
                EntityTemplateValue.capture(hit.getEntity()));
    }

    public static boolean handleKey(int action, KeyEvent event) {
        if (active == null || action != GLFW.GLFW_PRESS || event.key() != GLFW.GLFW_KEY_ESCAPE) return false;
        finish(active, null, null);
        return true;
    }

    public static void reset() {
        reset(MuiModApi::postToUiThread);
    }

    static void reset(Consumer<Runnable> uiQueue) {
        connectionGeneration++;
        Session previous = active;
        active = null;
        if (previous != null) uiQueue.accept(() -> complete(previous, null, null));
    }

    private static void complete(Session session, UUID reference, EntityTemplateValue preview) {
        try {
            if (reference != null && session.picked != null) session.picked.accept(reference, preview);
        } finally {
            if (session.dismissed != null) session.dismissed.run();
        }
    }

    private static void finish(Session session, UUID reference,
                               EntityTemplateValue preview) {
        if (active != session) return;
        active = null;
        Minecraft.getInstance().setScreen(session.previousScreen);
        long generation = connectionGeneration;
        MuiModApi.postToUiThread(() -> complete(session,
                generation == connectionGeneration ? reference : null, preview));
    }

    private record Session(Screen previousScreen,
                           BiConsumer<UUID, EntityTemplateValue> picked,
                           Runnable dismissed) {}
}
