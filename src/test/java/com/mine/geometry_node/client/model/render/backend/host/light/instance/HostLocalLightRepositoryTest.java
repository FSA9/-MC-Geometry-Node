package com.mine.geometry_node.client.model.render.backend.host.light.instance;

import com.mine.geometry_node.client.model.gpu.RenderThreadDispatcher;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldIdentity;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLocalLightField;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import com.mine.geometry_node.client.model.runtime.ModelInstanceId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostLocalLightRepositoryTest {
    @Test
    void sameAssetInstancesNeverShareFields() {
        List<TestField> retired = new ArrayList<>();
        HostLocalLightRepository repository = repository(retired);
        ModelInstanceId first = new ModelInstanceId("first");
        ModelInstanceId second = new ModelInstanceId("second");
        TestField firstField = new TestField(identity(first, 1, 1), 10);
        TestField secondField = new TestField(identity(second, 1, 1), 20);

        repository.publish(repository.beginTarget(identity(first, 1, 1)), firstField);
        repository.publish(repository.beginTarget(identity(second, 1, 1)), secondField);

        assertSame(firstField, repository.active(first));
        assertSame(secondField, repository.active(second));
        assertNotSame(repository.active(first), repository.active(second));
        assertEquals(30, repository.diagnostics().activeBytes());
    }

    @Test
    void staleCompletionCannotReplaceNewerTarget() {
        List<TestField> retired = new ArrayList<>();
        HostLocalLightRepository repository = repository(retired);
        ModelInstanceId id = new ModelInstanceId("moving");
        TestField active = new TestField(identity(id, 1, 1), 10);
        repository.publish(repository.beginTarget(identity(id, 1, 1)), active);
        HostLocalLightRepository.Target stale = repository.beginTarget(identity(id, 2, 1));
        HostLocalLightRepository.Target current = repository.beginTarget(identity(id, 3, 1));
        TestField staleField = new TestField(stale.identity(), 20);

        assertFalse(repository.publish(stale, staleField));
        assertSame(active, repository.active(id));
        assertTrue(staleField.closed);
        TestField replacement = new TestField(current.identity(), 30);
        assertTrue(repository.publish(current, replacement));
        assertSame(replacement, repository.active(id));
        assertTrue(active.closed);
    }

    @Test
    void failureKeepsActiveAndRemoveCancelsTarget() {
        List<TestField> retired = new ArrayList<>();
        HostLocalLightRepository repository = repository(retired);
        ModelInstanceId id = new ModelInstanceId("failure");
        TestField active = new TestField(identity(id, 1, 1), 12);
        repository.publish(repository.beginTarget(identity(id, 1, 1)), active);
        HostLocalLightRepository.Target failure = repository.beginTarget(identity(id, 1, 2));

        assertTrue(repository.fail(failure, HostLocalLightRepository.FailureKind.BUDGET_REJECTED));
        assertSame(active, repository.active(id));
        HostLocalLightRepository.Target removed = repository.beginTarget(identity(id, 1, 3));
        repository.remove(id);
        assertTrue(removed.cancelled());
        assertTrue(active.closed);
        assertEquals(1, repository.diagnostics().budgetRejected());
    }

    @Test
    void retirementRemainsVisibleUntilFenceCompletion() {
        CurrentThread thread = new CurrentThread();
        List<Runnable> completions = new ArrayList<>();
        HostLocalLightRepository repository = new HostLocalLightRepository(thread,
                (field, completion) -> completions.add(completion));
        ModelInstanceId id = new ModelInstanceId("retiring");
        HostLightFieldIdentity identity = identity(id, 1, 1);
        repository.publish(repository.beginTarget(identity), new TestField(identity, 17));

        repository.remove(id);

        assertEquals(1, repository.diagnostics().retiringFields());
        assertEquals(17, repository.diagnostics().retiringBytes());
        completions.getFirst().run();
        assertEquals(0, repository.diagnostics().retiringFields());
    }

    @Test
    void fieldWithDifferentCompleteIdentityCannotPublish() {
        ModelInstanceId id = new ModelInstanceId("identity");
        HostLightFieldIdentity expected = identity(id, 1, 1);
        List<HostLightFieldIdentity> mismatches = List.of(
                new HostLightFieldIdentity(new ModelInstanceId("other"), "asset", 1,
                        expected.dimension(), 1, 1),
                new HostLightFieldIdentity(id, "other-asset", 1, expected.dimension(), 1, 1),
                new HostLightFieldIdentity(id, "asset", 2, expected.dimension(), 1, 1),
                new HostLightFieldIdentity(id, "asset", 1,
                        new ModelDimensionId("minecraft:the_nether"), 1, 1),
                new HostLightFieldIdentity(id, "asset", 1, expected.dimension(), 2, 1),
                new HostLightFieldIdentity(id, "asset", 1, expected.dimension(), 1, 2));

        for (HostLightFieldIdentity mismatch : mismatches) {
            HostLocalLightRepository repository = repository(new ArrayList<>());
            HostLocalLightRepository.Target target = repository.beginTarget(expected);
            TestField wrong = new TestField(mismatch, 9);
            assertFalse(repository.publish(target, wrong), mismatch.toString());
            assertNull(repository.active(id));
            assertTrue(target.cancelled());
            assertTrue(wrong.closed);
            assertEquals(1, repository.diagnostics().staleCompletions());
        }
    }

    @Test
    void closedRepositoryAndReplacementRejectOldSessionCompletion() {
        ModelInstanceId id = new ModelInstanceId("session");
        HostLightFieldIdentity identity = identity(id, 1, 1);
        List<TestField> oldRetired = new ArrayList<>();
        HostLocalLightRepository oldRepository = repository(oldRetired);
        HostLocalLightRepository.Target oldTarget = oldRepository.beginTarget(identity);
        oldRepository.close();

        TestField lateOldField = new TestField(identity, 11);
        assertFalse(oldRepository.publish(oldTarget, lateOldField));
        assertTrue(lateOldField.closed);
        assertThrows(IllegalStateException.class, () -> oldRepository.beginTarget(identity));

        List<TestField> replacementRetired = new ArrayList<>();
        HostLocalLightRepository replacement = repository(replacementRetired);
        HostLocalLightRepository.Target currentTarget = replacement.beginTarget(identity);
        TestField crossSessionField = new TestField(identity, 13);
        assertFalse(replacement.publish(oldTarget, crossSessionField));
        assertTrue(crossSessionField.closed);
        assertNull(replacement.active(id));

        TestField currentField = new TestField(identity, 17);
        assertTrue(replacement.publish(currentTarget, currentField));
        assertSame(currentField, replacement.active(id));
    }

    private static HostLocalLightRepository repository(List<TestField> retired) {
        return new HostLocalLightRepository(new CurrentThread(), (field, completion) -> {
            TestField test = (TestField) field;
            retired.add(test);
            test.close();
            completion.run();
        });
    }

    private static HostLightFieldIdentity identity(ModelInstanceId id, long placement, long world) {
        return new HostLightFieldIdentity(id, "asset", placement,
                new ModelDimensionId("minecraft:overworld"), world, 1);
    }

    private static final class TestField implements HostLocalLightField {
        private final HostLightFieldIdentity identity;
        private final long bytes;
        private boolean closed;
        private TestField(HostLightFieldIdentity identity, long bytes) {
            this.identity = identity;
            this.bytes = bytes;
        }
        @Override public HostLightFieldIdentity identity() { return identity; }
        @Override public long residentBytes() { return bytes; }
        @Override public void close() { closed = true; }
    }

    private static final class CurrentThread implements RenderThreadDispatcher {
        @Override public boolean isRenderThread() { return true; }
        @Override public void execute(Runnable task) { task.run(); }
    }
}
