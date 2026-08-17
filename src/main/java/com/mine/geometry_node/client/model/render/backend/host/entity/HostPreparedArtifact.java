package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.IrisLabPbrProjector;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.LabPbrProjectionEncoder;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.gpu.DecodedModelImage;
import com.mine.geometry_node.client.model.gpu.minecraft.NativeImageModelDecoder;
import com.mine.geometry_node.client.model.render.integration.ModelCompatibilityLoss;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.ModelAlphaMode;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.util.*;
import java.util.function.DoubleConsumer;

/** Asset-owned HOST plan plus render-thread-owned binding variants. */
public final class HostPreparedArtifact {
    private final HostDrawPlan drawPlan;
    private final Map<Integer, DecodedModelImage> decodedImages;
    private final Map<Integer, String> imageFailures;
    private final Map<StaticModelMaterial, LabPbrImages> labPbrImages;
    private final HostPreparationMemoryBudget.Reservation memoryReservation;
    final Map<TextureKey, CompatibilityTexture> textures = new HashMap<>();
    final Set<TextureKey> failedTextures = new HashSet<>();
    final Set<TextureKey> loggedRuntimeTextureFailures = new HashSet<>();
    final Set<String> loggedGeometryFailures = new HashSet<>();
    private final Set<BindingRequest> readyBindings = new HashSet<>();
    private final Set<BindingRequest> pendingBindings = new HashSet<>();
    private final Set<BindingRequest> failedBindings = new HashSet<>();
    private final Map<HostEntityGeometry, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>>
            staticVariants = new IdentityHashMap<>();
    private final Map<HostEntityGeometry, IdentityHashMap<Object, HostPackedLightVariantGate>>
            staticVariantGates = new IdentityHashMap<>();
    private long bindingGeneration;
    private long staticGeneration;
    private boolean closed;

    HostPreparedArtifact(HostDrawPlan drawPlan, Map<Integer, DecodedModelImage> decodedImages,
                         Map<Integer, String> imageFailures,
                         Map<StaticModelMaterial, LabPbrImages> labPbrImages,
                         HostPreparationMemoryBudget.Reservation memoryReservation) {
        this.drawPlan = Objects.requireNonNull(drawPlan, "drawPlan");
        this.decodedImages = Map.copyOf(decodedImages);
        this.imageFailures = Map.copyOf(imageFailures);
        this.labPbrImages = Map.copyOf(labPbrImages);
        this.memoryReservation = Objects.requireNonNull(memoryReservation, "memoryReservation");
    }

    public static HostPreparedArtifact prepare(ModelDefinition definition, StaticModelRenderMetadata metadata) {
        return prepare(definition, metadata, ignored -> {});
    }

    public static HostPreparedArtifact prepare(ModelDefinition definition, StaticModelRenderMetadata metadata,
                                               DoubleConsumer progress) {
        HostPreparationMemoryBudget.Reservation memory = HostPreparationMemoryBudget.INSTANCE.reserve(
                estimatedAdditionalBytes(definition, metadata));
        try {
            HostDrawPlan plan = HostDrawPlan.compile(
                    definition, metadata, value -> progress.accept(value * 0.70));
            Map<Integer, DecodedModelImage> decoded = new HashMap<>();
            Map<Integer, String> failures = new HashMap<>();
            NativeImageModelDecoder decoder = new NativeImageModelDecoder();
            for (int index = 0; index < definition.images().size(); index++) {
                try {
                    decoded.put(index, decoder.decode(definition.images().get(index)));
                } catch (Exception failure) {
                    failures.put(index, failure.getClass().getSimpleName() + ": "
                            + Objects.toString(failure.getMessage(), "image decode failed"));
                }
                progress.accept(0.70 + 0.15 * (index + 1.0) / definition.images().size());
            }
            Map<StaticModelMaterial, LabPbrImages> labPbr = new HashMap<>();
            for (int index = 0; index < definition.materials().size(); index++) {
                StaticModelMaterial material = metadata.material(index);
                if (!labPbr.containsKey(material)) {
                    StaticModelTexture coordinate = coordinateSource(material);
                    DecodedModelImage mr = compatible(material.metallicRoughnessTexture(), coordinate, decoded);
                    DecodedModelImage normal = compatible(material.normalTexture(), coordinate, decoded);
                    DecodedModelImage ao = compatible(material.occlusionTexture(), coordinate, decoded);
                    int specWidth = mr == null ? 1 : mr.width(), specHeight = mr == null ? 1 : mr.height();
                    int normalWidth = normal != null ? normal.width() : ao != null ? ao.width() : 1;
                    int normalHeight = normal != null ? normal.height() : ao != null ? ao.height() : 1;
                    labPbr.put(material, new LabPbrImages(
                            LabPbrProjectionEncoder.buildDecodedNormal(normal, ao, normalWidth, normalHeight,
                                    material.normalScale(), material.occlusionStrength()),
                            LabPbrProjectionEncoder.buildDecodedSpecular(mr, specWidth, specHeight,
                                    material.metallicFactor(), material.roughnessFactor()),
                            LabPbrProjectionEncoder.decodedMetallicEndpointsOnly(mr, material.metallicFactor()),
                            failedCompatible(material.metallicRoughnessTexture(), coordinate, failures),
                            failedCompatible(material.normalTexture(), coordinate, failures),
                            failedCompatible(material.occlusionTexture(), coordinate, failures)));
                }
                progress.accept(0.85 + 0.15 * (index + 1.0) / definition.materials().size());
            }
            progress.accept(1.0);
            return new HostPreparedArtifact(plan, decoded, failures, labPbr, memory);
        } catch (RuntimeException | Error failure) {
            memory.close();
            throw failure;
        }
    }

    public HostDrawPlan drawPlan() { return drawPlan; }

    DecodedModelImage decodedImage(int index) throws java.io.IOException {
        DecodedModelImage image = decodedImages.get(index);
        if (image != null) return image;
        throw new java.io.IOException(imageFailures.getOrDefault(index, "model image was not decoded"));
    }

    LabPbrImages labPbrImages(StaticModelMaterial material) {
        return Objects.requireNonNull(labPbrImages.get(material), "prepared LabPBR material");
    }

    boolean bindingsReady(BindingRequest request) { return readyBindings.contains(request); }
    boolean beginBindings(BindingRequest request) {
        return !readyBindings.contains(request) && !failedBindings.contains(request) && pendingBindings.add(request);
    }
    boolean bindingsFailed(BindingRequest request) { return failedBindings.contains(request); }
    long bindingGeneration() { return bindingGeneration; }
    boolean bindingGeneration(long generation) { return !closed && bindingGeneration == generation; }
    void completeBindings(BindingRequest request, long generation) {
        if (!bindingGeneration(generation)) return;
        pendingBindings.remove(request);
        readyBindings.add(request);
    }
    void failBindings(BindingRequest request) {
        pendingBindings.remove(request);
        failedBindings.add(request);
    }
    void cancelBindings(BindingRequest request) { pendingBindings.remove(request); }

    long staticGeneration() { return staticGeneration; }
    boolean staticGeneration(long generation) { return !closed && staticGeneration == generation; }

    HostPackedLightVariantGate staticVariantGate(HostEntityGeometry geometry, Object instanceIdentity) {
        return staticVariantGates.computeIfAbsent(Objects.requireNonNull(geometry, "geometry"),
                        ignored -> new IdentityHashMap<>())
                .computeIfAbsent(Objects.requireNonNull(instanceIdentity, "instanceIdentity"),
                        ignored -> new HostPackedLightVariantGate());
    }

    HostStaticVariantBudget.Reservation reserveStaticVariant(long bytes) {
        if (closed) return null;
        return HostStaticVariantBudget.INSTANCE.tryReserve(this, bytes);
    }

    HostStaticGeometryVariant staticVariant(HostEntityGeometry geometry, HostStaticVariantKey key,
                                            long generation) {
        if (!staticGeneration(generation)) return null;
        LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = staticVariants.get(geometry);
        return variants == null ? null : variants.get(key);
    }

    /**
     * Detaches one LRU entry before a budget retry. The caller must retire the result behind a GPU fence and
     * continue through the immediate path until that retirement releases its reservation.
     */
    List<HostStaticGeometryVariant> detachLeastRecentlyUsedStaticVariant(
            HostEntityGeometry geometry, HostStaticVariantKey requestedKey, long generation) {
        if (!staticGeneration(generation)) return List.of();
        LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = staticVariants.get(geometry);
        if (variants == null || variants.containsKey(requestedKey) || variants.isEmpty()) return List.of();
        Iterator<HostStaticGeometryVariant> eldest = variants.values().iterator();
        HostStaticGeometryVariant detached = eldest.next();
        eldest.remove();
        if (variants.isEmpty()) staticVariants.remove(geometry);
        return List.of(detached);
    }

    List<HostStaticGeometryVariant> detachStaticVariantForBudget(
            HostEntityGeometry requestedGeometry, HostStaticVariantKey requestedKey, long generation) {
        return detachLeastRecentlyUsedStaticVariant(requestedGeometry, requestedKey, generation);
    }

    StaticVariantPublication publishStaticVariant(HostEntityGeometry geometry, HostStaticVariantKey key,
                                                   long generation, HostStaticGeometryVariant variant) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(variant, "variant");
        if (!staticGeneration(generation)) return new StaticVariantPublication(false, List.of(variant));
        LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = staticVariants.computeIfAbsent(
                geometry, ignored -> new LinkedHashMap<>(8, 0.75f, true));
        HostStaticGeometryVariant existing = variants.get(key);
        if (existing != null) return new StaticVariantPublication(false, List.of(variant));

        List<HostStaticGeometryVariant> retired = new ArrayList<>(1);
        if (variants.size() >= HostStaticVariantBudget.MAX_VARIANTS_PER_GEOMETRY) {
            Iterator<HostStaticGeometryVariant> eldest = variants.values().iterator();
            retired.add(eldest.next());
            eldest.remove();
        }
        variants.put(key, variant);
        return new StaticVariantPublication(true, retired);
    }

    List<HostStaticGeometryVariant> detachStaticVariants() {
        List<HostStaticGeometryVariant> detached = new ArrayList<>();
        for (Map<HostStaticVariantKey, HostStaticGeometryVariant> variants : staticVariants.values()) {
            detached.addAll(variants.values());
        }
        staticVariants.clear();
        staticVariantGates.clear();
        staticGeneration++;
        return detached;
    }

    void closeStaticVariants() {
        detachStaticVariants().forEach(HostStaticGeometryVariant::close);
    }

    long bindingBytes(TextureKey key) {
        long bytes = key.material().baseColorTexture().present()
                ? decodedImages.getOrDefault(key.material().baseColorTexture().imageIndex(), FALLBACK_IMAGE).byteSize()
                : FALLBACK_IMAGE.byteSize();
        if (key.labPbr()) {
            LabPbrImages prepared = labPbrImages(key.material());
            bytes = Math.addExact(bytes, prepared.specular().byteSize());
            if (prepared.normal() != null) bytes = Math.addExact(bytes, prepared.normal().byteSize());
        }
        return bytes;
    }

    int bindingObjects(TextureKey key) {
        if (!key.labPbr()) return 1;
        return labPbrImages(key.material()).normal() == null ? 2 : 3;
    }

    List<CompatibilityTexture> removeBindings(Collection<TextureKey> keys) {
        List<CompatibilityTexture> removed = new ArrayList<>();
        for (TextureKey key : keys) {
            CompatibilityTexture texture = textures.remove(key);
            if (texture != null) removed.add(texture);
        }
        return removed;
    }

    private static DecodedModelImage compatible(StaticModelTexture texture, StaticModelTexture coordinate,
                                                Map<Integer, DecodedModelImage> decoded) {
        return compatibleCoordinates(texture, coordinate) ? decoded.get(texture.imageIndex()) : null;
    }

    private static boolean failedCompatible(StaticModelTexture texture, StaticModelTexture coordinate,
                                            Map<Integer, String> failures) {
        return compatibleCoordinates(texture, coordinate) && failures.containsKey(texture.imageIndex());
    }

    private static boolean compatibleCoordinates(StaticModelTexture texture, StaticModelTexture coordinate) {
        return texture.present() && texture.texCoord() == coordinate.texCoord()
                && texture.transform().equals(coordinate.transform())
                && texture.sampler().equals(coordinate.sampler());
    }

    private static StaticModelTexture coordinateSource(StaticModelMaterial material) {
        StaticModelTexture[] textures = {material.baseColorTexture(), material.metallicRoughnessTexture(),
                material.normalTexture(), material.occlusionTexture(), material.emissiveTexture()};
        for (StaticModelTexture texture : textures) if (texture.present()) return texture;
        return StaticModelTexture.absent();
    }

    List<CompatibilityTexture> detachBindings() {
        if (closed) return List.of();
        List<CompatibilityTexture> detached = List.copyOf(textures.values());
        textures.clear();
        failedTextures.clear();
        loggedRuntimeTextureFailures.clear();
        readyBindings.clear();
        pendingBindings.clear();
        failedBindings.clear();
        bindingGeneration++;
        return detached;
    }

    void close(TextureManager manager) {
        if (closed) return;
        List<CompatibilityTexture> detached = detachBindings();
        List<HostStaticGeometryVariant> detachedStatic = detachStaticVariants();
        closed = true;
        try {
            try {
                releaseBindings(manager, detached);
            } finally {
                detachedStatic.forEach(HostStaticGeometryVariant::close);
            }
        } finally {
            memoryReservation.close();
        }
    }

    static long estimatedAdditionalBytes(ModelDefinition definition, StaticModelRenderMetadata metadata) {
        long bytes = HostDrawPlan.projectedGeometryBytes(definition, metadata);
        long maximumScratch = 0;
        for (var image : definition.images()) {
            long imageBytes = imageBytes(image.width(), image.height());
            bytes = Math.addExact(bytes, imageBytes);
            maximumScratch = Math.max(maximumScratch, imageBytes);
        }
        Set<StaticModelMaterial> uniqueMaterials = new HashSet<>();
        for (int index = 0; index < definition.materials().size(); index++) {
            StaticModelMaterial material = metadata.material(index);
            if (!uniqueMaterials.add(material)) continue;
            StaticModelTexture coordinate = coordinateSource(material);
            StaticModelTexture mr = material.metallicRoughnessTexture();
            long specularBytes = compatibleCoordinates(mr, coordinate)
                    ? sourceImageBytes(definition, mr) : 4L;
            bytes = Math.addExact(bytes, specularBytes);
            maximumScratch = Math.max(maximumScratch, specularBytes);
            StaticModelTexture normal = material.normalTexture();
            StaticModelTexture ao = material.occlusionTexture();
            long normalBytes = compatibleCoordinates(normal, coordinate)
                    ? sourceImageBytes(definition, normal)
                    : compatibleCoordinates(ao, coordinate) ? sourceImageBytes(definition, ao) : 0L;
            bytes = Math.addExact(bytes, normalBytes);
            maximumScratch = Math.max(maximumScratch, normalBytes);
        }
        return Math.addExact(bytes, maximumScratch);
    }

    private static long sourceImageBytes(ModelDefinition definition, StaticModelTexture texture) {
        var image = definition.images().get(texture.imageIndex());
        return imageBytes(image.width(), image.height());
    }

    private static long imageBytes(int width, int height) {
        return Math.multiplyExact(Math.multiplyExact((long) width, height), 4L);
    }

    static void releaseBindings(TextureManager manager, List<CompatibilityTexture> bindings) {
        for (CompatibilityTexture binding : bindings) {
            try {
                try {
                    IrisLabPbrProjector.beforeAlbedoRelease(binding.texture());
                } finally {
                    manager.release(binding.identifier());
                }
            } catch (RuntimeException failure) {
                GeometryNode.LOGGER.warn("Failed to retire HOST texture binding {}", binding.identifier(), failure);
            }
        }
    }

    record TextureKey(StaticModelMaterial material, boolean labPbr, boolean opaqueFallback) {
        ModelAlphaMode alphaMode() { return material.alphaMode(); }
    }

    record BindingRequest(Set<TextureKey> keys) {
        BindingRequest { keys = Set.copyOf(keys); }
    }

    record StaticVariantPublication(boolean published, List<HostStaticGeometryVariant> retired) {
        StaticVariantPublication { retired = List.copyOf(retired); }
    }

    record CompatibilityTexture(Identifier identifier,
                                IrisLabPbrProjector.LabPbrAlbedoTexture texture,
                                Set<ModelCompatibilityLoss> losses,
                                boolean defaultMaterialFallback) {
        CompatibilityTexture {
            losses = Set.copyOf(losses);
        }
    }

    record LabPbrImages(DecodedModelImage normal, DecodedModelImage specular,
                        boolean metallicEndpointsOnly, boolean metallicRoughnessDecodeFailed,
                        boolean normalDecodeFailed, boolean occlusionDecodeFailed) {}

    private static final DecodedModelImage FALLBACK_IMAGE =
            new DecodedModelImage(1, 1, new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255});
}
