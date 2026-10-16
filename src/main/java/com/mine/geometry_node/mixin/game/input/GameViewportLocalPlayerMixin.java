package com.mine.geometry_node.mixin.game.input;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class GameViewportLocalPlayerMixin {
    /** A portal must not close the editor host while its game view owns player control. */
    @ModifyExpressionValue(method = "handlePortalTransitionEffect", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
    private Screen geometryNode$keepCapturedEditorInPortal(Screen original) {
        return GameViewportInput.screenForGameInput(original);
    }
}
