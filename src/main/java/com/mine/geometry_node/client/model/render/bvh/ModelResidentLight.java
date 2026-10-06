package com.mine.geometry_node.client.model.render.bvh;

import com.mine.geometry_node.client.model.render.pipeline.ModelIntegerAttributeOverride;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

/**
 * MC 26.1 OpenGL adapter for per-draw entity UV2. Iris receives the real block/sky
 * coordinates while all static interleaved vertex bytes remain resident.
 * The mixin invokes drawElements only after the host has bound the mesh VAO.
 */
public final class ModelResidentLight {
    private static int attribute = -1;
    private static int packedLight;
    private static boolean applied;
    private static long draws;
    private static final ModelIntegerAttributeOverride.Driver GL = new ModelIntegerAttributeOverride.Driver() {
        public boolean enabled(int index) { return GL20C.glGetVertexAttribi(index, GL20C.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0; }
        public void enabled(int index, boolean value) {
            if (value) GL20C.glEnableVertexAttribArray(index); else GL20C.glDisableVertexAttribArray(index);
        }
        public void current(int index, int x, int y, int z, int w) { GL30C.glVertexAttribI4i(index, x, y, z, w); }
    };

    public static void draw(RenderPass pass, VertexFormat format, int light, int indexCount) {
        draw(pass, format, light, 0, indexCount);
    }

    public static void draw(RenderPass pass, VertexFormat format, int light, int firstIndex, int indexCount) {
        RenderSystem.assertOnRenderThread();
        if (attribute >= 0) throw new IllegalStateException("nested resident light override");
        int slot = format.getElementAttributeNames().indexOf("UV2");
        if (slot < 0) throw new IllegalStateException("Iris entity layout has no UV2 attribute");
        attribute = slot;
        packedLight = light;
        applied = false;
        try {
            pass.drawIndexed(0, firstIndex, indexCount, 1);
            if (!applied) throw new IllegalStateException("MC 26.1 resident UV2 adapter was not invoked");
            draws++;
        } finally {
            attribute = -1;
            packedLight = 0;
            applied = false;
        }
    }

    /** Called by the narrowly scoped host draw hook; unrelated draws pass through unchanged. */
    public static void drawElements(int mode, int count, int type, long offset) {
        if (attribute < 0) {
            GlStateManager._drawElements(mode, count, type, offset);
            return;
        }
        ModelIntegerAttributeOverride.draw(GL, attribute, packedLight,
                () -> GlStateManager._drawElements(mode, count, type, offset));
        applied = true;
    }

    public static long draws() { return draws; }
    private ModelResidentLight() {}
}
