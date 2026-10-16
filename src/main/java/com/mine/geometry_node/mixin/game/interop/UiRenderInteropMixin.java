package com.mine.geometry_node.mixin.game.interop;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mine.geometry_node.client.runtime.game.interop.UiRenderInterop;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;

/** Publish the external GPU-state change even if a UI submission fails. */
@Mixin(targets = "icyllis.modernui.mc.UIManager", remap = false)
public abstract class UiRenderInteropMixin {
    @WrapMethod(method = "render", remap = false)
    private void geometryNode$finishUiSubmission(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                                float deltaTick, Operation<Void> original) {
        try { original.call(graphics, mouseX, mouseY, deltaTick); }
        finally { UiRenderInterop.afterUiRendering(); }
    }
}
