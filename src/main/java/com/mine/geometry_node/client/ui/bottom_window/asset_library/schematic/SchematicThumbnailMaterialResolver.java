package com.mine.geometry_node.client.ui.bottom_window.asset_library.schematic;

import com.mine.geometry_node.core.schematic.LegacySchematicBlockStateMapper;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SchematicThumbnailMaterialResolver {
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final int SAMPLE_STEPS = 5;
    private static final int MAX_UNCACHED_RESOLVES_PER_FRAME = 4;
    private static final long FRAME_BUDGET_WINDOW_NANOS = 16_000_000L;
    private static final Map<String, MaterialColors> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MaterialColors> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private static Method sGetPixelRgbaMethod;
    private static boolean sGetPixelRgbaChecked;
    private static Method sGetOriginalImageMethod;
    private static boolean sOriginalImageChecked;
    private static Field sOriginalImageField;
    private static boolean sOriginalImageFieldChecked;
    private static long sResolveBudgetWindowNanos;
    private static int sUncachedResolveCount;

    private SchematicThumbnailMaterialResolver() {
    }

    static MaterialColors resolve(String stateText, int fallbackColor) {
        String key = (stateText == null ? "" : stateText) + "|" + fallbackColor;
        synchronized (CACHE) {
            MaterialColors cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }

        MaterialColors resolved = resolveUncached(stateText, fallbackColor);
        synchronized (CACHE) {
            CACHE.put(key, resolved);
        }
        return resolved;
    }

    static MaterialColors resolveBudgeted(String stateText, int fallbackColor, boolean[] deferred) {
        String key = (stateText == null ? "" : stateText) + "|" + fallbackColor;
        synchronized (CACHE) {
            MaterialColors cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            if (!consumeResolveBudgetLocked()) {
                if (deferred != null && deferred.length > 0) {
                    deferred[0] = true;
                }
                return MaterialColors.fromFallback(fallbackColor);
            }
        }

        MaterialColors resolved = resolveUncached(stateText, fallbackColor);
        synchronized (CACHE) {
            CACHE.put(key, resolved);
        }
        return resolved;
    }

    private static boolean consumeResolveBudgetLocked() {
        long now = System.nanoTime();
        if (now - sResolveBudgetWindowNanos > FRAME_BUDGET_WINDOW_NANOS) {
            sResolveBudgetWindowNanos = now;
            sUncachedResolveCount = 0;
        }
        if (sUncachedResolveCount >= MAX_UNCACHED_RESOLVES_PER_FRAME) {
            return false;
        }
        sUncachedResolveCount++;
        return true;
    }

    private static MaterialColors resolveUncached(String stateText, int fallbackColor) {
        BlockState state = parseBlockState(stateText);
        if (state == null) {
            return MaterialColors.fromFallback(fallbackColor);
        }

        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getModelManager() == null) {
                return MaterialColors.fromFallback(fallbackColor);
            }

            BlockStateModelSet modelSet = minecraft.getModelManager().getBlockStateModelSet();
            BlockStateModel model = modelSet.get(state);
            if (model == null) {
                return MaterialColors.fromFallback(fallbackColor);
            }

            List<BlockStateModelPart> parts = collectParts(model);

            int top = colorForDirections(parts, fallbackColor, Direction.UP);
            int left = colorForDirections(parts, fallbackColor, Direction.NORTH, Direction.WEST);
            int right = colorForDirections(parts, fallbackColor, Direction.EAST, Direction.SOUTH);
            int particle = particleColor(model);

            if (top == 0) top = particle;
            if (left == 0) left = particle;
            if (right == 0) right = particle;
            if (top == 0) top = fallbackColor;
            if (left == 0) left = fallbackColor;
            if (right == 0) right = fallbackColor;
            return new MaterialColors(top, left, right);
        } catch (Exception ignored) {
            return MaterialColors.fromFallback(fallbackColor);
        }
    }

    @SuppressWarnings("deprecation")
    private static List<BlockStateModelPart> collectParts(BlockStateModel model) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        return parts;
    }

    private static int particleColor(BlockStateModel model) {
        try {
            Material.Baked material = model.particleMaterial();
            if (material == null || material.sprite() == null) {
                return 0;
            }
            return averageSprite(material.sprite());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int colorForDirections(List<BlockStateModelPart> parts, int fallbackColor, Direction... directions) {
        long a = 0L;
        long r = 0L;
        long g = 0L;
        long b = 0L;
        int count = 0;

        for (BlockStateModelPart part : parts) {
            for (Direction direction : directions) {
                for (BakedQuad quad : part.getQuads(direction)) {
                    int color = averageSprite(quad.materialInfo().sprite());
                    if (color == 0) continue;
                    if (quad.materialInfo().isTinted()) {
                        color = tint(color, fallbackColor);
                    }
                    a += (color >>> 24) & 0xFF;
                    r += (color >>> 16) & 0xFF;
                    g += (color >>> 8) & 0xFF;
                    b += color & 0xFF;
                    count++;
                }
            }
        }

        if (count == 0) {
            return 0;
        }
        return ((int) (a / count) << 24)
                | ((int) (r / count) << 16)
                | ((int) (g / count) << 8)
                | (int) (b / count);
    }

    private static int averageSprite(TextureAtlasSprite sprite) {
        if (sprite == null || sprite.contents() == null) {
            return 0;
        }

        SpriteContents contents = sprite.contents();
        NativeImage image = originalImage(contents);
        int width = Math.max(1, contents.width());
        int height = Math.max(1, contents.height());
        int stepX = Math.max(1, width / SAMPLE_STEPS);
        int stepY = Math.max(1, height / SAMPLE_STEPS);
        long a = 0L;
        long r = 0L;
        long g = 0L;
        long b = 0L;
        int count = 0;

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int color = pixelArgb(sprite, image, x, y);
                int alpha = (color >>> 24) & 0xFF;
                if (alpha < 16) {
                    continue;
                }
                a += alpha;
                r += (color >>> 16) & 0xFF;
                g += (color >>> 8) & 0xFF;
                b += color & 0xFF;
                count++;
            }
        }

        if (count == 0) {
            return 0;
        }
        return ((int) (a / count) << 24)
                | ((int) (r / count) << 16)
                | ((int) (g / count) << 8)
                | (int) (b / count);
    }

    private static int pixelArgb(TextureAtlasSprite sprite, NativeImage image, int x, int y) {
        Integer direct = pixelViaSpriteMethod(sprite, x, y);
        if (direct != null) {
            return direct;
        }
        try {
            if (image == null || image.isClosed()) {
                return 0;
            }
            return image.getPixel(x, y);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Integer pixelViaSpriteMethod(TextureAtlasSprite sprite, int x, int y) {
        try {
            if (!sGetPixelRgbaChecked) {
                sGetPixelRgbaChecked = true;
                sGetPixelRgbaMethod = sprite.getClass().getMethod("getPixelRGBA", int.class, int.class, int.class);
            }
            if (sGetPixelRgbaMethod == null) {
                return null;
            }
            Object value = sGetPixelRgbaMethod.invoke(sprite, 0, x, y);
            return value instanceof Integer i ? i : null;
        } catch (Exception ignored) {
            sGetPixelRgbaMethod = null;
            return null;
        }
    }

    private static NativeImage originalImage(SpriteContents contents) {
        NativeImage viaMethod = originalImageViaMethod(contents);
        if (viaMethod != null) {
            return viaMethod;
        }
        return originalImageViaField(contents);
    }

    private static NativeImage originalImageViaMethod(SpriteContents contents) {
        try {
            if (!sOriginalImageChecked) {
                sOriginalImageChecked = true;
                sGetOriginalImageMethod = contents.getClass().getMethod("getOriginalImage");
            }
            Object image = sGetOriginalImageMethod == null ? null : sGetOriginalImageMethod.invoke(contents);
            return image instanceof NativeImage nativeImage ? nativeImage : null;
        } catch (Exception ignored) {
            sGetOriginalImageMethod = null;
            return null;
        }
    }

    private static NativeImage originalImageViaField(SpriteContents contents) {
        try {
            if (!sOriginalImageFieldChecked) {
                sOriginalImageFieldChecked = true;
                sOriginalImageField = contents.getClass().getDeclaredField("originalImage");
                sOriginalImageField.setAccessible(true);
            }
            Object image = sOriginalImageField == null ? null : sOriginalImageField.get(contents);
            return image instanceof NativeImage nativeImage ? nativeImage : null;
        } catch (Exception ignored) {
            sOriginalImageField = null;
            return null;
        }
    }

    private static int tint(int color, int tintColor) {
        int a = (color >>> 24) & 0xFF;
        int r = ((color >>> 16) & 0xFF) * ((tintColor >>> 16) & 0xFF) / 255;
        int g = ((color >>> 8) & 0xFF) * ((tintColor >>> 8) & 0xFF) / 255;
        int b = (color & 0xFF) * (tintColor & 0xFF) / 255;
        return ARGB.color(a, r, g, b);
    }

    private static BlockState parseBlockState(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.startsWith("legacy:")) {
            return LegacySchematicBlockStateMapper.fromRaw(raw);
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, raw, false).blockState();
        } catch (Exception ignored) {
        }

        try {
            String id = raw;
            int bracket = id.indexOf('[');
            if (bracket >= 0) {
                id = id.substring(0, bracket);
            }
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) {
                return null;
            }
            Block block = BuiltInRegistries.BLOCK.getValue(identifier);
            if (block == null) {
                return null;
            }
            BlockState state = block.defaultBlockState();
            return state.isAir() ? null : state;
        } catch (Exception ignored) {
            return null;
        }
    }

    record MaterialColors(int top, int left, int right) {
        private static MaterialColors fromFallback(int color) {
            return new MaterialColors(color, color, color);
        }
    }
}
