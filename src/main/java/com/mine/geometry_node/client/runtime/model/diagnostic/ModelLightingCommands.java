package com.mine.geometry_node.client.runtime.model.diagnostic;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelLightingSettings;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import java.util.function.Function;

/** Global defaults and explicit per-instance receiver overrides. */
final class ModelLightingCommands {
    static <S> void register(LiteralArgumentBuilder<S> root) {
        root.then(module("local", true));
        root.then(module("gi", false));
    }

    private static <S> LiteralArgumentBuilder<S> module(String name, boolean local) {
        var branch = LiteralArgumentBuilder.<S>literal(name);
        controls(branch, local, c -> null);
        var instance = RequiredArgumentBuilder.<S, String>argument("id", StringArgumentType.word());
        controls(instance, local, c -> StringArgumentType.getString(c, "id"));
        branch.then(LiteralArgumentBuilder.<S>literal("instance").then(instance));
        if (local) branch.then(ModelCommands.<S>profileCommands());
        return branch;
    }

    private static <S, T extends ArgumentBuilder<S, T>> void controls(
            T branch, boolean local, Function<CommandContext<S>, String> target) {
        branch.executes(c -> run(target.apply(c), local, null));
        branch.then(LiteralArgumentBuilder.<S>literal("status")
                .executes(c -> run(target.apply(c), local, null)));
        for (boolean enabled : new boolean[]{true, false}) {
            branch.then(LiteralArgumentBuilder.<S>literal(enabled ? "on" : "off")
                    .executes(c -> run(target.apply(c), local, () -> {
                        if (local) ModelLightingSettings.setLocalEnabled(target.apply(c), enabled);
                        else ModelLightingSettings.setGiEnabled(target.apply(c), enabled);
                    })));
        }
        branch.then(LiteralArgumentBuilder.<S>literal("reset")
                .executes(c -> run(target.apply(c), local, () -> {
                    if (local) ModelLightingSettings.resetLocal(target.apply(c));
                    else ModelLightingSettings.resetGi(target.apply(c));
                })));
        if (!local) return;
        branch.then(LiteralArgumentBuilder.<S>literal("auto")
                .executes(c -> run(target.apply(c), true,
                        () -> ModelLightingSettings.setAutomatic(target.apply(c)))));
        branch.then(LiteralArgumentBuilder.<S>literal("samples")
                .then(RequiredArgumentBuilder.<S, Integer>argument("count", IntegerArgumentType.integer(0, 4))
                        .executes(c -> run(target.apply(c), true, () -> ModelLightingSettings.setSamples(
                                target.apply(c), IntegerArgumentType.getInteger(c, "count"))))));
        branch.then(LiteralArgumentBuilder.<S>literal("resolution")
                .then(RequiredArgumentBuilder.<S, Float>argument("scale", FloatArgumentType.floatArg(0, 1))
                        .executes(c -> run(target.apply(c), true, () -> ModelLightingSettings.setResolution(
                                target.apply(c), FloatArgumentType.getFloat(c, "scale"))))));
    }

    private static int run(String id, boolean local, Runnable change) {
        return ModelLightingCommandFeedback.run(id, local, change);
    }

    private ModelLightingCommands() {}
}
