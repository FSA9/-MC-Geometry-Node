package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.model.runtime.*;
import com.mine.geometry_node.client.model.render.integration.ModelIntegrationController;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.Set;

public final class LocalModelPreviewCommand {
    private static final ModelInstanceId SHARED_LEFT = new ModelInstanceId("geometry_node:preview_shared_left");
    private static final ModelInstanceId SHARED_RIGHT = new ModelInstanceId("geometry_node:preview_shared_right");
    private static final ModelInstanceId SKIN_LEFT = new ModelInstanceId("geometry_node:preview_skin_left");
    private static final ModelInstanceId SKIN_RIGHT = new ModelInstanceId("geometry_node:preview_skin_right");
    private static final Set<ModelInstanceId> TEST_INSTANCES = new java.util.LinkedHashSet<>();
    private static String benchmarkAsset = "";
    private static int benchmarkInstances;

    private LocalModelPreviewCommand() {}

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("model_preview")
                .then(LiteralArgumentBuilder.<S>literal("load")
                        .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.greedyString())
                                .executes(context -> load(StringArgumentType.getString(context, "path")))))
                .then(LiteralArgumentBuilder.<S>literal("clear").executes(context -> clear()))
                .then(LiteralArgumentBuilder.<S>literal("reload").executes(context -> reloadBindings()))
                .then(LiteralArgumentBuilder.<S>literal("status").executes(context -> status()))
                .then(pathCommand("shared", LocalModelPreviewCommand::spawnShared))
                .then(pathCommand("correctness", LocalModelPreviewCommand::spawnCorrectness))
                .then(LiteralArgumentBuilder.<S>literal("benchmark")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("instances", IntegerArgumentType.integer(1, 100))
                                .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.greedyString())
                                        .executes(context -> spawnBenchmark(IntegerArgumentType.getInteger(context, "instances"),
                                                StringArgumentType.getString(context, "path"))))))
                .then(LiteralArgumentBuilder.<S>literal("benchmark_begin").executes(context -> beginBenchmark()))
                .then(LiteralArgumentBuilder.<S>literal("hide")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("node", IntegerArgumentType.integer(0))
                                .executes(context -> setHiddenNode(IntegerArgumentType.getInteger(context, "node")))))
                .then(LiteralArgumentBuilder.<S>literal("show_all").executes(context -> showAllNodes()))
                .then(LiteralArgumentBuilder.<S>literal("remove_left").executes(context -> removeSharedLeft()))
                .then(LiteralArgumentBuilder.<S>literal("distance")
                        .then(RequiredArgumentBuilder.<S, Double>argument("blocks", DoubleArgumentType.doubleArg(0, 1024))
                                .executes(context -> setSharedDistance(DoubleArgumentType.getDouble(context, "blocks")))))
                .then(LiteralArgumentBuilder.<S>literal("lifetime")
                        .then(RequiredArgumentBuilder.<S, Double>argument("seconds", DoubleArgumentType.doubleArg(0.1, 3600))
                                .executes(context -> setSharedLifetime(DoubleArgumentType.getDouble(context, "seconds")))))
                .then(animationCommands())
                .then(pathCommand("skin", LocalModelPreviewCommand::spawnSkin))
                .then(LiteralArgumentBuilder.<S>literal("skin_play").executes(context -> playSkin())));
    }

    private static <S> LiteralArgumentBuilder<S> animationCommands() {
        return LiteralArgumentBuilder.<S>literal("animation")
                .then(LiteralArgumentBuilder.<S>literal("list").executes(context -> listAnimations()))
                .then(LiteralArgumentBuilder.<S>literal("status").executes(context -> animationStatus()))
                .then(LiteralArgumentBuilder.<S>literal("select")
                        .then(RequiredArgumentBuilder.<S, Integer>argument("index", IntegerArgumentType.integer(0))
                                .executes(context -> selectAnimation(IntegerArgumentType.getInteger(context, "index")))))
                .then(LiteralArgumentBuilder.<S>literal("play").executes(context -> animationAction("play",
                        registry -> registry.playAnimation(ClientModelRuntime.PREVIEW_INSTANCE_ID))))
                .then(LiteralArgumentBuilder.<S>literal("pause").executes(context -> animationAction("pause",
                        registry -> registry.pauseAnimation(ClientModelRuntime.PREVIEW_INSTANCE_ID))))
                .then(LiteralArgumentBuilder.<S>literal("stop").executes(context -> animationAction("stop",
                        registry -> registry.stopAnimation(ClientModelRuntime.PREVIEW_INSTANCE_ID))))
                .then(LiteralArgumentBuilder.<S>literal("reset").executes(context -> animationAction("reset",
                        registry -> registry.resetAnimation(ClientModelRuntime.PREVIEW_INSTANCE_ID))))
                .then(LiteralArgumentBuilder.<S>literal("seek")
                        .then(RequiredArgumentBuilder.<S, Double>argument("seconds", DoubleArgumentType.doubleArg(0))
                                .executes(context -> animationAction("seek", registry -> registry.seekAnimation(
                                        ClientModelRuntime.PREVIEW_INSTANCE_ID,
                                        (float) DoubleArgumentType.getDouble(context, "seconds"))))))
                .then(LiteralArgumentBuilder.<S>literal("speed")
                        .then(RequiredArgumentBuilder.<S, Double>argument("multiplier", DoubleArgumentType.doubleArg(0.001))
                                .executes(context -> animationAction("speed", registry -> registry.setAnimationSpeed(
                                        ClientModelRuntime.PREVIEW_INSTANCE_ID,
                                        (float) DoubleArgumentType.getDouble(context, "multiplier"))))))
                .then(LiteralArgumentBuilder.<S>literal("loop")
                        .then(RequiredArgumentBuilder.<S, Boolean>argument("enabled", BoolArgumentType.bool())
                                .executes(context -> animationAction("loop", registry -> registry.setAnimationLooping(
                                        ClientModelRuntime.PREVIEW_INSTANCE_ID,
                                        BoolArgumentType.getBool(context, "enabled"))))))
                .then(LiteralArgumentBuilder.<S>literal("reverse")
                        .then(RequiredArgumentBuilder.<S, Boolean>argument("enabled", BoolArgumentType.bool())
                                .executes(context -> animationAction("reverse", registry -> registry.setAnimationReverse(
                                        ClientModelRuntime.PREVIEW_INSTANCE_ID,
                                        BoolArgumentType.getBool(context, "enabled"))))));
    }

    private static <S> LiteralArgumentBuilder<S> pathCommand(String name, java.util.function.ToIntFunction<String> action) {
        return LiteralArgumentBuilder.<S>literal(name)
                .then(RequiredArgumentBuilder.<S, String>argument("path", StringArgumentType.greedyString())
                        .executes(context -> action.applyAsInt(StringArgumentType.getString(context, "path"))));
    }

    private static int load(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return 0;
        ModelInstancePlacement placement = previewPlacement(player.getEyePosition(), player.getLookAngle(), 7);
        ClientModelRuntime.INSTANCE.load(Path.of(path), placement);
        ClientCommandUtils.sendClientMsg("§aModel loading started with authored materials and sidedness: " + path);
        return 1;
    }

    private static ModelInstancePlacement previewPlacement(Vec3 eyePosition, Vec3 lookDirection, double distance) {
        Vec3 horizontal = new Vec3(lookDirection.x, 0, lookDirection.z);
        if (horizontal.lengthSqr() < 1.0E-6) horizontal = new Vec3(0, 0, 1);
        else horizontal = horizontal.normalize();
        Vec3 center = eyePosition.add(horizontal.scale(distance));
        Vector3f towardPlayer = new Vector3f((float) -horizontal.x, 0, (float) -horizontal.z);
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), towardPlayer);
        return new ModelInstancePlacement(new Vector3d(center.x, center.y, center.z), rotation, new Vector3f(1),
                false, false, 1, 1, 1, 1);
    }

    private static int clear() {
        ClientModelRuntime.INSTANCE.closeModel();
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        registry.remove(SHARED_LEFT);
        registry.remove(SHARED_RIGHT);
        registry.remove(SKIN_LEFT);
        registry.remove(SKIN_RIGHT);
        clearTestInstances();
        ClientCommandUtils.sendClientMsg("§aModel previews and test instances cleared.");
        return 1;
    }

    private static int reloadBindings() {
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        var before = runtime.gpuDiagnostics();
        long generation = ModelResourceReloadListener.reloadBindings();
        var after = runtime.gpuDiagnostics();
        ClientCommandUtils.sendClientMsg("§aModel material and shader bindings reloaded generation=" + generation
                + "; static GPU resources retained.");
        ClientCommandUtils.sendClientMsg("§eReload GPU before/after uploads=" + before.completedUploads() + '/'
                + after.completedUploads() + " liveResources=" + before.liveResources() + '/'
                + after.liveResources() + " bufferBytes=" + before.liveBufferBytes() + '/'
                + after.liveBufferBytes() + " textureBytes=" + before.liveTextureBytes() + '/'
                + after.liveTextureBytes());
        return 1;
    }

    private static int status() {
        LocalModelStatus status = ClientModelRuntime.INSTANCE.status();
        String details = status.failure().isEmpty() ? "" : " failure=" + status.failure();
        ClientCommandUtils.sendClientMsg("§eModel state=" + status.state() + " triangles=" + status.triangles()
                + " draws=" + status.drawCalls() + " loadMs=" + nanosToMs(status.loadNanos())
                + " renderCpuMs=" + nanosToMs(status.lastRenderCpuNanos())
                + " gpuMs=" + nanosToMs(status.lastGpuNanos()) + details);
        var integration = ModelIntegrationController.integrationStatus();
        ClientCommandUtils.sendClientMsg("§eModel integration requested=" + integration.requestedMode()
                + " effective=" + integration.effectiveMode() + " profile=" + integration.profileId()
                + " fidelity=" + integration.fidelity() + " verification=" + integration.verification()
                + " generation=" + integration.generation()
                + (integration.semanticLosses().isEmpty() ? "" : " losses=" + integration.semanticLosses())
                + (integration.runtimeFaults().isEmpty() ? "" : " runtimeFaults=" + integration.runtimeFaults())
                + (integration.rejectedDraws().isEmpty() ? "" : " rejectedDraws=" + integration.rejectedDraws())
                + (integration.fallback() == com.mine.geometry_node.client.model.render.integration.ModelIntegrationFallback.NONE
                ? "" : " fallback=" + integration.fallback() + " detail=" + integration.fallbackDetail()));
        ClientCommandUtils.sendClientMsg("§eModel integration capabilities=" + integration.capabilities());
        ClientCommandUtils.sendClientMsg("§eModel binding reload generation="
                + ModelResourceReloadListener.reloadGeneration());
        statusShared();
        statusSkin();
        statusTests();
        return 1;
    }

    private static int spawnShared(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return 0;
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 lateral = forward.cross(new Vec3(0, 1, 0));
        if (lateral.lengthSqr() < 1.0E-6) lateral = new Vec3(1, 0, 0);
        else lateral = lateral.normalize();
        Vec3 center = player.getEyePosition().add(forward.scale(5));
        Path modelPath = Path.of(path);
        ModelDimensionId dimension = currentDimension();
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        registry.upsertLocal(SHARED_LEFT, modelPath, new ModelInstanceState(dimension,
                placement(center.add(lateral.scale(1.5)), 0.85F, 0.55F, 1.0F, 0.65F),
                true, 0, 0, ModelInstanceNodeState.IDENTITY));
        registry.upsertLocal(SHARED_RIGHT, modelPath, new ModelInstanceState(dimension,
                placement(center.add(lateral.scale(-1.5)), 1.15F, 1.0F, 0.62F, 0.3F),
                true, 0, 0, ModelInstanceNodeState.IDENTITY));
        ClientCommandUtils.sendClientMsg("§aShared-resource preview loading: " + path);
        return 1;
    }

    private static int statusShared() {
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        ClientModelInstanceRegistry registry = runtime.instances();
        ClientModelInstanceRegistry.InstanceStatus left = registry.status(SHARED_LEFT);
        ClientModelInstanceRegistry.InstanceStatus right = registry.status(SHARED_RIGHT);
        if (left.state() == ModelLoadState.CLOSED && right.state() == ModelLoadState.CLOSED) return 1;
        boolean shared = left.resource() != null && left.resource() == right.resource();
        ClientCommandUtils.sendClientMsg("§eShared instances=" + registry.size()
                + " resources=" + runtime.resourceCount() + " draws=" + runtime.status().drawCalls()
                + " submittedTriangles=" + runtime.status().submittedTriangles()
                + " singularSkips=" + runtime.status().singularTransformSkips()
                + " left=" + left.state() + " right=" + right.state() + " shared=" + shared);
        appendFailure("left", left);
        appendFailure("right", right);
        return 1;
    }

    private static int setHiddenNode(int nodeIndex) {
        return updateRight(state -> new ModelInstanceState(state.dimension(), state.placement(), state.visible(),
                state.maxDistance(), state.expiresAtNanos(), new ModelInstanceNodeState(Set.of(nodeIndex),
                state.nodeState().revision() + 1)), "Right instance now hides node " + nodeIndex + '.');
    }

    private static int showAllNodes() {
        return updateRight(state -> new ModelInstanceState(state.dimension(), state.placement(), state.visible(),
                state.maxDistance(), state.expiresAtNanos(), new ModelInstanceNodeState(Set.of(),
                state.nodeState().revision() + 1)), "Right instance shows all nodes.");
    }

    private static int setSharedDistance(double blocks) {
        return updateRight(state -> new ModelInstanceState(state.dimension(), state.placement(), state.visible(),
                blocks, state.expiresAtNanos(), state.nodeState()),
                "Right instance maximum distance set to " + blocks + " blocks.");
    }

    private static int setSharedLifetime(double seconds) {
        long deadline = System.nanoTime() + (long) (seconds * 1_000_000_000L);
        return updateRight(state -> new ModelInstanceState(state.dimension(), state.placement(), state.visible(),
                state.maxDistance(), deadline, state.nodeState()),
                "Right instance will expire in " + seconds + " seconds.");
    }

    private static int removeSharedLeft() {
        ClientModelRuntime.INSTANCE.instances().remove(SHARED_LEFT);
        ClientCommandUtils.sendClientMsg("§aLeft shared instance removed.");
        return 1;
    }

    private static int statusSkin() {
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        ClientModelInstanceRegistry registry = runtime.instances();
        var left = registry.status(SKIN_LEFT);
        var right = registry.status(SKIN_RIGHT);
        if (left.state() == ModelLoadState.CLOSED && right.state() == ModelLoadState.CLOSED) return 1;
        boolean shared = left.resource() != null && left.resource() == right.resource();
        ClientCommandUtils.sendClientMsg("§eSkin left=" + left.state() + " right=" + right.state()
                + " shared=" + shared + " resources=" + runtime.resourceCount()
                + " draws=" + runtime.status().drawCalls()
                + " submittedTriangles=" + runtime.status().submittedTriangles()
                + " singularSkips=" + runtime.status().singularTransformSkips());
        appendFailure("skin-left", left);
        appendFailure("skin-right", right);
        return 1;
    }

    private static int listAnimations() {
        ModelInstancePose pose = previewPose();
        if (pose == null) return 0;
        if (pose.animations().isEmpty()) {
            ClientCommandUtils.sendClientMsg("§eThe loaded model has no animations.");
            return 1;
        }
        for (int index = 0; index < pose.animations().size(); index++) {
            var animation = pose.animations().get(index);
            ClientCommandUtils.sendClientMsg("§e[" + index + "] " + animation.name()
                    + " duration=" + animation.durationSeconds() + "s channels=" + animation.channels().size());
        }
        return 1;
    }

    private static int animationStatus() {
        ModelInstancePose pose = previewPose();
        if (pose == null) return 0;
        ClientCommandUtils.sendClientMsg("§eAnimation selected=" + pose.animationIndex()
                + " state=" + pose.playbackState() + " time=" + pose.timeSeconds() + "/" + pose.durationSeconds()
                + " speed=" + pose.speed() + " loop=" + pose.looping() + " reverse=" + pose.reverse()
                + " poseRevision=" + pose.revision());
        statusTests();
        return 1;
    }

    private static int selectAnimation(int index) {
        return animationAction("select " + index,
                registry -> registry.selectAnimation(ClientModelRuntime.PREVIEW_INSTANCE_ID, index));
    }

    private static int animationAction(String action,
                                       java.util.function.Predicate<ClientModelInstanceRegistry> operation) {
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        try {
            if (!operation.test(registry)) {
                ClientCommandUtils.sendClientMsg("§cThe local preview is not READY. Load a model first.");
                return 0;
            }
            ClientCommandUtils.sendClientMsg("§aAnimation " + action + " applied.");
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ClientCommandUtils.sendClientMsg("§cAnimation " + action + " failed: " + exception.getMessage());
            return 0;
        }
    }

    private static ModelInstancePose previewPose() {
        ModelInstancePose pose = ClientModelRuntime.INSTANCE.instances()
                .instancePose(ClientModelRuntime.PREVIEW_INSTANCE_ID);
        if (pose == null) ClientCommandUtils.sendClientMsg("§cThe local preview is not READY. Load a model first.");
        return pose;
    }

    private static int spawnSkin(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return 0;
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        registry.remove(SKIN_LEFT);
        registry.remove(SKIN_RIGHT);
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 lateral = forward.cross(new Vec3(0, 1, 0));
        if (lateral.lengthSqr() < 1.0E-6) lateral = new Vec3(1, 0, 0); else lateral = lateral.normalize();
        Vec3 center = player.getEyePosition().add(forward.scale(6));
        Path model = Path.of(path);
        ModelDimensionId dimension = currentDimension();
        registry.upsertLocal(SKIN_LEFT, model, new ModelInstanceState(dimension,
                placement(center.add(lateral.scale(-1.1)), 1, 1, 1, 1), true, 0, 0,
                ModelInstanceNodeState.IDENTITY));
        registry.upsertLocal(SKIN_RIGHT, model, new ModelInstanceState(dimension,
                placement(center.add(lateral.scale(1.1)), 1, 1, 1, 1), true, 0, 0,
                ModelInstanceNodeState.IDENTITY));
        ClientCommandUtils.sendClientMsg("§aSkin acceptance instances loading: " + path);
        ClientCommandUtils.sendClientMsg("§eWait for READY, run /model_preview status, then /model_preview skin_play.");
        return 1;
    }

    private static int playSkin() {
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        try {
            if (!registry.selectAnimation(SKIN_RIGHT, 0) || !registry.setAnimationLooping(SKIN_RIGHT, true)
                    || !registry.playAnimation(SKIN_RIGHT)) {
                ClientCommandUtils.sendClientMsg("§cSkin instances are not READY. Run /model_preview skin <path> first.");
                return 0;
            }
            ClientCommandUtils.sendClientMsg("§aRight skin instance is playing animation 0; left remains at rest.");
            return 1;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            ClientCommandUtils.sendClientMsg("§cSkin playback failed: " + exception.getMessage());
            return 0;
        }
    }

    private static int spawnCorrectness(String path) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return 0;
        clearTestInstances();
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 lateral = forward.cross(new Vec3(0, 1, 0));
        if (lateral.lengthSqr() < 1.0E-6) lateral = new Vec3(1, 0, 0); else lateral = lateral.normalize();
        Vec3 center = player.getEyePosition().add(forward.scale(6));
        Path model = Path.of(path);
        addTest("world_nonuniform", model, center.add(lateral.scale(3)),
                new Vector3f(0.7F, 1.4F, 0.9F), false, false, 1, 1, 1, 1);
        addTest("fullbright", model, center.add(lateral),
                new Vector3f(1), true, false, 1, 1, 1, 1);
        addTest("tint_alpha", model, center.add(lateral.scale(-1)),
                new Vector3f(1), false, false, 0.45F, 0.8F, 1, 0.55F);
        addTest("mirrored", model, center.add(lateral.scale(-3)),
                new Vector3f(-1, 1, 1), false, false, 1, 1, 1, 1);
        ClientCommandUtils.sendClientMsg("§aCorrectness scene loading: world-lit nonuniform, full-bright, tinted alpha, mirrored single-sided.");
        return 1;
    }

    private static int spawnBenchmark(int count, String path) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || minecraft.level == null) return 0;
        clearTestInstances();
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 lateral = forward.cross(new Vec3(0, 1, 0));
        if (lateral.lengthSqr() < 1.0E-6) lateral = new Vec3(1, 0, 0); else lateral = lateral.normalize();
        Vec3 origin = player.getEyePosition().add(forward.scale(20));
        Path model = Path.of(path);
        int columns = (int) Math.ceil(Math.sqrt(count));
        for (int index = 0; index < count; index++) {
            double x = (index % columns - (columns - 1) * 0.5) * 2.0;
            double y = ((columns - 1) * 0.5 - index / columns) * 2.0;
            addTest("bench_" + index, model, origin.add(lateral.scale(x)).add(0, y, 0), new Vector3f(1),
                    true, false, 1, 1, 1, 1);
        }
        benchmarkAsset = path;
        benchmarkInstances = count;
        ClientCommandUtils.sendClientMsg("§aBenchmark loading: " + count + " instances share " + path);
        ClientCommandUtils.sendClientMsg("§eWait for READY, then run /model_preview benchmark_begin.");
        return 1;
    }

    private static int beginBenchmark() {
        if (benchmarkInstances < 1 || TEST_INSTANCES.size() != benchmarkInstances) {
            ClientCommandUtils.sendClientMsg("§cRun /model_preview benchmark <instances> <path> first.");
            return 0;
        }
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        LoadedModelResource shared = null;
        for (ModelInstanceId id : TEST_INSTANCES) {
            var status = registry.status(id);
            if (status.state() != ModelLoadState.READY || status.resource() == null) {
                ClientCommandUtils.sendClientMsg("§cAll benchmark instances must be READY before sampling.");
                return 0;
            }
            if (shared == null) shared = status.resource();
            else if (shared != status.resource()) {
                ClientCommandUtils.sendClientMsg("§cBenchmark instances do not share one loaded resource.");
                return 0;
            }
        }
        ClientModelRuntime.INSTANCE.beginBenchmark(benchmarkAsset, benchmarkInstances, 120, 600);
        ClientCommandUtils.sendClientMsg("§aBenchmark sampling started: 120 warmup + 600 measured frames.");
        return 1;
    }

    private static int statusTests() {
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        reconcileTestSession(runtime.instances());
        var gpu = runtime.gpuDiagnostics();
        var benchmark = runtime.benchmarkSnapshot();
        long ready = TEST_INSTANCES.stream().filter(id ->
                runtime.instances().status(id).state() == ModelLoadState.READY).count();
        LoadedModelResource sampleResource = TEST_INSTANCES.stream().map(runtime.instances()::status)
                .map(ClientModelInstanceRegistry.InstanceStatus::resource).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        ClientCommandUtils.sendClientMsg("§eTest instances=" + TEST_INSTANCES.size() + " ready=" + ready
                + " resources=" + runtime.resourceCount() + " draws=" + runtime.status().drawCalls()
                + " assetTriangles=" + (sampleResource == null ? 0 : sampleResource.triangles())
                + " submittedTriangles=" + runtime.status().submittedTriangles()
                + " singularSkips=" + runtime.status().singularTransformSkips()
                + " uploads=" + gpu.completedUploads() + "/" + gpu.uploadAttempts()
                + " uploadFailures=" + gpu.failedUploads() + " uploadCancelled=" + gpu.cancelledUploads()
                + " liveGpuResources=" + gpu.liveResources()
                + " gpuEntries=" + gpu.repositoryEntries() + " pendingUploads=" + gpu.pendingUploads()
                + " liveBufferBytes=" + gpu.liveBufferBytes() + " liveTextureBytes=" + gpu.liveTextureBytes()
                + " released=" + gpu.releasedResources() + " renderCpuMs="
                + nanosToMs(runtime.status().lastRenderCpuNanos()) + " gpuMs="
                + nanosToMs(runtime.status().lastGpuNanos()));
        if (benchmark != null) {
            ClientCommandUtils.sendClientMsg("§eBenchmark sample=" + benchmark.cpuSamples() + "/" + benchmark.targetFrames()
                    + " warmup=" + benchmark.warmupFrames() + " cpuComplete=" + benchmark.cpuComplete()
                    + " gpuComplete=" + benchmark.gpuComplete()
                    + " avgDraws=" + benchmark.averageDraws()
                    + " avgSubmittedTriangles=" + benchmark.averageSubmittedTriangles()
                    + " cpu(mean/p95/max)="
                    + nanosToMs(benchmark.cpu().meanNanos()) + "/" + nanosToMs(benchmark.cpu().p95Nanos())
                    + "/" + nanosToMs(benchmark.cpu().maxNanos()) + " gpuSamples=" + benchmark.gpuSamples()
                    + " gpu(mean/p95/max)=" + nanosToMs(benchmark.gpu().meanNanos()) + "/"
                    + nanosToMs(benchmark.gpu().p95Nanos()) + "/" + nanosToMs(benchmark.gpu().maxNanos())
                    + " asset=" + benchmark.asset());
        }
        return 1;
    }

    private static void addTest(String suffix, Path path, Vec3 position, Vector3f scale,
                              boolean fullBright, boolean forceDoubleSided,
                              float red, float green, float blue, float alpha) {
        ModelInstanceId id = new ModelInstanceId("geometry_node:preview_test_" + suffix);
        TEST_INSTANCES.add(id);
        ClientModelRuntime.INSTANCE.instances().upsertLocal(id, path, new ModelInstanceState(currentDimension(),
                new ModelInstancePlacement(new Vector3d(position.x, position.y, position.z), new Quaternionf(), scale,
                        fullBright, forceDoubleSided, red, green, blue, alpha),
                true, 0, 0, ModelInstanceNodeState.IDENTITY));
    }

    private static void clearTestInstances() {
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        for (ModelInstanceId id : TEST_INSTANCES) registry.remove(id);
        TEST_INSTANCES.clear();
        ClientModelRuntime.INSTANCE.cancelBenchmark();
        benchmarkAsset = "";
        benchmarkInstances = 0;
    }

    private static void reconcileTestSession(ClientModelInstanceRegistry registry) {
        TEST_INSTANCES.removeIf(id -> registry.status(id).state() == ModelLoadState.CLOSED);
        if (TEST_INSTANCES.isEmpty()) {
            ClientModelRuntime.INSTANCE.cancelBenchmark();
            benchmarkAsset = "";
            benchmarkInstances = 0;
        }
    }

    private static int updateRight(java.util.function.UnaryOperator<ModelInstanceState> update, String success) {
        ClientModelInstanceRegistry registry = ClientModelRuntime.INSTANCE.instances();
        ModelInstanceState current = registry.instanceState(SHARED_RIGHT);
        if (current == null) {
            ClientCommandUtils.sendClientMsg("§cRun /model_preview shared <path> first.");
            return 0;
        }
        registry.updateState(SHARED_RIGHT, update.apply(current));
        ClientCommandUtils.sendClientMsg("§a" + success);
        return 1;
    }

    private static ModelInstancePlacement placement(Vec3 position, float scale, float red, float green, float blue) {
        return new ModelInstancePlacement(new Vector3d(position.x, position.y, position.z), new Quaternionf(),
                new Vector3f(scale), true, false, red, green, blue, 1);
    }

    private static ModelDimensionId currentDimension() {
        return new ModelDimensionId(Minecraft.getInstance().level.dimension().identifier().toString());
    }

    private static void appendFailure(String name, ClientModelInstanceRegistry.InstanceStatus status) {
        if (!status.failure().isEmpty()) ClientCommandUtils.sendClientMsg("§c" + name + " failure=" + status.failure());
    }

    private static String nanosToMs(long nanos) { return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0); }
}
