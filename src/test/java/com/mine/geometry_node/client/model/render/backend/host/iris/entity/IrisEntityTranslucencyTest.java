package com.mine.geometry_node.client.model.render.backend.host.iris.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class IrisEntityTranslucencyTest {
    @Test
    void missingIrisFailsClosedToOpaqueFallback() {
        IrisEntityTranslucency.clear();
        IrisEntityTranslucency.Snapshot snapshot = IrisEntityTranslucency.snapshot();
        assertFalse(snapshot.dedicatedProgram());
    }
}
