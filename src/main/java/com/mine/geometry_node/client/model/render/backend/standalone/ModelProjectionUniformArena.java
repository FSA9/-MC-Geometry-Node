package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Fenced std140 projection matrices supplied by external render passes such as Iris shadows. */
final class ModelProjectionUniformArena implements AutoCloseable {
    private static final int MATRIX_BYTES = 16 * Float.BYTES;
    private final List<Slot> slots = new ArrayList<>();

    GpuBufferSlice upload(CommandEncoder encoder, Matrix4fc projection) {
        RenderSystem.assertOnRenderThread();
        Slot slot = acquire();
        ByteBuffer bytes = slot.staging.clear();
        projection.get(0, bytes);
        bytes.position(0).limit(MATRIX_BYTES);
        encoder.writeToBuffer(slot.buffer.slice(), bytes);
        slot.inFlight = true;
        RenderSystem.queueFencedTask(() -> slot.inFlight = false);
        return slot.buffer.slice();
    }

    private Slot acquire() {
        for (Slot slot : slots) if (!slot.inFlight && !slot.buffer.isClosed()) return slot;
        Slot slot = new Slot(RenderSystem.getDevice().createBuffer(() -> "GeometryNode model projection",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, MATRIX_BYTES),
                ByteBuffer.allocateDirect(MATRIX_BYTES).order(ByteOrder.nativeOrder()));
        slots.add(slot);
        return slot;
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
        private boolean inFlight;

        private Slot(GpuBuffer buffer, ByteBuffer staging) {
            this.buffer = buffer;
            this.staging = staging;
        }
    }
}
