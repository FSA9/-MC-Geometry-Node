package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.system.model.domain.ModelImageSource;
import com.mine.geometry_node.core.engine.system.model.importer.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.Locale;

final class GlbImageReader {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private GlbImageReader() {
    }

    static ModelImageSource read(JsonObject image, String location, GlbDocument document,
                                 GlbAccessorDecoder decoder, ModelImportSession session)
            throws ModelImportException {
        String declaredMime = GlbJson.string(image, "mimeType", "", location).toLowerCase(Locale.ROOT);
        byte[] content;
        String mime;
        if (image.has("bufferView")) {
            if (image.has("uri")) throw GlbFailures.invalid(location, "image cannot define both bufferView and uri");
            int viewIndex = GlbJson.requiredInt(image, "bufferView", location);
            GlbDocument.BufferView view = document.bufferView(viewIndex, location + ".bufferView");
            session.budgetTracker().claim(ModelBudgetResource.ENCODED_IMAGE_BYTES, view.length(), location);
            content = decoder.bufferViewBytes(viewIndex, location + ".bufferView");
            mime = declaredMime;
        } else if (image.has("uri")) {
            String uri = GlbJson.string(image, "uri", "", location);
            DataUri decoded = decodeDataUri(uri, location + ".uri", session);
            mime = declaredMime.isEmpty() ? decoded.mime : declaredMime;
            if (!declaredMime.isEmpty() && !declaredMime.equals(decoded.mime)) {
                throw GlbFailures.invalid(location + ".mimeType", "declared image MIME type does not match data URI");
            }
            content = decoded.content;
        } else {
            throw GlbFailures.invalid(location, "image requires an embedded bufferView or data URI");
        }
        if (!mime.equals("image/png") && !mime.equals("image/jpeg")) {
            throw GlbFailures.unsupported(location + ".mimeType", "only embedded PNG and JPEG images are supported");
        }
        Dimensions dimensions = mime.equals("image/png") ? pngDimensions(content, location) : jpegDimensions(content, location);
        int maxDimension = session.budget().maxImageDimension();
        if (dimensions.width > maxDimension || dimensions.height > maxDimension) {
            throw new ModelImportException(new ModelImportFailure(ModelImportErrorCode.LIMIT_EXCEEDED,
                    location, "image dimensions exceed the model import budget",
                    Math.max(dimensions.width, dimensions.height), maxDimension));
        }
        long decodedBytes = Math.multiplyExact(Math.multiplyExact((long) dimensions.width, dimensions.height), 4L);
        session.budgetTracker().claim(ModelBudgetResource.DECODED_IMAGE_BYTES, decodedBytes, location);
        return new ModelImageSource(mime, dimensions.width, dimensions.height, content);
    }

    private static DataUri decodeDataUri(String uri, String location, ModelImportSession session)
            throws ModelImportException {
        int comma = uri.indexOf(',');
        if (!uri.startsWith("data:") || comma < 0) throw GlbFailures.unsupported(location, "external image URI is not supported");
        String metadata = uri.substring(5, comma).toLowerCase(Locale.ROOT);
        if (!metadata.endsWith(";base64")) throw GlbFailures.unsupported(location, "image data URI must use base64 encoding");
        String mime = metadata.substring(0, metadata.length() - 7);
        String payload = uri.substring(comma + 1);
        if ((payload.length() & 3) != 0) throw GlbFailures.invalid(location, "image base64 length is invalid");
        int padding = payload.endsWith("==") ? 2 : payload.endsWith("=") ? 1 : 0;
        long decodedLength = (long) payload.length() / 4L * 3L - padding;
        session.budgetTracker().claim(ModelBudgetResource.ENCODED_IMAGE_BYTES, decodedLength, location);
        try {
            return new DataUri(mime, Base64.getDecoder().decode(payload));
        } catch (IllegalArgumentException exception) {
            throw GlbFailures.invalid(location, "image data URI contains invalid base64");
        }
    }

    private static Dimensions pngDimensions(byte[] content, String location) throws ModelImportException {
        if (content.length < 24) throw GlbFailures.invalid(location, "PNG image is truncated");
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (content[i] != PNG_SIGNATURE[i]) throw GlbFailures.invalid(location, "image does not contain a PNG signature");
        }
        if (content[12] != 'I' || content[13] != 'H' || content[14] != 'D' || content[15] != 'R') {
            throw GlbFailures.invalid(location, "PNG does not begin with an IHDR chunk");
        }
        ByteBuffer buffer = ByteBuffer.wrap(content).order(ByteOrder.BIG_ENDIAN);
        int width = buffer.getInt(16);
        int height = buffer.getInt(20);
        if (width < 1 || height < 1) throw GlbFailures.invalid(location, "PNG dimensions are invalid");
        return new Dimensions(width, height);
    }

    private static Dimensions jpegDimensions(byte[] content, String location) throws ModelImportException {
        if (content.length < 4 || (content[0] & 0xFF) != 0xFF || (content[1] & 0xFF) != 0xD8) {
            throw GlbFailures.invalid(location, "image does not contain a JPEG SOI marker");
        }
        int offset = 2;
        while (offset + 3 < content.length) {
            while (offset < content.length && (content[offset] & 0xFF) != 0xFF) offset++;
            while (offset < content.length && (content[offset] & 0xFF) == 0xFF) offset++;
            if (offset >= content.length) break;
            int marker = content[offset++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) break;
            if (marker == 0x01 || marker >= 0xD0 && marker <= 0xD7) continue;
            if (offset + 2 > content.length) break;
            int length = ((content[offset] & 0xFF) << 8) | (content[offset + 1] & 0xFF);
            if (length < 2 || offset + length > content.length) throw GlbFailures.invalid(location, "JPEG segment is truncated");
            if (isStartOfFrame(marker)) {
                if (length < 7) throw GlbFailures.invalid(location, "JPEG SOF segment is invalid");
                int height = ((content[offset + 3] & 0xFF) << 8) | (content[offset + 4] & 0xFF);
                int width = ((content[offset + 5] & 0xFF) << 8) | (content[offset + 6] & 0xFF);
                if (width < 1 || height < 1) throw GlbFailures.invalid(location, "JPEG dimensions are invalid");
                return new Dimensions(width, height);
            }
            offset += length;
        }
        throw GlbFailures.invalid(location, "JPEG does not contain a supported SOF marker");
    }

    private static boolean isStartOfFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xC3 || marker >= 0xC5 && marker <= 0xC7
                || marker >= 0xC9 && marker <= 0xCB || marker >= 0xCD && marker <= 0xCF;
    }

    private record Dimensions(int width, int height) { }
    private record DataUri(String mime, byte[] content) { }
}
