package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorTreeProcess;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Collection;
import java.util.Set;

/** Operator-facing binding and lifecycle controls for server behavior-tree instances. */
public final class BehaviorTreeCommand {
    private BehaviorTreeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("behavior_tree")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(targetOperation("status", BehaviorTreeCommand::status))
                .then(targetOperation("start", BehaviorTreeCommand::start))
                .then(targetOperation("suspend", BehaviorTreeCommand::suspend))
                .then(targetOperation("resume", BehaviorTreeCommand::resume))
                .then(targetOperation("stop", BehaviorTreeCommand::stop))
                .then(Commands.literal("unbind")
                        .then(Commands.literal("all")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> runTargets(context, BehaviorTreeCommand::unbindAll))))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_BEHAVIOR_TREES)
                                        .executes(BehaviorTreeCommand::unbind))))
                .then(Commands.literal("bind")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_BEHAVIOR_TREES)
                                        .executes(BehaviorTreeCommand::bind))))
                .then(Commands.literal("switch")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_BEHAVIOR_TREES)
                                        .executes(BehaviorTreeCommand::switchTree)))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> targetOperation(
            String name, TargetHandler handler) {
        return Commands.literal(name).then(Commands.argument("targets", EntityArgument.entities())
                .executes(context -> runTargets(context, handler)));
    }

    private static int bind(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String graphId = StringArgumentType.getString(context, "graph_id");
        return runTargets(context, (source, mob) -> {
            boolean changed = BehaviorTreeRuntime.INSTANCE.bind(mob, graphId);
            source.sendSuccess(() -> Component.translatable(changed ? "geometry_node.command.behavior.bind" : "geometry_node.command.behavior.already_bound", graphId, mob.getName().getString()), false);
            return changed;
        });
    }

    private static int switchTree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String graphId = StringArgumentType.getString(context, "graph_id");
        return runTargets(context, (source, mob) -> {
            BehaviorTreeProcess instance = BehaviorTreeRuntime.INSTANCE.switchTo(mob, graphId);
            source.sendSuccess(() -> Component.translatable("geometry_node.command.behavior.switched", mob.getName().getString(), graphId, instance.instanceId()), false);
            return true;
        });
    }

    private static boolean status(CommandSourceStack source, Mob mob) {
        BehaviorTreeProcess instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        Set<String> bindings = BehaviorTreeRuntime.INSTANCE.boundGraphs(mob);
        String selected = BehaviorTreeRuntime.INSTANCE.selectedGraph(mob);
        BehaviorTerminationReason lastStop = BehaviorTreeRuntime.INSTANCE.lastStopReasonForOwner(
                source.getServer(), mob.getUUID());
        source.sendSuccess(() -> Component.translatable("geometry_node.command.behavior.status", mob.getName().getString(), bindings,
                selected != null ? selected : "none", instance != null ? instance.state() + " (" + instance.instanceId() + ")" : "none",
                lastStop != null ? lastStop : "none"), false);
        return true;
    }

    private static boolean start(CommandSourceStack source, Mob mob) {
        BehaviorTreeProcess instance = BehaviorTreeRuntime.INSTANCE.startBound(mob);
        source.sendSuccess(() -> Component.translatable("geometry_node.command.behavior.started", instance.graphId(), mob.getName().getString(), instance.instanceId()), false);
        return true;
    }

    private static boolean suspend(CommandSourceStack source, Mob mob) {
        BehaviorTreeProcess instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        boolean changed = instance != null && BehaviorTreeRuntime.INSTANCE.suspend(
                source.getServer(), instance.instanceId());
        if (changed) sendLifecycle(source, mob, "Suspended");
        return changed;
    }

    private static boolean resume(CommandSourceStack source, Mob mob) {
        BehaviorTreeProcess instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        boolean changed = instance != null && BehaviorTreeRuntime.INSTANCE.resume(
                source.getServer(), instance.instanceId());
        if (changed) sendLifecycle(source, mob, "Resumed");
        return changed;
    }

    private static boolean stop(CommandSourceStack source, Mob mob) {
        BehaviorTreeProcess instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        boolean changed = instance != null && BehaviorTreeRuntime.INSTANCE.stop(source.getServer(),
                instance.instanceId(), BehaviorTerminationReason.TREE_STOPPED);
        if (changed) sendLifecycle(source, mob, "Stopped");
        return changed;
    }

    private static int unbind(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String graphId = StringArgumentType.getString(context, "graph_id");
        return runTargets(context, (source, mob) -> {
            boolean changed = BehaviorTreeRuntime.INSTANCE.unbind(mob, graphId);
            if (changed) source.sendSuccess(() -> Component.translatable("geometry_node.command.behavior.unbound", graphId, mob.getName().getString()), false);
            return changed;
        });
    }

    private static boolean unbindAll(CommandSourceStack source, Mob mob) {
        boolean changed = BehaviorTreeRuntime.INSTANCE.unbindAll(mob);
        if (changed) source.sendSuccess(() -> Component.translatable("geometry_node.command.behavior.unbound_all", mob.getName().getString()), false);
        return changed;
    }

    private static void sendLifecycle(CommandSourceStack source, Mob mob, String action) {
        source.sendSuccess(() -> Component.translatable("geometry_node.command.behavior.lifecycle." + action.toLowerCase(), mob.getName().getString()), false);
    }

    private static int runTargets(CommandContext<CommandSourceStack> context,
                                  TargetHandler handler) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "targets");
        int changed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob)) {
                context.getSource().sendFailure(Component.translatable("geometry_node.command.behavior.not_mob", entity.getName().getString()));
                continue;
            }
            try {
                if (handler.apply(context.getSource(), mob)) changed++;
            } catch (RuntimeException exception) {
                context.getSource().sendFailure(Component.translatable("geometry_node.command.behavior.error", mob.getName().getString(), exception.getMessage()));
            }
        }
        return changed;
    }

    @FunctionalInterface
    private interface TargetHandler {
        boolean apply(CommandSourceStack source, Mob mob);
    }
}
