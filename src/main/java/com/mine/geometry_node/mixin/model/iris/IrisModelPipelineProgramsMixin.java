package com.mine.geometry_node.mixin.model.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mine.geometry_node.client.model.render.bvh.ModelIrisHeldLight;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Cache hits retain their original group; new pipelines collect their own program registrations. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public abstract class IrisModelPipelineProgramsMixin {
    @WrapOperation(method = "preparePipeline", at = @At(value = "INVOKE", target =
            "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"), remap = false, require = 0)
    private Object geometryNode$collectPipelinePrograms(Function<Object, Object> factory, Object dimension,
                                                        Operation<Object> original) {
        return ModelIrisHeldLight.collectPipelinePrograms(() -> original.call(factory, dimension));
    }
}
