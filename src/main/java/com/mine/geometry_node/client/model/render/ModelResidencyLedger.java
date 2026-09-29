package com.mine.geometry_node.client.model.render;

import java.util.*;

/** Render-thread ledger for bounded CPU/GPU/texture residency. Eviction only targets inactive GPU entries. */
public final class ModelResidencyLedger {
    public record Snapshot(long cpuBytes, long gpuBytes, long textureBytes, int assets, int evictableAssets, long evictions, String lastFailure) {}
    private static final class Entry {
        final ModelRenderAsset asset;
        final long cpuBytes;
        final long gpuBytes;
        final long textureBytes;
        long lastTouch;
        boolean active;
        Entry(ModelRenderAsset asset, long cpuBytes, long gpuBytes, long textureBytes, long lastTouch) {
            this.asset = asset; this.cpuBytes = cpuBytes; this.gpuBytes = gpuBytes; this.textureBytes = textureBytes; this.lastTouch = lastTouch;
        }
    }
    private final IdentityHashMap<ModelRenderAsset,Entry> entries = new IdentityHashMap<>();
    private ModelRenderBudget budget = ModelRenderBudget.DEFAULT;
    private long cpuBytes;
    private long gpuBytes;
    private long textureBytes;
    private long clock;
    private long evictions;
    private String lastFailure = "";

    public void setBudget(ModelRenderBudget budget) { this.budget = Objects.requireNonNull(budget); }
    public ModelRenderBudget budget() { return budget; }
    public void beginFrame() { entries.values().forEach(entry -> entry.active = false); }
    public void touch(ModelRenderAsset asset) {
        Entry entry = entries.get(asset);
        if (entry != null) { entry.active = true; entry.lastTouch = ++clock; }
    }
    public synchronized boolean reserve(ModelRenderAsset asset, long cpu, long gpu, long texture) {
        Objects.requireNonNull(asset);
        if (cpu < 0 || gpu < 0 || texture < 0) throw new IllegalArgumentException("negative model residency");
        Entry existing = entries.get(asset);
        if (existing != null) { existing.active = true; existing.lastTouch = ++clock; return true; }
        if (cpu > budget.maxResidentCpuBytes() || gpu > budget.maxResidentGpuBytes() || texture > budget.maxResidentTextureBytes()) {
            lastFailure = "single asset exceeds configured CPU/GPU/texture residency budget";
            return false;
        }
        makeRoom(cpu, gpu, texture, asset);
        if (cpuBytes + cpu > budget.maxResidentCpuBytes() || gpuBytes + gpu > budget.maxResidentGpuBytes() || textureBytes + texture > budget.maxResidentTextureBytes()) {
            lastFailure = "resident model budget exhausted by active assets";
            return false;
        }
        Entry entry = new Entry(asset, cpu, gpu, texture, ++clock);
        entries.put(asset, entry);
        cpuBytes += cpu; gpuBytes += gpu; textureBytes += texture;
        return true;
    }
    private void makeRoom(long cpu, long gpu, long texture, ModelRenderAsset protectedAsset) {
        while (cpuBytes + cpu > budget.maxResidentCpuBytes() || gpuBytes + gpu > budget.maxResidentGpuBytes() || textureBytes + texture > budget.maxResidentTextureBytes()) {
            Entry victim = entries.values().stream()
                    .filter(entry -> !entry.active && entry.asset != protectedAsset)
                    .min(Comparator.comparingLong(entry -> entry.lastTouch)).orElse(null);
            if (victim == null) return;
            entries.remove(victim.asset);
            cpuBytes -= victim.cpuBytes; gpuBytes -= victim.gpuBytes; textureBytes -= victim.textureBytes;
            evictions++;
            victim.asset.evictGpu();
        }
    }
    public synchronized void release(ModelRenderAsset asset) {
        Entry entry = entries.remove(asset);
        if (entry == null) return;
        cpuBytes -= entry.cpuBytes; gpuBytes -= entry.gpuBytes; textureBytes -= entry.textureBytes;
    }
    public synchronized Snapshot snapshot() {
        int evictable = (int) entries.values().stream().filter(entry -> !entry.active).count();
        return new Snapshot(cpuBytes, gpuBytes, textureBytes, entries.size(), evictable, evictions, lastFailure);
    }
}
