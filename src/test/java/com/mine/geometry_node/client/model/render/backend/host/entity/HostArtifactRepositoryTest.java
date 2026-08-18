package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostGeometryProjector;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import org.junit.jupiter.api.Test;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class HostArtifactRepositoryTest {
    @Test
    void finalLeaseReleaseDispatchesOneDeferredRetirement() {
        FakeRenderThread renderThread = new FakeRenderThread();
        AtomicInteger retired = new AtomicInteger();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> retired.incrementAndGet(), ignored -> {});
        ModelDefinition definition = emptyDefinition();

        var lease = repository.acquire(definition, StaticModelRenderMetadata.from(definition));
        assertEquals(1, repository.liveCount());

        renderThread.renderThread = false;
        lease.close();
        lease.close();
        assertEquals(1, repository.liveCount());
        assertEquals(0, retired.get());

        renderThread.renderThread = true;
        renderThread.runQueued();
        assertEquals(0, repository.liveCount());
        assertEquals(1, retired.get());
    }

    @Test
    void bindingInvalidationKeepsPlanAndArtifactResident() {
        FakeRenderThread renderThread = new FakeRenderThread();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> {}, ignored -> {});
        ModelDefinition definition = emptyDefinition();
        var lease = repository.acquire(definition, StaticModelRenderMetadata.from(definition));
        HostPreparedArtifact artifact = lease.artifact();
        HostDrawPlan plan = artifact.drawPlan();

        repository.invalidateBindings();

        assertEquals(1, repository.liveCount());
        assertSame(plan, artifact.drawPlan());
    }

    @Test
    void asynchronousPreparationPublishesOnlyAfterWorkerCompletion() {
        FakeRenderThread renderThread = new FakeRenderThread();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> {}, ignored -> {});
        List<Runnable> worker = new ArrayList<>();
        List<Double> progress = new ArrayList<>();
        ModelDefinition definition = emptyDefinition();

        CompletableFuture<com.mine.geometry_node.client.model.runtime.BackendArtifactLease<HostPreparedArtifact>> pending =
                repository.acquireAsync(definition, StaticModelRenderMetadata.from(definition), worker::add,
                        progress::add);

        assertFalse(pending.isDone());
        assertEquals(0, repository.liveCount());
        worker.removeFirst().run();
        assertTrue(pending.isDone());
        assertEquals(1, repository.liveCount());
        assertEquals(1.0, progress.getLast());
        pending.join().close();
    }

    @Test
    void repositoryGenerationChangeRejectsLateAsynchronousArtifact() {
        FakeRenderThread renderThread = new FakeRenderThread();
        AtomicInteger closed = new AtomicInteger();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> {}, ignored -> closed.incrementAndGet());
        List<Runnable> worker = new ArrayList<>();
        ModelDefinition definition = emptyDefinition();
        var pending = repository.acquireAsync(definition, StaticModelRenderMetadata.from(definition), worker::add,
                ignored -> {});

        repository.close();
        worker.removeFirst().run();

        assertTrue(pending.isCompletedExceptionally());
        assertEquals(0, repository.liveCount());
        assertEquals(1, closed.get());
    }

    @Test
    void repositoryCloseRetiresPublishedArtifactsThroughDeferredPath() {
        FakeRenderThread renderThread = new FakeRenderThread();
        AtomicInteger retired = new AtomicInteger();
        AtomicInteger immediate = new AtomicInteger();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> retired.incrementAndGet(), ignored -> immediate.incrementAndGet());
        ModelDefinition definition = emptyDefinition();
        repository.acquire(definition, StaticModelRenderMetadata.from(definition));

        repository.close();

        assertEquals(0, repository.liveCount());
        assertEquals(1, retired.get());
        assertEquals(0, immediate.get());
    }

    @Test
    void capacityRequestRetiresOneColdInstanceAndWaitsForCompletionBeforeAnother() {
        FakeRenderThread renderThread = new FakeRenderThread();
        List<List<HostStaticGeometryVariant>> retirements = new ArrayList<>();
        List<Runnable> completions = new ArrayList<>();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> {}, ignored -> {}, (variants, completion) -> {
                    retirements.add(variants);
                    completions.add(completion);
                });
        ModelDefinition definition = emptyDefinition();
        var lease = repository.acquire(definition, StaticModelRenderMetadata.from(definition));
        HostPreparedArtifact artifact = lease.artifact();
        HostEntityGeometry geometry = geometry();
        Object layout = new Object();
        Object firstInstance = new Object();
        Object secondInstance = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey firstKey = key(firstInstance, layout);
        HostStaticVariantKey secondKey = key(secondInstance, layout);
        HostStaticGeometryVariant first = variant(artifact);
        HostStaticGeometryVariant second = variant(artifact);
        artifact.publishStaticVariant(geometry, firstKey, generation, first);
        artifact.publishStaticVariant(geometry, secondKey, generation, second);

        repository.requestStaticCapacity(artifact, HostArtifactRepository.STATIC_COLD_NANOS + 1);
        assertEquals(1, retirements.size());
        assertEquals(1, retirements.getFirst().size());
        repository.requestStaticCapacity(artifact, HostArtifactRepository.STATIC_COLD_NANOS + 2);
        assertEquals(1, retirements.size(), "pending fence must prevent another retirement");

        HostStaticVariantUpload.closeAll(retirements.getFirst());
        completions.getFirst().run();
        repository.requestStaticCapacity(artifact, HostArtifactRepository.STATIC_COLD_NANOS + 3);
        assertEquals(2, retirements.size());
        HostStaticVariantUpload.closeAll(retirements.get(1));
        completions.get(1).run();
        assertNull(artifact.staticVariant(geometry, firstKey, generation));
        assertNull(artifact.staticVariant(geometry, secondKey, generation));
        lease.close();
    }

    @Test
    void watermarkedMaintenanceStopsAfterFencedRetirementReachesLowWater() {
        FakeRenderThread renderThread = new FakeRenderThread();
        List<List<HostStaticGeometryVariant>> retirements = new ArrayList<>();
        List<Runnable> completions = new ArrayList<>();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> {}, ignored -> {}, (variants, completion) -> {
                    retirements.add(variants);
                    completions.add(completion);
                });
        ModelDefinition definition = emptyDefinition();
        var firstLease = repository.acquire(definition, StaticModelRenderMetadata.from(definition));
        var secondLease = repository.acquire(definition, StaticModelRenderMetadata.from(definition));
        HostPreparedArtifact firstArtifact = firstLease.artifact();
        HostPreparedArtifact secondArtifact = secondLease.artifact();
        HostEntityGeometry geometry = geometry();
        Object layout = new Object();
        Object firstInstance = new Object();
        Object secondInstance = new Object();
        long firstBytes = 220L << 20;
        long secondBytes = 200L << 20;
        HostStaticGeometryVariant first = variant(firstArtifact, firstBytes);
        HostStaticGeometryVariant second = variant(secondArtifact, secondBytes);
        firstArtifact.publishStaticVariant(geometry, key(firstInstance, layout),
                firstArtifact.staticGeneration(), first);
        secondArtifact.publishStaticVariant(geometry, key(secondInstance, layout),
                secondArtifact.staticGeneration(), second);

        long cold = HostArtifactRepository.STATIC_COLD_NANOS + 1;
        repository.maintainStaticCache(cold);
        assertEquals(1, retirements.size());
        repository.maintainStaticCache(cold + 1);
        assertEquals(1, retirements.size(), "fence must serialize watermarked retirement");

        HostStaticVariantUpload.closeAll(retirements.getFirst());
        completions.getFirst().run();
        repository.maintainStaticCache(cold + 2);
        assertEquals(1, retirements.size(), "reclaim must stop below the low-water mark");

        firstLease.close();
        secondLease.close();
    }

    @Test
    void waitingInitialStateCanBeCancelledWhenInstanceLeavesTheCandidateSet() {
        FakeRenderThread renderThread = new FakeRenderThread();
        HostArtifactRepository repository = new HostArtifactRepository(renderThread, ignored -> {},
                ignored -> {}, ignored -> {});
        HostPreparedArtifact artifact = HostPreparedArtifact.prepare(
                emptyDefinition(), StaticModelRenderMetadata.from(emptyDefinition()));
        Object instance = new Object();
        artifact.waitForInitialStaticWorkset(instance);

        artifact.cancelWaitingInitialStaticWorkset(instance);

        assertEquals(HostPreparedArtifact.InitialWorksetStatus.EMPTY,
                artifact.initialStaticWorksetStatus(instance));
        artifact.closeStaticVariants();
    }

    private static HostStaticVariantKey key(Object instance, Object layout) {
        return new HostStaticVariantKey(instance, 1, new Matrix4f(), new Matrix3f(),
                0, 1, false, 1, 1, 1, 1, 0, 1, layout, 1);
    }

    private static HostStaticGeometryVariant variant(HostPreparedArtifact artifact) {
        return variant(artifact, 1);
    }

    private static HostStaticGeometryVariant variant(HostPreparedArtifact artifact, long bytes) {
        HostStaticVariantBudget.Reservation reservation = artifact.reserveStaticVariant(bytes);
        assertNotNull(reservation);
        reservation.markResident();
        return new HostStaticGeometryVariant(new FakeBuffer(Math.toIntExact(bytes)), null, 1, 1, reservation);
    }

    private static HostEntityGeometry geometry() {
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new LinkedHashMap<>();
        ByteBuffer data = ByteBuffer.allocate(9 * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0}) data.putFloat(value);
        attributes.put(ModelAttributeSemantic.POSITION, new ModelVertexAttribute(
                ModelAttributeSemantic.POSITION, ModelComponentType.FLOAT32, 3, false, 3, data.array()));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes,
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
        return HostGeometryProjector.project(primitive, StaticModelTexture.absent());
    }

    private static ModelDefinition emptyDefinition() {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "host-repository",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, ModelVector3.ONE);
        return new ModelDefinition(asset, List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("root", ModelTransform.Trs.IDENTITY, -1, List.of(), Optional.empty())),
                List.of(), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }

    private static final class FakeRenderThread implements RenderThreadDispatcher {
        private final List<Runnable> queued = new ArrayList<>();
        private boolean renderThread = true;

        @Override public boolean isRenderThread() { return renderThread; }
        @Override public void execute(Runnable task) {
            if (renderThread) task.run(); else queued.add(task);
        }
        void runQueued() {
            List<Runnable> tasks = List.copyOf(queued);
            queued.clear();
            tasks.forEach(Runnable::run);
        }
    }

    private static final class FakeBuffer implements ModelGpuBuffer {
        private final int bytes;
        private boolean closed;
        private FakeBuffer() { this(1); }
        private FakeBuffer(int bytes) { this.bytes = bytes; }
        @Override public int byteSize() { return bytes; }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() { closed = true; }
    }
}
