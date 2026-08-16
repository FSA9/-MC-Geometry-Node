package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;

/** Stable NATIVE caster selection; Iris decides whether its shadow programs preserve translucent coverage. */
public final class ModelShadowPolicy {
    private ModelShadowPolicy() {}

    public static ModelAlphaMode effectiveAlphaMode(ModelAlphaMode authoredMode, float instanceAlpha,
                                                    boolean opaqueTranslucencyFallback) {
        return opaqueTranslucencyFallback && (authoredMode == ModelAlphaMode.BLEND || instanceAlpha < 0.999F)
                ? ModelAlphaMode.OPAQUE : authoredMode;
    }

    public static boolean castsShadow(ModelAlphaMode effectiveMode, float instanceAlpha,
                                      boolean opaqueTranslucencyFallback, ModelShadowPhase phase) {
        return (opaqueTranslucencyFallback || instanceAlpha >= 0.999F) && phase.accepts(effectiveMode);
    }
}
