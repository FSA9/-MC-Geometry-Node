package com.mine.geometry_node.client.model.render.compat.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.compat.*;
import com.mine.geometry_node.client.model.render.compat.iris.Iris111LabPbrProjector;
import com.mine.geometry_node.client.model.render.compat.iris.LabPbrProjectionEncoder;
import com.mine.geometry_node.client.model.render.ModelShaderCompatibility;
import com.mine.geometry_node.client.model.render.ModelWorldRenderer;
import com.mine.geometry_node.client.model.runtime.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/** Minimum-loss projection into Minecraft's standard entity pipeline. */
public final class EntityCompatibilityRenderer {
    private static final int FULL_BRIGHT = 15728880;
    private static final Map<GeometryKey, EntityGeometry> GEOMETRY = new HashMap<>();
    private static final Set<GeometryKey> FAILED_GEOMETRY = new HashSet<>();
    private static final Map<TextureKey, CompatibilityTexture> TEXTURES = new HashMap<>();
    private static final Set<TextureKey> FAILED_TEXTURES = new HashSet<>();
    private static final Set<TextureKey> LOGGED_RUNTIME_TEXTURE_FAILURES = new HashSet<>();
    private static long nextTextureId;
    private static ModelProjectorCapability lastProjectorCapability;
    private static Set<ModelCompatibilityLoss> lastLosses = Set.of();

    private EntityCompatibilityRenderer() { }

    public static void submit(PoseStack root, SubmitNodeCollector collector) {
        if (Minecraft.getInstance().level == null) return;
        long started = System.nanoTime();
        ClientModelRuntime runtime = ClientModelRuntime.INSTANCE;
        List<ClientModelInstanceRegistry.ReadyInstance> ready = runtime.instances().readySnapshot();
        if (ready.isEmpty()) {
            prune(ready);
            lastLosses = Set.of();
            ModelShaderCompatibility.reportCompatibility(Set.of(), ModelIntegrationVerification.NOT_APPLICABLE,
                    List.of(), Map.of());
            runtime.recordFrame(0, 0, 0, System.nanoTime() - started, -1);
            return;
        }
        Iris111LabPbrProjector.Snapshot projectorSnapshot = Iris111LabPbrProjector.snapshot(
                ModelResourceReloadListener.reloadGeneration());
        ModelProjectorCapability projector = projectorSnapshot.capability();
        synchronizeCapability(projector);
        runtime.instances().tickAnimations(System.nanoTime());
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        ModelDimensionId dimension = new ModelDimensionId(Minecraft.getInstance().level.dimension().identifier().toString());
        EnumSet<ModelCompatibilityLoss> frameLosses = EnumSet.noneOf(ModelCompatibilityLoss.class);
        EnumMap<ModelDrawRejection, Integer> frameRejections = new EnumMap<>(ModelDrawRejection.class);
        List<String> runtimeFaults = new ArrayList<>();
        if (projector.runtimeFault()) {
            runtimeFaults.add(projectorSnapshot.diagnostic());
        }
        FrameStatistics statistics = new FrameStatistics();
        prune(ready);
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            if (!instance.state().visible() || !dimension.equals(instance.state().dimension())) continue;
            if (instance.state().maxDistance() > 0 && ModelWorldRenderer.worldBounds(
                    instance.pose().modelBounds(), instance.state().placement()).distanceToSqr(camera)
                    > instance.state().maxDistance() * instance.state().maxDistance()) continue;
            submitInstance(root, collector, camera, instance, frameLosses, frameRejections,
                    runtimeFaults, statistics, projector);
        }
        ModelIntegrationVerification verification = frameLosses.remove(ModelCompatibilityLoss.PROJECTOR_HOLDER_PENDING)
                ? ModelIntegrationVerification.PENDING
                : projector.verification();
        if (frameLosses.remove(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED)) {
            runtimeFaults.add("AUXILIARY_TEXTURE_DECODE_FAILED");
        }
        if (frameLosses.remove(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE)) {
            runtimeFaults.add("PROJECTOR_RUNTIME_UNAVAILABLE");
        }
        lastLosses = Set.copyOf(frameLosses);
        Set<ModelIntegrationCapability> capabilities = EnumSet.of(ModelIntegrationCapability.HOST_ENTITY_SHADER,
                ModelIntegrationCapability.BASE_COLOR, ModelIntegrationCapability.ALPHA_MODES);
        if (projector.auxiliaryEnabled()) {
            capabilities.add(ModelIntegrationCapability.LABPBR_AUXILIARY_TEXTURES);
        }
        ModelShaderCompatibility.reportCompatibility(projector.profile(), capabilities, lastLosses,
                verification, runtimeFaults, frameRejections);
        runtime.recordFrame(statistics.drawCalls, statistics.triangles, statistics.singularTransformSkips,
                System.nanoTime() - started, -1);
    }

    public static Set<ModelCompatibilityLoss> lastLosses() { return lastLosses; }

    public static void clear() {
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(texture -> release(manager, texture));
        TEXTURES.clear(); FAILED_TEXTURES.clear(); LOGGED_RUNTIME_TEXTURE_FAILURES.clear();
        GEOMETRY.clear(); FAILED_GEOMETRY.clear();
        nextTextureId = 0; lastProjectorCapability = null; lastLosses = Set.of();
    }

    private static void synchronizeCapability(ModelProjectorCapability capability) {
        if (lastProjectorCapability == null) {
            lastProjectorCapability = capability;
            return;
        }
        if (lastProjectorCapability.auxiliaryEnabled() == capability.auxiliaryEnabled()) {
            lastProjectorCapability = capability;
            return;
        }
        var manager = Minecraft.getInstance().getTextureManager();
        TEXTURES.values().forEach(texture -> release(manager, texture));
        TEXTURES.clear();
        FAILED_TEXTURES.clear();
        LOGGED_RUNTIME_TEXTURE_FAILURES.clear();
        lastProjectorCapability = capability;
    }

    private static void prune(List<ClientModelInstanceRegistry.ReadyInstance> ready) {
        Set<String> assets = new HashSet<>();
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            assets.add(instance.resource().asset().cacheIdentity());
        }
        GEOMETRY.keySet().removeIf(key -> !assets.contains(key.asset()));
        FAILED_GEOMETRY.removeIf(key -> !assets.contains(key.asset()));
        FAILED_TEXTURES.removeIf(key -> !assets.contains(key.asset()));
        LOGGED_RUNTIME_TEXTURE_FAILURES.removeIf(key -> !assets.contains(key.asset()));
        var manager = Minecraft.getInstance().getTextureManager();
        Iterator<Map.Entry<TextureKey, CompatibilityTexture>> iterator = TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TextureKey, CompatibilityTexture> entry = iterator.next();
            if (!assets.contains(entry.getKey().asset())) {
                release(manager, entry.getValue());
                iterator.remove();
            }
        }
    }

    private static void submitInstance(PoseStack root, SubmitNodeCollector collector, Vec3 camera,
                                       ClientModelInstanceRegistry.ReadyInstance instance,
                                       EnumSet<ModelCompatibilityLoss> losses,
                                       EnumMap<ModelDrawRejection, Integer> rejections,
                                       List<String> runtimeFaults, FrameStatistics statistics,
                                       ModelProjectorCapability projector) {
        LoadedModelResource loaded = instance.resource();
        ModelDefinition definition = loaded.definition();
        ModelInstancePlacement placement = instance.state().placement();
        for (int nodeIndex = 0; nodeIndex < definition.nodes().size(); nodeIndex++) {
            ModelNode node = definition.nodes().get(nodeIndex);
            if (node.meshIndex() < 0 || !loaded.metadata().nodeVisible(nodeIndex)
                    || !instance.state().nodeState().visible(nodeIndex)) continue;
            ModelMesh mesh = definition.meshes().get(node.meshIndex());
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
                StaticModelMaterial material = loaded.metadata().material(primitive.materialIndex());
                boolean labPbr = projector.auxiliaryEnabled();
                ModelCompatibilityProjection projection = ModelMaterialFidelityAnalyzer.analyze(
                        projector.profile(), material, node.skinIndex() >= 0);
                losses.addAll(projection.losses());
                ModelMaterialFidelityAnalyzer.addInstanceLosses(material, placement.alpha(),
                        placement.forceDoubleSided(), losses);
                if (!projection.selectable()) {
                    reject(rejections, ModelDrawRejection.UNSUPPORTED_SKINNING);
                    continue;
                }
                StaticModelTexture coordinateSource = coordinateSource(material);
                GeometryKey key = new GeometryKey(loaded.asset().cacheIdentity(), node.meshIndex(), primitiveIndex,
                        coordinateSource.texCoord(), coordinateSource.transform());
                if (FAILED_GEOMETRY.contains(key)) {
                    reject(rejections, ModelDrawRejection.GEOMETRY_PROJECTION_FAILED);
                    continue;
                }
                EntityGeometry geometry;
                try {
                    geometry = GEOMETRY.computeIfAbsent(key, ignored -> project(primitive, coordinateSource));
                } catch (RuntimeException exception) {
                    reject(rejections, ModelDrawRejection.GEOMETRY_PROJECTION_FAILED);
                    runtimeFaults.add("geometry-projection:" + exception.getClass().getSimpleName());
                    if (FAILED_GEOMETRY.add(key)) {
                        GeometryNode.LOGGER.warn("Skipping compatibility draw whose geometry could not be projected: {}",
                                loaded.asset().cacheIdentity(), exception);
                    }
                    continue;
                }
                CompatibilityTexture texture;
                try {
                    texture = texture(loaded, material, labPbr);
                } catch (TextureProjectionFailure exception) {
                    TextureKey failed = textureKey(loaded, material, labPbr);
                    if (exception.cacheForAssetLifetime()) {
                        runtimeFaults.add("texture-decode:" + exception.getClass().getSimpleName());
                        if (FAILED_TEXTURES.add(failed)) {
                            GeometryNode.LOGGER.warn("Skipping compatibility draw whose base-color asset is invalid: {}",
                                    loaded.asset().cacheIdentity(), exception);
                        }
                    } else {
                        runtimeFaults.add("texture-projection:" + exception.getClass().getSimpleName());
                        losses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
                        if (LOGGED_RUNTIME_TEXTURE_FAILURES.add(failed)) {
                            GeometryNode.LOGGER.warn("Compatibility texture runtime projection failed; this generation may retry: {}",
                                    loaded.asset().cacheIdentity(), exception);
                        }
                    }
                    reject(rejections, ModelDrawRejection.TEXTURE_PROJECTION_FAILED);
                    continue;
                }
                mergeTextureLosses(losses, texture.losses());
                if (labPbr) Iris111LabPbrProjector.reportHolderState(texture.texture(), losses);
                RenderType renderType = renderType(material, placement, texture.identifier());
                Matrix4f transform = new Matrix4f().translate(
                        (float) (placement.position().x - camera.x), (float) (placement.position().y - camera.y),
                        (float) (placement.position().z - camera.z)).rotate(placement.rotation()).scale(placement.scale())
                        .mul(instance.pose().worldMatrix(nodeIndex));
                float determinant = transform.determinant3x3();
                if (!Float.isFinite(determinant) || Math.abs(determinant) <= 1.0E-8F) {
                    reject(rejections, ModelDrawRejection.SINGULAR_TRANSFORM);
                    statistics.singularTransformSkips++;
                    continue;
                }
                boolean mirrored = determinant < 0;
                root.pushPose();
                try {
                    root.mulPose(transform);
                    Vector3f localCenter = boundsCenter(primitive.bounds());
                    Vector3f drawPosition = transform.transformPosition(localCenter);
                    Vector3d worldPosition = new Vector3d(camera.x + drawPosition.x,
                            camera.y + drawPosition.y, camera.z + drawPosition.z);
                    int light = placement.fullBright() ? FULL_BRIGHT : LevelRenderer.getLightCoords(
                            Minecraft.getInstance().level, BlockPos.containing(worldPosition.x, worldPosition.y, worldPosition.z));
                    float red = material.red() * placement.red(), green = material.green() * placement.green();
                    float blue = material.blue() * placement.blue();
                    float alpha = (material.alphaMode() == ModelAlphaMode.OPAQUE ? 1 : material.alpha()) * placement.alpha();
                    collector.submitCustomGeometry(root, renderType,
                            (pose, vertices) -> geometry.emit(pose, vertices, red, green, blue, alpha, light, mirrored));
                    statistics.drawCalls++;
                    statistics.triangles += geometry.triangleCount();
                } finally {
                    root.popPose();
                }
            }
        }
    }

    private static RenderType renderType(StaticModelMaterial material, ModelInstancePlacement placement,
                                         Identifier texture) {
        boolean doubleSided = material.doubleSided() || placement.forceDoubleSided();
        if (material.alphaMode() == ModelAlphaMode.BLEND || placement.alpha() < 0.999F) {
            return RenderTypes.entityTranslucent(texture, false);
        }
        if (material.alphaMode() == ModelAlphaMode.MASK || doubleSided) {
            return RenderTypes.entityCutout(texture, false);
        }
        return RenderTypes.entityCutoutCull(texture);
    }

    static Vector3f boundsCenter(ModelBounds bounds) {
        return new Vector3f((bounds.min().x() + bounds.max().x()) * 0.5F,
                (bounds.min().y() + bounds.max().y()) * 0.5F,
                (bounds.min().z() + bounds.max().z()) * 0.5F);
    }

    private static EntityGeometry project(ModelPrimitive primitive, StaticModelTexture coordinateSource) {
        ModelVertexAttribute positions = required(primitive, ModelAttributeSemantic.POSITION);
        ModelVertexAttribute normals = primitive.attributes().get(ModelAttributeSemantic.NORMAL);
        ModelVertexAttribute uv = primitive.attributes().get(ModelAttributeSemantic.indexed(
                ModelAttributeSemantic.Kind.TEXCOORD, coordinateSource.texCoord()));
        ModelVertexAttribute colors = primitive.attributes().get(ModelAttributeSemantic.COLOR_0);
        float[] output = new float[primitive.indices().indexCount() * 12];
        int cursor = 0;
        for (int i = 0; i < primitive.indices().indexCount(); i++) {
            int vertex = Math.toIntExact(primitive.indices().indexAt(i));
            cursor = copy(output, cursor, positions, vertex, 3, new float[]{0, 0, 0});
            cursor = copy(output, cursor, normals, vertex, 3, new float[]{0, 1, 0});
            float[] selectedUv = uv == null && !coordinateSource.present()
                    ? syntheticTriangleUv(i % 3)
                    : values(uv, vertex, 2, new float[]{0, 0});
            ModelTextureTransform t = coordinateSource.transform();
            float x = selectedUv[0] * t.scaleX(), y = selectedUv[1] * t.scaleY();
            float cos = (float) Math.cos(t.rotation()), sin = (float) Math.sin(t.rotation());
            output[cursor++] = cos * x - sin * y + t.offsetX();
            output[cursor++] = sin * x + cos * y + t.offsetY();
            cursor = copy(output, cursor, colors, vertex, 4, new float[]{1, 1, 1, 1});
        }
        if (normals == null) generateFaceNormals(output);
        return new EntityGeometry(output);
    }

    private static float[] syntheticTriangleUv(int triangleVertex) {
        return switch (triangleVertex) {
            case 0 -> new float[]{0, 0};
            case 1 -> new float[]{1, 0};
            default -> new float[]{0, 1};
        };
    }

    private static void generateFaceNormals(float[] vertices) {
        for (int triangle = 0; triangle < vertices.length; triangle += 36) {
            float ax = vertices[triangle + 12] - vertices[triangle];
            float ay = vertices[triangle + 13] - vertices[triangle + 1];
            float az = vertices[triangle + 14] - vertices[triangle + 2];
            float bx = vertices[triangle + 24] - vertices[triangle];
            float by = vertices[triangle + 25] - vertices[triangle + 1];
            float bz = vertices[triangle + 26] - vertices[triangle + 2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0E-12F) { nx /= length; ny /= length; nz /= length; }
            else { nx = 0; ny = 1; nz = 0; }
            for (int vertex = 0; vertex < 3; vertex++) {
                int offset = triangle + vertex * 12;
                vertices[offset + 3] = nx; vertices[offset + 4] = ny; vertices[offset + 5] = nz;
            }
        }
    }

    private static ModelVertexAttribute required(ModelPrimitive primitive, ModelAttributeSemantic semantic) {
        ModelVertexAttribute value = primitive.attributes().get(semantic);
        if (value == null) throw new IllegalStateException("validated primitive lacks " + semantic);
        return value;
    }

    private static int copy(float[] target, int cursor, ModelVertexAttribute source, int element,
                            int components, float[] fallback) {
        float[] values = values(source, element, components, fallback);
        for (float value : values) target[cursor++] = value;
        return cursor;
    }

    private static float[] values(ModelVertexAttribute attribute, int element, int count, float[] fallback) {
        if (attribute == null) return fallback.clone();
        ByteBuffer data = attribute.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
        int stride = attribute.componentType().byteSize() * attribute.componentCount();
        float[] result = fallback.clone();
        for (int component = 0; component < Math.min(count, attribute.componentCount()); component++) {
            int offset = element * stride + component * attribute.componentType().byteSize();
            result[component] = component(data, offset, attribute.componentType(), attribute.normalized());
        }
        return result;
    }

    private static float component(ByteBuffer data, int offset, ModelComponentType type, boolean normalized) {
        return switch (type) {
            case FLOAT32 -> data.getFloat(offset);
            case UINT8 -> normalized ? Byte.toUnsignedInt(data.get(offset)) / 255F : Byte.toUnsignedInt(data.get(offset));
            case INT8 -> normalized ? Math.max(data.get(offset) / 127F, -1F) : data.get(offset);
            case UINT16 -> normalized ? Short.toUnsignedInt(data.getShort(offset)) / 65535F : Short.toUnsignedInt(data.getShort(offset));
            case INT16 -> normalized ? Math.max(data.getShort(offset) / 32767F, -1F) : data.getShort(offset);
            case UINT32 -> normalized ? Integer.toUnsignedLong(data.getInt(offset)) / 4294967295F
                    : Integer.toUnsignedLong(data.getInt(offset));
        };
    }

    private static CompatibilityTexture texture(LoadedModelResource loaded, StaticModelMaterial material,
                                                boolean labPbr) {
        TextureKey key = textureKey(loaded, material, labPbr);
        if (FAILED_TEXTURES.contains(key)) {
            throw TextureProjectionFailure.asset("compatibility base-color asset previously failed", null);
        }
        return TEXTURES.computeIfAbsent(key, ignored -> {
            Identifier id = Identifier.fromNamespaceAndPath(GeometryNode.MODID,
                    "model_compat/texture_" + nextTextureId++);
            Iris111LabPbrProjector.LabPbrAlbedoTexture dynamic = null;
            boolean registered = false;
            List<NativeImage> inputs = new ArrayList<>();
            try {
                NativeImage image;
                try {
                    image = decode(loaded, material.baseColorTexture(), inputs);
                } catch (IOException | RuntimeException assetFailure) {
                    throw TextureProjectionFailure.asset("failed to decode compatibility base-color asset", assetFailure);
                }
                if (image == null) {
                    image = new NativeImage(1, 1, false);
                    image.setPixel(0, 0, 0xFFFFFFFF);
                    inputs.add(image);
                }
                if (key.alphaMode() == ModelAlphaMode.OPAQUE) {
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                        image.setPixel(x, y, image.getPixel(x, y) | 0xFF000000);
                    }
                }
                NativeImage normal = null, specular = null;
                EnumSet<ModelCompatibilityLoss> textureLosses = EnumSet.noneOf(ModelCompatibilityLoss.class);
                if (key.labPbr()) {
                    NativeImage mr = auxiliaryInput(loaded, material.metallicRoughnessTexture(), material, inputs,
                            textureLosses, ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE,
                            ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE);
                    NativeImage normalInput = auxiliaryInput(loaded, material.normalTexture(), material, inputs,
                            textureLosses, ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE);
                    NativeImage ao = auxiliaryInput(loaded, material.occlusionTexture(), material, inputs,
                            textureLosses, ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE);
                    try {
                        int specWidth = dimension(mr, null), specHeight = dimensionHeight(mr, null);
                        specular = LabPbrProjectionEncoder.buildSpecular(mr, specWidth, specHeight,
                                material.metallicFactor(), material.roughnessFactor());
                        inputs.add(specular);
                        if (!LabPbrProjectionEncoder.metallicEndpointsOnly(mr, material.metallicFactor())) {
                            textureLosses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
                        }
                        int normalWidth = dimension(normalInput, ao), normalHeight = dimensionHeight(normalInput, ao);
                        normal = LabPbrProjectionEncoder.buildNormal(normalInput, ao, normalWidth, normalHeight,
                                material.normalScale(), material.occlusionStrength());
                        if (normal != null) inputs.add(normal);
                    } catch (RuntimeException auxiliaryFailure) {
                        textureLosses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
                        textureLosses.add(ModelCompatibilityLoss.METALLIC_UNREPRESENTABLE);
                        if (material.roughnessFactor() != 1 || material.metallicRoughnessTexture().present()) {
                            textureLosses.add(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE);
                        }
                        if (material.normalTexture().present()) {
                            textureLosses.add(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE);
                        }
                        if (material.occlusionTexture().present()) {
                            textureLosses.add(ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE);
                        }
                        normal = null;
                        specular = null;
                    }
                }
                // The caller retains failure cleanup until the composite constructor succeeds.
                // NativeImage.close is idempotent, so partial DynamicTexture construction remains safe.
                dynamic = new Iris111LabPbrProjector.LabPbrAlbedoTexture(image, normal, specular);
                inputs.remove(image);
                inputs.remove(normal);
                inputs.remove(specular);
                Minecraft.getInstance().getTextureManager().register(id, dynamic);
                registered = true;
                if (key.labPbr()) Iris111LabPbrProjector.afterAlbedoRegistration(dynamic);
                return new CompatibilityTexture(id, dynamic, textureLosses);
            } catch (TextureProjectionFailure exception) {
                if (registered) Minecraft.getInstance().getTextureManager().release(id);
                else if (dynamic != null) dynamic.close();
                throw exception;
            } catch (RuntimeException exception) {
                if (registered) Minecraft.getInstance().getTextureManager().release(id);
                else if (dynamic != null) dynamic.close();
                throw TextureProjectionFailure.runtime("compatibility texture host operation failed", exception);
            } finally {
                inputs.forEach(NativeImage::close);
            }
        });
    }

    private static TextureKey textureKey(LoadedModelResource loaded, StaticModelMaterial material, boolean labPbr) {
        return new TextureKey(loaded.asset().cacheIdentity(), material, labPbr);
    }

    private static NativeImage decode(LoadedModelResource loaded, StaticModelTexture texture, List<NativeImage> owned)
            throws IOException {
        if (!texture.present()) return null;
        NativeImage image = NativeImage.read(loaded.definition().images().get(texture.imageIndex()).encodedData());
        owned.add(image);
        return image;
    }

    private static NativeImage compatibleInput(LoadedModelResource loaded, StaticModelTexture texture,
                                                StaticModelMaterial material, List<NativeImage> owned) throws IOException {
        if (!texture.present()) return null;
        StaticModelTexture coordinates = coordinateSource(material);
        if (texture.texCoord() != coordinates.texCoord()
                || !texture.transform().equals(coordinates.transform())
                || !texture.sampler().equals(coordinates.sampler())) return null;
        return decode(loaded, texture, owned);
    }

    private static NativeImage auxiliaryInput(LoadedModelResource loaded, StaticModelTexture texture,
                                              StaticModelMaterial material, List<NativeImage> owned,
                                              EnumSet<ModelCompatibilityLoss> losses,
                                              ModelCompatibilityLoss... roleLosses) {
        try {
            return compatibleInput(loaded, texture, material, owned);
        } catch (IOException | RuntimeException exception) {
            losses.add(ModelCompatibilityLoss.TEXTURE_DECODE_FAILED);
            losses.addAll(Arrays.asList(roleLosses));
            return null;
        }
    }

    private static void mergeTextureLosses(EnumSet<ModelCompatibilityLoss> target,
                                           Set<ModelCompatibilityLoss> textureLosses) {
        if (textureLosses.contains(ModelCompatibilityLoss.ROUGHNESS_UNREPRESENTABLE)) {
            target.remove(ModelCompatibilityLoss.ROUGHNESS_APPROXIMATED);
        }
        if (textureLosses.contains(ModelCompatibilityLoss.NORMAL_TEXTURE_UNREPRESENTABLE)) {
            target.remove(ModelCompatibilityLoss.NORMAL_TEXTURE_APPROXIMATED);
        }
        if (textureLosses.contains(ModelCompatibilityLoss.OCCLUSION_TEXTURE_UNREPRESENTABLE)) {
            target.remove(ModelCompatibilityLoss.OCCLUSION_TEXTURE_APPROXIMATED);
        }
        target.addAll(textureLosses);
    }

    private static StaticModelTexture coordinateSource(StaticModelMaterial material) {
        StaticModelTexture[] textures = {material.baseColorTexture(), material.metallicRoughnessTexture(),
                material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (StaticModelTexture texture : textures) if (texture.present()) return texture;
        return StaticModelTexture.absent();
    }

    private static int dimension(NativeImage first, NativeImage second) {
        return first != null ? first.getWidth() : second != null ? second.getWidth() : 1;
    }

    private static void reject(EnumMap<ModelDrawRejection, Integer> rejections, ModelDrawRejection reason) {
        rejections.merge(reason, 1, Integer::sum);
    }
    private static int dimensionHeight(NativeImage first, NativeImage second) {
        return first != null ? first.getHeight() : second != null ? second.getHeight() : 1;
    }

    private record GeometryKey(String asset, int mesh, int primitive, int uvSet, ModelTextureTransform transform) { }
    private record TextureKey(String asset, StaticModelMaterial material, boolean labPbr) {
        ModelAlphaMode alphaMode() { return material.alphaMode(); }
    }
    private static void release(net.minecraft.client.renderer.texture.TextureManager manager,
                                CompatibilityTexture texture) {
        Iris111LabPbrProjector.beforeAlbedoRelease(texture.texture());
        manager.release(texture.identifier());
    }

    private record CompatibilityTexture(Identifier identifier,
                                        Iris111LabPbrProjector.LabPbrAlbedoTexture texture,
                                        Set<ModelCompatibilityLoss> losses) {
        private CompatibilityTexture {
            losses = Set.copyOf(losses);
        }
    }
    private static final class FrameStatistics {
        private int drawCalls;
        private long triangles;
        private int singularTransformSkips;
    }

    private record EntityGeometry(float[] vertices) {
        long triangleCount() { return vertices.length / 36L; }

        void emit(PoseStack.Pose pose, VertexConsumer out, float red, float green, float blue, float alpha, int light,
                  boolean mirrored) {
            int triangleCount = vertices.length / 36;
            for (int triangle = 0; triangle < triangleCount; triangle++) {
                int first = triangle * 3;
                int second = mirrored ? first + 2 : first + 1;
                int third = mirrored ? first + 1 : first + 2;
                // Entity RenderTypes assemble QUADS. Duplicating the third vertex preserves the
                // glTF triangle as the quad's first triangle and makes its second triangle degenerate.
                emitVertex(pose, out, first, red, green, blue, alpha, light);
                emitVertex(pose, out, second, red, green, blue, alpha, light);
                emitVertex(pose, out, third, red, green, blue, alpha, light);
                emitVertex(pose, out, third, red, green, blue, alpha, light);
            }
        }

        private void emitVertex(PoseStack.Pose pose, VertexConsumer out, int sourceVertex,
                                float red, float green, float blue, float alpha, int light) {
            int i = sourceVertex * 12;
            out.addVertex(pose, vertices[i], vertices[i + 1], vertices[i + 2])
                    .setColor(vertices[i + 8] * red, vertices[i + 9] * green,
                            vertices[i + 10] * blue, vertices[i + 11] * alpha)
                    .setUv(vertices[i + 6], vertices[i + 7]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose, vertices[i + 3], vertices[i + 4], vertices[i + 5]);
        }
    }
}
