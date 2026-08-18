package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

import static com.mine.geometry_node.client.model.render.backend.host.entity.HostStaticVariantAdmissionGate.Decision.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HostStaticVariantAdmissionGateTest {
    private static final Map<Integer, HostStaticAdmissionKey> TOKENS = new HashMap<>();
    @Test
    void existingVariantHitsImmediatelyEvenDuringCooldown() {
        HostStaticVariantAdmissionGate gate = cooldownGate();

        assertEquals(HIT, gate.evaluate(token(15), true, 1, millis(30)));
    }

    @Test
    void newVariantRequiresDefaultStablePeriod() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();

        assertEquals(WAIT, gate.evaluate(token(15), false, 1, 0));
        assertEquals(WAIT, gate.evaluate(token(15), false, 1, millis(499)));
        assertEquals(BUILD, gate.evaluate(token(15), false, 1, millis(500)));
        assertEquals(BUILDING, gate.evaluate(token(15), false, 1, millis(501)));
    }

    @Test
    void completionAndCancellationEndSingleFlightBuild() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        gate.evaluate(token(15), false, 1, 0);
        assertEquals(BUILD, gate.evaluate(token(15), false, 1, millis(500)));

        gate.recordCancelled(token(15), 1);
        assertEquals(BUILD, gate.evaluate(token(15), false, 1, millis(501)));
        gate.recordSuccess(token(15), 1);
        assertEquals(BUILD, gate.evaluate(token(15), false, 1, millis(502)));
        assertEquals(HIT, gate.evaluate(token(15), true, 1, millis(503)));
    }

    @Test
    void newGenerationMayReplaceAnAbandonedBuild() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        gate.evaluate(token(15), false, 1, 0);
        assertEquals(BUILD, gate.evaluate(token(15), false, 1, millis(500)));

        assertEquals(BUILD, gate.evaluate(token(15), false, 2, millis(501)));
        assertEquals(BUILDING, gate.evaluate(token(15), false, 2, millis(502)));
    }

    @Test
    void thirdChangeWithinWindowStartsCooldown() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();

        assertEquals(WAIT, gate.evaluate(token(1), false, 1, 0));
        assertEquals(WAIT, gate.evaluate(token(2), false, 1, millis(10)));
        assertEquals(COOLDOWN, gate.evaluate(token(3), false, 1, millis(20)));
        assertEquals(COOLDOWN, gate.evaluate(token(3), false, 1, millis(4_999)));
    }

    @Test
    void expiredChangesDoNotTriggerCooldown() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();

        assertEquals(WAIT, gate.evaluate(token(1), false, 1, 0));
        assertEquals(WAIT, gate.evaluate(token(2), false, 1, millis(2_001)));
        assertEquals(WAIT, gate.evaluate(token(3), false, 1, millis(4_002)));
    }

    @Test
    void cooldownRequiresOneSecondOfPostCooldownStability() {
        HostStaticVariantAdmissionGate gate = cooldownGate();

        assertEquals(WAIT, gate.evaluate(token(3), false, 1, millis(5_020)));
        assertEquals(WAIT, gate.evaluate(token(3), false, 1, millis(6_019)));
        assertEquals(BUILD, gate.evaluate(token(3), false, 1, millis(6_020)));
    }

    @Test
    void candidateChangeAfterCooldownRestartsRecoveryStability() {
        HostStaticVariantAdmissionGate gate = cooldownGate();

        assertEquals(WAIT, gate.evaluate(token(3), false, 1, millis(5_020)));
        assertEquals(WAIT, gate.evaluate(token(4), false, 1, millis(5_900)));
        assertEquals(WAIT, gate.evaluate(token(4), false, 1, millis(6_899)));
        assertEquals(BUILD, gate.evaluate(token(4), false, 1, millis(6_900)));
    }

    @Test
    void failureIsRememberedOnlyForItsGenerationAndCanBeCleared() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        gate.recordFailure(token(15), 7);

        assertEquals(FAILED, gate.evaluate(token(15), false, 7, 0));
        assertEquals(WAIT, gate.evaluate(token(15), false, 8, 0));
        gate.recordFailure(token(15), 8);
        assertEquals(FAILED, gate.evaluate(token(15), false, 8, 0));
        gate.clearFailures();
        assertEquals(WAIT, gate.evaluate(token(15), false, 8, 0));
    }

    @Test
    void clearResetsCooldownAndCandidateHistory() {
        HostStaticVariantAdmissionGate gate = cooldownGate();

        gate.clear();

        assertEquals(WAIT, gate.evaluate(token(3), false, 1, millis(30)));
        assertEquals(BUILD, gate.evaluate(token(3), false, 1, millis(530)));
    }

    @Test
    void budgetWaitRetriesAfterBoundedBackoffWithoutBecomingPermanentFailure() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        gate.evaluate(token(15), false, 7, 0);
        assertEquals(BUILD, gate.evaluate(token(15), false, 7, millis(500)));
        gate.recordBudgetWait(token(15), 7, millis(500));

        assertEquals(BUDGET_WAIT, gate.evaluate(token(15), false, 7, millis(749)));
        assertEquals(BUILD, gate.evaluate(token(15), false, 7, millis(750)));
    }

    @Test
    void collidingVariantHashesDoNotShareAdmissionState() {
        Object instance = new Object();
        Object layout = new Object();
        HostStaticVariantKey first = variant(instance, layout, 1, 0);
        HostStaticVariantKey second = variant(instance, layout, 2, -29_791);
        assertNotEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode(), "fixture must exercise the former hash-token collision");

        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        HostStaticAdmissionKey firstAdmission = first.admissionKey();
        HostStaticAdmissionKey secondAdmission = second.admissionKey();
        gate.recordFailure(firstAdmission, 7);

        assertEquals(FAILED, gate.evaluate(firstAdmission, false, 7, 0));
        assertEquals(WAIT, gate.evaluate(secondAdmission, false, 7, 0));
    }

    @Test
    void failedRevisionsDoNotAccumulateUnboundedState() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        for (int revision = 0; revision < 10_000; revision++) {
            gate.recordFailure(token(revision + 100), revision);
            assertEquals(1, gate.rememberedStateCount());
        }

        HostStaticAdmissionKey latest = token(10_099);
        assertEquals(FAILED, gate.evaluate(latest, false, 9_999, 0));
        assertEquals(WAIT, gate.evaluate(token(99), false, 9_999, 0));
    }

    @Test
    void failureBuildAndBudgetMemoryHaveStrictConstantBound() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        gate.recordFailure(token(1), 1);
        gate.evaluate(token(2), false, 2, 0);
        assertEquals(BUILD, gate.evaluate(token(2), false, 2, millis(500)));
        gate.recordBudgetWait(token(3), 3, 0);

        assertEquals(3, gate.rememberedStateCount());
        gate.recordFailure(token(4), 4);
        gate.recordBudgetWait(token(5), 5, 0);
        assertEquals(3, gate.rememberedStateCount());
    }

    private static HostStaticVariantAdmissionGate cooldownGate() {
        HostStaticVariantAdmissionGate gate = new HostStaticVariantAdmissionGate();
        gate.evaluate(token(1), false, 1, 0);
        gate.evaluate(token(2), false, 1, millis(10));
        assertEquals(COOLDOWN, gate.evaluate(token(3), false, 1, millis(20)));
        return gate;
    }

    private static long millis(long value) {
        return value * 1_000_000L;
    }

    private static HostStaticAdmissionKey token(int value) {
        return TOKENS.computeIfAbsent(value, ignored ->
                variant(new Object(), new Object(), value, value).admissionKey());
    }

    private static HostStaticVariantKey variant(Object instance, Object layout, long revision, int light) {
        return new HostStaticVariantKey(instance, revision, new Matrix4f(), new Matrix3f(),
                0, light, false, 1, 1, 1, 1, 0, 1, layout, 0);
    }
}
