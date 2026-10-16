package com.mine.geometry_node.mixin.input;

import com.mine.geometry_node.client.input.ClientBlueprintInputManager;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import com.mine.geometry_node.client.runtime.game.EditViewportFlightInput;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private boolean isLeftPressed;
    @Shadow private boolean isMiddlePressed;
    @Shadow private boolean isRightPressed;
    @Shadow private MouseButtonInfo activeButton;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Shadow private double lastHandleMovementTime;

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void geometryNode$protectBrowsingEditor(CallbackInfo ci) {
        // PauseScreen and AdvancementsScreen explicitly grab again after setScreen(null).
        // The screen bridge has already restored our editor in browsing mode by then.
        if (GameViewportInput.isEditor(Minecraft.getInstance().screen)
                && !GameViewportInput.isGameInputActive() && !EditViewportFlightInput.isCaptured()) ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("TAIL"))
    private void geometryNode$clearEditorMouseState(CallbackInfo ci) {
        if (GameViewportInput.isEditor(Minecraft.getInstance().screen)) {
            isLeftPressed = false;
            isMiddlePressed = false;
            isRightPressed = false;
            activeButton = null;
            accumulatedDX = 0;
            accumulatedDY = 0;
        }
    }

    @ModifyExpressionValue(method = {"onButton", "onScroll", "handleAccumulatedMovement"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
    private Screen geometryNode$gameInputScreen(Screen original) {
        return GameViewportInput.screenForGameInput(original);
    }

    @WrapOperation(method = "grabMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void geometryNode$retainCapturedEditor(Minecraft minecraft, Screen screen, Operation<Void> original) {
        if (!GameViewportInput.isGameInputActive() && !EditViewportFlightInput.isCaptured()) original.call(minecraft, screen);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void geometryNode$interceptBlueprintMouse(long handle, MouseButtonInfo buttonInfo,
                                                       int action, CallbackInfo ci) {
        if (EditViewportFlightInput.isCaptured()) {
            EditViewportFlightInput.validate();
            ci.cancel();
            return;
        }
        if (ClientBlueprintInputManager.interceptMouse(action, buttonInfo)) {
            ci.cancel();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void geometryNode$muteFlightScroll(CallbackInfo ci) {
        if (EditViewportFlightInput.isCaptured()) ci.cancel();
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void geometryNode$turnFlightCamera(CallbackInfo ci) {
        if (!EditViewportFlightInput.isCaptured()) return;
        // onMove still handles native cursor deltas/ignoreFirstMove. Consume them
        // before either Screen.mouseMoved or LocalPlayer.turn can see them.
        EditViewportFlightInput.look(accumulatedDX, accumulatedDY);
        accumulatedDX = 0;
        accumulatedDY = 0;
        lastHandleMovementTime = com.mojang.blaze3d.Blaze3D.getTime();
        ci.cancel();
    }
}
