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
    private static final Map<String, String> RESOURCE_TEXTURES = new HashMap<>();
    private static final Map<String, TextureEntry> TEXTURES = new HashMap<>();
    private static final Map<String, PendingContent> PENDING_SERVER_ASSETS = new HashMap<>();
    private static final Map<String, ServerContent> RETAINED_SERVER_ASSETS = new HashMap<>();
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
            ServerContent retained = RETAINED_SERVER_ASSETS.get(cacheKey);
            if (retained != null && retained.data == null) {
                // A definition arrived before its bytes. One transfer satisfies its waiting owners.
                retained.data = data;
                return;
            }
            PendingContent pending = PENDING_SERVER_ASSETS.get(cacheKey);
            if (pending == null) PENDING_SERVER_ASSETS.put(cacheKey, new PendingContent(data));
            else pending.references++;
        } catch (IOException | RuntimeException exception) {
            reportOnce("server:" + assetId, exception);
        }
    }

    /** Retains server bytes for a definition without allocating a texture; survives local dormancy. */
    public static synchronized ServerAssetLease retainServerAsset(String assetId) {
        String key = cacheKey("server", assetId);
        ServerContent content = RETAINED_SERVER_ASSETS.computeIfAbsent(key, ignored -> new ServerContent());
        PendingContent pending = PENDING_SERVER_ASSETS.get(key);
        if (pending != null) {
            content.data = pending.data;
            if (--pending.references == 0) PENDING_SERVER_ASSETS.remove(key);
        }
        content.references++;
        return new ServerAssetLease(key, assetId, content);
    }

    @Nullable
    public static synchronized TextureLease acquire(String source, String reference) {
        if ("resource".equals(source)) {
            String key = RESOURCE_TEXTURES.get(reference);
            TextureEntry cached = TEXTURES.get(key);
            if (cached != null) {
                cached.references++;
                return new TextureLease(key, cached);
            }
            try {
                var resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.parse(reference))
                        .orElseThrow(() -> new IOException("Missing resource image: " + reference));
                byte[] data;
                try (var stream = resource.open()) { data = stream.readNBytes(16 * 1024 * 1024 + 1); }
                ImageAssetValidator.validateImage(data, 16 * 1024 * 1024, 8192, 16 * 1024 * 1024);
                String contentId = ImageAssetValidator.contentId(data);
                key = cacheKey(source, contentId);
                TextureEntry entry = retainTexture(key, source, contentId, data);
                RESOURCE_TEXTURES.put(reference, key);
                return new TextureLease(key, entry);
            } catch (IOException | RuntimeException exception) {
                reportOnce("resource:" + reference, exception);
                return null;
            }
        }
        if ("server".equals(source)) {
            String cacheKey = cacheKey("server", reference);
            PendingContent pending = PENDING_SERVER_ASSETS.get(cacheKey);
            ServerContent retained = RETAINED_SERVER_ASSETS.get(cacheKey);
            byte[] data = pending != null ? pending.data : retained == null ? null : retained.data;
            if (data == null) return null;
            if (pending != null && --pending.references == 0) PENDING_SERVER_ASSETS.remove(cacheKey);
            try {
                TextureEntry entry = retainTexture(cacheKey, "server", reference, data);
                return new TextureLease(cacheKey, entry);
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
            if (size <= 0 || size > 16 * 1024 * 1024) throw new IOException("Local image exceeds 16 MiB byte budget");
            FileTime modified = Files.getLastModifiedTime(path);
            LocalAsset cached = LOCAL_TEXTURES.get(path);
            if (cached != null && cached.size == size && cached.modified.equals(modified)) {
                TextureEntry entry = TEXTURES.get(cached.cacheKey);
                if (entry != null) {
                    entry.references++;
                    return new TextureLease(cached.cacheKey, entry);
                }
            }

            byte[] data;
            try (var stream = Files.newInputStream(path)) { data = stream.readNBytes(16 * 1024 * 1024 + 1); }
            ImageAssetValidator.validateImage(data, 16 * 1024 * 1024, 8192, 16 * 1024 * 1024);
            String contentId = ImageAssetValidator.contentId(data);
            String cacheKey = cacheKey("local", contentId);
            TextureEntry entry = retainTexture(cacheKey, "local", contentId, data);
            LOCAL_TEXTURES.put(path, new LocalAsset(size, modified, cacheKey));
            return new TextureLease(cacheKey, entry);
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
        RESOURCE_TEXTURES.clear();
        TEXTURES.clear();
        PENDING_SERVER_ASSETS.clear();
        RETAINED_SERVER_ASSETS.values().forEach(content -> content.data = null);
        RETAINED_SERVER_ASSETS.clear();
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

    private static synchronized void release(String cacheKey, TextureEntry expected) {
        TextureEntry entry = TEXTURES.get(cacheKey);
        if (entry != expected || --entry.references > 0) return;
        TEXTURES.remove(cacheKey);
        RESOURCE_TEXTURES.values().removeIf(cacheKey::equals);
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
        private final TextureEntry entry;
        private boolean closed;

        private TextureLease(String cacheKey, TextureEntry entry) {
            this.cacheKey = cacheKey;
            this.entry = entry;
        }

        public Identifier texture() {
            return entry.texture;
        }

        @Override
        public void close() {
            synchronized (ClientImageAssetManager.class) {
                if (closed) return;
                closed = true;
                release(cacheKey, entry);
            }
        }
    }

    public static final class ServerAssetLease implements AutoCloseable {
        private final String cacheKey, assetId;
        private final ServerContent content;
        private boolean closed;

        private ServerAssetLease(String cacheKey, String assetId, ServerContent content) {
            this.cacheKey = cacheKey;
            this.assetId = assetId;
            this.content = content;
        }

        public boolean ready() {
            synchronized (ClientImageAssetManager.class) {
                return !closed && RETAINED_SERVER_ASSETS.get(cacheKey) == content && content.data != null;
            }
        }

        @Nullable
        public TextureLease acquireTexture() {
            synchronized (ClientImageAssetManager.class) {
                if (!ready()) return null;
                try {
                    return new TextureLease(cacheKey, retainTexture(cacheKey, "server", assetId, content.data));
                } catch (IOException | RuntimeException exception) {
                    reportOnce(cacheKey, exception);
                    return null;
                }
            }
        }

        @Override public void close() {
            synchronized (ClientImageAssetManager.class) {
                if (closed) return;
                closed = true;
                if (RETAINED_SERVER_ASSETS.get(cacheKey) == content && --content.references == 0) {
                    RETAINED_SERVER_ASSETS.remove(cacheKey);
                    content.data = null;
                }
            }
        }
    }

    private static final class ServerContent {
        private byte[] data;
        private int references;
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
