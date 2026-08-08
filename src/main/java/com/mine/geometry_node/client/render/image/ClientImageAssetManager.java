package com.mine.geometry_node.client.render.image;

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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Owns runtime image textures used by temporary world visuals. */
public final class ClientImageAssetManager {
    private static final int MAX_REGISTERED_TEXTURES = 32;
    private static final Map<String, Identifier> SERVER_TEXTURES = new HashMap<>();
    private static final Map<Path, LocalAsset> LOCAL_TEXTURES = new HashMap<>();
    private static final Map<String, Identifier> CONTENT_TEXTURES = new HashMap<>();
    private static final Set<Identifier> REGISTERED_TEXTURES = new HashSet<>();
    private static final Set<String> REPORTED_FAILURES = new HashSet<>();

    private ClientImageAssetManager() {
    }

    public static synchronized void acceptServerAsset(String assetId, byte[] data) {
        try {
            ImageAssetValidator.validateImage(data);
            String actualId = ImageAssetValidator.contentId(data);
            if (!actualId.equals(assetId)) {
                throw new IOException("content hash does not match the asset ID");
            }
            SERVER_TEXTURES.put(assetId, registerTexture("server", actualId, data));
        } catch (IOException | RuntimeException exception) {
            reportOnce("server:" + assetId, exception);
        }
    }

    @Nullable
    public static synchronized Identifier resolve(String source, String reference) {
        if ("server".equals(source)) {
            return SERVER_TEXTURES.get(reference);
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
            if (size <= 0 || size > ImageAssetValidator.MAX_ENCODED_BYTES) {
                throw new IOException("unsupported local image size: " + size);
            }
            FileTime modified = Files.getLastModifiedTime(path);
            LocalAsset cached = LOCAL_TEXTURES.get(path);
            if (cached != null && cached.size == size && cached.modified.equals(modified)) {
                return cached.texture;
            }

            byte[] data = Files.readAllBytes(path);
            ImageAssetValidator.validateImage(data);
            String contentId = ImageAssetValidator.contentId(data);
            Identifier texture = registerTexture("local", contentId, data);
            LOCAL_TEXTURES.put(path, new LocalAsset(size, modified, texture));
            return texture;
        } catch (IOException | RuntimeException exception) {
            reportOnce("local:" + reference, exception);
            return null;
        }
    }

    public static synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (Identifier texture : REGISTERED_TEXTURES) {
            minecraft.getTextureManager().release(texture);
        }
        SERVER_TEXTURES.clear();
        LOCAL_TEXTURES.clear();
        CONTENT_TEXTURES.clear();
        REGISTERED_TEXTURES.clear();
        REPORTED_FAILURES.clear();
    }

    private static Identifier registerTexture(String scope, String contentId, byte[] data) throws IOException {
        String cacheKey = scope + ':' + contentId;
        Identifier cached = CONTENT_TEXTURES.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (REGISTERED_TEXTURES.size() >= MAX_REGISTERED_TEXTURES) {
            throw new IOException("runtime image texture limit reached: " + MAX_REGISTERED_TEXTURES);
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
        CONTENT_TEXTURES.put(cacheKey, id);
        REGISTERED_TEXTURES.add(id);
        return id;
    }

    private static void reportOnce(String key, Exception exception) {
        if (REPORTED_FAILURES.add(key)) {
            GeometryNode.LOGGER.warn("Unable to load runtime image {}: {}", key, exception.getMessage());
        }
    }

    private record LocalAsset(long size, FileTime modified, Identifier texture) {
    }
}
