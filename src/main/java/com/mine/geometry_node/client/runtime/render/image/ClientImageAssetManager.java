package com.mine.geometry_node.client.runtime.render.image;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.visual.image.ImageAssetValidator;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Owns runtime image textures used by temporary world visuals. */
public final class ClientImageAssetManager {
    private static final Map<Path, LocalAsset> LOCAL_TEXTURES = new HashMap<>();
    private static final Map<String, TextureEntry> TEXTURES = new HashMap<>();
    private static final Map<String, PendingContent> PENDING_SERVER_ASSETS = new HashMap<>();
    private static final java.util.Set<String> REPORTED_FAILURES = new java.util.HashSet<>();

    private ClientImageAssetManager() {
    }

    public static synchronized void acceptServerAsset(String assetId, byte[] data) {
        try {
            String actualId = ImageAssetValidator.contentId(data);
            if (!actualId.equals(assetId)) {
                throw new IOException("content hash does not match the asset ID");
            }
            String cacheKey = cacheKey("server", actualId);
            PendingContent pending = PENDING_SERVER_ASSETS.get(cacheKey);
            if (pending == null) PENDING_SERVER_ASSETS.put(cacheKey, new PendingContent(data));
            else pending.references++;
        } catch (IOException | RuntimeException exception) {
            reportOnce("server:" + assetId, exception);
        }
    }

    @Nullable
    public static synchronized TextureLease acquire(String source, String reference) {
        if ("server".equals(source)) {
            String cacheKey = cacheKey("server", reference);
            PendingContent pending = PENDING_SERVER_ASSETS.get(cacheKey);
            if (pending == null) return null;
            if (--pending.references == 0) PENDING_SERVER_ASSETS.remove(cacheKey);
            try {
                TextureEntry entry = retainTexture(cacheKey, "server", reference, pending.data);
                return new TextureLease(cacheKey, entry.texture);
            } catch (IOException | RuntimeException exception) {
                reportOnce("server:" + reference, exception);
                return null;
            }
        }
        if (!"local".equals(source) || reference == null || reference.isBlank()) {
            return null;
        }

        try {
            Path path = Path.of(reference);
            if (!path.isAbsolute()) {
                path = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            }
            path = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw new IOException("local image does not exist: " + path);
            }

            long size = Files.size(path);
            FileTime modified = Files.getLastModifiedTime(path);
            LocalAsset cached = LOCAL_TEXTURES.get(path);
            if (cached != null && cached.size == size && cached.modified.equals(modified)) {
                TextureEntry entry = TEXTURES.get(cached.cacheKey);
                if (entry != null) {
                    entry.references++;
                    return new TextureLease(cached.cacheKey, entry.texture);
                }
            }

            byte[] data = Files.readAllBytes(path);
            String contentId = ImageAssetValidator.contentId(data);
            String cacheKey = cacheKey("local", contentId);
            TextureEntry entry = retainTexture(cacheKey, "local", contentId, data);
            LOCAL_TEXTURES.put(path, new LocalAsset(size, modified, cacheKey));
            return new TextureLease(cacheKey, entry.texture);
        } catch (IOException | RuntimeException exception) {
            reportOnce("local:" + reference, exception);
            return null;
        }
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (TextureEntry entry : TEXTURES.values()) {
            minecraft.getTextureManager().release(entry.texture);
        }
        LOCAL_TEXTURES.clear();
        TEXTURES.clear();
        PENDING_SERVER_ASSETS.clear();
        REPORTED_FAILURES.clear();
    }

    public static synchronized void discardPendingServerAsset(String assetId) {
        String cacheKey = cacheKey("server", assetId);
        PendingContent pending = PENDING_SERVER_ASSETS.get(cacheKey);
        if (pending != null && --pending.references == 0) PENDING_SERVER_ASSETS.remove(cacheKey);
    }

    private static TextureEntry retainTexture(String cacheKey, String scope,
                                              String contentId, byte[] data) throws IOException {
        TextureEntry cached = TEXTURES.get(cacheKey);
        if (cached != null) {
            cached.references++;
            return cached;
        }
        Identifier id = Identifier.fromNamespaceAndPath(
                GeometryNode.MODID,
                "dynamic_images/" + scope + '/' + contentId
        );
        NativeImage image = NativeImage.read(data);
        DynamicTexture texture = new DynamicTexture(() -> "GeometryNode runtime image " + contentId, image);
        try {
            Minecraft.getInstance().getTextureManager().register(id, texture);
        } catch (RuntimeException exception) {
            texture.close();
            throw exception;
        }
        TextureEntry entry = new TextureEntry(id);
        TEXTURES.put(cacheKey, entry);
        return entry;
    }

    private static synchronized void release(String cacheKey) {
        TextureEntry entry = TEXTURES.get(cacheKey);
        if (entry == null || --entry.references > 0) return;
        TEXTURES.remove(cacheKey);
        Minecraft.getInstance().getTextureManager().release(entry.texture);
        Iterator<Map.Entry<Path, LocalAsset>> iterator = LOCAL_TEXTURES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().cacheKey.equals(cacheKey)) iterator.remove();
        }
    }

    private static String cacheKey(String scope, String contentId) {
        return scope + ':' + contentId;
    }

    private static void reportOnce(String key, Exception exception) {
        if (REPORTED_FAILURES.add(key)) {
            GeometryNode.LOGGER.warn("Unable to load runtime image {}: {}", key, exception.getMessage());
        }
    }

    public static final class TextureLease implements AutoCloseable {
        private final String cacheKey;
        private final Identifier texture;
        private boolean closed;

        private TextureLease(String cacheKey, Identifier texture) {
            this.cacheKey = cacheKey;
            this.texture = texture;
        }

        public Identifier texture() {
            return texture;
        }

        @Override
        public void close() {
            synchronized (ClientImageAssetManager.class) {
                if (closed) return;
                closed = true;
                release(cacheKey);
            }
        }
    }

    private static final class TextureEntry {
        private final Identifier texture;
        private int references = 1;

        private TextureEntry(Identifier texture) {
            this.texture = texture;
        }
    }

    private static final class PendingContent {
        private final byte[] data;
        private int references = 1;

        private PendingContent(byte[] data) {
            this.data = data;
        }
    }

    private record LocalAsset(long size, FileTime modified, String cacheKey) {
    }
}
