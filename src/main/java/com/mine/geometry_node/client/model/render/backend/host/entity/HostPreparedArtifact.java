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
    private final Map<HostEntityGeometry,
            IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>>>
            staticVariants = new IdentityHashMap<>();
    private final Map<HostEntityGeometry, IdentityHashMap<Object, HostPackedLightVariantGate>>
            staticVariantGates = new IdentityHashMap<>();
    private final IdentityHashMap<Object, Long> staticInstanceLastUsedNanos = new IdentityHashMap<>();
    private final IdentityHashMap<Object, InitialStaticWorkset> initialStaticWorksets = new IdentityHashMap<>();
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
        IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances =
                staticVariants.get(geometry);
        LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = instances == null
                ? null : instances.get(key.instanceIdentity());
        return variants == null ? null : variants.get(key);
    }

    /**
     * Detaches one LRU entry before a budget retry. The caller must retire the result behind a GPU fence and
     * continue through the immediate path until that retirement releases its reservation.
     */
    List<HostStaticGeometryVariant> detachLeastRecentlyUsedStaticVariant(
            HostEntityGeometry geometry, HostStaticVariantKey requestedKey, long generation) {
        if (!staticGeneration(generation)) return List.of();
        IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances =
                staticVariants.get(geometry);
        LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = instances == null
                ? null : instances.get(requestedKey.instanceIdentity());
        if (variants == null || variants.containsKey(requestedKey) || variants.isEmpty()) return List.of();
        Iterator<HostStaticGeometryVariant> eldest = variants.values().iterator();
        HostStaticGeometryVariant detached = eldest.next();
        eldest.remove();
        if (variants.isEmpty()) instances.remove(requestedKey.instanceIdentity());
        if (instances.isEmpty()) staticVariants.remove(geometry);
        return List.of(detached);
    }

    List<HostStaticGeometryVariant> detachStaticVariantForBudget(
            HostEntityGeometry requestedGeometry, HostStaticVariantKey requestedKey, long generation) {
        return detachLeastRecentlyUsedStaticVariant(requestedGeometry, requestedKey, generation);
    }

    InitialWorksetStatus initialStaticWorksetStatus(Object instanceIdentity) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        return workset == null ? InitialWorksetStatus.EMPTY : workset.status;
    }

    void waitForInitialStaticWorkset(Object instanceIdentity) {
        initialStaticWorksets.computeIfAbsent(Objects.requireNonNull(instanceIdentity, "instanceIdentity"),
                ignored -> InitialStaticWorkset.waiting());
    }

    void beginInitialStaticWorkset(Object instanceIdentity, List<InitialStaticRequirement> requirements,
                                   HostStaticVariantBudget.BatchReservation reservation) {
        if (closed || requirements.isEmpty()) throw new IllegalStateException("invalid initial static workset");
        InitialStaticWorkset current = initialStaticWorksets.get(instanceIdentity);
        if (current != null && current.status != InitialWorksetStatus.WAITING) {
            throw new IllegalStateException("initial static workset cannot enter BUILDING");
        }
        initialStaticWorksets.put(instanceIdentity,
                InitialStaticWorkset.building(requirements, Objects.requireNonNull(reservation, "reservation")));
    }

    void beginStaticWorksetReplacement(Object instanceIdentity,
                                       List<InitialStaticRequirement> requirements,
                                       HostStaticVariantBudget.BatchReservation reservation) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        if (closed || workset == null || workset.status != InitialWorksetStatus.READY
                || requirements.isEmpty()) {
            throw new IllegalStateException("invalid static workset replacement");
        }
        workset.beginReplacement(requirements, Objects.requireNonNull(reservation, "reservation"));
        clearStaticVariantGatesForInstance(instanceIdentity);
    }

    void rejectInitialStaticWorkset(Object instanceIdentity) {
        InitialStaticWorkset current = initialStaticWorksets.get(instanceIdentity);
        if (current != null && current.status == InitialWorksetStatus.BUILDING) {
            throw new IllegalStateException("building initial static workset must fail through batch rollback");
        }
        InitialStaticWorkset previous = initialStaticWorksets.put(
                Objects.requireNonNull(instanceIdentity, "instanceIdentity"), InitialStaticWorkset.failed());
        if (previous != null) previous.closeReservation();
    }

    void cancelWaitingInitialStaticWorkset(Object instanceIdentity) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        if (workset != null && workset.status == InitialWorksetStatus.WAITING) {
            initialStaticWorksets.remove(instanceIdentity);
        }
    }

    void retainInitialStaticWorksets(Set<?> liveInstanceIdentities) {
        initialStaticWorksets.entrySet().removeIf(entry -> entry.getValue().status == InitialWorksetStatus.FAILED
                && !liveInstanceIdentities.contains(entry.getKey()));
    }

    List<HostStaticGeometryVariant> cancelInactiveInitialStaticWorksets(Set<?> activeInstanceIdentities) {
        List<HostStaticGeometryVariant> detached = new ArrayList<>();
        Iterator<Map.Entry<Object, InitialStaticWorkset>> iterator = initialStaticWorksets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, InitialStaticWorkset> entry = iterator.next();
            InitialStaticWorkset workset = entry.getValue();
            if (workset.status != InitialWorksetStatus.BUILDING
                    && workset.status != InitialWorksetStatus.REPLACING
                    || activeInstanceIdentities.contains(entry.getKey())) continue;
            detached.addAll(workset.detachStaged());
            workset.closeReservation();
            clearStaticVariantGatesForInstance(entry.getKey());
            if (workset.status == InitialWorksetStatus.REPLACING) workset.rollbackReplacement();
            else iterator.remove();
        }
        return detached;
    }

    InitialStaticRequirement initialStaticRequirement(Object instanceIdentity, StaticDrawSlot slot) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        return workset == null || workset.status != InitialWorksetStatus.BUILDING
                && workset.status != InitialWorksetStatus.REPLACING
                ? null : workset.requirements.get(slot);
    }

    InitialStaticRequirement activeStaticRequirement(Object instanceIdentity, StaticDrawSlot slot) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        return workset == null ? null : workset.activeRequirements.get(slot);
    }

    boolean activeStaticWorksetMatches(Object instanceIdentity,
                                       List<InitialStaticRequirement> requirements) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        return workset != null && workset.status == InitialWorksetStatus.READY
                && workset.activeRequirements.equals(InitialStaticWorkset.index(requirements));
    }

    long activeStaticWorksetBytes(Object instanceIdentity) {
        long bytes = 0;
        for (IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances
                : staticVariants.values()) {
            Map<HostStaticVariantKey, HostStaticGeometryVariant> variants = instances.get(instanceIdentity);
            if (variants == null) continue;
            for (HostStaticGeometryVariant variant : variants.values()) {
                bytes = Math.addExact(bytes, variant.byteSize());
            }
        }
        return bytes;
    }

    static List<Long> uniqueInitialStaticRequirementSizes(List<InitialStaticRequirement> requirements) {
        return InitialStaticWorkset.unique(requirements).values().stream()
                .map(InitialStaticRequirement::bytes).toList();
    }

    boolean initialStaticWorksetMatches(Object instanceIdentity,
                                        List<InitialStaticRequirement> requirements) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        return workset != null && (workset.status == InitialWorksetStatus.BUILDING
                || workset.status == InitialWorksetStatus.REPLACING)
                && workset.requirements.equals(InitialStaticWorkset.index(requirements));
    }

    List<HostStaticGeometryVariant> restartInitialStaticWorkset(Object instanceIdentity) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        if (workset == null || workset.status != InitialWorksetStatus.BUILDING
                && workset.status != InitialWorksetStatus.REPLACING) return List.of();
        List<HostStaticGeometryVariant> detached = workset.detachStaged();
        workset.closeReservation();
        clearStaticVariantGatesForInstance(instanceIdentity);
        if (workset.status == InitialWorksetStatus.REPLACING) {
            workset.rollbackReplacement();
        } else {
            initialStaticWorksets.remove(instanceIdentity);
        }
        return detached;
    }

    HostStaticVariantBudget.Reservation claimInitialStaticVariant(
            Object instanceIdentity, StaticDrawSlot slot, HostEntityGeometry geometry,
            HostStaticVariantKey key, long bytes) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        return workset == null || workset.status != InitialWorksetStatus.BUILDING
                && workset.status != InitialWorksetStatus.REPLACING
                ? null : workset.claim(slot, geometry, key, bytes);
    }

    boolean initialStaticWorksetBuilding(Object instanceIdentity) {
        InitialWorksetStatus status = initialStaticWorksetStatus(instanceIdentity);
        return status == InitialWorksetStatus.BUILDING || status == InitialWorksetStatus.REPLACING;
    }

    List<HostStaticGeometryVariant> failInitialStaticWorkset(Object instanceIdentity) {
        InitialStaticWorkset workset = initialStaticWorksets.get(instanceIdentity);
        if (workset == null || workset.status != InitialWorksetStatus.BUILDING
                && workset.status != InitialWorksetStatus.REPLACING) return List.of();
        List<HostStaticGeometryVariant> detached = workset.detachStaged();
        workset.closeReservation();
        clearStaticVariantGatesForInstance(instanceIdentity);
        if (workset.status == InitialWorksetStatus.REPLACING) workset.rollbackReplacement();
        else workset.status = InitialWorksetStatus.FAILED;
        return detached;
    }

    private void clearStaticVariantGatesForInstance(Object instanceIdentity) {
        for (Iterator<IdentityHashMap<Object, HostPackedLightVariantGate>> gates =
                staticVariantGates.values().iterator(); gates.hasNext();) {
            IdentityHashMap<Object, HostPackedLightVariantGate> instances = gates.next();
            HostPackedLightVariantGate removed = instances.remove(instanceIdentity);
            if (removed != null) removed.clear();
            if (instances.isEmpty()) gates.remove();
        }
    }

    void touchStaticInstance(Object instanceIdentity, long nowNanos) {
        if (closed) return;
        staticInstanceLastUsedNanos.put(Objects.requireNonNull(instanceIdentity, "instanceIdentity"), nowNanos);
    }

    ColdStaticInstance oldestColdStaticInstance(long nowNanos, long coldNanos) {
        if (closed) return null;
        Object oldestIdentity = null;
        long oldestUse = Long.MAX_VALUE;
        Set<Object> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances
                : staticVariants.values()) candidates.addAll(instances.keySet());
        for (Object identity : candidates) {
            long lastUse = staticInstanceLastUsedNanos.getOrDefault(identity, 0L);
            long age = nowNanos >= lastUse ? nowNanos - lastUse : 0L;
            if (age < coldNanos || staticInstanceBuilding(identity)) continue;
            if (lastUse < oldestUse) {
                oldestUse = lastUse;
                oldestIdentity = identity;
            }
        }
        return oldestIdentity == null ? null : new ColdStaticInstance(oldestIdentity, oldestUse);
    }

    List<HostStaticGeometryVariant> detachStaticVariantsForInstance(Object instanceIdentity) {
        List<HostStaticGeometryVariant> detached = new ArrayList<>();
        InitialStaticWorkset workset = initialStaticWorksets.remove(instanceIdentity);
        if (workset != null) {
            detached.addAll(workset.detachStaged());
            workset.closeReservation();
        }
        Iterator<Map.Entry<HostEntityGeometry,
                IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>>>> geometry =
                staticVariants.entrySet().iterator();
        while (geometry.hasNext()) {
            var instances = geometry.next().getValue();
            Map<HostStaticVariantKey, HostStaticGeometryVariant> removed = instances.remove(instanceIdentity);
            if (removed != null) detached.addAll(removed.values());
            if (instances.isEmpty()) geometry.remove();
        }
        for (Iterator<IdentityHashMap<Object, HostPackedLightVariantGate>> gates =
                staticVariantGates.values().iterator(); gates.hasNext();) {
            IdentityHashMap<Object, HostPackedLightVariantGate> instances = gates.next();
            HostPackedLightVariantGate removed = instances.remove(instanceIdentity);
            if (removed != null) removed.clear();
            if (instances.isEmpty()) gates.remove();
        }
        staticInstanceLastUsedNanos.remove(instanceIdentity);
        return detached;
    }

    private boolean staticInstanceBuilding(Object instanceIdentity) {
        if (initialStaticWorksetBuilding(instanceIdentity)) return true;
        for (IdentityHashMap<Object, HostPackedLightVariantGate> gates : staticVariantGates.values()) {
            HostPackedLightVariantGate gate = gates.get(instanceIdentity);
            if (gate != null && gate.building()) return true;
        }
        return false;
    }

    StaticVariantPublication publishStaticVariant(StaticDrawSlot slot, HostEntityGeometry geometry,
                                                   HostStaticVariantKey key,
                                                   long generation, HostStaticGeometryVariant variant) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(variant, "variant");
        if (!staticGeneration(generation)) return new StaticVariantPublication(
                false, false, List.of(variant), () -> {});
        InitialStaticWorkset workset = initialStaticWorksets.get(key.instanceIdentity());
        if (workset != null && (workset.status == InitialWorksetStatus.BUILDING
                || workset.status == InitialWorksetStatus.REPLACING)) {
            if (!workset.stage(slot, geometry, key, variant)) {
                return new StaticVariantPublication(false, false, List.of(variant), () -> {});
            }
            if (!workset.complete()) return new StaticVariantPublication(true, false, List.of(), () -> {});
            boolean replacement = workset.status == InitialWorksetStatus.REPLACING;
            List<HostStaticGeometryVariant> retired = publishInitialStaticWorkset(key.instanceIdentity(), workset);
            workset.closeReservation();
            HostStaticVariantBudget.BatchReservation completedReservation = workset.reservation;
            workset.activateTarget();
            workset.status = InitialWorksetStatus.READY;
            return new StaticVariantPublication(true, true, retired,
                    replacement ? completedReservation::promoteReplacementToSteady : () -> {});
        }
        IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances =
                staticVariants.computeIfAbsent(geometry, ignored -> new IdentityHashMap<>());
        LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = instances.computeIfAbsent(
                key.instanceIdentity(), ignored -> new LinkedHashMap<>(8, 0.75f, true));
        HostStaticGeometryVariant existing = variants.get(key);
        if (existing != null) return new StaticVariantPublication(false, false, List.of(variant), () -> {});
        List<HostStaticGeometryVariant> retired = new ArrayList<>(1);
        if (variants.size() >= HostStaticVariantBudget.MAX_VARIANTS_PER_INSTANCE_GEOMETRY) {
            Iterator<HostStaticGeometryVariant> eldest = variants.values().iterator();
            retired.add(eldest.next());
            eldest.remove();
        }
        variants.put(key, variant);
        return new StaticVariantPublication(true, true, retired, () -> {});
    }

    StaticVariantPublication publishStaticVariant(HostEntityGeometry geometry, HostStaticVariantKey key,
                                                   long generation, HostStaticGeometryVariant variant) {
        return publishStaticVariant(null, geometry, key, generation, variant);
    }

    private List<HostStaticGeometryVariant> publishInitialStaticWorkset(
            Object instanceIdentity, InitialStaticWorkset workset) {
        List<HostStaticGeometryVariant> retired = workset.status == InitialWorksetStatus.REPLACING
                ? detachPublishedStaticVariantsForInstance(instanceIdentity) : new ArrayList<>();
        for (StagedStaticVariant staged : workset.staged.values()) {
            IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances =
                    staticVariants.computeIfAbsent(staged.geometry, ignored -> new IdentityHashMap<>());
            LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant> variants = instances.computeIfAbsent(
                    instanceIdentity, ignored -> new LinkedHashMap<>(8, 0.75F, true));
            HostStaticGeometryVariant duplicate = variants.putIfAbsent(staged.key, staged.variant);
            if (duplicate != null) retired.add(staged.variant);
        }
        workset.staged.clear();
        return retired;
    }

    private List<HostStaticGeometryVariant> detachPublishedStaticVariantsForInstance(Object instanceIdentity) {
        List<HostStaticGeometryVariant> detached = new ArrayList<>();
        Iterator<Map.Entry<HostEntityGeometry,
                IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>>>> iterator =
                staticVariants.entrySet().iterator();
        while (iterator.hasNext()) {
            var instances = iterator.next().getValue();
            Map<HostStaticVariantKey, HostStaticGeometryVariant> removed = instances.remove(instanceIdentity);
            if (removed != null) detached.addAll(removed.values());
            if (instances.isEmpty()) iterator.remove();
        }
        return detached;
    }

    List<HostStaticGeometryVariant> detachStaticVariants() {
        List<HostStaticGeometryVariant> detached = new ArrayList<>();
        for (IdentityHashMap<Object, LinkedHashMap<HostStaticVariantKey, HostStaticGeometryVariant>> instances
                : staticVariants.values()) {
            for (Map<HostStaticVariantKey, HostStaticGeometryVariant> variants : instances.values()) {
                detached.addAll(variants.values());
            }
        }
        staticVariants.clear();
        staticVariantGates.clear();
        staticInstanceLastUsedNanos.clear();
        for (InitialStaticWorkset workset : initialStaticWorksets.values()) {
            detached.addAll(workset.detachStaged());
            workset.closeReservation();
        }
        initialStaticWorksets.clear();
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

    long bindingResidentBytes() {
        long bytes = 0;
        for (CompatibilityTexture texture : textures.values()) bytes = Math.addExact(bytes, texture.byteSize());
        return bytes;
    }

    int bindingResidentObjects() {
        int objects = 0;
        for (CompatibilityTexture texture : textures.values()) objects = Math.addExact(objects, texture.objects());
        return objects;
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
            } finally {
                binding.reservation().close();
            }
        }
    }

    record TextureKey(StaticModelMaterial material, boolean labPbr, boolean opaqueFallback) {
        ModelAlphaMode alphaMode() { return material.alphaMode(); }
    }

    record BindingRequest(Set<TextureKey> keys) {
        BindingRequest { keys = Set.copyOf(keys); }
    }

    record StaticVariantPublication(boolean published, boolean activated,
                                    List<HostStaticGeometryVariant> retired,
                                    Runnable retirementComplete) {
        StaticVariantPublication { retired = List.copyOf(retired); }
    }

    enum InitialWorksetStatus { EMPTY, WAITING, BUILDING, REPLACING, READY, FAILED }

    record StaticDrawSlot(int nodeIndex, int primitiveIndex) {}

    record InitialStaticRequirement(StaticDrawSlot slot, HostEntityGeometry geometry,
                                    HostStaticVariantKey key, long bytes,
                                    Object renderType, Object texture) {
        InitialStaticRequirement(StaticDrawSlot slot, HostEntityGeometry geometry,
                                 HostStaticVariantKey key, long bytes) {
            this(slot, geometry, key, bytes, null, null);
        }
        InitialStaticRequirement {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(geometry, "geometry");
            Objects.requireNonNull(key, "key");
            if (bytes < 1) throw new IllegalArgumentException("initial static requirement bytes must be positive");
        }
    }

    private static final class InitialStaticWorkset {
        private Map<StaticDrawSlot, InitialStaticRequirement> activeRequirements = Map.of();
        private Map<StaticDrawSlot, InitialStaticRequirement> requirements;
        private Map<StaticVariantIdentity, InitialStaticRequirement> uniqueRequirements;
        private HostStaticVariantBudget.BatchReservation reservation;
        private final Map<StaticVariantIdentity, StagedStaticVariant> staged = new LinkedHashMap<>();
        private final Set<StaticVariantIdentity> claimed = new HashSet<>();
        private InitialWorksetStatus status;

        private InitialStaticWorkset(Map<StaticDrawSlot, InitialStaticRequirement> requirements,
                                     HostStaticVariantBudget.BatchReservation reservation,
                                     InitialWorksetStatus status) {
            this.requirements = requirements;
            this.uniqueRequirements = unique(requirements.values().stream().toList());
            this.reservation = reservation;
            this.status = status;
        }

        static InitialStaticWorkset waiting() {
            return new InitialStaticWorkset(Map.of(), null, InitialWorksetStatus.WAITING);
        }
        static InitialStaticWorkset building(List<InitialStaticRequirement> requirements,
                                             HostStaticVariantBudget.BatchReservation reservation) {
            return new InitialStaticWorkset(Map.copyOf(index(requirements)), reservation,
                    InitialWorksetStatus.BUILDING);
        }
        static Map<StaticDrawSlot, InitialStaticRequirement> index(
                List<InitialStaticRequirement> requirements) {
            Map<StaticDrawSlot, InitialStaticRequirement> indexed = new LinkedHashMap<>();
            for (InitialStaticRequirement requirement : requirements) {
                if (indexed.putIfAbsent(requirement.slot(), requirement) != null) {
                    throw new IllegalArgumentException("duplicate initial static draw slot " + requirement.slot());
                }
            }
            return indexed;
        }
        static Map<StaticVariantIdentity, InitialStaticRequirement> unique(
                List<InitialStaticRequirement> requirements) {
            Map<StaticVariantIdentity, InitialStaticRequirement> unique = new LinkedHashMap<>();
            for (InitialStaticRequirement requirement : requirements) {
                unique.putIfAbsent(new StaticVariantIdentity(requirement.geometry(), requirement.key()), requirement);
            }
            return unique;
        }
        static InitialStaticWorkset failed() {
            return new InitialStaticWorkset(Map.of(), null, InitialWorksetStatus.FAILED);
        }
        void beginReplacement(List<InitialStaticRequirement> target,
                              HostStaticVariantBudget.BatchReservation replacementReservation) {
            requirements = Map.copyOf(index(target));
            uniqueRequirements = unique(target);
            reservation = replacementReservation;
            staged.clear();
            claimed.clear();
            status = InitialWorksetStatus.REPLACING;
        }
        void activateTarget() {
            activeRequirements = requirements;
            staged.clear();
            claimed.clear();
        }
        void rollbackReplacement() {
            requirements = activeRequirements;
            uniqueRequirements = unique(activeRequirements.values().stream().toList());
            reservation = null;
            staged.clear();
            claimed.clear();
            status = InitialWorksetStatus.READY;
        }
        HostStaticVariantBudget.Reservation claim(StaticDrawSlot slot, HostEntityGeometry geometry,
                                                  HostStaticVariantKey key, long bytes) {
            InitialStaticRequirement required = requirements.get(slot);
            StaticVariantIdentity identity = new StaticVariantIdentity(geometry, key);
            if (required == null || required.geometry() != geometry || !required.key().equals(key)
                    || required.bytes() != bytes || !uniqueRequirements.containsKey(identity)
                    || !claimed.add(identity)) return null;
            HostStaticVariantBudget.Reservation result = reservation.claim(bytes);
            if (result == null) claimed.remove(identity);
            return result;
        }
        boolean stage(StaticDrawSlot slot, HostEntityGeometry geometry, HostStaticVariantKey key,
                      HostStaticGeometryVariant variant) {
            InitialStaticRequirement required = requirements.get(slot);
            StaticVariantIdentity identity = new StaticVariantIdentity(geometry, key);
            if (required == null || required.geometry() != geometry || !required.key().equals(key)
                    || !claimed.contains(identity) || staged.containsKey(identity)) return false;
            staged.put(identity, new StagedStaticVariant(geometry, key, variant));
            return true;
        }
        boolean complete() {
            return staged.size() == uniqueRequirements.size() && reservation.unclaimedBytes() == 0;
        }
        List<HostStaticGeometryVariant> detachStaged() {
            List<HostStaticGeometryVariant> variants = staged.values().stream()
                    .map(StagedStaticVariant::variant).toList();
            staged.clear();
            return variants;
        }
        void closeReservation() { if (reservation != null) reservation.close(); }
    }

    private record StagedStaticVariant(HostEntityGeometry geometry, HostStaticVariantKey key,
                                       HostStaticGeometryVariant variant) {}

    private static final class StaticVariantIdentity {
        private final HostEntityGeometry geometry;
        private final HostStaticVariantKey key;

        private StaticVariantIdentity(HostEntityGeometry geometry, HostStaticVariantKey key) {
            this.geometry = geometry;
            this.key = key;
        }

        @Override public boolean equals(Object other) {
            return other instanceof StaticVariantIdentity identity
                    && geometry == identity.geometry && key.equals(identity.key);
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(geometry) + key.hashCode();
        }
    }

    record CompatibilityTexture(Identifier identifier,
                                IrisLabPbrProjector.LabPbrAlbedoTexture texture,
                                Set<ModelCompatibilityLoss> losses,
                                boolean defaultMaterialFallback,
                                long byteSize, int objects,
                                HostTextureBindingBudget.Reservation reservation) {
        CompatibilityTexture {
            losses = Set.copyOf(losses);
            if (byteSize < 1 || objects < 1) throw new IllegalArgumentException("invalid HOST texture footprint");
            Objects.requireNonNull(reservation, "reservation");
        }
    }

    record ColdStaticInstance(Object instanceIdentity, long lastUsedNanos) {}

    record LabPbrImages(DecodedModelImage normal, DecodedModelImage specular,
                        boolean metallicEndpointsOnly, boolean metallicRoughnessDecodeFailed,
                        boolean normalDecodeFailed, boolean occlusionDecodeFailed) {}

    private static final DecodedModelImage FALLBACK_IMAGE =
            new DecodedModelImage(1, 1, new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255});
}
