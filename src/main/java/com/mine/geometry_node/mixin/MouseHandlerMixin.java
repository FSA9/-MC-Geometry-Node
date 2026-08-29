package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.input.ClientBlueprintInputManager;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void geometryNode$interceptBlueprintMouse(long handle, MouseButtonInfo buttonInfo,
                                                       int action, CallbackInfo ci) {
        if (ClientBlueprintInputManager.interceptMouse(action, buttonInfo)) {
            ci.cancel();
        }
    }
}
