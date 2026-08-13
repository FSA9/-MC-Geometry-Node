package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.importer.ModelImportException;
import com.mine.geometry_node.core.engine.system.model.importer.ModelImportSession;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class GlbContainerReader {
    private static final int MAGIC = 0x46546C67;
    private static final int VERSION = 2;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;
    private static final int HEADER_BYTES = 12;
    private static final int CHUNK_HEADER_BYTES = 8;
    private static final int MAX_JSON_BYTES = 16 << 20;

    private GlbContainerReader() {
    }

    static GlbContainer read(ByteBuffer content, ModelImportSession session) throws ModelImportException {
        session.checkpoint("glb.header");
        ByteBuffer input = content.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int contentLength = input.remaining();
        if (contentLength < HEADER_BYTES + CHUNK_HEADER_BYTES) {
            throw GlbFailures.invalid("glb.header", "GLB is shorter than its required header and JSON chunk");
        }
        if (input.getInt() != MAGIC) throw GlbFailures.invalid("glb.header.magic", "invalid GLB magic");
        if (input.getInt() != VERSION) throw GlbFailures.unsupported("glb.header.version", "only GLB 2.0 is supported");
        long declaredLength = Integer.toUnsignedLong(input.getInt());
        if (declaredLength != contentLength) {
            throw GlbFailures.invalid("glb.header.length", "declared GLB length does not match source length");
        }

        ByteBuffer jsonChunk = readChunk(input, JSON_CHUNK, "glb.json");
        byte[] json = new byte[jsonChunk.remaining()];
        jsonChunk.get(json);
        if (json.length == 0 || json.length > MAX_JSON_BYTES) {
            throw GlbFailures.invalid("glb.json", "JSON chunk is empty or exceeds the 16 MiB hard limit");
        }
        ByteBuffer binary = ByteBuffer.allocate(0).asReadOnlyBuffer();
        if (input.hasRemaining()) binary = readChunk(input, BIN_CHUNK, "glb.bin");
        if (input.hasRemaining()) throw GlbFailures.unsupported("glb.chunks", "additional GLB chunks are not supported");
        return new GlbContainer(json, binary);
    }

    private static ByteBuffer readChunk(ByteBuffer input, int requiredType, String location) throws ModelImportException {
        if (input.remaining() < CHUNK_HEADER_BYTES) throw GlbFailures.invalid(location, "truncated GLB chunk header");
        long length = Integer.toUnsignedLong(input.getInt());
        int type = input.getInt();
        if (type != requiredType) throw GlbFailures.invalid(location, "GLB chunk appears in an invalid order or has an invalid type");
        if ((length & 3L) != 0L) throw GlbFailures.invalid(location, "GLB chunk length must be aligned to four bytes");
        if (length > input.remaining()) throw GlbFailures.invalid(location, "GLB chunk exceeds the declared file boundary");
        int start = input.position();
        int end = Math.toIntExact(start + length);
        ByteBuffer view = input.asReadOnlyBuffer();
        view.position(start).limit(end);
        ByteBuffer result = view.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        input.position(end);
        return result;
    }

    record GlbContainer(byte[] json, ByteBuffer binary) {
    }
}
