package com.mine.geometry_node.client.ui.editor.graph.picker;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketCaptureEntityTemplateRequest;
import com.mine.geometry_node.core.network.packet.s2c.PacketCaptureEntityTemplateResponse;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Temporarily returns control to the world and captures the next attacked entity as a template. */
public final class EntityTemplatePickerController {
    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);

    private static Session activeSession;

    private EntityTemplatePickerController() {
    }

    public static boolean open(Consumer<EntityTemplateValue> onPicked, Runnable onDismissed) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> open(onPicked, onDismissed));
            return true;
        }
        if (activeSession != null || minecraft.player == null || minecraft.level == null) {
            return false;
        }

        activeSession = new Session(
                NEXT_REQUEST_ID.getAndUpdate(value -> value == Integer.MAX_VALUE ? 1 : value + 1),
                minecraft.screen,
                onPicked,
                onDismissed
        );
        minecraft.setScreen(null);
        return true;
    }

    public static void handleInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Session session = activeSession;
        if (session == null || !event.isAttack()) return;

        event.setCanceled(true);
        event.setSwingHand(false);
        if (session.awaitingResponse) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof EntityHitResult hitResult)) return;

        session.awaitingResponse = true;
        NetworkHandler.sendToServer(new PacketCaptureEntityTemplateRequest(
                session.requestId,
                hitResult.getEntity().getId()
        ));
    }

    public static boolean handleKey(int action, KeyEvent event) {
        if (activeSession == null || action != GLFW.GLFW_PRESS || event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return false;
        }
        cancel();
        return true;
    }

    public static void handleResponse(PacketCaptureEntityTemplateResponse packet) {
        Session session = activeSession;
        if (session == null || session.requestId != packet.requestId()) return;

        EntityTemplateValue template = packet.success()
                ? new EntityTemplateValue(packet.entityTypeId(), packet.entityData())
                : EntityTemplateValue.EMPTY;
        finish(session, template, packet.messageKey());
    }

    public static void cancel() {
        Session session = activeSession;
        if (session == null) return;
        finish(session, EntityTemplateValue.EMPTY, "");
    }

    public static void reset() {
        activeSession = null;
    }

    public static boolean isActive() {
        return activeSession != null;
    }

    private static void finish(Session session, EntityTemplateValue template, String messageKey) {
        if (activeSession != session) return;
        activeSession = null;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(session.previousScreen);
        if (messageKey != null && !messageKey.isBlank() && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.translatable(messageKey));
        }

        MuiModApi.postToUiThread(() -> {
            if (!template.isEmpty() && session.onPicked != null) {
                session.onPicked.accept(template);
            }
            if (session.onDismissed != null) {
                session.onDismissed.run();
            }
        });
    }

    private static final class Session {
        private final int requestId;
        private final Screen previousScreen;
        private final Consumer<EntityTemplateValue> onPicked;
        private final Runnable onDismissed;
        private boolean awaitingResponse;

        private Session(
                int requestId,
                Screen previousScreen,
                Consumer<EntityTemplateValue> onPicked,
                Runnable onDismissed
        ) {
            this.requestId = requestId;
            this.previousScreen = previousScreen;
            this.onPicked = onPicked;
            this.onDismissed = onDismissed;
        }
    }
}
