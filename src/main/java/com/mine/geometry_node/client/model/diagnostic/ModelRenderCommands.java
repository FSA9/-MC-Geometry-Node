package com.mine.geometry_node.client.model.diagnostic;

import com.mine.geometry_node.client.model.render.lod.model.ModelLodPolicy;
import com.mine.geometry_node.client.model.render.shadow.ModelShadowDistancePolicy;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Render controls; asset management and report IO remain in ModelCommands. */
final class ModelRenderCommands {
    static <S> void register(LiteralArgumentBuilder<S> root) {
        root.then(ModelRenderCommands.<S>shadow());
        ModelLightingCommands.register(root);
        root.then(ModelRenderCommands.<S>lod());
    }

    private static <S> LiteralArgumentBuilder<S> shadow() {
        return ModelRenderCommands.<S>module("shadow", ModelShadowDistancePolicy::setEnabled,
                        ModelShadowDistancePolicy::reset, ModelRenderCommands::shadowStatus)
                .then(LiteralArgumentBuilder.<S>literal("auto")
                        .executes(c -> change(ModelShadowDistancePolicy::setAutomatic, ModelRenderCommands::shadowStatus)))
                .then(LiteralArgumentBuilder.<S>literal("tolerance")
                        .then(RequiredArgumentBuilder.<S, Float>argument("texels", FloatArgumentType.floatArg(0, 2))
                                .executes(c -> change(() -> ModelShadowDistancePolicy.setTolerance(
                                        FloatArgumentType.getFloat(c, "texels")), ModelRenderCommands::shadowStatus))))
                .then(LiteralArgumentBuilder.<S>literal("distance")
                        .then(RequiredArgumentBuilder.<S, Double>argument("blocks", DoubleArgumentType.doubleArg(0))
                                .executes(c -> change(() -> ModelShadowDistancePolicy.setMaxDistance(
                                        DoubleArgumentType.getDouble(c, "blocks")), ModelRenderCommands::shadowStatus))));
    }

    private static <S> LiteralArgumentBuilder<S> lod() {
        return ModelRenderCommands.<S>module("lod",
                        enabled -> ModelLodPolicy.setActive(ModelLodPolicy.active().withEnabled(enabled)),
                        () -> ModelLodPolicy.setActive(ModelLodPolicy.DEFAULT), ModelRenderCommands::lodStatus)
                .then(LiteralArgumentBuilder.<S>literal("auto")
                        .executes(c -> change(() -> ModelLodPolicy.setActive(ModelLodPolicy.active().withAuto()), ModelRenderCommands::lodStatus)))
                .then(LiteralArgumentBuilder.<S>literal("level")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("level", IntegerArgumentType.integer(0, 3))
                                .executes(c -> change(() -> ModelLodPolicy.setActive(ModelLodPolicy.active()
                                        .withLevel(IntegerArgumentType.getInteger(c, "level"))), ModelRenderCommands::lodStatus))))
                .then(LiteralArgumentBuilder.<S>literal("distance")
                        .then(RequiredArgumentBuilder.<S, Double>argument("level1", DoubleArgumentType.doubleArg(Double.MIN_VALUE))
                                .then(RequiredArgumentBuilder.<S, Double>argument("level2", DoubleArgumentType.doubleArg(Double.MIN_VALUE))
                                        .then(RequiredArgumentBuilder.<S, Double>argument("level3", DoubleArgumentType.doubleArg(Double.MIN_VALUE))
                                                .executes(c -> change(() -> ModelLodPolicy.setActive(ModelLodPolicy.active().withDistances(
                                                        DoubleArgumentType.getDouble(c, "level1"), DoubleArgumentType.getDouble(c, "level2"),
                                                        DoubleArgumentType.getDouble(c, "level3"))), ModelRenderCommands::lodStatus))))))
                .then(LiteralArgumentBuilder.<S>literal("hysteresis")
                        .then(RequiredArgumentBuilder.<S, Double>argument("fraction", DoubleArgumentType.doubleArg(0, Math.nextDown(1.0)))
                                .executes(c -> change(() -> ModelLodPolicy.setActive(ModelLodPolicy.active()
                                        .withHysteresis(DoubleArgumentType.getDouble(c, "fraction"))), ModelRenderCommands::lodStatus))));
    }

    private static <S> LiteralArgumentBuilder<S> module(String name, Consumer<Boolean> enabled,
                                                       Runnable reset, Supplier<String> status) {
        return LiteralArgumentBuilder.<S>literal(name).executes(c -> ModelCommands.run(() -> ModelCommands.say(status.get())))
                .then(LiteralArgumentBuilder.<S>literal("status").executes(c -> ModelCommands.run(() -> ModelCommands.say(status.get()))))
                .then(LiteralArgumentBuilder.<S>literal("on").executes(c -> change(() -> enabled.accept(true), status)))
                .then(LiteralArgumentBuilder.<S>literal("off").executes(c -> change(() -> enabled.accept(false), status)))
                .then(LiteralArgumentBuilder.<S>literal("reset").executes(c -> change(reset, status)));
    }

    private static int change(Runnable action, Supplier<String> status) {
        return ModelCommands.run(() -> {
            ModelCommands.requireLightingProfileIdle();
            action.run();
            ModelCommands.say(status.get());
        });
    }

    private static String shadowStatus() {
        return "Shadow: " + (ModelShadowDistancePolicy.enabled() ? "on" : "off")
                + (ModelShadowDistancePolicy.automatic() ? "; auto tolerance 0..2 texels at 32..256 blocks"
                : "; manual tolerance " + ModelShadowDistancePolicy.manualTolerance() + " texels")
                + "; caster distance " + ModelShadowDistancePolicy.maxDistance() + " blocks; session only.";
    }

    private static String lodStatus() {
        var p = ModelLodPolicy.active();
        return String.format(Locale.ROOT, "Object LOD: %s; %s; distances %s / %s / %s; hysteresis %s; ratios 1 / 0.8 / 0.6 / 0.3; session only.",
                p.enabled() ? "on" : "off (level 0)", p.automatic() ? "auto" : "manual level " + p.manualLevel(),
                p.level1Distance(), p.level2Distance(), p.level3Distance(), p.hysteresisFraction());
    }

    private ModelRenderCommands() {}
}
