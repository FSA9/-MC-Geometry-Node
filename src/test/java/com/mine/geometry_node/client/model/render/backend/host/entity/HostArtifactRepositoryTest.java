package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
}
