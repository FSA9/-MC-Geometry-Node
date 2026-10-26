package com.mine.geometry_node.client.runtime.model.diagnostic;

import com.mine.geometry_node.client.runtime.model.runtime.ClientModelCollisions;
import com.mine.geometry_node.client.runtime.model.runtime.ClientModelService;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;
import com.mine.geometry_node.core.engine.system.model.runtime.ModelInstanceLifetime;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Collision command grammar is independent of rendering quality controls. */
final class ModelCollisionCommands {
    private static final ClientModelService MODELS = ClientModelService.INSTANCE;
    private ModelCollisionCommands() {}

    static <S> void register(LiteralArgumentBuilder<S> root) {
        var instance = RequiredArgumentBuilder.<S, String>argument("id", StringArgumentType.word());
        instance.then(LiteralArgumentBuilder.<S>literal("on").executes(c -> run(() -> configure(c, ModelCollisionSettings.DEFAULT))));
        instance.then(LiteralArgumentBuilder.<S>literal("off").executes(c -> run(() -> configure(c, null))));
        instance.then(LiteralArgumentBuilder.<S>literal("generate").then(parameters(c -> configure(c, settings(c)))));
        instance.then(LiteralArgumentBuilder.<S>literal("status").executes(c -> run(() -> {
            String id = StringArgumentType.getString(c, "id");
            if (MODELS.query(id).isEmpty()) throw new IllegalArgumentException("unknown model instance: " + id);
            var settings = ClientModelCollisions.INSTANCE.settings(id);
            if (settings == null) say("off", id);
            else say("settings", id, settings.mergeScale(), settings.fitError());
            var status = ClientModelCollisions.INSTANCE.status(id);
            say("details", id, state(status.active()), state(status.pending()), status.parts(), state(status.cacheHit()));
            if (status.skippedTriangles() > 0)
                say("partial", id, status.incompleteObjects(), status.skippedTriangles());
            if (!status.failure().isEmpty()) say("failed", id, status.failure());
        })));
        root.then(LiteralArgumentBuilder.<S>literal("collision").then(instance));
        root.then(LiteralArgumentBuilder.<S>literal("load").then(load("preview", false)));
        root.then(LiteralArgumentBuilder.<S>literal("create")
                .then(RequiredArgumentBuilder.<S, String>argument("id", StringArgumentType.word()).then(load(null, true))));
    }

    private static <S> LiteralArgumentBuilder<S> load(String fixedId, boolean named) {
        var path = RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.greedyString())
                .executes(c -> run(() -> {
                    long started = System.nanoTime();
                    String id = named ? StringArgumentType.getString(c, "id") : fixedId;
                    var source = ClientModelService.Source.local(ModelCommands.localPath(StringArgumentType.getString(c, "path")));
                    observe(id, MODELS.create(id, source, ClientModelService.previewTransform(), -1, true,
                            ModelInstanceLifetime.TEMPORARY, settings(c)), started);
                }));
        return LiteralArgumentBuilder.<S>literal("collision").then(
                RequiredArgumentBuilder.<S, Double>argument("mergeScale", DoubleArgumentType.doubleArg(0, 16)).then(
                        RequiredArgumentBuilder.<S, Double>argument("fitError", DoubleArgumentType.doubleArg(0.0001, 1)).then(path)));
    }

    private static <S> RequiredArgumentBuilder<S, Double> parameters(Consumer<CommandContext<S>> action) {
        return RequiredArgumentBuilder.<S, Double>argument("mergeScale", DoubleArgumentType.doubleArg(0, 16)).then(
                RequiredArgumentBuilder.<S, Double>argument("fitError", DoubleArgumentType.doubleArg(0.0001, 1))
                        .executes(c -> run(() -> action.accept(c))));
    }

    private static <S> ModelCollisionSettings settings(CommandContext<S> context) {
        return new ModelCollisionSettings(DoubleArgumentType.getDouble(context, "mergeScale"),
                DoubleArgumentType.getDouble(context, "fitError"));
    }

    private static <S> void configure(CommandContext<S> context, ModelCollisionSettings settings) {
        long started = System.nanoTime();
        String id = StringArgumentType.getString(context, "id");
        if (settings == null) {
            MODELS.collision(id, null);
            say("off", id);
        } else observe(id, MODELS.collision(id, settings), started);
    }

    private static void observe(String id, CompletionStage<?> completion, long started) {
        say("queued", id);
        completion.whenComplete((ignored, error) -> {
            if (error == null) {
                long elapsed = System.nanoTime() - started;
                ClientModelCollisions.INSTANCE.commandCompleted(id, elapsed);
                say("ready", id, state(ClientModelCollisions.INSTANCE.status(id).cacheHit()),
                        String.format(java.util.Locale.ROOT, "%.2f", elapsed / 1e9));
                var status = ClientModelCollisions.INSTANCE.status(id);
                if (status.skippedTriangles() > 0)
                    say("partial", id, status.incompleteObjects(), status.skippedTriangles());
            }
            else say("failed", id, ClientModelService.rootMessage(error));
        });
    }

    private static int run(Runnable action) {
        try { action.run(); return 1; }
        catch (RuntimeException error) { say("error", ClientModelService.rootMessage(error)); return 0; }
    }

    private static void say(String key, Object... args) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.translatable("geometry_node.model.collision." + key, args));
    }

    private static Component state(boolean value) {
        return Component.translatable("geometry_node.model.collision." + (value ? "yes" : "no"));
    }
}
