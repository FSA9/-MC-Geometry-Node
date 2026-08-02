package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.ui.MainUI;
import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.mc.MuiScreen;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.InputType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(
            method = "keyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;onInputReceived()V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void geometryNode$routeMainUiKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
        Screen screen = minecraft.screen;
        if (!(screen instanceof MuiScreen muiScreen) || !(muiScreen.getFragment() instanceof MainUI)) {
            return;
        }

        updateLastInputType(event);
        dispatchToScreen(screen, action, event);
        ci.cancel();
    }

    private void updateLastInputType(KeyEvent event) {
        switch (event.key()) {
            case GLFW_KEY_TAB -> minecraft.setLastInputType(InputType.KEYBOARD_TAB);
            case GLFW_KEY_RIGHT, GLFW_KEY_LEFT, GLFW_KEY_DOWN, GLFW_KEY_UP ->
                    minecraft.setLastInputType(InputType.KEYBOARD_ARROW);
            default -> {
            }
        }
    }

    private static void dispatchToScreen(Screen screen, int action, KeyEvent event) {
        try {
            if (action == GLFW_RELEASE) {
                if (!ClientHooks.onScreenKeyReleasedPre(screen, event)
                        && !screen.keyReleased(event)) {
                    ClientHooks.onScreenKeyReleasedPost(screen, event);
                }
                KeyMapping.set(InputConstants.getKey(event), false);
                return;
            }

            if (action == GLFW_PRESS || action == GLFW_REPEAT) {
                screen.afterKeyboardAction();
                if (!ClientHooks.onScreenKeyPressedPre(screen, event)
                        && !screen.keyPressed(event)) {
                    ClientHooks.onScreenKeyPressedPost(screen, event);
                }
            }
        } catch (Throwable throwable) {
            CrashReport report = CrashReport.forThrowable(throwable, "keyPressed event handler");
            screen.fillCrashDetails(report);
            CrashReportCategory keyDetails = report.addCategory("Key");
            keyDetails.setDetail("Key", event.key());
            keyDetails.setDetail("Scancode", event.scancode());
            keyDetails.setDetail("Mods", event.modifiers());
            throw new ReportedException(report);
        }
    }
}
