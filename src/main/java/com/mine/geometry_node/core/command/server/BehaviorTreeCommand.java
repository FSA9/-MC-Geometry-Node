package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.behavior.BehaviorTreeRuntime;
import com.mine.geometry_node.core.engine.behavior.contract.BehaviorTerminationReason;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorTreeInstance;
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
                .then(targetOperation("unbind", BehaviorTreeCommand::unbind))
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
            BehaviorTreeRuntime.INSTANCE.bind(mob, graphId);
            source.sendSuccess(() -> Component.literal("Bound " + graphId + " to "
                    + mob.getName().getString()), false);
            return true;
        });
    }

    private static int switchTree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String graphId = StringArgumentType.getString(context, "graph_id");
        return runTargets(context, (source, mob) -> {
            BehaviorTreeInstance instance = BehaviorTreeRuntime.INSTANCE.switchTo(mob, graphId);
            source.sendSuccess(() -> Component.literal("Switched " + mob.getName().getString()
                    + " to " + graphId + " (" + instance.instanceId() + ")"), false);
            return true;
        });
    }

    private static boolean status(CommandSourceStack source, Mob mob) {
        BehaviorTreeInstance instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        String bound = BehaviorTreeRuntime.INSTANCE.boundGraph(mob);
        source.sendSuccess(() -> Component.literal(mob.getName().getString()
                + ": bound=" + (bound != null ? bound : "none")
                + ", runtime=" + (instance != null
                ? instance.state() + " (" + instance.instanceId() + ")" : "none")), false);
        return true;
    }

    private static boolean start(CommandSourceStack source, Mob mob) {
        BehaviorTreeInstance instance = BehaviorTreeRuntime.INSTANCE.startBound(mob);
        source.sendSuccess(() -> Component.literal("Started " + instance.graphId() + " on "
                + mob.getName().getString() + " (" + instance.instanceId() + ")"), false);
        return true;
    }

    private static boolean suspend(CommandSourceStack source, Mob mob) {
        BehaviorTreeInstance instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        boolean changed = instance != null && BehaviorTreeRuntime.INSTANCE.suspend(
                source.getServer(), instance.instanceId());
        if (changed) sendLifecycle(source, mob, "Suspended");
        return changed;
    }

    private static boolean resume(CommandSourceStack source, Mob mob) {
        BehaviorTreeInstance instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        boolean changed = instance != null && BehaviorTreeRuntime.INSTANCE.resume(
                source.getServer(), instance.instanceId());
        if (changed) sendLifecycle(source, mob, "Resumed");
        return changed;
    }

    private static boolean stop(CommandSourceStack source, Mob mob) {
        BehaviorTreeInstance instance = BehaviorTreeRuntime.INSTANCE.getForOwner(mob);
        boolean changed = instance != null && BehaviorTreeRuntime.INSTANCE.stop(source.getServer(),
                instance.instanceId(), BehaviorTerminationReason.TREE_STOPPED);
        if (changed) sendLifecycle(source, mob, "Stopped");
        return changed;
    }

    private static boolean unbind(CommandSourceStack source, Mob mob) {
        boolean changed = BehaviorTreeRuntime.INSTANCE.unbind(mob);
        if (changed) sendLifecycle(source, mob, "Unbound");
        return changed;
    }

    private static void sendLifecycle(CommandSourceStack source, Mob mob, String action) {
        source.sendSuccess(() -> Component.literal(action + " behavior tree on "
                + mob.getName().getString()), false);
    }

    private static int runTargets(CommandContext<CommandSourceStack> context,
                                  TargetHandler handler) throws CommandSyntaxException {
        Collection<? extends Entity> entities = EntityArgument.getEntities(context, "targets");
        int changed = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob)) {
                context.getSource().sendFailure(Component.literal(
                        entity.getName().getString() + " is not a Mob"));
                continue;
            }
            try {
                if (handler.apply(context.getSource(), mob)) changed++;
            } catch (RuntimeException exception) {
                context.getSource().sendFailure(Component.literal(mob.getName().getString()
                        + ": " + exception.getMessage()));
            }
        }
        return changed;
    }

    @FunctionalInterface
    private interface TargetHandler {
        boolean apply(CommandSourceStack source, Mob mob);
    }
}
