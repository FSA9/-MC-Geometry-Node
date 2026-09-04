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

/** Temporarily returns to the world and captures an entity reference plus a preview snapshot. */
public final class DataLibraryEntityPickerController {
    private static Session active;

    private DataLibraryEntityPickerController() {}

    public static boolean open(BiConsumer<UUID, EntityTemplateValue> picked,
                               Runnable dismissed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> open(picked, dismissed));
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
        active = null;
    }

    private static void finish(Session session, UUID reference,
                               EntityTemplateValue preview) {
        if (active != session) return;
        active = null;
        Minecraft.getInstance().setScreen(session.previousScreen);
        MuiModApi.postToUiThread(() -> {
            if (reference != null && session.picked != null) session.picked.accept(reference, preview);
            if (session.dismissed != null) session.dismissed.run();
        });
    }

    private record Session(Screen previousScreen,
                           BiConsumer<UUID, EntityTemplateValue> picked,
                           Runnable dismissed) {}
}
