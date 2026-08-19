package com.mine.geometry_node.client.model.render.backend.host.light.source;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostPlacedLightSourceProviderTest {
    private static final ModelDimensionId DIMENSION = new ModelDimensionId("minecraft:overworld");
    private static final HostLightSectionKey SECTION = new HostLightSectionKey(DIMENSION, 1, 2, 3);

    @Test
    void completeSectionReplacementIsStableAndRemovesMissingSources() {
        HostPlacedLightSourceProvider provider = new HostPlacedLightSourceProvider(DIMENSION);
        HostLightSource first = source("first", 1, 4);
        HostLightSource second = source("second", 1, 8);

        provider.replaceSection(SECTION, List.of(first, second));
        long populatedRevision = provider.revision();
        provider.replaceSection(SECTION, List.of(second, first));
        assertEquals(populatedRevision, provider.revision());

        provider.replaceSection(SECTION, List.of(second));
        assertEquals(populatedRevision + 1, provider.revision());
        assertEquals(List.of(second), provider.snapshot().sources());
    }

    @Test
    void providerIdentityCannotCollideWithinOneSection() {
        HostPlacedLightSourceProvider provider = new HostPlacedLightSourceProvider(DIMENSION);
        HostLightSource firstProvider = source("same", 1, 4);
        HostLightSource otherProvider = new HostLightSource(new HostLightSourceId(DIMENSION, "other",
                HostLightSourceKind.PLACED_BLOCK, 1, 2, 3, "same"), 1,
                1.5, 2.5, 3.5, 1, 1, 1, 8, 8);

        provider.replaceSection(SECTION, List.of(firstProvider, otherProvider));
        assertEquals(2, provider.snapshot().sources().size());
        assertTrue(provider.remove(SECTION, "same"));
        assertTrue(provider.snapshot().sources().isEmpty());
    }

    private static HostLightSource source(String localIdentity, long revision, int intensity) {
        return new HostLightSource(new HostLightSourceId(DIMENSION, "world-emission",
                HostLightSourceKind.PLACED_BLOCK, 1, 2, 3, localIdentity), revision,
                1.5, 2.5, 3.5, 1, 1, 1, intensity, intensity);
    }
}
