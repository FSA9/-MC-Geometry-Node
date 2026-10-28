package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.client.runtime.entity.appearance.ClientEntityAppearances;
import com.mine.geometry_node.client.runtime.light.store.ClientLightStore;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearances;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelLightOcclusionBvh;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;
import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;
import com.mine.geometry_node.mixin.model.render.LivingEntityShadowAccessor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Animated living-entity body geometry; independent of camera visibility and the entity draw pass. */
public final class NodeLightEntityShadows {
    private static final int MAX_ENTITIES = 32;
    private static final int MAX_TRIANGLES = 8192;
    private static final int WIDTH = 256;
    private static final long MAX_BUILD_NANOS = 8_000_000L;
    private static final Collector COLLECTOR = new Collector();
    private static final float[] BOUNDS = new float[MAX_ENTITIES * 4];
    private static int boundsCount;
    private static int texture, allocatedHeight, nodeCount, triangleOffset, triangles, entities, omitted;
    private static long nanos;
    private static long gpuBytes;
    private static float[] cachedSoup = new float[0];
    private static int[] cachedMasks = new int[0];
    private static int cachedNodes, cachedOffset, cachedTriangles;
    private static long builds, reuses;
    private static long contentRevision, revisionBuilds = -1;
    private static int revisionTexture = -1, revisionNodes, revisionOffset, revisionTriangles, revisionBoundsCount;
    private static final float[] revisionBounds = new float[MAX_ENTITIES * 4];
    private static long captureBudgetOmissions, buildBudgetFailures;
    private static int frameCaptureBudgetOmissions, frameBuildBudgetFailures;
    private static final long CAPTURE_NANOS = 4_000_000L;
    private static String failure = "";
    private static LivingEntityRenderer<?, ?, ?> capturingRenderer;
    private static final SubmitNodeStorage UNUSED_SUBMITS = new SubmitNodeStorage();

    private NodeLightEntityShadows() {}
    static int cachedGeometryCount() { return cachedSoup.length == 0 ? 0 : 1; }
    static void clearCachedGeometry() {
        // Current BVH texture is referenced by receiver bindings until the owner prepares a replacement.
        cachedSoup = new float[0];
        cachedMasks = new int[0];
    }

    public static void prepare(Vec3 camera, List<ClientLightStore.ResolvedLight> lights, float partialTick) {
        long started = System.nanoTime();
        nodeCount = triangleOffset = triangles = entities = omitted = 0;
        frameCaptureBudgetOmissions = frameBuildBudgetFailures = 0;
        boundsCount = 0;
        failure = "";
        var mc = Minecraft.getInstance();
        COLLECTOR.reset();
        if (mc.level == null || lights.stream().noneMatch(l -> l.definition().config().shadow().enabled())) {
            clear();
            nanos = System.nanoTime() - started;
            return;
        }
        var candidates = new ArrayList<Candidate>();
        var dispatcher = mc.getEntityRenderDispatcher();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity.isRemoved() || entity.isInvisible() || entity.isSpectator()) continue;
            double distance = Double.POSITIVE_INFINITY;
            int excluded = 0;
            // Match LevelRenderer: frozen entities use their frozen pose, while
            // players and entities exempt from the freeze keep interpolating.
            float entityPartialTick = mc.level.tickRateManager().isEntityFrozen(entity)
                    ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : partialTick;
            // Scan visual bounds only. Full extraction belongs inside the capture budget below.
            final AABB visualBounds;
            try {
                boolean hasAppearance = !EntityAppearances.get(entity).isNone();
                var visualEntity = hasAppearance ? ClientEntityAppearances.visualEntity(entity) : entity;
                if (!(dispatcher.getRenderer(visualEntity) instanceof LivingEntityRenderer<?, ?, ?>)) continue;
                visualBounds = hasAppearance ? ClientEntityAppearances.visualBounds(entity) : entity.getBoundingBox();
            } catch (RuntimeException e) {
                omitted++;
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
                continue;
            }
            var interpolationOffset = entity.getPosition(entityPartialTick).subtract(entity.position());
            var interpolatedBounds = visualBounds.move(interpolationOffset);
            var bounds = shadowBounds(visualBounds, interpolationOffset);
            for (int i = 0; i < lights.size(); i++) {
                var light = lights.get(i);
                if (!light.definition().config().shadow().enabled()) continue;
                double d = bounds.distanceToSqr(light.position());
                double reach = light.definition().range() + light.definition().emitterRadius();
                if (d <= reach * reach && intersectsBeam(light,bounds)) distance = Math.min(distance, d);
                var owner = light.definition().config().transform().anchor().entityId();
                if (entity.getUUID().equals(owner) && interpolatedBounds.inflate(0.15).contains(light.position())) excluded |= 1 << i;
            }
            if (Double.isFinite(distance)) candidates.add(new Candidate(entity, distance, excluded, entityPartialTick));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance).thenComparingInt(c -> c.entity.getId()));
        long captureStarted = System.nanoTime();
        int attempted = 0;
        for (var candidate : candidates) {
            if (attempted >= MAX_ENTITIES) { omitted++; continue; }
            if (attempted > 0 && System.nanoTime() - captureStarted > CAPTURE_NANOS) {
                omitted++; frameCaptureBudgetOmissions++; captureBudgetOmissions++;
                continue;
            }
            attempted++;
            int before = COLLECTOR.vertices.size;
            LivingEntityRenderer<?, ?, ?> renderer = null;
            EntityModel<?> previousModel = null;
            COLLECTOR.begin(candidate.excluded);
            try {
                // Resolve once, after both budget checks. Keep this extraction's exact renderer/state pair.
                var spec = EntityAppearances.get(candidate.entity);
                var appearance = spec.isNone() ? null
                        : ClientEntityAppearances.resolve(candidate.entity, spec, candidate.partialTick);
                EntityRenderer<?, ?> matchedRenderer = appearance == null
                        ? dispatcher.getRenderer(candidate.entity) : appearance.renderer();
                if (!(matchedRenderer instanceof LivingEntityRenderer<?, ?, ?> livingRenderer)
                        || appearance != null && !(appearance.state() instanceof LivingEntityRenderState)) continue;
                renderer = livingRenderer;
                previousModel = renderer.getModel();
                // NONE/failed appearance uses this native renderer directly, never a second dispatcher extraction.
                var state = appearance == null ? extractNative(renderer, candidate.entity, candidate.partialTick)
                        : (LivingEntityRenderState) appearance.state();
                prepareBody(renderer, state, camera);
                if (COLLECTOR.vertices.overflow) {
                    COLLECTOR.vertices.size = before;
                    omitted++;
                } else if (COLLECTOR.vertices.size > before) {
                    captureBounds(before, COLLECTOR.vertices.size);
                    entities++;
                }
            } catch (CaptureOverflow e) {
                COLLECTOR.vertices.size = before;
                omitted++;
            } catch (RuntimeException e) {
                COLLECTOR.vertices.size = before;
                omitted++;
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                if (renderer != null) ((LivingEntityShadowAccessor) renderer).geometryNode$shadowModel(previousModel);
                capturingRenderer = null;
                UNUSED_SUBMITS.clear();
            }
        }
        if (COLLECTOR.vertices.size > 0) {
            try {
                int size = COLLECTOR.vertices.size;
                // Reuse only bit-identical captured geometry and light-owner exclusion masks.
                if (texture != 0 && cachedSoup.length == size
                        && Arrays.equals(cachedSoup, 0, size, COLLECTOR.vertices.soup, 0, size)
                        && Arrays.equals(cachedMasks, 0, size / 9, COLLECTOR.vertices.masks, 0, size / 9)) {
                    nodeCount = cachedNodes;
                    triangleOffset = cachedOffset;
                    triangles = cachedTriangles;
                    reuses++;
                    COLLECTOR.reset();
                    nanos = System.nanoTime() - started;
                    return;
                }
                var soup = Arrays.copyOf(COLLECTOR.vertices.soup, COLLECTOR.vertices.size);
                Map<TriangleKey, Integer> masks = new HashMap<>();
                boolean excludesOwner = false;
                for (int i = 0; i < soup.length / 9; i++) excludesOwner |= COLLECTOR.vertices.masks[i] != 0;
                if (excludesOwner)
                    for (int i = 0; i < soup.length; i += 9)
                        masks.merge(TriangleKey.soup(soup, i), COLLECTOR.vertices.masks[i / 9], (a, b) -> a & b);
                var bvh = ModelLightOcclusionBvh.build(soup,
                        new ModelLightOcclusionBvh.Budget(MAX_TRIANGLES, 4L * 1024 * 1024, MAX_BUILD_NANOS), new ModelCancellation());
                float[] data = bvh.gpuTexels(2L * 1024 * 1024);
                if (excludesOwner)
                    for (int t = bvh.triangleTexelOffset() * 4; t < data.length; t += 12)
                        data[t + 7] = masks.getOrDefault(TriangleKey.upload(data, t), 0);
                cachedSoup = new float[0];
                upload(data);
                nodeCount = bvh.nodeCount();
                triangleOffset = bvh.triangleTexelOffset();
                triangles = bvh.triangleCount();
                cachedSoup = soup;
                cachedMasks = Arrays.copyOf(COLLECTOR.vertices.masks, soup.length / 9);
                cachedNodes = nodeCount;
                cachedOffset = triangleOffset;
                cachedTriangles = triangles;
                builds++;
            } catch (IOException | RuntimeException e) {
                if (e instanceof ModelLightOcclusionBvh.BudgetExceeded) {
                    frameBuildBudgetFailures++; buildBudgetFailures++;
                }
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }
        COLLECTOR.reset();
        nanos = System.nanoTime() - started;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LivingEntityRenderState extractNative(LivingEntityRenderer renderer, Entity entity, float partialTick) {
        return (LivingEntityRenderState) renderer.createRenderState(entity, partialTick);
    }

    /** The visual extent is independent of the source entity's physical collision box. */
    static AABB shadowBounds(AABB visualBounds, Vec3 interpolationOffset) {
        return visualBounds.minmax(visualBounds.move(interpolationOffset)).inflate(0.5);
    }

    private static void captureBounds(int first, int end) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        float[] soup = COLLECTOR.vertices.soup;
        for (int i = first; i < end; i += 3) {
            minX = Math.min(minX, soup[i]); maxX = Math.max(maxX, soup[i]);
            minY = Math.min(minY, soup[i + 1]); maxY = Math.max(maxY, soup[i + 1]);
            minZ = Math.min(minZ, soup[i + 2]); maxZ = Math.max(maxZ, soup[i + 2]);
        }
        int offset = boundsCount++ * 4;
        BOUNDS[offset] = (float) ((minX + maxX) * 0.5);
        BOUNDS[offset + 1] = (float) ((minY + maxY) * 0.5);
        BOUNDS[offset + 2] = (float) ((minZ + maxZ) * 0.5);
        double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        // Use captured animated vertices, not the entity's smaller gameplay box.
        BOUNDS[offset + 3] = (float) (0.5 * Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.002);
    }

    static boolean intersectsBeam(ClientLightStore.ResolvedLight light, AABB bounds) {
        if ("point".equals(light.definition().type())) return true;
        Vec3 delta=bounds.getCenter().subtract(light.position());
        double radius=0.5*Math.sqrt(bounds.getXsize()*bounds.getXsize()+bounds.getYsize()*bounds.getYsize()+bounds.getZsize()*bounds.getZsize());
        double axial=delta.dot(light.direction());
        if (axial+radius<0) return false;
        if ("area".equals(light.definition().type())) return true;
        double lateral=Math.sqrt(Math.max(0,delta.lengthSqr()-axial*axial));
        double beamRadius=light.definition().config().shadow().sourceSize()
                +Math.max(0,axial+radius)*Math.tan(Math.toRadians(light.definition().coneAngle()*0.5));
        // Enclose both the complete entity AABB and every ray from the emitter disk.
        return lateral<=beamRadius+radius;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void prepareBody(LivingEntityRenderer renderer, LivingEntityRenderState state, Vec3 camera) {
        var offset = renderer.getRenderOffset(state);
        var pose = new PoseStack();
        pose.translate(state.x - camera.x + offset.x, state.y - camera.y + offset.y, state.z - camera.z + offset.z);
        if (state instanceof AvatarRenderState) {
            captureBody(renderer, state, pose);
        } else {
            capturingRenderer = renderer;
            var cameraState = new CameraRenderState();
            cameraState.pos = camera;
            cameraState.initialized = true;
            renderer.submit(state, pose, UNUSED_SUBMITS, cameraState);
        }
    }

    public static boolean captureSubmission(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state, PoseStack pose) {
        if (capturingRenderer != renderer) return false;
        captureBody(renderer, state, pose);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void captureBody(LivingEntityRenderer renderer, LivingEntityRenderState state, PoseStack pose) {
        var transforms = (LivingEntityShadowAccessor) renderer;
        if (!transforms.geometryNode$shadowBodyVisible(state)) return;
        Model<?> model = renderer.getModel();
        var savedParts = model.allParts().stream()
                .map(part -> new SavedPart(part, part.storePose(), part.visible, part.skipDraw)).toList();
        pose.pushPose();
        try {
            if (state.hasPose(Pose.SLEEPING) && state.bedOrientation != null) {
                float headOffset = state.eyeHeight - 0.1F;
                pose.translate(-state.bedOrientation.getStepX() * headOffset, 0, -state.bedOrientation.getStepZ() * headOffset);
            }
            pose.scale(state.scale, state.scale, state.scale);
            transforms.geometryNode$shadowRotations(state, pose, state.bodyRot, state.scale);
            pose.scale(-1, -1, 1);
            transforms.geometryNode$shadowScale(state, pose);
            pose.translate(0, -1.501F, 0);
            COLLECTOR.captureModel(renderer.getModel(), state, pose);
        } finally {
            savedParts.forEach(SavedPart::restore);
            pose.popPose();
        }
    }

    private static void upload(float[] data) {
        int previousTexture = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        int unpack = GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int rowLength = GL11C.glGetInteger(GL11C.GL_UNPACK_ROW_LENGTH);
        int skipRows = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_ROWS);
        int skipPixels = GL11C.glGetInteger(GL11C.GL_UNPACK_SKIP_PIXELS);
        int swapBytes = GL11C.glGetInteger(GL11C.GL_UNPACK_SWAP_BYTES);
        int alignment = GL11C.glGetInteger(GL11C.GL_UNPACK_ALIGNMENT);
        try {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
            int height = (data.length + WIDTH * 4 - 1) / (WIDTH * 4);
            if (allocatedHeight < height) {
                int nextHeight = Math.max(1, Integer.highestOneBit(height - 1) << 1);
                long nextBytes = (long) WIDTH * nextHeight * 16;
                if (!ModelRenderAsset.residency().reserveTransientGpu(nextBytes))
                    throw new IllegalStateException("Entity shadow GPU residency budget exhausted");
                int oldTexture = texture;
                long oldBytes = gpuBytes;
                int nextTexture = GL11C.glGenTextures();
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, nextTexture);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_RGBA32F, WIDTH, nextHeight, 0,
                        GL11C.GL_RGBA, GL11C.GL_FLOAT, 0L);
                if (GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_WIDTH) != WIDTH
                        || GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_TEXTURE_HEIGHT) != nextHeight) {
                    GL11C.glDeleteTextures(nextTexture);
                    ModelRenderAsset.residency().releaseTransientGpu(nextBytes);
                    throw new IllegalStateException("Entity shadow texture allocation failed");
                }
                texture = nextTexture;
                allocatedHeight = nextHeight;
                gpuBytes = nextBytes;
                retire(oldTexture, oldBytes);
            }
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture);
            GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, 0, 0, 0, WIDTH, height, GL11C.GL_RGBA, GL11C.GL_FLOAT,
                    Arrays.copyOf(data, height * WIDTH * 4));
        } finally {
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, unpack);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SWAP_BYTES, swapBytes);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, alignment);
        }
    }

    public static int texture() { return texture; }
    public static int nodeCount() { return nodeCount; }
    public static int triangleTexelOffset() { return triangleOffset; }
    public static int textureWidth() { return WIDTH; }
    public static int boundsCount() { return nodeCount > 0 ? boundsCount : 0; }
    public static float[] bounds() { return BOUNDS; }
    /** Exact GPU geometry and broad-phase inputs, including empty and failed capture frames. */
    static long contentRevision() {
        int count = boundsCount();
        int length = count * 4;
        if (revisionBuilds != builds || revisionTexture != texture || revisionNodes != nodeCount
                || revisionOffset != triangleOffset || revisionTriangles != triangles || revisionBoundsCount != count
                || !Arrays.equals(revisionBounds, 0, length, BOUNDS, 0, length)) {
            contentRevision++;
            revisionBuilds = builds;
            revisionTexture = texture;
            revisionNodes = nodeCount;
            revisionOffset = triangleOffset;
            revisionTriangles = triangles;
            revisionBoundsCount = count;
            System.arraycopy(BOUNDS, 0, revisionBounds, 0, length);
        }
        return contentRevision;
    }
    public static Map<String, Object> report() {
        var out = new java.util.LinkedHashMap<String, Object>(Map.of("entities", entities, "triangles", triangles, "nodes", nodeCount, "omittedEntities", omitted,
                "prepareMs", nanos / 1_000_000.0, "gpuBytes", gpuBytes,
                "failure", failure, "volumeBroadPhase", "captured_entity_spheres_vs_emitter_ray_sweep",
                "scope", "living_body_geometry; excludes equipment, skin overlays and custom geometry; body alpha treated opaque"));
        out.put("bvhBuilds", builds);
        out.put("identicalGeometryReuses", reuses);
        out.put("contentRevision", contentRevision());
        out.put("captureBudgetOmissions", captureBudgetOmissions);
        out.put("buildBudgetFailures", buildBudgetFailures);
        out.put("frameCaptureBudgetOmissions", frameCaptureBudgetOmissions);
        out.put("frameBuildBudgetFailures", frameBuildBudgetFailures);
        out.put("budgetCounterScope", "Lifetime totals and current prepare-frame counts. Capture omissions count entities skipped by the 4 ms deadline, excluding the entity/triangle count caps. Build failures count BVH budget exceptions, including time, memory and upload limits; failure contains the latest reason. No stale geometry fallback.");
        return out;
    }
    public static void clear() {
        retire(texture, gpuBytes);
        gpuBytes = 0;
        texture = allocatedHeight = nodeCount = triangleOffset = triangles = entities = omitted = 0;
        frameCaptureBudgetOmissions = frameBuildBudgetFailures = 0;
        boundsCount = 0;
        cachedSoup = new float[0];
        cachedMasks = new int[0];
        COLLECTOR.reset();
    }
    private static void retire(int oldTexture, long bytes) {
        if (oldTexture != 0) RenderSystem.queueFencedTask(() -> {
            GL11C.glDeleteTextures(oldTexture);
            ModelRenderAsset.residency().releaseTransientGpu(bytes);
        });
    }

    private record Candidate(Entity entity, double distance, int excluded, float partialTick) {}
    private record SavedPart(ModelPart part, PartPose pose, boolean visible, boolean skipDraw) {
        void restore() { part.loadPose(pose); part.visible = visible; part.skipDraw = skipDraw; }
    }

    private static final class Collector {
        final Triangles vertices = new Triangles();
        void begin(int mask) { vertices.excluded = mask; vertices.overflow = false; vertices.quadSize = 0; }
        void reset() { vertices.size = 0; }

        <S> void captureModel(Model<? super S> model, S state, PoseStack pose) {
            model.setupAnim(state);
            // Outer player skin texels are mostly transparent; keep the solid animated body only.
            List<ModelPart> overlays = model instanceof PlayerModel player
                    ? List.of(player.hat, player.jacket, player.leftSleeve, player.rightSleeve, player.leftPants, player.rightPants)
                    : List.of();
            boolean[] old = new boolean[overlays.size()];
            for (int i = 0; i < overlays.size(); i++) { old[i] = overlays.get(i).skipDraw; overlays.get(i).skipDraw = true; }
            try { model.renderToBuffer(pose, vertices, 0, 0, -1); }
            finally { for (int i = 0; i < overlays.size(); i++) overlays.get(i).skipDraw = old[i]; }
        }
    }

    static final class Triangles implements VertexConsumer {
        final float[] soup = new float[MAX_TRIANGLES * 9];
        final float[] quad = new float[12];
        final int[] masks = new int[MAX_TRIANGLES];
        int size, quadSize, excluded;
        boolean overflow;
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (overflow) return this;
            quad[quadSize++] = x; quad[quadSize++] = y; quad[quadSize++] = z;
            if (quadSize == 12) {
                quadSize = 0;
                if (size + 18 > soup.length) { overflow = true; throw CaptureOverflow.INSTANCE; }
                triangle(0, 3, 6); triangle(0, 6, 9);
            }
            return this;
        }
        void triangle(int a, int b, int c) {
            System.arraycopy(quad, a, soup, size, 3); System.arraycopy(quad, b, soup, size + 3, 3);
            System.arraycopy(quad, c, soup, size + 6, 3);
            masks[size / 9] = excluded;
            size += 9;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
    private static final class CaptureOverflow extends RuntimeException {
        static final CaptureOverflow INSTANCE = new CaptureOverflow();
        private CaptureOverflow() { super(null, null, false, false); }
    }

    private record TriangleKey(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        static TriangleKey soup(float[] data, int i) {
            return new TriangleKey(data[i], data[i + 1], data[i + 2], data[i + 3], data[i + 4], data[i + 5], data[i + 6], data[i + 7], data[i + 8]);
        }
        static TriangleKey upload(float[] data, int i) {
            return new TriangleKey(data[i], data[i + 1], data[i + 2], data[i + 4], data[i + 5], data[i + 6], data[i + 8], data[i + 9], data[i + 10]);
        }
    }
}
