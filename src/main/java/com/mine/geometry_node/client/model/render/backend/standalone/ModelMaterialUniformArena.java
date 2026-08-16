package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector4fc;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Frame-owned, versioned std140 material data. */
final class ModelMaterialUniformArena implements AutoCloseable {
    static final int HEADER_VEC4S = 7;
    static final int TEXTURE_SLOTS = 5;
    static final int TRANSFORM_VEC4S_PER_SLOT = 2;
    static final int MATERIAL_BYTES = (HEADER_VEC4S + TEXTURE_SLOTS * TRANSFORM_VEC4S_PER_SLOT)
            * 4 * Float.BYTES;
    private final List<Slot> slots = new ArrayList<>();

    List<GpuBufferSlice> upload(CommandEncoder encoder, List<ModelMaterialUniform> materials) {
        RenderSystem.assertOnRenderThread();
        if (materials.isEmpty()) return List.of();
        int alignment = Math.max(1, RenderSystem.getDevice().getUniformOffsetAlignment());
        int stride = align(MATERIAL_BYTES, alignment);
        int required = Math.multiplyExact(stride, materials.size());
        Slot slot = acquire(required);
        ByteBuffer bytes = slot.staging.clear();
        List<GpuBufferSlice> slices = new ArrayList<>(materials.size());
        int offset = 0;
        for (ModelMaterialUniform material : materials) {
            bytes.position(offset);
            write(bytes, material);
            slices.add(slot.buffer.slice(offset, MATERIAL_BYTES));
            offset += stride;
        }
        bytes.position(0).limit(required);
        encoder.writeToBuffer(slot.buffer.slice(0, required), bytes);
        slot.inFlight = true;
        RenderSystem.queueFencedTask(() -> slot.inFlight = false);
        return List.copyOf(slices);
    }

    static ByteBuffer encode(ModelMaterialUniform material) {
        ByteBuffer bytes = ByteBuffer.allocate(MATERIAL_BYTES).order(ByteOrder.nativeOrder());
        write(bytes, material);
        return bytes.flip();
    }

    private static void write(ByteBuffer bytes, ModelMaterialUniform material) {
        put(bytes, material.baseColor());
        put(bytes, material.emissiveAndCutoff());
        put(bytes, material.pbrFactors());
        put(bytes, material.texturePresence0());
        put(bytes, material.texturePresence1());
        put(bytes, material.uvSlots0());
        put(bytes, material.uvSlots1());
        for (ModelTextureTransform transform : material.textureTransforms()) {
            bytes.putFloat(transform.offsetX()).putFloat(transform.offsetY())
                    .putFloat(transform.rotation()).putFloat(0.0F);
            bytes.putFloat(transform.scaleX()).putFloat(transform.scaleY())
                    .putFloat(0.0F).putFloat(0.0F);
        }
    }

    private Slot acquire(int required) {
        for (Slot slot : slots) if (!slot.inFlight && !slot.buffer.isClosed() && slot.capacity >= required) return slot;
        int capacity = Integer.highestOneBit(required) == required ? required : Math.multiplyExact(Integer.highestOneBit(required), 2);
        Slot slot = new Slot(RenderSystem.getDevice().createBuffer(() -> "GeometryNode model material arena " + capacity,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, capacity),
                ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder()), capacity);
        slots.add(slot);
        return slot;
    }

    private static void put(ByteBuffer bytes, Vector4fc value) {
        bytes.putFloat(value.x()).putFloat(value.y()).putFloat(value.z()).putFloat(value.w());
    }

    private static int align(int value, int alignment) {
        return Math.toIntExact((Math.addExact((long) value, alignment - 1L) / alignment) * alignment);
    }

    @Override public void close() {
        for (Slot slot : slots) {
            if (slot.buffer.isClosed()) continue;
            if (slot.inFlight) RenderSystem.queueFencedTask(slot.buffer::close); else slot.buffer.close();
        }
        slots.clear();
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
}
