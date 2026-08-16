package com.mine.geometry_node.client.model.render.backend.host.entity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostTransparentOrderKeyTest {
    @Test
    void sortsFarToNearAndUsesStableIdentityForEqualDistance() {
        var near = new HostTransparentOrderKey(4, "b", 0, 0, "instance");
        var far = new HostTransparentOrderKey(25, "z", 0, 0, "instance");
        var equalFirst = new HostTransparentOrderKey(9, "a", 0, 0, "instance");
        var equalSecond = new HostTransparentOrderKey(9, "a", 0, 1, "instance");
        List<HostTransparentOrderKey> keys = new ArrayList<>(
                List.of(near, equalSecond, far, equalFirst));

        keys.sort(null);

        assertEquals(List.of(far, equalFirst, equalSecond, near), keys);
    }

    @Test
    void invalidDistanceSortsAfterFiniteDraws() {
        assertEquals(1, Integer.signum(HostTransparentOrderKey.compareDistanceFarToNear(Float.NaN, 1)));
        assertEquals(-1, Integer.signum(HostTransparentOrderKey.compareDistanceFarToNear(1, Float.NaN)));
    }
}
