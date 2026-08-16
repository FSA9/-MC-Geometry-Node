package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.core.engine.system.model.domain.ModelSkin;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Render-thread-owned fenced UBO pool. One palette slice is shared by every draw of an instance skin. */
final class ModelSkinPaletteArena implements AutoCloseable {
    static final int PALETTE_FLOATS = ModelSkin.MAX_JOINTS * 16 * 2;
    static final int PALETTE_BYTES = PALETTE_FLOATS * Float.BYTES;
    private final List<Slot> slots = new ArrayList<>();

    Map<PaletteKey, GpuBufferSlice> upload(CommandEncoder encoder, Map<PaletteKey, float[]> palettes) {
        RenderSystem.assertOnRenderThread();
        if (palettes.isEmpty()) return Map.of();
        int alignment = Math.max(1, RenderSystem.getDevice().getUniformOffsetAlignment());
        int stride = align(PALETTE_BYTES, alignment);
        int required = Math.multiplyExact(stride, palettes.size());
        Slot slot = acquire(required);
        ByteBuffer bytes = slot.staging;
        bytes.clear();
        Map<PaletteKey, GpuBufferSlice> slices = new LinkedHashMap<>();
        int offset = 0;
        for (Map.Entry<PaletteKey, float[]> entry : palettes.entrySet()) {
            float[] palette = entry.getValue();
            if (palette.length != PALETTE_FLOATS) {
                throw new IllegalArgumentException("skin palette does not match the fixed GPU contract");
            }
            bytes.position(offset);
            for (float value : palette) bytes.putFloat(value);
            slices.put(entry.getKey(), slot.buffer.slice(offset, PALETTE_BYTES));
            offset += stride;
        }
        bytes.position(0).limit(required);
        encoder.writeToBuffer(slot.buffer.slice(0, required), bytes);
        slot.inFlight = true;
        RenderSystem.queueFencedTask(() -> slot.inFlight = false);
        return Map.copyOf(slices);
    }

    private Slot acquire(int required) {
        for (Slot slot : slots) {
            if (!slot.inFlight && !slot.buffer.isClosed() && slot.capacity >= required) return slot;
        }
        int capacity = nextPowerOfTwo(required);
        Slot slot = new Slot(RenderSystem.getDevice().createBuffer(
                () -> "GeometryNode model skin palette arena " + capacity,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, capacity),
                ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder()), capacity);
        slots.add(slot);
        return slot;
    }

    @Override public void close() {
        for (Slot slot : slots) {
            if (slot.buffer.isClosed()) continue;
            if (slot.inFlight) {
                RenderSystem.queueFencedTask(() -> {
                    if (!slot.buffer.isClosed()) slot.buffer.close();
                });
            } else {
                slot.buffer.close();
            }
        }
        slots.clear();
    }

    private static int align(int value, int alignment) {
        return Math.toIntExact((Math.addExact((long) value, alignment - 1L) / alignment) * alignment);
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 0) return 1;
        int highest = Integer.highestOneBit(value);
        return highest == value ? value : Math.multiplyExact(highest, 2);
    }

    private static final class Slot {
        private final GpuBuffer buffer;
        private final ByteBuffer staging;
        private final int capacity;
        private boolean inFlight;

        private Slot(GpuBuffer buffer, ByteBuffer staging, int capacity) {
            this.buffer = buffer;
            this.staging = staging;
            this.capacity = capacity;
        }
    }

    record PaletteKey(String instanceId, int skinIndex, int meshNodeIndex, long poseRevision) { }
}
