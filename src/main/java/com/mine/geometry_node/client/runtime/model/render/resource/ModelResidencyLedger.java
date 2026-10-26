package com.mine.geometry_node.client.runtime.model.render.resource;

import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;

import java.util.*;

/** Render-thread ledger for bounded CPU/GPU/texture residency. Eviction only targets inactive GPU entries. */
public final class ModelResidencyLedger {
    public record Snapshot(long cpuBytes, long gpuBytes, long textureBytes, int assets, int evictableAssets, long evictions, String lastFailure) {}
    private static final class Entry {
        final ModelRenderAsset asset;
        final long cpuBytes;
        long gpuBytes;
        long textureBytes;
        long retiringGeometryBytes;
        long lastTouch;
        boolean active;
        boolean retiring;
        Entry(ModelRenderAsset asset, long cpuBytes, long gpuBytes, long textureBytes, long lastTouch) {
            this.asset = asset; this.cpuBytes = cpuBytes; this.gpuBytes = gpuBytes; this.textureBytes = textureBytes; this.lastTouch = lastTouch; this.active = true;
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
        if (existing != null && existing.retiring) return false;
        if (existing != null) { existing.active = true; existing.lastTouch = ++clock; return true; }
        if (cpu > budget.maxResidentCpuBytes() || gpu > budget.maxResidentGpuBytes() || texture > budget.maxResidentTextureBytes()) {
            lastFailure = "single asset exceeds configured CPU/GPU/texture residency budget";
            return false;
        }
        if (cpuBytes + cpu <= budget.maxResidentCpuBytes() && textureBytes + texture <= budget.maxResidentTextureBytes()
                && gpu > budget.maxResidentGpuBytes() - gpuBytes)
            retireUnusedGeometry(gpu, null);
        makeRoom(cpu, gpu, texture, asset);
        if (cpuBytes + cpu > budget.maxResidentCpuBytes() || gpuBytes + gpu > budget.maxResidentGpuBytes() || textureBytes + texture > budget.maxResidentTextureBytes()) {
            lastFailure = cpuBytes + cpu <= budget.maxResidentCpuBytes() && textureBytes + texture <= budget.maxResidentTextureBytes()
                    && gpu <= budget.maxResidentGpuBytes() - projectedGpuBytes()
                    ? "awaiting fenced geometry retirement" : "resident model budget exhausted by active assets";
            return false;
        }
        Entry entry = new Entry(asset, cpu, gpu, texture, ++clock);
        entries.put(asset, entry);
        cpuBytes += cpu; gpuBytes += gpu; textureBytes += texture;
        return true;
    }
    /** Includes pending fenced retirements in the limit so reload cannot transiently double VRAM. */
    public synchronized boolean reserveAdditionalGpu(ModelRenderAsset asset, long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative GPU reservation");
        Entry entry = entries.get(asset);
        if (entry == null || entry.retiring) { lastFailure = "model residency is unavailable"; return false; }
        if (bytes > budget.maxResidentGpuBytes() - entry.gpuBytes || bytes > budget.maxResidentGpuBytes() - gpuBytes)
            retireUnusedGeometry(bytes, entry);
        if (bytes > budget.maxResidentGpuBytes() - entry.gpuBytes) {
            lastFailure = entry.retiringGeometryBytes > 0 ? "awaiting fenced geometry retirement"
                    : "single asset active geometry exceeds configured GPU budget";
            return false;
        }
        if (bytes > budget.maxResidentGpuBytes() - gpuBytes) makeRoom(0, bytes, 0, asset);
        if (bytes > budget.maxResidentGpuBytes() - gpuBytes) {
            lastFailure = bytes <= budget.maxResidentGpuBytes() - projectedGpuBytes()
                    ? "awaiting fenced geometry retirement" : "active geometry exceeds configured GPU budget";
            return false;
        }
        entry.gpuBytes += bytes; gpuBytes += bytes; entry.active = true; entry.lastTouch = ++clock;
        return true;
    }
    /** Material replacements count both old and staging textures until the retirement fence. */
    public synchronized boolean reserveAdditionalTextures(ModelRenderAsset asset, long bytes) {
        if(bytes<0) throw new IllegalArgumentException("negative texture reservation");
        Entry entry=entries.get(asset);
        if(entry==null || entry.retiring || bytes>budget.maxResidentTextureBytes()-textureBytes) { lastFailure="material texture residency budget exhausted"; return false; }
        if(!reserveAdditionalGpu(asset,bytes)) return false;
        entry.textureBytes+=bytes; textureBytes+=bytes; return true;
    }
    public synchronized void releaseAdditionalTextures(ModelRenderAsset asset,long bytes) {
        Entry entry=entries.get(asset); if(entry==null)return;
        if(bytes<0 || bytes>entry.textureBytes) throw new IllegalArgumentException("invalid texture release");
        entry.textureBytes-=bytes; textureBytes-=bytes; releaseAdditionalGpu(asset,bytes);
    }
    /** Fixed-size renderer-owned light snapshots share the global CPU limit with prepared assets. */
    public synchronized boolean reserveTransientCpu(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative CPU reservation");
        if (bytes > budget.maxResidentCpuBytes() - cpuBytes) {
            lastFailure = "local-light CPU snapshot budget exhausted";
            return false;
        }
        cpuBytes += bytes;
        return true;
    }
    public synchronized void releaseTransientCpu(long bytes) {
        if (bytes < 0 || bytes > cpuBytes) throw new IllegalArgumentException("invalid transient CPU release");
        cpuBytes -= bytes;
    }
    /** Renderer-owned sorted indices remain charged independently of any one model lifetime. */
    public synchronized boolean reserveTransientGpu(long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative GPU reservation");
        if (bytes > budget.maxResidentGpuBytes() - gpuBytes && bytes <= budget.maxResidentGpuBytes())
            retireUnusedGeometry(bytes, null);
        if (bytes > budget.maxResidentGpuBytes() - gpuBytes) {
            lastFailure = bytes <= budget.maxResidentGpuBytes() - projectedGpuBytes()
                    ? "awaiting fenced geometry retirement" : "transient GPU budget exhausted by active geometry";
            return false;
        }
        gpuBytes += bytes;
        return true;
    }
    public synchronized void releaseTransientGpu(long bytes) {
        if (bytes < 0 || bytes > gpuBytes) throw new IllegalArgumentException("invalid transient GPU release");
        gpuBytes -= bytes;
    }
    public synchronized void releaseAdditionalGpu(ModelRenderAsset asset, long bytes) {
        if (bytes < 0) throw new IllegalArgumentException("negative GPU release");
        Entry entry = entries.get(asset);
        if (entry == null) return;
        if (bytes > entry.gpuBytes) throw new IllegalStateException("GPU release exceeds reservation");
        entry.gpuBytes -= bytes; gpuBytes -= bytes;
    }

    public synchronized void markGeometryRetiring(ModelRenderAsset asset, long bytes) {
        Entry entry = entries.get(asset);
        if (entry == null) return;
        if (bytes < 0 || bytes > entry.gpuBytes - entry.retiringGeometryBytes)
            throw new IllegalArgumentException("invalid retiring geometry reservation");
        entry.retiringGeometryBytes += bytes;
    }

    public synchronized void releaseRetiredGeometry(ModelRenderAsset asset, long bytes) {
        Entry entry = entries.get(asset);
        if (entry == null) return;
        if (bytes < 0 || bytes > entry.retiringGeometryBytes)
            throw new IllegalArgumentException("invalid retired geometry release");
        entry.retiringGeometryBytes -= bytes;
        releaseAdditionalGpu(asset, bytes);
    }

    private long projectedGpuBytes() {
        long projected = gpuBytes;
        for (Entry entry : entries.values()) projected -= entry.retiring ? entry.gpuBytes : entry.retiringGeometryBytes;
        return projected;
    }

    private void retireUnusedGeometry(long requestedBytes, Entry owner) {
        long frame = ModelIrisUploadBudget.INSTANCE.frame();
        if (frame < 2) return;
        long ownerNeeded = owner == null ? 0
                : Math.max(0, requestedBytes - (budget.maxResidentGpuBytes() - owner.gpuBytes + owner.retiringGeometryBytes));
        long globalNeeded = Math.max(0, requestedBytes - (budget.maxResidentGpuBytes() - projectedGpuBytes()));
        if (owner != null && Math.max(ownerNeeded, globalNeeded) > 0)
            owner.asset.retireUnusedGeometry(frame, Math.max(ownerNeeded, globalNeeded));
        List<Entry> others = entries.values().stream().filter(entry -> entry != owner && !entry.retiring)
                .sorted(Comparator.comparingLong(entry -> entry.lastTouch)).toList();
        for (Entry candidate : others) {
            globalNeeded = Math.max(0, requestedBytes - (budget.maxResidentGpuBytes() - projectedGpuBytes()));
            if (globalNeeded == 0) break;
            candidate.asset.retireUnusedGeometry(frame, globalNeeded);
        }
    }

    private void makeRoom(long cpu, long gpu, long texture, ModelRenderAsset protectedAsset) {
        // Fences complete asynchronously. Select only enough victims for the requested allocation;
        // this reservation may still need to retry after those retirements actually finish.
        long projectedCpu = cpuBytes, projectedGpu = gpuBytes, projectedTexture = textureBytes;
        for (Entry entry : entries.values()) {
            if (entry.retiring) {
                projectedCpu -= entry.cpuBytes; projectedGpu -= entry.gpuBytes; projectedTexture -= entry.textureBytes;
            } else projectedGpu -= entry.retiringGeometryBytes;
        }
        while (projectedCpu + cpu > budget.maxResidentCpuBytes() || projectedGpu + gpu > budget.maxResidentGpuBytes() || projectedTexture + texture > budget.maxResidentTextureBytes()) {
            Entry victim = entries.values().stream()
                    .filter(entry -> !entry.active && !entry.retiring && entry.asset != protectedAsset)
                    .min(Comparator.comparingLong(entry -> entry.lastTouch)).orElse(null);
            if (victim == null) return;
            victim.retiring = true;
            projectedCpu -= victim.cpuBytes; projectedGpu -= victim.gpuBytes - victim.retiringGeometryBytes; projectedTexture -= victim.textureBytes;
            evictions++;
            victim.asset.evictGpu();
        }
    }
    /** Called between frames on the render thread; fenced retirement preserves outstanding GPU use. */
    public synchronized void clearInactive() {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        for (Entry entry : List.copyOf(entries.values())) {
            if (entry.active || entry.retiring) continue;
            entry.retiring = true;
            evictions++;
            entry.asset.evictGpu();
        }
    }

    public synchronized void release(ModelRenderAsset asset) {
        Entry entry = entries.remove(asset);
        if (entry == null) return;
        cpuBytes -= entry.cpuBytes; gpuBytes -= entry.gpuBytes; textureBytes -= entry.textureBytes;
    }
    public synchronized Snapshot snapshot() {
        int evictable = (int) entries.values().stream().filter(entry -> !entry.active && !entry.retiring).count();
        return new Snapshot(cpuBytes, gpuBytes, textureBytes, entries.size(), evictable, evictions, lastFailure);
    }
}
