package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import static com.mine.geometry_node.client.model.render.backend.host.entity.HostPackedLightVariantGate.Decision.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HostPackedLightVariantGateTest {
    @Test
    void existingVariantHitsImmediatelyEvenDuringCooldown() {
        HostPackedLightVariantGate gate = cooldownGate();

        assertEquals(HIT, gate.evaluate(15, true, 1, millis(30)));
    }

    @Test
    void newVariantRequiresDefaultStablePeriod() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();

        assertEquals(WAIT, gate.evaluate(15, false, 1, 0));
        assertEquals(WAIT, gate.evaluate(15, false, 1, millis(499)));
        assertEquals(BUILD, gate.evaluate(15, false, 1, millis(500)));
        assertEquals(BUILDING, gate.evaluate(15, false, 1, millis(501)));
    }

    @Test
    void completionAndCancellationEndSingleFlightBuild() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();
        gate.evaluate(15, false, 1, 0);
        assertEquals(BUILD, gate.evaluate(15, false, 1, millis(500)));

        gate.recordCancelled(15, 1);
        assertEquals(BUILD, gate.evaluate(15, false, 1, millis(501)));
        gate.recordSuccess(15, 1);
        assertEquals(BUILD, gate.evaluate(15, false, 1, millis(502)));
        assertEquals(HIT, gate.evaluate(15, true, 1, millis(503)));
    }

    @Test
    void newGenerationMayReplaceAnAbandonedBuild() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();
        gate.evaluate(15, false, 1, 0);
        assertEquals(BUILD, gate.evaluate(15, false, 1, millis(500)));

        assertEquals(BUILD, gate.evaluate(15, false, 2, millis(501)));
        assertEquals(BUILDING, gate.evaluate(15, false, 2, millis(502)));
    }

    @Test
    void thirdChangeWithinWindowStartsCooldown() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();

        assertEquals(WAIT, gate.evaluate(1, false, 1, 0));
        assertEquals(WAIT, gate.evaluate(2, false, 1, millis(10)));
        assertEquals(COOLDOWN, gate.evaluate(3, false, 1, millis(20)));
        assertEquals(COOLDOWN, gate.evaluate(3, false, 1, millis(4_999)));
    }

    @Test
    void expiredChangesDoNotTriggerCooldown() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();

        assertEquals(WAIT, gate.evaluate(1, false, 1, 0));
        assertEquals(WAIT, gate.evaluate(2, false, 1, millis(2_001)));
        assertEquals(WAIT, gate.evaluate(3, false, 1, millis(4_002)));
    }

    @Test
    void cooldownRequiresOneSecondOfPostCooldownStability() {
        HostPackedLightVariantGate gate = cooldownGate();

        assertEquals(WAIT, gate.evaluate(3, false, 1, millis(5_020)));
        assertEquals(WAIT, gate.evaluate(3, false, 1, millis(6_019)));
        assertEquals(BUILD, gate.evaluate(3, false, 1, millis(6_020)));
    }

    @Test
    void candidateChangeAfterCooldownRestartsRecoveryStability() {
        HostPackedLightVariantGate gate = cooldownGate();

        assertEquals(WAIT, gate.evaluate(3, false, 1, millis(5_020)));
        assertEquals(WAIT, gate.evaluate(4, false, 1, millis(5_900)));
        assertEquals(WAIT, gate.evaluate(4, false, 1, millis(6_899)));
        assertEquals(BUILD, gate.evaluate(4, false, 1, millis(6_900)));
    }

    @Test
    void failureIsRememberedOnlyForItsGenerationAndCanBeCleared() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();
        gate.recordFailure(15, 7);

        assertEquals(FAILED, gate.evaluate(15, false, 7, 0));
        assertEquals(WAIT, gate.evaluate(15, false, 8, 0));
        gate.recordFailure(15, 8);
        assertEquals(FAILED, gate.evaluate(15, false, 8, 0));
        gate.clearFailures();
        assertEquals(WAIT, gate.evaluate(15, false, 8, 0));
    }

    @Test
    void clearResetsCooldownAndCandidateHistory() {
        HostPackedLightVariantGate gate = cooldownGate();

        gate.clear();

        assertEquals(WAIT, gate.evaluate(3, false, 1, millis(30)));
        assertEquals(BUILD, gate.evaluate(3, false, 1, millis(530)));
    }

    private static HostPackedLightVariantGate cooldownGate() {
        HostPackedLightVariantGate gate = new HostPackedLightVariantGate();
        gate.evaluate(1, false, 1, 0);
        gate.evaluate(2, false, 1, millis(10));
        assertEquals(COOLDOWN, gate.evaluate(3, false, 1, millis(20)));
        return gate;
    }

    private static long millis(long value) {
        return value * 1_000_000L;
    }
}
