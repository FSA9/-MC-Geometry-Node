package com.mine.geometry_node.client.model.render.backend.host.light.runtime;

import com.mine.geometry_node.client.model.render.backend.common.ModelRenderBounds;
import com.mine.geometry_node.client.model.render.backend.host.entity.HostArtifactRepository;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostPreparedLightingAsset;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostReceiverProbeSet;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.HostWorldLightCaptureBudget;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.HostWorldLightCaptureTask;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldIdentity;
import com.mine.geometry_node.client.model.render.backend.host.light.occlusion.HostModelOccluderInstance;
import com.mine.geometry_node.client.model.render.backend.host.light.solve.HostLightingSolveCoordinator;
import com.mine.geometry_node.client.model.render.backend.host.light.solve.HostUv2LightingSolver;
import com.mine.geometry_node.client.model.runtime.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Render-thread F3 pipeline: bounded incremental capture followed by asynchronous CPU solve. */
public final class HostLocalLightingRuntime {
    private static final int CAPTURE_GUARD_BLOCKS = 15;
    private static final long REFRESH_TICKS = 10;
    private static final long ALGORITHM_GENERATION = 1;

    private final HostWorldLightCaptureBudget captureBudget;
    private final HostLightingSolveCoordinator coordinator;
    private final Map<ModelInstanceId, InstanceState> states = new HashMap<>();
    private ActiveCapture activeCapture;
    private long nextWorldRevision;
    private long started, completed, rejected, unsupported;

    public HostLocalLightingRuntime(HostWorldLightCaptureBudget captureBudget,
                                    HostLightingSolveCoordinator coordinator) {
        this.captureBudget = Objects.requireNonNull(captureBudget, "captureBudget");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    public void tick(ClientLevel level, List<ClientModelInstanceRegistry.ReadyInstance> ready, long tick) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(ready, "ready");
        captureBudget.beginFrame(tick);
        ModelDimensionId dimension = new ModelDimensionId(level.dimension().identifier().toString());
        Set<ModelInstanceId> live = new HashSet<>();
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            if (instance.state().visible() && dimension.equals(instance.state().dimension())) live.add(instance.id());
        }
        Iterator<ModelInstanceId> tracked = states.keySet().iterator();
        while (tracked.hasNext()) {
            ModelInstanceId id = tracked.next();
            if (live.contains(id)) continue;
            coordinator.cancel(id);
            tracked.remove();
        }
        if (activeCapture != null && !live.contains(activeCapture.instanceId())) activeCapture = null;

        if (activeCapture == null) startNext(level, ready, dimension, tick);
        if (activeCapture == null) return;
        int claimed = captureBudget.claim(activeCapture.task().totalCells()
                - activeCapture.task().capturedCells());
        activeCapture.task().capture(level, claimed);
        if (activeCapture.task().complete()) complete(activeCapture, tick);
    }

    public void remove(ModelInstanceId id) {
        states.remove(id);
        if (activeCapture != null && activeCapture.instanceId().equals(id)) activeCapture = null;
    }

    public void clear() {
        states.clear();
        activeCapture = null;
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(states.size(), activeCapture == null ? 0 : 1,
                activeCapture == null ? 0 : activeCapture.task().capturedCells(),
                activeCapture == null ? 0 : activeCapture.task().totalCells(),
                started, completed, rejected, unsupported);
    }

    private void startNext(ClientLevel level, List<ClientModelInstanceRegistry.ReadyInstance> ready,
                           ModelDimensionId dimension, long tick) {
        for (ClientModelInstanceRegistry.ReadyInstance instance : ready) {
            if (!instance.state().visible() || !dimension.equals(instance.state().dimension())) continue;
            var artifact = instance.resource().existingBackendArtifact(HostArtifactRepository.KEY).orElse(null);
            if (artifact == null) continue;
            HostPreparedLightingAsset lighting = artifact.preparedAsset().lightingAsset();
            if (!lighting.ready() || lighting.receiverProbes().size() == 0 || instance.pose().animated()) {
                unsupported++;
                continue;
            }
            InstanceSignature signature = new InstanceSignature(instance.resource(), instance.state().placement());
            InstanceState state = states.get(instance.id());
            if (state == null || !state.signature().equals(signature)) {
                long placementRevision = state == null ? 1 : state.placementRevision() + 1;
                state = new InstanceState(signature, placementRevision, Long.MIN_VALUE);
                states.put(instance.id(), state);
            }
            if (state.lastSubmittedTick() != Long.MIN_VALUE
                    && tick - state.lastSubmittedTick() < REFRESH_TICKS) continue;
            if (coordinator.pending(instance.id())) continue;
            try {
                HostWorldLightCaptureTask task = captureTask(level, dimension, instance, nextWorldRevision());
                activeCapture = new ActiveCapture(instance.id(), instance.resource(), instance.state().placement(),
                        lighting, state.placementRevision(), task);
                started++;
                return;
            } catch (IllegalArgumentException tooLarge) {
                states.put(instance.id(), state.withLastSubmittedTick(tick));
                rejected++;
            }
        }
    }

    private void complete(ActiveCapture capture, long tick) {
        activeCapture = null;
        InstanceState state = states.get(capture.instanceId());
        if (state == null || state.placementRevision() != capture.placementRevision()
                || state.signature().resource() != capture.resource()
                || !state.signature().placement().equals(capture.placement())) return;
        HostWorldLightCaptureTask.Result world = capture.task().finish();
        List<HostUv2LightingSolver.Receiver> receivers = receivers(
                capture.lighting().receiverProbes(), capture.placement());
        HostLightFieldIdentity identity = new HostLightFieldIdentity(capture.instanceId(),
                capture.resource().asset().toString(), capture.placementRevision(),
                world.scalar().dimension(), world.scalar().worldRevision(),
                world.sources().revision(), ALGORITHM_GENERATION);
        HostModelOccluderInstance modelOccluder = new HostModelOccluderInstance(
                capture.lighting().bvh(), capture.lighting().voxelGrid(), capture.placement());
        boolean admitted = coordinator.submit(new HostLightingSolveCoordinator.SolveRequest(
                identity, receivers, world.sources(), world.scalar(), world.occluders(), modelOccluder));
        states.put(capture.instanceId(), state.withLastSubmittedTick(tick));
        if (admitted) completed++;
        else rejected++;
    }

    private static HostWorldLightCaptureTask captureTask(ClientLevel level, ModelDimensionId dimension,
                                                         ClientModelInstanceRegistry.ReadyInstance instance,
                                                         long revision) {
        AABB model = ModelRenderBounds.worldBounds(instance.pose().modelBounds(), instance.state().placement());
        int minX = floor(model.minX) - CAPTURE_GUARD_BLOCKS;
        int minY = Math.max(level.getMinY(), floor(model.minY) - CAPTURE_GUARD_BLOCKS);
        int minZ = floor(model.minZ) - CAPTURE_GUARD_BLOCKS;
        int maxX = ceil(model.maxX) + CAPTURE_GUARD_BLOCKS;
        int maxY = Math.min(level.getMaxY(), ceil(model.maxY) + CAPTURE_GUARD_BLOCKS);
        int maxZ = ceil(model.maxZ) + CAPTURE_GUARD_BLOCKS;
        return new HostWorldLightCaptureTask(dimension, revision, minX, minY, minZ,
                Math.max(1, maxX - minX), Math.max(1, maxY - minY), Math.max(1, maxZ - minZ));
    }

    private static List<HostUv2LightingSolver.Receiver> receivers(HostReceiverProbeSet probes,
                                                                  ModelInstancePlacement placement) {
        ArrayList<HostUv2LightingSolver.Receiver> result = new ArrayList<>(probes.size());
        Vector3f scale = placement.scale();
        Quaternionf rotation = placement.rotation();
        var origin = placement.position();
        float orientation = scale.x * scale.y * scale.z < 0 ? -1F : 1F;
        for (int probe = 0; probe < probes.size(); probe++) {
            Vector3f position = rotation.transform(new Vector3f(
                    probes.position(probe, 0) * scale.x,
                    probes.position(probe, 1) * scale.y,
                    probes.position(probe, 2) * scale.z));
            Vector3f normal = rotation.transform(new Vector3f(
                    probes.normal(probe, 0) / scale.x,
                    probes.normal(probe, 1) / scale.y,
                    probes.normal(probe, 2) / scale.z)).mul(orientation).normalize();
            result.add(new HostUv2LightingSolver.Receiver(origin.x + position.x,
                    origin.y + position.y, origin.z + position.z, normal.x, normal.y, normal.z));
        }
        return List.copyOf(result);
    }

    private static int floor(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("bounds overflow");
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("bounds overflow");
        return (int) Math.ceil(value);
    }

    private long nextWorldRevision() {
        if (nextWorldRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("HOST world capture revision exhausted");
        }
        return ++nextWorldRevision;
    }

    public record Diagnostics(int trackedInstances, int activeCaptures, int capturedCells, int captureCells,
                              long started, long completed, long rejected, long unsupported) {}

    private record ActiveCapture(ModelInstanceId instanceId, LoadedModelResource resource,
                                 ModelInstancePlacement placement, HostPreparedLightingAsset lighting,
                                 long placementRevision, HostWorldLightCaptureTask task) {}
    private record InstanceSignature(LoadedModelResource resource, ModelInstancePlacement placement) {}
    private record InstanceState(InstanceSignature signature, long placementRevision, long lastSubmittedTick) {
        private InstanceState withLastSubmittedTick(long tick) {
            return new InstanceState(signature, placementRevision, tick);
        }
    }
}
