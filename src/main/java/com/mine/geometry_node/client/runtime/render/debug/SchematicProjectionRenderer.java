package com.mine.geometry_node.client.runtime.render.debug;

import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.schematic.SchematicBlockEntityUtils;
import com.mine.geometry_node.core.schematic.LegacySchematicBlockStateMapper;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class SchematicProjectionRenderer {
    private static final int MAX_ACTIVE_PROJECTIONS = 64;
    private static final long MAX_TOTAL_GEOMETRY = 524_288L;
    private static final long MAX_TOTAL_NBT_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_TOTAL_ESTIMATED_BYTES = 64L * 1024L * 1024L;
    private static final int WHITE = 255;
    private static final int BOUNDS_FACE_ALPHA = 38;
    private static final int BOUNDS_LINE_ALPHA = 210;
    private static final float BOUNDS_LINE_WIDTH = 2.0f;
    private static final int FALLBACK_FACE_ALPHA_SCALE = 130;
    private static final int FALLBACK_EDGE_ALPHA_SCALE = 210;
    private static final float EDGE_WIDTH = 1.2f;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Map<String, Projection> PROJECTIONS = new LinkedHashMap<>();
    private static ModelBlockRenderer modelRenderer;
    private static FluidRenderer fluidRenderer;
    private static Object fluidModelSet;

    private SchematicProjectionRenderer() {
    }

    public static void handleProjection(PacketSchematicProjection packet) {
        if (packet == null || packet.resourceId().isBlank()) {
            return;
        }
        String cacheKey = packet.resourceId();
        if (packet.blocks().isEmpty() && packet.blockEntities().isEmpty() && packet.entities().isEmpty()) {
            Projection removed = PROJECTIONS.remove(cacheKey);
            if (removed != null) {
                removed.close();
            }
            return;
        }
        long geometry = projectionGeometry(packet);
        long nbtBytes = projectionNbtBytes(packet);
        long estimatedBytes = projectionEstimatedBytes(packet);
        if (geometry > MAX_TOTAL_GEOMETRY || nbtBytes > MAX_TOTAL_NBT_BYTES
                || estimatedBytes > MAX_TOTAL_ESTIMATED_BYTES) return;
        while (!fitsProjectionBudget(cacheKey, geometry, nbtBytes, estimatedBytes)) {
            if (!evictOldestProjectionOtherThan(cacheKey)) return;
        }

        Projection previous = PROJECTIONS.remove(cacheKey);
        if (previous != null) {
            previous.close();
        }
        Projection replacement = new Projection(packet, Minecraft.getInstance().level,
                geometry, nbtBytes, estimatedBytes);
        PROJECTIONS.put(cacheKey, replacement);
    }

    private static boolean fitsProjectionBudget(String replacedKey, long geometry, long nbtBytes,
                                                long estimatedBytes) {
        int count = 1;
        long totalGeometry = geometry;
        long totalNbtBytes = nbtBytes;
        long totalEstimatedBytes = estimatedBytes;
        for (Map.Entry<String, Projection> entry : PROJECTIONS.entrySet()) {
            if (entry.getKey().equals(replacedKey)) continue;
            count++;
            totalGeometry = saturatingAdd(totalGeometry, entry.getValue().budgetGeometry);
            totalNbtBytes = saturatingAdd(totalNbtBytes, entry.getValue().budgetNbtBytes);
            totalEstimatedBytes = saturatingAdd(
                    totalEstimatedBytes, entry.getValue().budgetEstimatedBytes);
        }
        return count <= MAX_ACTIVE_PROJECTIONS && totalGeometry <= MAX_TOTAL_GEOMETRY
                && totalNbtBytes <= MAX_TOTAL_NBT_BYTES
                && totalEstimatedBytes <= MAX_TOTAL_ESTIMATED_BYTES;
    }

    private static boolean evictOldestProjectionOtherThan(String protectedKey) {
        Iterator<Map.Entry<String, Projection>> iterator = PROJECTIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Projection> entry = iterator.next();
            if (entry.getKey().equals(protectedKey)) continue;
            iterator.remove();
            entry.getValue().close();
            return true;
        }
        return false;
    }

    private static long projectionGeometry(PacketSchematicProjection packet) {
        return saturatingAdd(packet.blocks().size(),
                saturatingAdd(packet.blockEntities().size(), packet.entities().size()));
    }

    private static long projectionNbtBytes(PacketSchematicProjection packet) {
        long bytes = 0L;
        for (PacketSchematicProjection.BlockEntity blockEntity : packet.blockEntities()) {
            bytes = saturatingAdd(bytes, blockEntity.tag().sizeInBytes());
        }
        for (PacketSchematicProjection.Entity entity : packet.entities()) {
            bytes = saturatingAdd(bytes, entity.tag().sizeInBytes());
        }
        return bytes;
    }

    private static long projectionEstimatedBytes(PacketSchematicProjection packet) {
        long bytes = 128L;
        bytes = saturatingAdd(bytes, (long) packet.resourceId().length() * 2L);
        bytes = saturatingAdd(bytes, (long) packet.graphId().length() * 2L);
        bytes = saturatingAdd(bytes, (long) packet.dimension().length() * 2L);
        for (String state : packet.states()) {
            bytes = saturatingAdd(bytes, 4L + (long) (state == null ? 0 : state.length()) * 2L);
        }
        bytes = saturatingAdd(bytes, (long) packet.blocks().size() * 20L);
        for (PacketSchematicProjection.BlockEntity blockEntity : packet.blockEntities()) {
            bytes = saturatingAdd(bytes, 16L + blockEntity.tag().sizeInBytes());
        }
        for (PacketSchematicProjection.Entity entity : packet.entities()) {
            bytes = saturatingAdd(bytes, 28L + entity.tag().sizeInBytes());
        }
        return bytes;
    }

    private static long saturatingAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    public static void clear() {
        for (Projection projection : PROJECTIONS.values()) {
            projection.close();
        }
        PROJECTIONS.clear();
    }

    public static void render(PoseStack poseStack, Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }

        String dimension = level.dimension().identifier().toString();
        if (PROJECTIONS.isEmpty()) return;

        removeExpired(level.getGameTime());
        if (PROJECTIONS.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 camPos = camera.position();
        double maxRenderDistanceSqr = maxProjectionRenderDistanceSqr(minecraft);
        Frustum frustum = camera.getCullFrustum();

        for (Projection projection : PROJECTIONS.values()) {
            if (shouldRenderProjection(projection, dimension, camPos, maxRenderDistanceSqr, frustum)) {
                projection.renderBaked(minecraft, camPos);
            }
        }

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer fallbackFaces = bufferSource.getBuffer(GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_FACE);
        for (Projection projection : PROJECTIONS.values()) {
            if (shouldRenderProjection(projection, dimension, camPos, maxRenderDistanceSqr, frustum)) {
                drawFallbackFaces(projection, fallbackFaces, matrix, camPos);
            }
        }
        bufferSource.endBatch(GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_FACE);

        VertexConsumer boundsFaces = bufferSource.getBuffer(GeometryDebugRenderTypes.GEOMETRY_FACE);
        for (Projection projection : PROJECTIONS.values()) {
            if (shouldRenderProjection(projection, dimension, camPos, maxRenderDistanceSqr, frustum)) {
                drawProjectionBoundsFaces(projection, boundsFaces, matrix, camPos);
            }
        }
        bufferSource.endBatch(GeometryDebugRenderTypes.GEOMETRY_FACE);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer fallbackLines = bufferSource.getBuffer(GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_LINE);
        for (Projection projection : PROJECTIONS.values()) {
            if (shouldRenderProjection(projection, dimension, camPos, maxRenderDistanceSqr, frustum)) {
                drawFallbackEdges(projection, fallbackLines, pose, matrix, camPos);
            }
        }
        bufferSource.endBatch(GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_LINE);

        VertexConsumer boundsLines = bufferSource.getBuffer(GeometryDebugRenderTypes.GEOMETRY_LINE);
        for (Projection projection : PROJECTIONS.values()) {
            if (shouldRenderProjection(projection, dimension, camPos, maxRenderDistanceSqr, frustum)) {
                drawProjectionBoundsLines(projection, boundsLines, pose, matrix, camPos);
            }
        }
        poseStack.popPose();
        bufferSource.endBatch(GeometryDebugRenderTypes.GEOMETRY_LINE);
    }

    public static void submitFeatures(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, LevelRenderState levelRenderState) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return;
        }

        if (PROJECTIONS.isEmpty() || submitNodeCollector == null || levelRenderState == null) return;

        removeExpired(level.getGameTime());
        if (PROJECTIONS.isEmpty()) return;

        CameraRenderState cameraRenderState = levelRenderState.cameraRenderState;
        if (cameraRenderState == null) return;

        String dimension = level.dimension().identifier().toString();
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxRenderDistanceSqr = maxProjectionRenderDistanceSqr(minecraft);

        BlockEntityRenderDispatcher blockEntityDispatcher = minecraft.getBlockEntityRenderDispatcher();
        blockEntityDispatcher.prepare(cameraRenderState.pos);

        EntityRenderDispatcher entityDispatcher = minecraft.getEntityRenderDispatcher();
        entityDispatcher.prepare(minecraft.gameRenderer.getMainCamera(), minecraft.getCameraEntity());

        for (Projection projection : PROJECTIONS.values()) {
            if (!shouldRenderProjection(projection, dimension, cameraRenderState.pos, maxRenderDistanceSqr, cameraRenderState.cullFrustum)) {
                continue;
            }
            submitBlockEntities(projection, blockEntityDispatcher, poseStack, submitNodeCollector, cameraRenderState, partialTick);
            submitEntities(projection, entityDispatcher, poseStack, submitNodeCollector, cameraRenderState, partialTick);
        }
    }

    private static boolean shouldRenderProjection(Projection projection, String dimension, Vec3 camPos, double maxRenderDistanceSqr, Frustum frustum) {
        return dimension.equals(projection.dimension)
                && projection.closeEnoughTo(camPos, maxRenderDistanceSqr)
                && projection.visibleIn(frustum);
    }

    private static void removeExpired(long gameTime) {
        PROJECTIONS.entrySet().removeIf(entry -> {
            if (!entry.getValue().expired(gameTime)) {
                return false;
            }
            entry.getValue().close();
            return true;
        });
    }

    private static double maxProjectionRenderDistanceSqr(Minecraft minecraft) {
        double distance = (minecraft.options.getEffectiveRenderDistance() + 1) * 16.0 + 32.0;
        return distance * distance;
    }

    private static ModelBlockRenderer modelRenderer(Minecraft minecraft) {
        if (modelRenderer == null) {
            modelRenderer = new ModelBlockRenderer(false, true, minecraft.getBlockColors());
        }
        return modelRenderer;
    }

    private static FluidRenderer fluidRenderer(Minecraft minecraft) {
        Object modelSet = minecraft.getModelManager().getFluidStateModelSet();
        if (fluidRenderer == null || fluidModelSet != modelSet) {
            fluidModelSet = modelSet;
            fluidRenderer = new FluidRenderer(minecraft.getModelManager().getFluidStateModelSet());
        }
        return fluidRenderer;
    }

    private static BakedProjectionGeometry bakeStaticGeometry(Minecraft minecraft, ClientLevel level, Projection projection) {
        if (level == null || (projection.texturedBlocks.isEmpty() && projection.fluidBlocks.isEmpty())) {
            return BakedProjectionGeometry.EMPTY;
        }

        Map<RenderType, LayerBuilder> builders = new HashMap<>();
        ProjectionBlockGetter blockGetter = new ProjectionBlockGetter(level, projection);
        int alpha = alphaByte(projection.alpha);
        try {
            bakeTexturedBlocks(minecraft, projection, blockGetter, builders, alpha);
            bakeFluids(minecraft, projection, blockGetter, builders, alpha);
            return uploadBakedGeometry(builders);
        } finally {
            for (LayerBuilder builder : builders.values()) {
                builder.close();
            }
        }
    }

    private static void bakeTexturedBlocks(Minecraft minecraft,
                                           Projection projection,
                                           ProjectionBlockGetter blockGetter,
                                           Map<RenderType, LayerBuilder> builders,
                                           int alpha) {
        if (projection.texturedBlocks.isEmpty()) {
            return;
        }

        ModelBlockRenderer renderer = modelRenderer(minecraft);
        BlockQuadOutput output = new ProjectionBakeBlockOutput(builders, alpha);

        BlockModelLighter.enableCaching();
        try {
            for (RenderBlock block : projection.texturedBlocks) {
                BlockState state = block.state();
                BlockPos worldPos = block.worldPos();
                BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
                if (model == null) {
                    continue;
                }
                float x = (float) (worldPos.getX() - projection.originX);
                float y = (float) (worldPos.getY() - projection.originY);
                float z = (float) (worldPos.getZ() - projection.originZ);
                try {
                    renderer.tesselateBlock(output, x, y, z, blockGetter, worldPos, state, model, block.seed());
                } catch (Exception ignored) {
                    // Bad or missing third-party models should not crash the projection layer.
                }
            }
        } finally {
            BlockModelLighter.clearCache();
        }
    }

    private static void bakeFluids(Minecraft minecraft,
                                   Projection projection,
                                   ProjectionBlockGetter blockGetter,
                                   Map<RenderType, LayerBuilder> builders,
                                   int alpha) {
        if (projection.fluidBlocks.isEmpty()) {
            return;
        }

        FluidRenderer renderer = fluidRenderer(minecraft);
        for (RenderFluidBlock block : projection.fluidBlocks) {
            BlockPos worldPos = block.worldPos();
            double offsetX = (worldPos.getX() & ~15) - projection.originX;
            double offsetY = (worldPos.getY() & ~15) - projection.originY;
            double offsetZ = (worldPos.getZ() & ~15) - projection.originZ;
            ProjectionBakeFluidOutput output = new ProjectionBakeFluidOutput(builders, alpha, offsetX, offsetY, offsetZ);
            try {
                renderer.tesselate(blockGetter, worldPos, output, block.state(), block.fluidState());
            } catch (Exception ignored) {
                // Bad third-party fluid models should not crash the projection layer.
            }
        }
    }

    private static BakedProjectionGeometry uploadBakedGeometry(Map<RenderType, LayerBuilder> builders) {
        if (builders.isEmpty()) {
            return BakedProjectionGeometry.EMPTY;
        }

        ArrayList<BakedLayer> layers = new ArrayList<>();
        for (Map.Entry<RenderType, LayerBuilder> entry : builders.entrySet()) {
            MeshData mesh = entry.getValue().build();
            if (mesh == null) {
                continue;
            }
            try {
                BakedLayer layer = BakedLayer.upload(entry.getKey(), mesh);
                if (layer != null) {
                    layers.add(layer);
                }
            } finally {
                mesh.close();
            }
        }
        if (layers.isEmpty()) {
            return BakedProjectionGeometry.EMPTY;
        }
        layers.sort(Comparator.comparingInt(layer -> renderOrder(layer.renderType())));
        return new BakedProjectionGeometry(List.copyOf(layers));
    }

    private static int renderOrder(RenderType renderType) {
        if (renderType == RenderTypes.solidMovingBlock()) {
            return 0;
        }
        if (renderType == RenderTypes.cutoutMovingBlock()) {
            return 1;
        }
        if (renderType == GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_TRANSLUCENT_BLOCK) {
            return 2;
        }
        return 3;
    }

    private static LayerBuilder layerBuilder(Map<RenderType, LayerBuilder> builders, RenderType renderType) {
        return builders.computeIfAbsent(renderType, LayerBuilder::new);
    }

    private static void renderBakedLayer(Minecraft minecraft, BakedLayer layer, Vec3 modelOffset) {
        RenderType renderType = layer.renderType();
        RenderTarget renderTarget = renderType.outputTarget().getRenderTarget();
        GpuTextureView colorTexture = RenderSystem.outputColorTextureOverride != null
                ? RenderSystem.outputColorTextureOverride
                : renderTarget.getColorTextureView();
        GpuTextureView depthTexture = renderTarget.useDepth
                ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : renderTarget.getDepthTextureView())
                : null;
        if (colorTexture == null || layer.vertexBuffer().isClosed()) {
            return;
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrix(),
                new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                new Vector3f((float) modelOffset.x, (float) modelOffset.y, (float) modelOffset.z),
                new Matrix4f()
        );
        GpuBuffer indexBuffer = layer.indexBuffer();
        VertexFormat.IndexType indexType = layer.indexType();
        if (indexBuffer == null) {
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(layer.mode());
            indexBuffer = sequential.getBuffer(layer.indexCount());
            indexType = sequential.type();
        }
        if (indexBuffer == null || indexBuffer.isClosed()) {
            return;
        }

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "GeometryNode schematic projection " + renderType, colorTexture, OptionalInt.empty(), depthTexture, OptionalDouble.empty())) {
            renderPass.setPipeline(renderType.pipeline());
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            bindBlockTextures(minecraft, renderPass);
            renderPass.setVertexBuffer(0, layer.vertexBuffer());

            renderPass.setIndexBuffer(indexBuffer, indexType);
            renderPass.drawIndexed(0, 0, layer.indexCount(), 1);
        }
    }

    private static void bindBlockTextures(Minecraft minecraft, RenderPass renderPass) {
        AbstractTexture blockAtlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        renderPass.bindTexture(
                "Sampler0",
                blockAtlas.getTextureView(),
                RenderSystem.getSamplerCache().getSampler(
                        AddressMode.CLAMP_TO_EDGE,
                        AddressMode.CLAMP_TO_EDGE,
                        FilterMode.LINEAR,
                        FilterMode.NEAREST,
                        true
                )
        );
        renderPass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }

    private static void submitBlockEntities(Projection projection,
                                            BlockEntityRenderDispatcher dispatcher,
                                            PoseStack poseStack,
                                            SubmitNodeCollector submitNodeCollector,
                                            CameraRenderState cameraRenderState,
                                            float partialTick) {
        for (RenderBlockEntity renderBlockEntity : projection.blockEntities) {
            try {
                BlockEntityRenderState state = dispatcher.tryExtractRenderState(renderBlockEntity.blockEntity(), partialTick, null);
                if (state != null) {
                    BlockPos blockPos = state.blockPos;
                    poseStack.pushPose();
                    try {
                        poseStack.translate(
                                blockPos.getX() - cameraRenderState.pos.x,
                                blockPos.getY() - cameraRenderState.pos.y,
                                blockPos.getZ() - cameraRenderState.pos.z
                        );
                        dispatcher.submit(state, poseStack, submitNodeCollector, cameraRenderState);
                    } finally {
                        poseStack.popPose();
                    }
                }
            } catch (Exception ignored) {
                // Projections are visual-only; unsupported block entity renderers are skipped.
            }
        }
    }

    private static void submitEntities(Projection projection,
                                       EntityRenderDispatcher dispatcher,
                                       PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector,
                                       CameraRenderState cameraRenderState,
                                       float partialTick) {
        for (RenderEntity renderEntity : projection.entities) {
            Entity entity = renderEntity.entity();
            try {
                EntityRenderState state = dispatcher.extractEntity(entity, partialTick);
                dispatcher.submit(
                        state,
                        cameraRenderState,
                        entity.getX() - cameraRenderState.pos.x,
                        entity.getY() - cameraRenderState.pos.y,
                        entity.getZ() - cameraRenderState.pos.z,
                        poseStack,
                        submitNodeCollector
                );
            } catch (Exception ignored) {
                // Projections are visual-only; unsupported entity renderers are skipped.
            }
        }
    }

    private static void drawFallbackFaces(Projection projection, VertexConsumer buffer, Matrix4f matrix, Vec3 camPos) {
        for (RenderFace face : projection.fallbackFaces) {
            drawFace(buffer, matrix, projection, face.color(), face.face(), face.x0(), face.y0(), face.z0(), face.x1(), face.y1(), face.z1(), camPos);
        }
    }

    private static void drawFallbackEdges(Projection projection,
                                          VertexConsumer buffer,
                                          PoseStack.Pose pose,
                                          Matrix4f matrix,
                                          Vec3 camPos) {
        for (RenderFace face : projection.fallbackFaces) {
            drawFaceEdges(buffer, pose, matrix, projection, face.color(), face.face(), face.x0(), face.y0(), face.z0(), face.x1(), face.y1(), face.z1(), camPos);
        }
    }

    private static void collectVisibleFallbackFaces(Projection projection, PacketSchematicProjection.Block block, List<RenderFace> faces) {
        int x = block.x();
        int y = block.y();
        int z = block.z();
        float x0 = x;
        float y0 = y;
        float z0 = z;
        float x1 = x + 1.0f;
        float y1 = y + 1.0f;
        float z1 = z + 1.0f;

        if (!projection.contains(x, y + 1, z)) faces.add(new RenderFace(Face.UP, block.color(), x0, y0, z0, x1, y1, z1));
        if (!projection.contains(x, y - 1, z)) faces.add(new RenderFace(Face.DOWN, block.color(), x0, y0, z0, x1, y1, z1));
        if (!projection.contains(x, y, z - 1)) faces.add(new RenderFace(Face.NORTH, block.color(), x0, y0, z0, x1, y1, z1));
        if (!projection.contains(x, y, z + 1)) faces.add(new RenderFace(Face.SOUTH, block.color(), x0, y0, z0, x1, y1, z1));
        if (!projection.contains(x + 1, y, z)) faces.add(new RenderFace(Face.EAST, block.color(), x0, y0, z0, x1, y1, z1));
        if (!projection.contains(x - 1, y, z)) faces.add(new RenderFace(Face.WEST, block.color(), x0, y0, z0, x1, y1, z1));
    }

    private static void drawFace(VertexConsumer buffer,
                                 Matrix4f matrix,
                                 Projection projection,
                                 int color,
                                 Face face,
                                 float x0,
                                 float y0,
                                 float z0,
                                 float x1,
                                 float y1,
                                 float z1,
                                 Vec3 camPos) {
        int alpha = scaledAlpha(projection.alpha, FALLBACK_FACE_ALPHA_SCALE);
        int r = red(color);
        int g = green(color);
        int b = blue(color);
        switch (face) {
            case UP -> {
                vertex(buffer, matrix, projection, x0, y1, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y1, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y1, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y1, z1, camPos, r, g, b, alpha);
            }
            case DOWN -> {
                vertex(buffer, matrix, projection, x0, y0, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y0, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y0, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y0, z0, camPos, r, g, b, alpha);
            }
            case NORTH -> {
                vertex(buffer, matrix, projection, x1, y0, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y1, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y1, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y0, z0, camPos, r, g, b, alpha);
            }
            case SOUTH -> {
                vertex(buffer, matrix, projection, x0, y0, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y1, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y1, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y0, z1, camPos, r, g, b, alpha);
            }
            case EAST -> {
                vertex(buffer, matrix, projection, x1, y0, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y1, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y1, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x1, y0, z0, camPos, r, g, b, alpha);
            }
            case WEST -> {
                vertex(buffer, matrix, projection, x0, y0, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y1, z0, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y1, z1, camPos, r, g, b, alpha);
                vertex(buffer, matrix, projection, x0, y0, z1, camPos, r, g, b, alpha);
            }
        }
    }

    private static void drawFaceEdges(VertexConsumer buffer,
                                      PoseStack.Pose pose,
                                      Matrix4f matrix,
                                      Projection projection,
                                      int color,
                                      Face face,
                                      float x0,
                                      float y0,
                                      float z0,
                                      float x1,
                                      float y1,
                                      float z1,
                                      Vec3 camPos) {
        int alpha = scaledAlpha(projection.alpha, FALLBACK_EDGE_ALPHA_SCALE);
        int r = lighten(red(color));
        int g = lighten(green(color));
        int b = lighten(blue(color));
        switch (face) {
            case UP -> {
                line(buffer, pose, matrix, projection, x0, y1, z0, x1, y1, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y1, z0, x1, y1, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y1, z1, x0, y1, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y1, z1, x0, y1, z0, camPos, r, g, b, alpha);
            }
            case DOWN -> {
                line(buffer, pose, matrix, projection, x0, y0, z0, x1, y0, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y0, z0, x1, y0, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y0, z1, x0, y0, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y0, z1, x0, y0, z0, camPos, r, g, b, alpha);
            }
            case NORTH -> {
                line(buffer, pose, matrix, projection, x0, y0, z0, x1, y0, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y0, z0, x1, y1, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y1, z0, x0, y1, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y1, z0, x0, y0, z0, camPos, r, g, b, alpha);
            }
            case SOUTH -> {
                line(buffer, pose, matrix, projection, x0, y0, z1, x1, y0, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y0, z1, x1, y1, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y1, z1, x0, y1, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y1, z1, x0, y0, z1, camPos, r, g, b, alpha);
            }
            case EAST -> {
                line(buffer, pose, matrix, projection, x1, y0, z0, x1, y1, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y1, z0, x1, y1, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y1, z1, x1, y0, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x1, y0, z1, x1, y0, z0, camPos, r, g, b, alpha);
            }
            case WEST -> {
                line(buffer, pose, matrix, projection, x0, y0, z0, x0, y1, z0, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y1, z0, x0, y1, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y1, z1, x0, y0, z1, camPos, r, g, b, alpha);
                line(buffer, pose, matrix, projection, x0, y0, z1, x0, y0, z0, camPos, r, g, b, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer buffer,
                               Matrix4f matrix,
                               Projection projection,
                               float x,
                               float y,
                               float z,
                               Vec3 camPos,
                               int r,
                               int g,
                               int b,
                               int a) {
        buffer.addVertex(matrix,
                        (float) (projection.originX + x - camPos.x),
                        (float) (projection.originY + y - camPos.y),
                        (float) (projection.originZ + z - camPos.z))
                .setColor(r, g, b, a);
    }

    private static void line(VertexConsumer buffer,
                             PoseStack.Pose pose,
                             Matrix4f matrix,
                             Projection projection,
                             float x0,
                             float y0,
                             float z0,
                             float x1,
                             float y1,
                             float z1,
                             Vec3 camPos,
                             int r,
                             int g,
                             int b,
                             int a) {
        float ax = (float) (projection.originX + x0 - camPos.x);
        float ay = (float) (projection.originY + y0 - camPos.y);
        float az = (float) (projection.originZ + z0 - camPos.z);
        float bx = (float) (projection.originX + x1 - camPos.x);
        float by = (float) (projection.originY + y1 - camPos.y);
        float bz = (float) (projection.originZ + z1 - camPos.z);
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        buffer.addVertex(matrix, ax, ay, az)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
        buffer.addVertex(matrix, bx, by, bz)
                .setColor(r, g, b, a)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(EDGE_WIDTH);
    }

    private static void drawProjectionBoundsFaces(Projection projection, VertexConsumer buffer, Matrix4f matrix, Vec3 camPos) {
        if (projection.width <= 0 || projection.height <= 0 || projection.length <= 0) {
            return;
        }

        float minX = 0.0f;
        float minY = 0.0f;
        float minZ = 0.0f;
        float maxX = projection.width;
        float maxY = projection.height;
        float maxZ = projection.length;
        drawBoundsQuad(buffer, matrix, projection, camPos, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        drawBoundsQuad(buffer, matrix, projection, camPos, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        drawBoundsQuad(buffer, matrix, projection, camPos, minX, minY, minZ, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ);
        drawBoundsQuad(buffer, matrix, projection, camPos, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        drawBoundsQuad(buffer, matrix, projection, camPos, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ);
        drawBoundsQuad(buffer, matrix, projection, camPos, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
    }

    private static void drawBoundsQuad(VertexConsumer buffer,
                                       Matrix4f matrix,
                                       Projection projection,
                                       Vec3 camPos,
                                       float x1,
                                       float y1,
                                       float z1,
                                       float x2,
                                       float y2,
                                       float z2,
                                       float x3,
                                       float y3,
                                       float z3,
                                       float x4,
                                       float y4,
                                       float z4) {
        boundsVertex(buffer, matrix, projection, camPos, x1, y1, z1);
        boundsVertex(buffer, matrix, projection, camPos, x2, y2, z2);
        boundsVertex(buffer, matrix, projection, camPos, x3, y3, z3);
        boundsVertex(buffer, matrix, projection, camPos, x4, y4, z4);
    }

    private static void boundsVertex(VertexConsumer buffer,
                                     Matrix4f matrix,
                                     Projection projection,
                                     Vec3 camPos,
                                     float x,
                                     float y,
                                     float z) {
        buffer.addVertex(matrix,
                        (float) (projection.originX + x - camPos.x),
                        (float) (projection.originY + y - camPos.y),
                        (float) (projection.originZ + z - camPos.z))
                .setColor(WHITE, WHITE, WHITE, BOUNDS_FACE_ALPHA);
    }

    private static void drawProjectionBoundsLines(Projection projection,
                                                  VertexConsumer buffer,
                                                  PoseStack.Pose pose,
                                                  Matrix4f matrix,
                                                  Vec3 camPos) {
        if (projection.width <= 0 || projection.height <= 0 || projection.length <= 0) {
            return;
        }

        float minX = 0.0f;
        float minY = 0.0f;
        float minZ = 0.0f;
        float maxX = projection.width;
        float maxY = projection.height;
        float maxZ = projection.length;

        boundsLine(buffer, pose, matrix, projection, camPos, minX, minY, minZ, maxX, minY, minZ);
        boundsLine(buffer, pose, matrix, projection, camPos, maxX, minY, minZ, maxX, minY, maxZ);
        boundsLine(buffer, pose, matrix, projection, camPos, maxX, minY, maxZ, minX, minY, maxZ);
        boundsLine(buffer, pose, matrix, projection, camPos, minX, minY, maxZ, minX, minY, minZ);

        boundsLine(buffer, pose, matrix, projection, camPos, minX, maxY, minZ, maxX, maxY, minZ);
        boundsLine(buffer, pose, matrix, projection, camPos, maxX, maxY, minZ, maxX, maxY, maxZ);
        boundsLine(buffer, pose, matrix, projection, camPos, maxX, maxY, maxZ, minX, maxY, maxZ);
        boundsLine(buffer, pose, matrix, projection, camPos, minX, maxY, maxZ, minX, maxY, minZ);

        boundsLine(buffer, pose, matrix, projection, camPos, minX, minY, minZ, minX, maxY, minZ);
        boundsLine(buffer, pose, matrix, projection, camPos, maxX, minY, minZ, maxX, maxY, minZ);
        boundsLine(buffer, pose, matrix, projection, camPos, maxX, minY, maxZ, maxX, maxY, maxZ);
        boundsLine(buffer, pose, matrix, projection, camPos, minX, minY, maxZ, minX, maxY, maxZ);
    }

    private static void boundsLine(VertexConsumer buffer,
                                   PoseStack.Pose pose,
                                   Matrix4f matrix,
                                   Projection projection,
                                   Vec3 camPos,
                                   float x0,
                                   float y0,
                                   float z0,
                                   float x1,
                                   float y1,
                                   float z1) {
        float ax = (float) (projection.originX + x0 - camPos.x);
        float ay = (float) (projection.originY + y0 - camPos.y);
        float az = (float) (projection.originZ + z0 - camPos.z);
        float bx = (float) (projection.originX + x1 - camPos.x);
        float by = (float) (projection.originY + y1 - camPos.y);
        float bz = (float) (projection.originZ + z1 - camPos.z);
        float nx = bx - ax;
        float ny = by - ay;
        float nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        buffer.addVertex(matrix, ax, ay, az)
                .setColor(WHITE, WHITE, WHITE, BOUNDS_LINE_ALPHA)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(BOUNDS_LINE_WIDTH);
        buffer.addVertex(matrix, bx, by, bz)
                .setColor(WHITE, WHITE, WHITE, BOUNDS_LINE_ALPHA)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(BOUNDS_LINE_WIDTH);
    }

    private static BlockState parseBlockState(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("legacy:")) {
            return LegacySchematicBlockStateMapper.fromRaw(raw);
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, raw, false).blockState();
        } catch (Exception ignored) {
        }

        try {
            String id = raw;
            int bracket = id.indexOf('[');
            if (bracket >= 0) {
                id = id.substring(0, bracket);
            }
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) {
                return null;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(identifier);
            if (block == null) {
                return null;
            }
            BlockState state = block.defaultBlockState();
            return state.isAir() ? null : state;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int alphaByte(float alpha) {
        return Math.max(1, Math.min(255, Math.round(alpha * 255.0f)));
    }

    private static int scaledAlpha(float alpha, int scale) {
        return Math.max(1, Math.min(255, (int) (alpha * scale)));
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int lighten(int value) {
        return Math.min(255, value + 64);
    }

    private static long pack(int x, int y, int z) {
        return (((long) x & 0x1FFFFFL) << 42)
                | (((long) y & 0x1FFFFFL) << 21)
                | ((long) z & 0x1FFFFFL);
    }

    private enum Face {
        UP,
        DOWN,
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    private record RenderFace(
            Face face,
            int color,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1
    ) {
    }

    private record RenderBlock(
            BlockPos worldPos,
            BlockState state,
            long seed
    ) {
    }

    private record RenderFluidBlock(
            BlockPos worldPos,
            BlockState state,
            FluidState fluidState
    ) {
    }

    private record RenderBlockEntity(
            BlockEntity blockEntity
    ) {
    }

    private record RenderEntity(
            Entity entity
    ) {
    }

    private static RenderType renderTypeForLayer(ChunkSectionLayer layer,
                                                 int alpha,
                                                 RenderType solidRenderType,
                                                 RenderType cutoutRenderType,
                                                 RenderType translucentRenderType) {
        if (alpha < 255) {
            return translucentRenderType;
        }
        return switch (layer) {
            case SOLID -> solidRenderType;
            case CUTOUT -> cutoutRenderType;
            case TRANSLUCENT -> translucentRenderType;
        };
    }

    private record BakedProjectionGeometry(List<BakedLayer> layers) implements AutoCloseable {
        private static final BakedProjectionGeometry EMPTY = new BakedProjectionGeometry(List.of());

        private void render(Minecraft minecraft, Projection projection, Vec3 camPos) {
            if (layers.isEmpty()) {
                return;
            }
            Vec3 modelOffset = new Vec3(
                    projection.originX - camPos.x,
                    projection.originY - camPos.y,
                    projection.originZ - camPos.z
            );
            for (BakedLayer layer : layers) {
                renderBakedLayer(minecraft, layer, modelOffset);
            }
        }

        @Override
        public void close() {
            for (BakedLayer layer : layers) {
                layer.close();
            }
        }
    }

    private record BakedLayer(
            RenderType renderType,
            GpuBuffer vertexBuffer,
            GpuBuffer indexBuffer,
            VertexFormat.IndexType indexType,
            VertexFormat.Mode mode,
            int indexCount
    ) implements AutoCloseable {
        private static BakedLayer upload(RenderType renderType, MeshData mesh) {
            MeshData.DrawState drawState = mesh.drawState();
            if (drawState.indexCount() <= 0) {
                return null;
            }

            GpuBuffer vertexBuffer = null;
            GpuBuffer indexBuffer = null;
            try {
                ByteBuffer vertexData = mesh.vertexBuffer();
                if (vertexData == null || !vertexData.hasRemaining()) {
                    return null;
                }
                vertexBuffer = RenderSystem.getDevice().createBuffer(
                        () -> "GeometryNode schematic projection vertices " + renderType,
                        GpuBuffer.USAGE_VERTEX,
                        vertexData
                );

                ByteBuffer indexData = mesh.indexBuffer();
                if (indexData != null && indexData.hasRemaining()) {
                    indexBuffer = RenderSystem.getDevice().createBuffer(
                            () -> "GeometryNode schematic projection indices " + renderType,
                            GpuBuffer.USAGE_INDEX,
                            indexData
                    );
                }

                BakedLayer layer = new BakedLayer(renderType, vertexBuffer, indexBuffer, drawState.indexType(), drawState.mode(), drawState.indexCount());
                vertexBuffer = null;
                indexBuffer = null;
                return layer;
            } finally {
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }
                if (indexBuffer != null) {
                    indexBuffer.close();
                }
            }
        }

        @Override
        public void close() {
            if (vertexBuffer != null && !vertexBuffer.isClosed()) {
                vertexBuffer.close();
            }
            if (indexBuffer != null && !indexBuffer.isClosed()) {
                indexBuffer.close();
            }
        }
    }

    private static final class LayerBuilder implements AutoCloseable {
        private final ByteBufferBuilder buffer;
        private final BufferBuilder builder;
        private boolean built;

        private LayerBuilder(RenderType renderType) {
            this.buffer = new ByteBufferBuilder(renderType.bufferSize());
            this.builder = new BufferBuilder(buffer, renderType.mode(), renderType.format());
        }

        private VertexConsumer consumer() {
            return builder;
        }

        private MeshData build() {
            if (built) {
                return null;
            }
            built = true;
            return builder.build();
        }

        @Override
        public void close() {
            buffer.close();
        }
    }

    private record ProjectionBakeBlockOutput(Map<RenderType, LayerBuilder> builders, int alpha) implements BlockQuadOutput {
        @Override
        public void put(float x, float y, float z, BakedQuad quad, QuadInstance instance) {
            consumer(quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
        }

        private VertexConsumer consumer(ChunkSectionLayer layer) {
            RenderType renderType = renderTypeForLayer(
                    layer,
                    alpha,
                    RenderTypes.solidMovingBlock(),
                    RenderTypes.cutoutMovingBlock(),
                    GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_TRANSLUCENT_BLOCK
            );
            return new AlphaVertexConsumer(layerBuilder(builders, renderType).consumer(), alpha);
        }
    }

    private record ProjectionBakeFluidOutput(
            Map<RenderType, LayerBuilder> builders,
            int alpha,
            double offsetX,
            double offsetY,
            double offsetZ
    ) implements FluidRenderer.Output {
        @Override
        public VertexConsumer getBuilder(ChunkSectionLayer layer) {
            RenderType renderType = renderTypeForLayer(
                    layer,
                    alpha,
                    RenderTypes.solidMovingBlock(),
                    RenderTypes.cutoutMovingBlock(),
                    GeometryDebugRenderTypes.SCHEMATIC_PROJECTION_TRANSLUCENT_BLOCK
            );
            return new AlphaVertexConsumer(layerBuilder(builders, renderType).consumer(), alpha, offsetX, offsetY, offsetZ);
        }
    }

    private static final class AlphaVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;
        private final float offsetX;
        private final float offsetY;
        private final float offsetZ;

        private AlphaVertexConsumer(VertexConsumer delegate, int alpha) {
            this(delegate, alpha, 0.0, 0.0, 0.0);
        }

        private AlphaVertexConsumer(VertexConsumer delegate, int alpha, double offsetX, double offsetY, double offsetZ) {
            this.delegate = delegate;
            this.alpha = Math.max(0, Math.min(255, alpha));
            this.offsetX = (float) offsetX;
            this.offsetY = (float) offsetY;
            this.offsetZ = (float) offsetZ;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x + offsetX, y + offsetY, z + offsetZ);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor(r, g, b, scaleAlpha(a));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(ARGB.color(scaleAlpha(ARGB.alpha(color)), color));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        private int scaleAlpha(int originalAlpha) {
            return Math.max(0, Math.min(255, originalAlpha * alpha / 255));
        }
    }

    private static final class ProjectionBlockGetter implements BlockAndTintGetter {
        private final ClientLevel level;
        private final Projection projection;

        private ProjectionBlockGetter(ClientLevel level, Projection projection) {
            this.level = level;
            this.projection = projection;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return projection.blockEntityAtWorld(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = projection.stateAtWorld(pos);
            return state != null ? state : AIR;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            BlockState state = getBlockState(pos);
            return state != null ? state.getFluidState() : Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinY() {
            return level.getMinY();
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return level.cardinalLighting();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return level.getBlockTint(pos, colorResolver);
        }
    }

    private static final class Projection {
        private final String dimension;
        private final int originBlockX;
        private final int originBlockY;
        private final int originBlockZ;
        private final double originX;
        private final double originY;
        private final double originZ;
        private final int width;
        private final int height;
        private final int length;
        private final AABB bounds;
        private final float alpha;
        private final List<RenderBlock> texturedBlocks;
        private final List<RenderFluidBlock> fluidBlocks;
        private final List<RenderBlockEntity> blockEntities;
        private final List<RenderEntity> entities;
        private final List<RenderFace> fallbackFaces;
        private final BakedProjectionGeometry bakedGeometry;
        private final LongOpenHashSet occupied;
        private final Long2ObjectOpenHashMap<BlockState> statesByPosition;
        private final Long2ObjectOpenHashMap<BlockEntity> blockEntitiesByPosition;
        private final long expiresAt;
        private final long budgetGeometry;
        private final long budgetNbtBytes;
        private final long budgetEstimatedBytes;

        private Projection(PacketSchematicProjection packet, ClientLevel level,
                           long budgetGeometry, long budgetNbtBytes, long budgetEstimatedBytes) {
            this.dimension = packet.dimension();
            this.originX = packet.originX();
            this.originY = packet.originY();
            this.originZ = packet.originZ();
            this.originBlockX = (int) Math.floor(packet.originX());
            this.originBlockY = (int) Math.floor(packet.originY());
            this.originBlockZ = (int) Math.floor(packet.originZ());
            this.width = packet.width();
            this.height = packet.height();
            this.length = packet.length();
            this.bounds = new AABB(originX, originY, originZ, originX + width, originY + height, originZ + length);
            this.alpha = packet.alpha();
            this.budgetGeometry = budgetGeometry;
            this.budgetNbtBytes = budgetNbtBytes;
            this.budgetEstimatedBytes = budgetEstimatedBytes;
            this.occupied = new LongOpenHashSet(Math.max(16, packet.blocks().size() * 2));
            this.statesByPosition = new Long2ObjectOpenHashMap<>(Math.max(16, packet.blocks().size() * 2));
            this.blockEntitiesByPosition = new Long2ObjectOpenHashMap<>(Math.max(16, packet.blockEntities().size() * 2));

            List<BlockState> states = new ArrayList<>(packet.states().size());
            for (String rawState : packet.states()) {
                states.add(parseBlockState(rawState));
            }

            ArrayList<RenderBlock> textured = new ArrayList<>();
            ArrayList<RenderFluidBlock> fluids = new ArrayList<>();
            ArrayList<PacketSchematicProjection.Block> fallbackBlocks = new ArrayList<>();
            for (PacketSchematicProjection.Block block : packet.blocks()) {
                occupied.add(pack(block.x(), block.y(), block.z()));
                BlockState state = stateFor(block, states);
                if (state != null && !state.isAir()) {
                    statesByPosition.put(pack(block.x(), block.y(), block.z()), state);
                    BlockPos worldPos = new BlockPos(originBlockX + block.x(), originBlockY + block.y(), originBlockZ + block.z());
                    FluidState fluidState = state.getFluidState();
                    if (!fluidState.isEmpty()) {
                        fluids.add(new RenderFluidBlock(worldPos, state, fluidState));
                    }
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        textured.add(new RenderBlock(worldPos, state, state.getSeed(worldPos)));
                    } else if (fluidState.isEmpty()) {
                        fallbackBlocks.add(block);
                    }
                } else {
                    fallbackBlocks.add(block);
                }
            }

            ArrayList<RenderBlockEntity> loadedBlockEntities = new ArrayList<>();
            if (level != null) {
                for (PacketSchematicProjection.BlockEntity blockEntityData : packet.blockEntities()) {
                    BlockState state = statesByPosition.get(pack(blockEntityData.x(), blockEntityData.y(), blockEntityData.z()));
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    BlockPos worldPos = new BlockPos(
                            originBlockX + blockEntityData.x(),
                            originBlockY + blockEntityData.y(),
                            originBlockZ + blockEntityData.z()
                    );
                    BlockEntity blockEntity = loadBlockEntity(level, blockEntityData, worldPos, state);
                    if (blockEntity != null) {
                        loadedBlockEntities.add(new RenderBlockEntity(blockEntity));
                        blockEntitiesByPosition.put(pack(blockEntityData.x(), blockEntityData.y(), blockEntityData.z()), blockEntity);
                    }
                }
            }

            ArrayList<RenderEntity> loadedEntities = new ArrayList<>();
            if (level != null) {
                for (PacketSchematicProjection.Entity entityData : packet.entities()) {
                    Entity entity = loadEntity(level, entityData, originX + entityData.x(), originY + entityData.y(), originZ + entityData.z());
                    if (entity != null) {
                        loadedEntities.add(new RenderEntity(entity));
                    }
                }
            }

            ArrayList<RenderFace> visibleFallbackFaces = new ArrayList<>();
            for (PacketSchematicProjection.Block block : fallbackBlocks) {
                collectVisibleFallbackFaces(this, block, visibleFallbackFaces);
            }
            this.texturedBlocks = List.copyOf(textured);
            this.fluidBlocks = List.copyOf(fluids);
            this.blockEntities = List.copyOf(loadedBlockEntities);
            this.entities = List.copyOf(loadedEntities);
            this.fallbackFaces = List.copyOf(visibleFallbackFaces);
            this.bakedGeometry = bakeStaticGeometry(Minecraft.getInstance(), level, this);

            long currentTick = currentGameTime();
            this.expiresAt = currentTick + Math.max(1, packet.durationTicks());
        }

        private void renderBaked(Minecraft minecraft, Vec3 camPos) {
            bakedGeometry.render(minecraft, this, camPos);
        }

        private BlockState stateAtWorld(BlockPos pos) {
            return statesByPosition.get(pack(pos.getX() - originBlockX, pos.getY() - originBlockY, pos.getZ() - originBlockZ));
        }

        private BlockEntity blockEntityAtWorld(BlockPos pos) {
            return blockEntitiesByPosition.get(pack(pos.getX() - originBlockX, pos.getY() - originBlockY, pos.getZ() - originBlockZ));
        }

        private boolean contains(int x, int y, int z) {
            return occupied.contains(pack(x, y, z));
        }

        private boolean expired(long tick) {
            return tick >= expiresAt;
        }

        private boolean closeEnoughTo(Vec3 camPos, double maxDistanceSqr) {
            return bounds.distanceToSqr(camPos) <= maxDistanceSqr;
        }

        private boolean visibleIn(Frustum frustum) {
            return frustum == null || frustum.isVisible(bounds);
        }

        private void close() {
            bakedGeometry.close();
        }

        private static BlockState stateFor(PacketSchematicProjection.Block block, List<BlockState> states) {
            int index = block.stateIndex();
            if (index < 0 || index >= states.size()) {
                return null;
            }
            return states.get(index);
        }

        private static long currentGameTime() {
            ClientLevel level = Minecraft.getInstance().level;
            return level != null ? level.getGameTime() : 0L;
        }

        private static BlockEntity loadBlockEntity(ClientLevel level,
                                                   PacketSchematicProjection.BlockEntity data,
                                                   BlockPos worldPos,
                                                   BlockState state) {
            return SchematicBlockEntityUtils.loadBlockEntity(level, worldPos, state, data.tag());
        }

        private static Entity loadEntity(ClientLevel level,
                                         PacketSchematicProjection.Entity data,
                                         double x,
                                         double y,
                                         double z) {
            try {
                CompoundTag tag = absoluteEntityTag(data.tag(), x, y, z);
                Entity entity = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
                if (entity != null) {
                    entity.noPhysics = true;
                    entity.snapTo(x, y, z, entity.getYRot(), entity.getXRot());
                }
                return entity;
            } catch (Exception ignored) {
                return null;
            }
        }

        private static CompoundTag absoluteEntityTag(CompoundTag source, double x, double y, double z) {
            CompoundTag tag = source == null ? new CompoundTag() : source.copy();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(x));
            pos.add(DoubleTag.valueOf(y));
            pos.add(DoubleTag.valueOf(z));
            tag.put("Pos", pos);
            return tag;
        }
    }
}
