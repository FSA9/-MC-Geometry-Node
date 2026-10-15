package com.mine.geometry_node.mixin.game;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.runtime.game.GameViewportInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compatibility is confined to a captured editor; ordinary screens retain vanilla behavior. */
@Mixin(Minecraft.class)
public abstract class GameViewportMinecraftMixin {
    @Shadow private int missTime;
    @Shadow private void handleKeybinds() { throw new AssertionError(); }

    @ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
    private Screen geometryNode$gameInputScreen(Screen original) {
        return GameViewportInput.screenForGameInput(original);
    }

    @WrapOperation(method = "tick", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/client/Minecraft;missTime:I"))
    private void geometryNode$keepGameAttackDelay(Minecraft instance, int value, Operation<Void> original) {
        if (value != 10000 || !GameViewportInput.isGameInputActive()) original.call(instance, value);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void geometryNode$tickCapturedGameInput(CallbackInfo ci) {
        GameViewportInput.tick();
        if (GameViewportInput.isGameInputActive() && !((Minecraft) (Object) this).isPaused()) {
            // grabMouse and the last uncaptured editor tick both seed this Screen-only delay.
            if (missTime >= 10000) missTime = 0;
            handleKeybinds();
            if (missTime > 0) missTime--;
        }
    }

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen geometryNode$prepareGameScreen(Screen screen) {
        return GameViewportInput.prepareScreenChange(screen);
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;removed()V"))
    private void geometryNode$suspendEditor(Screen screen, Operation<Void> original) {
        if (!GameViewportInput.retainEditor(screen)) original.call(screen);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void geometryNode$finishGameScreenChange(Screen screen, CallbackInfo ci) {
        GameViewportInput.screenChanged();
    }
}
