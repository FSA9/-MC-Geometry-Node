package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextureProjectionFailureTest {
    @Test
    void assetFailureIsStableForTheAssetLifetime() {
        TextureProjectionFailure failure = TextureProjectionFailure.asset("bad image", new Exception("decode"));

        assertEquals(TextureProjectionFailure.Kind.ASSET, failure.kind());
        assertTrue(failure.cacheForAssetLifetime());
        assertEquals("decode", failure.getCause().getMessage());
    }

    @Test
    void hostRuntimeFailureRemainsRetryable() {
        TextureProjectionFailure failure = TextureProjectionFailure.runtime("registration", new Exception("host"));

        assertEquals(TextureProjectionFailure.Kind.RUNTIME, failure.kind());
        assertFalse(failure.cacheForAssetLifetime());
        assertEquals("host", failure.getCause().getMessage());
    }
}
