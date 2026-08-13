package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.client.model.gpu.DecodedModelImage;
import com.mine.geometry_node.client.model.gpu.ModelImageDecoder;
import com.mine.geometry_node.core.engine.system.model.domain.ModelImageSource;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public final class NativeImageModelDecoder implements ModelImageDecoder {
    @Override
    public DecodedModelImage decode(ModelImageSource source) throws IOException {
        byte[] encodedBytes = source.encodedData();
        ByteBuffer encoded = MemoryUtil.memAlloc(encodedBytes.length);
        try {
            encoded.put(encodedBytes).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer sourceComponents = stack.mallocInt(1);
                ByteBuffer decoded = STBImage.stbi_load_from_memory(encoded, width, height, sourceComponents, 4);
                if (decoded == null) {
                    throw new IOException("Could not decode model image: " + STBImage.stbi_failure_reason());
                }
                try {
                    int byteCount = Math.multiplyExact(Math.multiplyExact(width.get(0), height.get(0)), 4);
                    byte[] rgba = new byte[byteCount];
                    decoded.get(0, rgba);
                    return new DecodedModelImage(width.get(0), height.get(0), rgba);
                } finally {
                    STBImage.stbi_image_free(decoded);
                }
            }
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }
}
