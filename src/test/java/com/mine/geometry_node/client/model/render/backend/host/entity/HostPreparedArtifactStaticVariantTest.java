package com.mine.geometry_node.client.model.render.backend.host.entity;

import com.mine.geometry_node.client.model.gpu.ModelGpuBuffer;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostEntityGeometry;
import com.mine.geometry_node.client.model.render.backend.host.geometry.HostGeometryProjector;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightBinding;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightFieldId;
import com.mine.geometry_node.client.model.runtime.StaticModelRenderMetadata;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class HostPreparedArtifactStaticVariantTest {
    @Test
    void keySeparatesInstancesPoseLightWindingAndLayout() {
        Object instance = new Object();
        Object layout = new Object();
        Matrix4f pose = new Matrix4f().scale(2, 1, 1);
        Matrix3f normal = new Matrix3f().scaling(0.5F, 1, 1);
        HostStaticVariantKey base = new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3);

        assertEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(new Object(), 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 8, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, new Matrix4f(pose).translate(1, 0, 0), normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                1, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, true, 1, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 0.5F, 1, 1, 1, 0, 3, layout, 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 0, 3, new Object(), 3));
        assertNotEquals(base, new HostStaticVariantKey(instance, 7, pose, normal,
                0, 0x00f000f0, false, 1, 1, 1, 1, 3, 2, layout, 3));
    }

    @Test
    void publishesAtMostFourVariantsAndUsesAccessOrderForEviction() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey[] keys = new HostStaticVariantKey[5];
        HostStaticGeometryVariant[] variants = new HostStaticGeometryVariant[5];
        for (int index = 0; index < 4; index++) {
            keys[index] = key(instance, layout, index);
            variants[index] = variant(artifact, 8);
            assertTrue(artifact.publishStaticVariant(geometry, keys[index], generation, variants[index]).published());
        }

        assertSame(variants[0], artifact.staticVariant(geometry, keys[0], generation));
        keys[4] = key(instance, layout, 4);
        variants[4] = variant(artifact, 8);
        HostPreparedArtifact.StaticVariantPublication publication =
                artifact.publishStaticVariant(geometry, keys[4], generation, variants[4]);

        assertTrue(publication.published());
        assertEquals(List.of(variants[1]), publication.retired());
        assertSame(variants[0], artifact.staticVariant(geometry, keys[0], generation));
        assertNull(artifact.staticVariant(geometry, keys[1], generation));
        publication.retired().forEach(HostStaticGeometryVariant::close);
        artifact.closeStaticVariants();
        for (HostStaticGeometryVariant variant : variants) assertTrue(variant.isClosed());
    }

    @Test
    void variantLimitIsIndependentForEachInstanceOfSharedGeometry() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object layout = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey[] keys = new HostStaticVariantKey[5];
        HostStaticGeometryVariant[] variants = new HostStaticGeometryVariant[5];

        for (int index = 0; index < keys.length; index++) {
            keys[index] = key(new Object(), layout, 1);
            variants[index] = variant(artifact, 8);
            HostPreparedArtifact.StaticVariantPublication publication =
                    artifact.publishStaticVariant(geometry, keys[index], generation, variants[index]);
            assertTrue(publication.published());
            assertTrue(publication.retired().isEmpty());
        }

        for (int index = 0; index < keys.length; index++) {
            assertSame(variants[index], artifact.staticVariant(geometry, keys[index], generation));
        }
        artifact.closeStaticVariants();
    }

    @Test
    void coldInstanceIsDetachedAsAWholeWithoutTouchingVisibleInstance() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry firstGeometry = geometry();
        HostEntityGeometry secondGeometry = geometry();
        Object layout = new Object();
        Object cold = new Object();
        Object visible = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey coldFirstKey = key(cold, layout, 1);
        HostStaticVariantKey coldSecondKey = key(cold, layout, 2);
        HostStaticVariantKey visibleKey = key(visible, layout, 1);
        HostStaticGeometryVariant coldFirst = variant(artifact, 8);
        HostStaticGeometryVariant coldSecond = variant(artifact, 8);
        HostStaticGeometryVariant visibleVariant = variant(artifact, 8);
        artifact.publishStaticVariant(firstGeometry, coldFirstKey, generation, coldFirst);
        artifact.publishStaticVariant(secondGeometry, coldSecondKey, generation, coldSecond);
        artifact.publishStaticVariant(firstGeometry, visibleKey, generation, visibleVariant);
        artifact.touchStaticInstance(cold, 10);
        artifact.touchStaticInstance(visible, 1_000);

        HostPreparedArtifact.ColdStaticInstance candidate = artifact.oldestColdStaticInstance(1_100, 500);
        assertNotNull(candidate);
        assertSame(cold, candidate.instanceIdentity());
        List<HostStaticGeometryVariant> detached = artifact.detachStaticVariantsForInstance(cold);

        assertEquals(2, detached.size());
        assertNull(artifact.staticVariant(firstGeometry, coldFirstKey, generation));
        assertNull(artifact.staticVariant(secondGeometry, coldSecondKey, generation));
        assertSame(visibleVariant, artifact.staticVariant(firstGeometry, visibleKey, generation));
        detached.forEach(HostStaticGeometryVariant::close);
        artifact.closeStaticVariants();
    }

    @Test
    void buildingInstanceIsNotAColdEvictionCandidate() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        HostStaticVariantKey key = key(instance, layout, 1);
        HostStaticGeometryVariant variant = variant(artifact, 8);
        artifact.publishStaticVariant(geometry, key, artifact.staticGeneration(), variant);
        HostStaticVariantAdmissionGate gate = artifact.staticVariantGate(geometry, instance);
        HostStaticAdmissionKey admissionKey = key(instance, new Object(), 1).admissionKey();
        assertEquals(HostStaticVariantAdmissionGate.Decision.WAIT, gate.evaluate(admissionKey, false, 1, 0));
        assertEquals(HostStaticVariantAdmissionGate.Decision.BUILD,
                gate.evaluate(admissionKey, false, 1, 500_000_000L));

        assertNull(artifact.oldestColdStaticInstance(10_000_000_000L, 1));
        artifact.closeStaticVariants();
    }

    @Test
    void staleAndDuplicatePublicationReturnUnownedCandidateForFencedRetirement() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        HostStaticVariantKey key = key(new Object(), new Object(), 1);
        long generation = artifact.staticGeneration();
        HostStaticGeometryVariant first = variant(artifact, 8);
        assertTrue(artifact.publishStaticVariant(geometry, key, generation, first).published());

        HostStaticGeometryVariant duplicate = variant(artifact, 8);
        HostPreparedArtifact.StaticVariantPublication duplicateResult =
                artifact.publishStaticVariant(geometry, key, generation, duplicate);
        assertFalse(duplicateResult.published());
        assertEquals(List.of(duplicate), duplicateResult.retired());

        List<HostStaticGeometryVariant> detached = artifact.detachStaticVariants();
        HostStaticGeometryVariant stale = variant(artifact, 8);
        HostPreparedArtifact.StaticVariantPublication staleResult =
                artifact.publishStaticVariant(geometry, key, generation, stale);
        assertFalse(staleResult.published());
        assertEquals(List.of(stale), staleResult.retired());

        duplicateResult.retired().forEach(HostStaticGeometryVariant::close);
        staleResult.retired().forEach(HostStaticGeometryVariant::close);
        detached.forEach(HostStaticGeometryVariant::close);
        artifact.closeStaticVariants();
    }

    @Test
    void variantCloseReleasesBuffersAndReservationExactlyOnce() {
        HostPreparedArtifact artifact = artifact();
        long before = HostStaticVariantBudget.INSTANCE.artifactBytes(artifact);
        FakeBuffer vertices = new FakeBuffer(5);
        FakeBuffer indices = new FakeBuffer(3);
        HostStaticVariantBudget.Reservation reservation = artifact.reserveStaticVariant(8);
        assertNotNull(reservation);
        HostStaticGeometryVariant variant = new HostStaticGeometryVariant(vertices, indices, 4, 6, reservation);
        assertEquals(before + 8, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));

        variant.close();
        variant.close();

        assertEquals(1, vertices.closeCount);
        assertEquals(1, indices.closeCount);
        assertEquals(before, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));
        artifact.closeStaticVariants();
    }

    @Test
    void batchRetirementContinuesAfterOneBufferCloseFails() {
        HostPreparedArtifact artifact = artifact();
        long before = HostStaticVariantBudget.INSTANCE.artifactBytes(artifact);
        HostStaticVariantBudget.Reservation failingReservation = artifact.reserveStaticVariant(1);
        HostStaticVariantBudget.Reservation healthyReservation = artifact.reserveStaticVariant(1);
        assertNotNull(failingReservation);
        assertNotNull(healthyReservation);
        HostStaticGeometryVariant failing = new HostStaticGeometryVariant(
                new ThrowingBuffer(), null, 1, 1, failingReservation);
        FakeBuffer healthyBuffer = new FakeBuffer(1);
        HostStaticGeometryVariant healthy = new HostStaticGeometryVariant(
                healthyBuffer, null, 1, 1, healthyReservation);

        assertThrows(IllegalStateException.class,
                () -> HostStaticVariantUpload.closeAll(List.of(failing, healthy)));

        assertTrue(failing.isClosed());
        assertTrue(healthy.isClosed());
        assertEquals(1, healthyBuffer.closeCount);
        assertEquals(before, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));
        artifact.closeStaticVariants();
    }

    @Test
    void lruCanBeDetachedBeforeRetryWhenArtifactBudgetIsFull() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        HostStaticVariantKey firstKey = key(instance, layout, 1);
        HostStaticVariantKey requestedKey = key(instance, layout, 2);
        long generation = artifact.staticGeneration();
        int fullBudget = Math.toIntExact(HostStaticVariantBudget.PER_ARTIFACT_BYTES);
        HostStaticGeometryVariant first = variant(artifact, fullBudget);
        assertTrue(artifact.publishStaticVariant(geometry, firstKey, generation, first).published());
        assertNull(artifact.reserveStaticVariant(1));

        List<HostStaticGeometryVariant> detached = artifact.detachLeastRecentlyUsedStaticVariant(
                geometry, requestedKey, generation);

        assertEquals(List.of(first), detached);
        assertNull(artifact.staticVariant(geometry, firstKey, generation));
        assertNull(artifact.reserveStaticVariant(1), "reservation remains charged until fenced retirement");
        detached.forEach(HostStaticGeometryVariant::close);
        HostStaticVariantBudget.Reservation retry = artifact.reserveStaticVariant(1);
        assertNotNull(retry);
        retry.close();
        artifact.closeStaticVariants();
    }

    @Test
    void budgetRetryDoesNotRetireAnotherGeometryAndCreateARebuildCycle() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry residentGeometry = geometry();
        HostEntityGeometry requestedGeometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        long generation = artifact.staticGeneration();
        HostStaticVariantKey residentKey = key(instance, layout, 1);
        HostStaticVariantKey requestedKey = key(instance, layout, 2);
        HostStaticGeometryVariant resident = variant(
                artifact, Math.toIntExact(HostStaticVariantBudget.PER_ARTIFACT_BYTES));
        assertTrue(artifact.publishStaticVariant(
                residentGeometry, residentKey, generation, resident).published());

        List<HostStaticGeometryVariant> detached = artifact.detachStaticVariantForBudget(
                requestedGeometry, requestedKey, generation);

        assertTrue(detached.isEmpty());
        assertSame(resident, artifact.staticVariant(residentGeometry, residentKey, generation));
        artifact.closeStaticVariants();
    }

    @Test
    void initialWorkingSetPublishesOnlyAfterEveryVariantIsReady() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry firstGeometry = geometry();
        HostEntityGeometry secondGeometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        HostStaticVariantKey firstKey = key(instance, layout, 1);
        HostStaticVariantKey secondKey = key(instance, layout, 2);
        HostStaticVariantBudget.BatchReservation batch =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L, 8L));
        assertNotNull(batch);
        artifact.waitForInitialStaticWorkset(instance);
        List<HostPreparedArtifact.InitialStaticRequirement> requirements = List.of(
                requirement(1, 1, firstGeometry, firstKey, 8),
                requirement(2, 1, secondGeometry, secondKey, 8));
        artifact.beginInitialStaticWorkset(instance, requirements, batch);

        HostStaticGeometryVariant first = initialVariant(artifact, instance, requirements.get(0));
        HostPreparedArtifact.StaticVariantPublication firstPublication = artifact.publishStaticVariant(
                requirements.get(0).slot(), firstGeometry, firstKey, artifact.staticGeneration(), first);

        assertTrue(firstPublication.published());
        assertFalse(firstPublication.activated());
        assertNull(artifact.staticVariant(firstGeometry, firstKey, artifact.staticGeneration()));
        assertEquals(HostPreparedArtifact.InitialWorksetStatus.BUILDING,
                artifact.initialStaticWorksetStatus(instance));

        HostStaticGeometryVariant second = initialVariant(artifact, instance, requirements.get(1));
        HostPreparedArtifact.StaticVariantPublication secondPublication = artifact.publishStaticVariant(
                requirements.get(1).slot(), secondGeometry, secondKey, artifact.staticGeneration(), second);

        assertTrue(secondPublication.activated());
        assertSame(first, artifact.staticVariant(firstGeometry, firstKey, artifact.staticGeneration()));
        assertSame(second, artifact.staticVariant(secondGeometry, secondKey, artifact.staticGeneration()));
        assertEquals(HostPreparedArtifact.InitialWorksetStatus.READY,
                artifact.initialStaticWorksetStatus(instance));
        artifact.closeStaticVariants();
    }

    @Test
    void failedInitialWorkingSetRetiresStagedVariantsAndUnclaimedReservation() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        HostStaticVariantKey key = key(instance, new Object(), 1);
        long before = HostStaticVariantBudget.INSTANCE.artifactBytes(artifact);
        HostStaticVariantBudget.BatchReservation batch =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L, 8L));
        assertNotNull(batch);
        artifact.waitForInitialStaticWorkset(instance);
        List<HostPreparedArtifact.InitialStaticRequirement> requirements = List.of(
                requirement(1, 1, geometry, key, 8),
                requirement(2, 1, geometry, key(instance, new Object(), 2), 8));
        artifact.beginInitialStaticWorkset(instance, requirements, batch);
        HostStaticGeometryVariant staged = initialVariant(artifact, instance, requirements.get(0));
        assertFalse(artifact.publishStaticVariant(requirements.get(0).slot(),
                geometry, key, artifact.staticGeneration(), staged).activated());

        List<HostStaticGeometryVariant> retired = artifact.failInitialStaticWorkset(instance);

        assertEquals(List.of(staged), retired);
        assertNull(artifact.staticVariant(geometry, key, artifact.staticGeneration()));
        assertEquals(HostPreparedArtifact.InitialWorksetStatus.FAILED,
                artifact.initialStaticWorksetStatus(instance));
        assertEquals(before + 8, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact),
                "only the staged variant remains charged until fenced retirement");
        retired.forEach(HostStaticGeometryVariant::close);
        assertEquals(before, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));
        artifact.closeStaticVariants();
    }

    @Test
    void distinctDrawSlotsCanShareTheSameGeometryAndExactKey() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        HostStaticVariantKey key = key(instance, new Object(), 1);
        List<HostPreparedArtifact.InitialStaticRequirement> requirements = List.of(
                requirement(1, 1, geometry, key, 8),
                requirement(2, 1, geometry, key, 8));
        HostStaticVariantBudget.BatchReservation batch =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L));
        assertNotNull(batch);
        artifact.waitForInitialStaticWorkset(instance);
        artifact.beginInitialStaticWorkset(instance, requirements, batch);
        HostStaticGeometryVariant first = initialVariant(artifact, instance, requirements.get(0));

        HostPreparedArtifact.StaticVariantPublication publication = artifact.publishStaticVariant(
                requirements.get(0).slot(), geometry, key, artifact.staticGeneration(), first);

        assertTrue(publication.activated());
        assertSame(first, artifact.staticVariant(geometry, key, artifact.staticGeneration()));
        assertTrue(publication.retired().isEmpty());
        assertTrue(artifact.initialStaticWorksetMatches(instance, requirements)
                || artifact.initialStaticWorksetStatus(instance) == HostPreparedArtifact.InitialWorksetStatus.READY);
        artifact.closeStaticVariants();
    }

    @Test
    void changedFrozenPlanRestartsWithoutPublishingOrLeakingTheBatch() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        long before = HostStaticVariantBudget.INSTANCE.artifactBytes(artifact);
        HostPreparedArtifact.InitialStaticRequirement original =
                requirement(1, 1, geometry, key(instance, new Object(), 1), 8);
        HostPreparedArtifact.InitialStaticRequirement changed =
                requirement(1, 1, geometry, key(instance, new Object(), 2), 8);
        HostStaticVariantBudget.BatchReservation batch =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L, 8L));
        assertNotNull(batch);
        HostPreparedArtifact.InitialStaticRequirement second =
                requirement(2, 1, geometry, key(instance, new Object(), 3), 8);
        artifact.waitForInitialStaticWorkset(instance);
        artifact.beginInitialStaticWorkset(instance, List.of(original, second), batch);
        HostStaticVariantAdmissionGate gate = artifact.staticVariantGate(geometry, instance);
        HostStaticAdmissionKey admissionKey = original.key().admissionKey();
        long generation = artifact.staticGeneration();
        assertEquals(HostStaticVariantAdmissionGate.Decision.WAIT,
                gate.evaluate(admissionKey, false, generation, 0));
        assertEquals(HostStaticVariantAdmissionGate.Decision.BUILD,
                gate.evaluate(admissionKey, false, generation, 500_000_000L));
        HostStaticGeometryVariant staged = initialVariant(artifact, instance, original);
        assertFalse(artifact.publishStaticVariant(original.slot(), geometry, original.key(),
                artifact.staticGeneration(), staged).activated());

        assertTrue(artifact.initialStaticWorksetMatches(instance, List.of(original, second)));
        assertFalse(artifact.initialStaticWorksetMatches(instance, List.of(changed, second)));
        List<HostStaticGeometryVariant> retired = artifact.restartInitialStaticWorkset(instance);

        assertEquals(List.of(staged), retired);
        assertEquals(HostPreparedArtifact.InitialWorksetStatus.EMPTY,
                artifact.initialStaticWorksetStatus(instance));
        HostStaticVariantAdmissionGate restartedGate = artifact.staticVariantGate(geometry, instance);
        assertNotSame(gate, restartedGate);
        assertEquals(HostStaticVariantAdmissionGate.Decision.WAIT,
                restartedGate.evaluate(admissionKey, false, generation, 500_000_001L));
        assertEquals(before + 8, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));
        retired.forEach(HostStaticGeometryVariant::close);
        assertEquals(before, HostStaticVariantBudget.INSTANCE.artifactBytes(artifact));
        artifact.closeStaticVariants();
    }

    @Test
    void fieldRevisionDoesNotInterruptACompleteStaticTransaction() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        Object layout = new Object();
        Object renderType = new Object();
        Object texture = new Object();
        HostLightBinding firstLight = HostLightBinding.field(
                new HostLightFieldId("field", 1), ignored -> 0x00100010);
        HostLightBinding newerLight = HostLightBinding.field(
                new HostLightFieldId("field", 2), ignored -> 0x00200020);
        HostStaticVariantKey firstKey = new HostStaticVariantKey(instance, 1,
                new Matrix4f(), new Matrix3f(), 0, firstLight, false,
                1, 1, 1, 1, 0, 1, layout, 1);
        HostStaticVariantKey newerKey = new HostStaticVariantKey(instance, 1,
                new Matrix4f(), new Matrix3f(), 0, newerLight, false,
                1, 1, 1, 1, 0, 1, layout, 1);
        HostPreparedArtifact.InitialStaticRequirement first =
                new HostPreparedArtifact.InitialStaticRequirement(
                        new HostPreparedArtifact.StaticDrawSlot(1, 1), geometry,
                        firstKey, firstLight, 8, renderType, texture);
        HostPreparedArtifact.InitialStaticRequirement newer =
                new HostPreparedArtifact.InitialStaticRequirement(
                        new HostPreparedArtifact.StaticDrawSlot(1, 1), geometry,
                        newerKey, newerLight, 8, renderType, texture);
        HostStaticVariantBudget.BatchReservation batch =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L));
        assertNotNull(batch);
        artifact.waitForInitialStaticWorkset(instance);
        artifact.beginInitialStaticWorkset(instance, List.of(first), batch);

        assertFalse(artifact.initialStaticWorksetMatches(instance, List.of(newer)));
        assertTrue(artifact.initialStaticWorksetStructureMatches(instance, List.of(newer)));

        artifact.closeStaticVariants();
    }

    @Test
    void replacementPublishesNewWorksetAtomicallyAndPromotesAfterOldRetirement() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        HostPreparedArtifact.InitialStaticRequirement oldRequirement =
                requirement(1, 1, geometry, key(instance, new Object(), 1), 8);
        HostStaticVariantBudget.BatchReservation initial =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L));
        assertNotNull(initial);
        artifact.waitForInitialStaticWorkset(instance);
        artifact.beginInitialStaticWorkset(instance, List.of(oldRequirement), initial);
        HostStaticGeometryVariant oldVariant = initialVariant(artifact, instance, oldRequirement);
        assertTrue(artifact.publishStaticVariant(oldRequirement.slot(), geometry, oldRequirement.key(),
                artifact.staticGeneration(), oldVariant).activated());

        HostPreparedArtifact.InitialStaticRequirement replacementRequirement =
                requirement(1, 1, geometry, key(instance, new Object(), 2), 8);
        HostStaticVariantBudget.BatchReservation replacement =
                HostStaticVariantBudget.INSTANCE.tryReserveReplacementBatch(
                        artifact, List.of(8L), artifact.activeStaticWorksetBytes(instance));
        assertNotNull(replacement);
        artifact.beginStaticWorksetReplacement(instance, List.of(replacementRequirement), replacement);
        HostStaticGeometryVariant newVariant = initialVariant(artifact, instance, replacementRequirement);

        assertSame(oldVariant, artifact.staticVariant(geometry, oldRequirement.key(),
                artifact.staticGeneration()));
        assertNull(artifact.staticVariant(geometry, replacementRequirement.key(),
                artifact.staticGeneration()));
        HostPreparedArtifact.StaticVariantPublication publication = artifact.publishStaticVariant(
                replacementRequirement.slot(), geometry, replacementRequirement.key(),
                artifact.staticGeneration(), newVariant);

        assertTrue(publication.activated());
        assertNull(artifact.staticVariant(geometry, oldRequirement.key(), artifact.staticGeneration()));
        assertSame(newVariant, artifact.staticVariant(
                geometry, replacementRequirement.key(), artifact.staticGeneration()));
        assertEquals(List.of(oldVariant), publication.retired());
        publication.retired().forEach(HostStaticGeometryVariant::close);
        publication.retirementComplete().run();
        assertTrue(artifact.activeStaticWorksetMatches(instance, List.of(replacementRequirement)));
        artifact.closeStaticVariants();
    }

    @Test
    void failedReplacementKeepsOldWorksetAndReleasesNewBatch() {
        HostPreparedArtifact artifact = artifact();
        HostEntityGeometry geometry = geometry();
        Object instance = new Object();
        HostPreparedArtifact.InitialStaticRequirement oldRequirement =
                requirement(1, 1, geometry, key(instance, new Object(), 1), 8);
        HostStaticVariantBudget.BatchReservation initial =
                HostStaticVariantBudget.INSTANCE.tryReserveBatch(artifact, List.of(8L));
        assertNotNull(initial);
        artifact.waitForInitialStaticWorkset(instance);
        artifact.beginInitialStaticWorkset(instance, List.of(oldRequirement), initial);
        HostStaticGeometryVariant oldVariant = initialVariant(artifact, instance, oldRequirement);
        artifact.publishStaticVariant(oldRequirement.slot(), geometry, oldRequirement.key(),
                artifact.staticGeneration(), oldVariant);
        HostPreparedArtifact.InitialStaticRequirement replacementRequirement =
                requirement(1, 1, geometry, key(instance, new Object(), 2), 8);
        HostEntityGeometry secondGeometry = geometry();
        HostPreparedArtifact.InitialStaticRequirement secondRequirement =
                requirement(2, 1, secondGeometry, key(instance, new Object(), 3), 8);
        HostStaticVariantBudget.BatchReservation replacement =
                HostStaticVariantBudget.INSTANCE.tryReserveReplacementBatch(
                        artifact, List.of(8L, 8L), artifact.activeStaticWorksetBytes(instance));
        assertNotNull(replacement);
        artifact.beginStaticWorksetReplacement(instance,
                List.of(replacementRequirement, secondRequirement), replacement);
        HostStaticGeometryVariant staged = initialVariant(artifact, instance, replacementRequirement);
        assertFalse(artifact.publishStaticVariant(replacementRequirement.slot(), geometry,
                replacementRequirement.key(), artifact.staticGeneration(), staged).activated());

        List<HostStaticGeometryVariant> retired = artifact.failInitialStaticWorkset(instance);

        assertEquals(List.of(staged), retired);
        assertSame(oldVariant, artifact.staticVariant(geometry, oldRequirement.key(),
                artifact.staticGeneration()));
        assertEquals(HostPreparedArtifact.InitialWorksetStatus.READY,
                artifact.initialStaticWorksetStatus(instance));
        retired.forEach(HostStaticGeometryVariant::close);
        assertTrue(artifact.activeStaticWorksetMatches(instance, List.of(oldRequirement)));
        artifact.closeStaticVariants();
    }

    private static HostPreparedArtifact artifact() {
        ModelDefinition definition = emptyDefinition();
        return HostPreparedArtifact.prepare(definition, StaticModelRenderMetadata.from(definition));
    }

    private static HostStaticGeometryVariant variant(HostPreparedArtifact artifact, int bytes) {
        HostStaticVariantBudget.Reservation reservation = artifact.reserveStaticVariant(bytes);
        assertNotNull(reservation);
        return new HostStaticGeometryVariant(new FakeBuffer(bytes), null, 4, 6, reservation);
    }

    private static HostStaticGeometryVariant initialVariant(HostPreparedArtifact artifact, Object instance,
                                                            HostPreparedArtifact.InitialStaticRequirement required) {
        HostStaticVariantBudget.Reservation reservation = artifact.claimInitialStaticVariant(
                instance, required.slot(), required.geometry(), required.key(), required.bytes());
        assertNotNull(reservation);
        return new HostStaticGeometryVariant(new FakeBuffer(Math.toIntExact(required.bytes())),
                null, 4, 6, reservation);
    }

    private static HostPreparedArtifact.InitialStaticRequirement requirement(
            int node, int primitive, HostEntityGeometry geometry, HostStaticVariantKey key, long bytes) {
        return new HostPreparedArtifact.InitialStaticRequirement(
                new HostPreparedArtifact.StaticDrawSlot(node, primitive), geometry, key, bytes);
    }

    private static HostStaticVariantKey key(Object instance, Object layout, long revision) {
        return new HostStaticVariantKey(instance, revision, new Matrix4f(), new Matrix3f(),
                0, (int) revision, false, 1, 1, 1, 1, 0, 1, layout, 1);
    }

    private static HostEntityGeometry geometry() {
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new LinkedHashMap<>();
        attributes.put(ModelAttributeSemantic.POSITION, attribute(3, 0, 0, 0, 1, 0, 0, 0, 1, 0));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, new ModelVector3(1, 1, 0));
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes,
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
        return HostGeometryProjector.project(primitive, StaticModelTexture.absent());
    }

    private static ModelVertexAttribute attribute(int components, float... values) {
        ByteBuffer data = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) data.putFloat(value);
        return new ModelVertexAttribute(ModelAttributeSemantic.POSITION, ModelComponentType.FLOAT32, components,
                false, values.length / components, data.array());
    }

    private static ModelDefinition emptyDefinition() {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "static-variant",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, ModelVector3.ONE);
        return new ModelDefinition(asset, List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("root", ModelTransform.Trs.IDENTITY, -1, List.of(), Optional.empty())),
                List.of(), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
    }

    private static final class FakeBuffer implements ModelGpuBuffer {
        private final int bytes;
        private int closeCount;

        private FakeBuffer(int bytes) { this.bytes = bytes; }
        @Override public int byteSize() { return bytes; }
        @Override public boolean isClosed() { return closeCount > 0; }
        @Override public void close() { closeCount++; }
    }

    private static final class ThrowingBuffer implements ModelGpuBuffer {
        private boolean closed;
        @Override public int byteSize() { return 1; }
        @Override public boolean isClosed() { return closed; }
        @Override public void close() {
            closed = true;
            throw new IllegalStateException("expected close failure");
        }
    }
}
