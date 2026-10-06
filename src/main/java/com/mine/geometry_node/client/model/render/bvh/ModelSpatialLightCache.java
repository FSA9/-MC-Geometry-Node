package com.mine.geometry_node.client.model.render.bvh;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/** World lifetime and brightness adapter for bounded spatial light samples. */
public final class ModelSpatialLightCache {
    private Level level;
    private final ModelLightSamples samples = new ModelLightSamples();
    private long worldGeneration = 1;

    public void beginFrame(Level next) {
        if (level != next) {
            level = next;
            worldGeneration++;
            samples.invalidateAll("world_changed");
        }
    }

    public int[] sample(Level world, BlockPos pos) {
        if (level != world) beginFrame(world);
        return samples.sample(pos.getX(), pos.getY(), pos.getZ(),
                world.getBrightness(LightLayer.BLOCK, pos), world.getBrightness(LightLayer.SKY, pos));
    }

    public void invalidate(BlockPos center) {
        invalidateRegion(center, 1, "block:" + center.asLong());
    }

    public void invalidateRegion(BlockPos center, int radius, String reason) {
        samples.invalidateRegion(center.getX(), center.getY(), center.getZ(), radius, reason);
    }

    public void invalidateAll(String reason) { samples.invalidateAll(reason); }

    int size() { return samples.size(); }

    public Map<String, Object> report() {
        var result = new LinkedHashMap<String, Object>(samples.report());
        result.put("worldGeneration", worldGeneration);
        return result;
    }
}
