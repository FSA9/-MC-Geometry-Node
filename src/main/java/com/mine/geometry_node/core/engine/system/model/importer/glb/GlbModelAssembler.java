package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.domain.animation.*;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

final class GlbModelAssembler {
    private final GlbDocument document;
    private final GlbAccessorDecoder decoder;
    private final ModelImportSession session;
    private final List<ModelMaterial> materials = new ArrayList<>();
    private int defaultMaterial = -1;

    GlbModelAssembler(GlbDocument document, ModelImportSession session) {
        this.document = document;
        this.decoder = new GlbAccessorDecoder(document, session);
        this.session = session;
    }

    ModelDefinition assemble(ModelAssetReference source) throws ModelImportException {
        List<ModelImageSource> images = parseImages();
        List<ModelTexture> textures = parseTextures();
        claimMipBytes(images, textures);
        parseMaterials();
        List<ModelMesh> meshes = parseMeshes();
        NodeAssembly nodeAssembly = parseNodes(meshes);
        List<ModelSkin> skins = parseSkins(nodeAssembly.nodes);
        List<ModelAnimation> animations = parseAnimations(nodeAssembly.nodes);
        SceneAssembly sceneAssembly = parseScenes(nodeAssembly);
        ModelBounds modelBounds = sceneAssembly.scenes.get(sceneAssembly.defaultScene).bounds()
                .orElseThrow(() -> GlbFailures.invalid("scene", "default scene contains no geometry"));
        return new ModelDefinition(source, sceneAssembly.scenes, sceneAssembly.defaultScene,
                nodeAssembly.nodes, meshes, materials, textures, images, animations, skins, modelBounds);
    }

    private List<ModelSkin> parseSkins(List<ModelNode> nodes) throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "skins");
        List<ModelSkin> skins = new ArrayList<>(array.size());
        for (int skinIndex = 0; skinIndex < array.size(); skinIndex++) {
            String location = "skins[" + skinIndex + "]";
            session.checkpoint(location);
            JsonObject value = GlbJson.object(array.get(skinIndex), location);
            JsonArray jointArray = GlbJson.array(value, "joints");
            if (jointArray.isEmpty()) throw GlbFailures.invalid(location + ".joints", "skin must contain at least one joint");
            if (jointArray.size() > ModelSkin.MAX_JOINTS) {
                throw GlbFailures.failure(ModelImportErrorCode.LIMIT_EXCEEDED, location + ".joints",
                        "skin exceeds the 128-joint GPU contract");
            }
            List<Integer> joints = new ArrayList<>(jointArray.size());
            Set<Integer> unique = new HashSet<>();
            for (int jointIndex = 0; jointIndex < jointArray.size(); jointIndex++) {
                int nodeIndex = integerElement(jointArray.get(jointIndex), location + ".joints[" + jointIndex + "]");
                if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
                    throw GlbFailures.reference(location + ".joints[" + jointIndex + "]", "joint node reference is out of range");
                }
                if (!unique.add(nodeIndex)) throw GlbFailures.invalid(location + ".joints", "skin contains a duplicate joint node");
                joints.add(nodeIndex);
            }
            int skeleton = GlbJson.optionalInt(value, "skeleton", -1, location);
            if (skeleton < -1) throw GlbFailures.reference(location + ".skeleton", "skeleton node reference must not be negative");
            if (skeleton >= nodes.size()) throw GlbFailures.reference(location + ".skeleton", "skeleton node reference is out of range");
            List<ModelMatrix4> inverseBindMatrices = value.has("inverseBindMatrices")
                    ? decoder.inverseBindMatrices(GlbJson.requiredInt(value, "inverseBindMatrices", location),
                    joints.size(), location + ".inverseBindMatrices")
                    : identityMatrices(joints.size());
            try {
                skins.add(new ModelSkin(GlbJson.string(value, "name", "", location), joints, skeleton,
                        inverseBindMatrices));
            } catch (IllegalArgumentException exception) {
                throw GlbFailures.invalid(location, exception.getMessage());
            }
        }
        return List.copyOf(skins);
    }

    private static List<ModelMatrix4> identityMatrices(int count) {
        float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        List<ModelMatrix4> matrices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) matrices.add(new ModelMatrix4(identity));
        return List.copyOf(matrices);
    }

    private List<ModelAnimation> parseAnimations(List<ModelNode> nodes) throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "animations");
        session.budgetTracker().claim(ModelBudgetResource.ANIMATIONS, array.size(), "animations");
        List<ModelAnimation> animations = new ArrayList<>(array.size());
        for (int animationIndex = 0; animationIndex < array.size(); animationIndex++) {
            String location = "animations[" + animationIndex + "]";
            session.checkpoint(location);
            JsonObject value = GlbJson.object(array.get(animationIndex), location);
            JsonArray samplerJson = GlbJson.array(value, "samplers");
            JsonArray channelJson = GlbJson.array(value, "channels");
            session.budgetTracker().claim(ModelBudgetResource.ANIMATION_CHANNELS, channelJson.size(), location + ".channels");
            List<ModelAnimationSampler> samplers = new ArrayList<>(samplerJson.size());
            for (int samplerIndex = 0; samplerIndex < samplerJson.size(); samplerIndex++) {
                String samplerLocation = location + ".samplers[" + samplerIndex + "]";
                JsonObject sampler = GlbJson.object(samplerJson.get(samplerIndex), samplerLocation);
                String interpolationName = GlbJson.string(sampler, "interpolation", "LINEAR", samplerLocation);
                ModelAnimationInterpolation interpolation = switch (interpolationName) {
                    case "STEP" -> ModelAnimationInterpolation.STEP;
                    case "LINEAR" -> ModelAnimationInterpolation.LINEAR;
                    case "CUBICSPLINE" -> throw GlbFailures.unsupported(samplerLocation + ".interpolation",
                            "CUBICSPLINE animation is not supported in M9");
                    default -> throw GlbFailures.invalid(samplerLocation + ".interpolation",
                            "unknown animation interpolation: " + interpolationName);
                };
                int input = GlbJson.requiredInt(sampler, "input", samplerLocation);
                int output = GlbJson.requiredInt(sampler, "output", samplerLocation);
                if (input < 0 || output < 0) throw GlbFailures.reference(samplerLocation, "animation accessor reference must not be negative");
                float[] times = decoder.animationFloats(input, 1, samplerLocation + ".input");
                session.budgetTracker().claim(ModelBudgetResource.ANIMATION_KEYFRAMES, times.length, samplerLocation + ".input");
                GlbDocument.Accessor outputAccessor = document.accessor(output, samplerLocation + ".output");
                if (outputAccessor.components() < 1 || outputAccessor.components() > 4) {
                    throw GlbFailures.unsupported(samplerLocation + ".output", "animation output component count is unsupported");
                }
                float[] outputs = decoder.animationFloats(output, outputAccessor.components(), samplerLocation + ".output");
                try {
                    samplers.add(new ModelAnimationSampler(interpolation, outputAccessor.components(), times, outputs));
                } catch (IllegalArgumentException exception) {
                    throw GlbFailures.invalid(samplerLocation, exception.getMessage());
                }
            }
            List<ModelAnimationChannel> channels = new ArrayList<>(channelJson.size());
            Set<String> targets = new HashSet<>();
            for (int channelIndex = 0; channelIndex < channelJson.size(); channelIndex++) {
                String channelLocation = location + ".channels[" + channelIndex + "]";
                JsonObject channel = GlbJson.object(channelJson.get(channelIndex), channelLocation);
                int samplerIndex = GlbJson.requiredInt(channel, "sampler", channelLocation);
                if (samplerIndex < 0 || samplerIndex >= samplers.size()) {
                    throw GlbFailures.reference(channelLocation + ".sampler", "animation sampler reference is out of range");
                }
                JsonObject target = GlbJson.object(channel.get("target"), channelLocation + ".target");
                int nodeIndex = GlbJson.requiredInt(target, "node", channelLocation + ".target");
                String pathName = GlbJson.string(target, "path", "", channelLocation + ".target");
                ModelAnimationPath path = switch (pathName) {
                    case "translation" -> ModelAnimationPath.TRANSLATION;
                    case "rotation" -> ModelAnimationPath.ROTATION;
                    case "scale" -> ModelAnimationPath.SCALE;
                    case "weights" -> throw GlbFailures.unsupported(channelLocation + ".target.path",
                            "morph-weight animation is not supported");
                    default -> throw GlbFailures.invalid(channelLocation + ".target.path", "unknown animation target path: " + pathName);
                };
                if (nodeIndex < 0 || nodeIndex >= nodes.size()) throw GlbFailures.reference(channelLocation + ".target.node", "node reference is out of range");
                if (nodes.get(nodeIndex).transform() instanceof ModelTransform.Matrix) {
                    throw GlbFailures.unsupported(channelLocation + ".target.node", "matrix nodes cannot be targeted by TRS animation");
                }
                ModelAnimationSampler modelSampler = samplers.get(samplerIndex);
                if (modelSampler.outputComponentCount() != path.componentCount()) {
                    throw GlbFailures.invalid(channelLocation + ".sampler",
                            "animation sampler output components do not match target path");
                }
                int outputAccessorIndex = GlbJson.requiredInt(
                        GlbJson.object(samplerJson.get(samplerIndex), location + ".samplers[" + samplerIndex + "]"),
                        "output", location + ".samplers[" + samplerIndex + "]");
                String expectedType = path == ModelAnimationPath.ROTATION ? "VEC4" : "VEC3";
                if (!expectedType.equals(document.accessor(outputAccessorIndex, channelLocation + ".sampler").type())) {
                    throw GlbFailures.invalid(channelLocation + ".sampler",
                            "animation sampler output accessor type must be " + expectedType + " for " + path);
                }
                if (!targets.add(nodeIndex + ":" + path)) {
                    throw GlbFailures.invalid(channelLocation, "animation contains duplicate channels for one node path");
                }
                if (path == ModelAnimationPath.ROTATION) validateRotations(modelSampler, channelLocation + ".sampler");
                channels.add(new ModelAnimationChannel(nodeIndex, path, samplerIndex));
            }
            try {
                animations.add(new ModelAnimation(GlbJson.string(value, "name", "", location), samplers, channels));
            } catch (IllegalArgumentException exception) {
                throw GlbFailures.invalid(location, exception.getMessage());
            }
        }
        return List.copyOf(animations);
    }

    private static void validateRotations(ModelAnimationSampler sampler, String location) throws ModelImportException {
        for (int key = 0; key < sampler.keyCount(); key++) {
            double lengthSquared = 0.0D;
            for (int component = 0; component < 4; component++) {
                double value = sampler.outputValue(key, component);
                lengthSquared += value * value;
            }
            if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0E-12D) {
                throw GlbFailures.invalid(location, "rotation quaternion must have non-zero finite length");
            }
        }
    }

    private List<ModelImageSource> parseImages() throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "images");
        session.budgetTracker().claim(ModelBudgetResource.IMAGES, array.size(), "images");
        List<ModelImageSource> images = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            session.checkpoint("images[" + i + "]");
            images.add(GlbImageReader.read(GlbJson.object(array.get(i), "images[" + i + "]"),
                    "images[" + i + "]", document, decoder, session));
        }
        return List.copyOf(images);
    }

    private List<ModelTexture> parseTextures() throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "textures");
        JsonArray samplerArray = GlbJson.array(document.root, "samplers");
        session.budgetTracker().claim(ModelBudgetResource.TEXTURES, array.size(), "textures");
        session.budgetTracker().claim(ModelBudgetResource.SAMPLERS, samplerArray.size(), "samplers");
        List<ModelTexture> textures = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String location = "textures[" + i + "]";
            JsonObject texture = GlbJson.object(array.get(i), location);
            int source = GlbJson.requiredInt(texture, "source", location);
            if (source < 0) throw GlbFailures.invalid(location + ".source", "image reference must not be negative");
            int samplerIndex = texture.has("sampler") ? GlbJson.requiredInt(texture, "sampler", location) : -1;
            if (texture.has("sampler") && samplerIndex < 0) {
                throw GlbFailures.invalid(location + ".sampler", "sampler reference must not be negative");
            }
            if (samplerIndex >= samplerArray.size()) {
                throw GlbFailures.invalid(location + ".sampler", "sampler reference is out of range");
            }
            ModelTextureSampler sampler = samplerIndex < 0 ? ModelTextureSampler.gltfDefault()
                    : parseSampler(GlbJson.object(samplerArray.get(samplerIndex), "samplers[" + samplerIndex + "]"),
                    "samplers[" + samplerIndex + "]");
            textures.add(new ModelTexture(GlbJson.string(texture, "name", "", location), source, sampler));
        }
        return List.copyOf(textures);
    }

    private void parseMaterials() throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "materials");
        boolean needsDefault = needsDefaultMaterial();
        session.budgetTracker().claim(ModelBudgetResource.MATERIALS,
                array.size() + (needsDefault ? 1L : 0L), "materials");
        for (int i = 0; i < array.size(); i++) {
            String location = "materials[" + i + "]";
            JsonObject material = GlbJson.object(array.get(i), location);
            JsonObject pbr = material.has("pbrMetallicRoughness")
                    ? GlbJson.object(material.get("pbrMetallicRoughness"), location + ".pbrMetallicRoughness")
                    : new JsonObject();
            float[] factor = GlbJson.floatArray(pbr, "baseColorFactor", 4,
                    new float[]{1, 1, 1, 1}, location + ".pbrMetallicRoughness");
            for (float component : factor) {
                if (component < 0.0F || component > 1.0F) throw GlbFailures.invalid(location + ".baseColorFactor", "base color components must be within [0, 1]");
            }
            ModelTextureInfo texture = ModelTextureInfo.absent();
            if (pbr.has("baseColorTexture")) {
                JsonObject info = GlbJson.object(pbr.get("baseColorTexture"), location + ".pbrMetallicRoughness.baseColorTexture");
                texture = parseTextureInfo(info, location + ".pbrMetallicRoughness.baseColorTexture");
            }
            String alphaName = GlbJson.string(material, "alphaMode", "OPAQUE", location);
            ModelAlphaMode alphaMode;
            try {
                alphaMode = ModelAlphaMode.valueOf(alphaName);
            } catch (IllegalArgumentException exception) {
                throw GlbFailures.invalid(location + ".alphaMode", "unknown alpha mode: " + alphaName);
            }
            float cutoff = material.has("alphaCutoff") ? GlbJson.number(material.get("alphaCutoff"), location + ".alphaCutoff") : 0.5F;
            if (cutoff < 0) throw GlbFailures.invalid(location + ".alphaCutoff", "alpha cutoff must not be negative");
            float[] emissive = GlbJson.floatArray(material, "emissiveFactor", 3,
                    new float[]{0, 0, 0}, location);
            for (float component : emissive) {
                if (component < 0.0F || component > 1.0F) {
                    throw GlbFailures.invalid(location + ".emissiveFactor",
                            "emissive components must be within [0, 1]");
                }
            }
            ModelTextureInfo emissiveTexture = material.has("emissiveTexture")
                    ? parseTextureInfo(GlbJson.object(material.get("emissiveTexture"), location + ".emissiveTexture"),
                    location + ".emissiveTexture") : ModelTextureInfo.absent();
            float metallicFactor = unitFactor(pbr, "metallicFactor", 1,
                    location + ".pbrMetallicRoughness");
            float roughnessFactor = unitFactor(pbr, "roughnessFactor", 1,
                    location + ".pbrMetallicRoughness");
            ModelTextureInfo metallicRoughnessTexture = pbr.has("metallicRoughnessTexture")
                    ? parseTextureInfo(GlbJson.object(pbr.get("metallicRoughnessTexture"),
                    location + ".pbrMetallicRoughness.metallicRoughnessTexture"),
                    location + ".pbrMetallicRoughness.metallicRoughnessTexture") : ModelTextureInfo.absent();
            ModelNormalTextureInfo normalTexture = parseNormalTexture(material, location);
            ModelOcclusionTextureInfo occlusionTexture = parseOcclusionTexture(material, location);
            materials.add(new ModelMaterial(GlbJson.string(material, "name", "", location),
                    factor[0], factor[1], factor[2], factor[3], texture, alphaMode, cutoff,
                    GlbJson.bool(material, "doubleSided", false, location),
                    emissive[0], emissive[1], emissive[2], emissiveTexture,
                    new ModelPbrMetallicRoughness(metallicFactor, roughnessFactor, metallicRoughnessTexture),
                    normalTexture, occlusionTexture));
        }
        if (needsDefault) {
            defaultMaterial = materials.size();
            materials.add(ModelMaterial.defaultMaterial());
        }
    }

    private float unitFactor(JsonObject object, String key, float fallback, String location)
            throws ModelImportException {
        float value = object.has(key) ? GlbJson.number(object.get(key), location + "." + key) : fallback;
        if (value < 0 || value > 1) throw GlbFailures.invalid(location + "." + key,
                key + " must be within [0, 1]");
        return value;
    }

    private ModelNormalTextureInfo parseNormalTexture(JsonObject material, String location)
            throws ModelImportException {
        if (!material.has("normalTexture")) return ModelNormalTextureInfo.absent();
        String textureLocation = location + ".normalTexture";
        JsonObject info = GlbJson.object(material.get("normalTexture"), textureLocation);
        float scale = info.has("scale") ? GlbJson.number(info.get("scale"), textureLocation + ".scale") : 1;
        ModelTextureInfo texture = parseTextureInfo(info, textureLocation);
        ModelTextureTransform transform = texture.transform();
        if (transform.scaleX() == 0.0F || transform.scaleY() == 0.0F) {
            throw GlbFailures.invalid(textureLocation + ".extensions.KHR_texture_transform.scale",
                    "normal texture transform must have an invertible UV Jacobian");
        }
        return new ModelNormalTextureInfo(texture, scale);
    }

    private ModelOcclusionTextureInfo parseOcclusionTexture(JsonObject material, String location)
            throws ModelImportException {
        if (!material.has("occlusionTexture")) return ModelOcclusionTextureInfo.absent();
        String textureLocation = location + ".occlusionTexture";
        JsonObject info = GlbJson.object(material.get("occlusionTexture"), textureLocation);
        float strength = info.has("strength")
                ? GlbJson.number(info.get("strength"), textureLocation + ".strength") : 1;
        if (strength < 0 || strength > 1) throw GlbFailures.invalid(textureLocation + ".strength",
                "occlusion strength must be within [0, 1]");
        return new ModelOcclusionTextureInfo(parseTextureInfo(info, textureLocation), strength);
    }

    private ModelTextureInfo parseTextureInfo(JsonObject info, String location) throws ModelImportException {
        int texture = GlbJson.requiredInt(info, "index", location);
        if (texture < 0) throw GlbFailures.invalid(location + ".index", "texture reference must not be negative");
        int texCoord = GlbJson.nonNegativeInt(info, "texCoord", 0, location);
        ModelTextureTransform transform = ModelTextureTransform.identity();
        if (info.has("extensions")) {
            JsonObject extensions = GlbJson.object(info.get("extensions"), location + ".extensions");
            if (extensions.has("KHR_texture_transform")) {
                if (!document.usesExtension("KHR_texture_transform")) {
                    throw GlbFailures.invalid(location + ".extensions.KHR_texture_transform",
                            "KHR_texture_transform must be declared in extensionsUsed");
                }
                JsonObject value = GlbJson.object(extensions.get("KHR_texture_transform"),
                        location + ".extensions.KHR_texture_transform");
                float[] offset = GlbJson.floatArray(value, "offset", 2, new float[]{0, 0},
                        location + ".extensions.KHR_texture_transform");
                float[] scale = GlbJson.floatArray(value, "scale", 2, new float[]{1, 1},
                        location + ".extensions.KHR_texture_transform");
                float rotation = value.has("rotation") ? GlbJson.number(value.get("rotation"),
                        location + ".extensions.KHR_texture_transform.rotation") : 0;
                texCoord = GlbJson.nonNegativeInt(value, "texCoord", texCoord,
                        location + ".extensions.KHR_texture_transform");
                transform = new ModelTextureTransform(offset[0], offset[1], rotation, scale[0], scale[1]);
            }
        }
        return new ModelTextureInfo(texture, texCoord, transform);
    }

    private void claimMipBytes(List<ModelImageSource> images, List<ModelTexture> textures)
            throws ModelImportException {
        Set<Integer> claimed = new HashSet<>();
        for (ModelTexture texture : textures) {
            if (!texture.sampler().minFilter().mipmapped() || !claimed.add(texture.imageIndex())) continue;
            if (texture.imageIndex() >= images.size()) continue; // The validator reports the reference precisely.
            ModelImageSource image = images.get(texture.imageIndex());
            int width = image.width(), height = image.height();
            long extra = 0;
            while (width > 1 || height > 1) {
                width = Math.max(1, width / 2);
                height = Math.max(1, height / 2);
                extra = Math.addExact(extra, Math.multiplyExact(Math.multiplyExact((long) width, height), 4L));
            }
            session.budgetTracker().claim(ModelBudgetResource.DECODED_IMAGE_BYTES, extra,
                    "images[" + texture.imageIndex() + "].mipChain");
        }
    }

    private ModelTextureSampler parseSampler(JsonObject sampler, String location) throws ModelImportException {
        int mag = GlbJson.optionalInt(sampler, "magFilter", 9729, location);
        int min = GlbJson.optionalInt(sampler, "minFilter", 9987, location);
        return new ModelTextureSampler(parseWrap(GlbJson.optionalInt(sampler, "wrapS", 10497, location), location + ".wrapS"),
                parseWrap(GlbJson.optionalInt(sampler, "wrapT", 10497, location), location + ".wrapT"),
                parseMinFilter(min, location + ".minFilter"), parseMagFilter(mag, location + ".magFilter"));
    }

    private ModelTextureWrap parseWrap(int value, String location) throws ModelImportException {
        return switch (value) {
            case 33071 -> ModelTextureWrap.CLAMP_TO_EDGE;
            case 33648 -> ModelTextureWrap.MIRRORED_REPEAT;
            case 10497 -> ModelTextureWrap.REPEAT;
            default -> throw GlbFailures.invalid(location, "unknown glTF wrap mode: " + value);
        };
    }

    private ModelTextureFilter parseMagFilter(int value, String location) throws ModelImportException {
        return switch (value) {
            case 9728 -> ModelTextureFilter.NEAREST;
            case 9729 -> ModelTextureFilter.LINEAR;
            default -> throw GlbFailures.invalid(location, "unknown glTF magnification filter: " + value);
        };
    }

    private ModelTextureFilter parseMinFilter(int value, String location) throws ModelImportException {
        return switch (value) {
            case 9728 -> ModelTextureFilter.NEAREST;
            case 9729 -> ModelTextureFilter.LINEAR;
            case 9984 -> ModelTextureFilter.NEAREST_MIPMAP_NEAREST;
            case 9985 -> ModelTextureFilter.LINEAR_MIPMAP_NEAREST;
            case 9986 -> ModelTextureFilter.NEAREST_MIPMAP_LINEAR;
            case 9987 -> ModelTextureFilter.LINEAR_MIPMAP_LINEAR;
            default -> throw GlbFailures.invalid(location, "unknown glTF minification filter: " + value);
        };
    }

    private boolean needsDefaultMaterial() throws ModelImportException {
        JsonArray meshes = GlbJson.array(document.root, "meshes");
        for (int mesh = 0; mesh < meshes.size(); mesh++) {
            JsonArray primitives = GlbJson.array(GlbJson.object(meshes.get(mesh), "meshes[" + mesh + "]"), "primitives");
            for (JsonElement primitive : primitives) {
                if (!GlbJson.object(primitive, "primitive").has("material")) return true;
            }
        }
        return false;
    }

    private List<ModelMesh> parseMeshes() throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "meshes");
        if (array.isEmpty()) throw GlbFailures.invalid("meshes", "model must contain at least one mesh");
        session.budgetTracker().claim(ModelBudgetResource.MESHES, array.size(), "meshes");
        List<ModelMesh> meshes = new ArrayList<>(array.size());
        for (int meshIndex = 0; meshIndex < array.size(); meshIndex++) {
            session.checkpoint("meshes[" + meshIndex + "]");
            String location = "meshes[" + meshIndex + "]";
            JsonObject mesh = GlbJson.object(array.get(meshIndex), location);
            if (mesh.has("weights")) throw GlbFailures.unsupported(location + ".weights", "morph target weights are not supported in M2");
            JsonArray primitivesJson = GlbJson.array(mesh, "primitives");
            if (primitivesJson.isEmpty()) throw GlbFailures.invalid(location + ".primitives", "mesh must contain a primitive");
            session.budgetTracker().claim(ModelBudgetResource.PRIMITIVES, primitivesJson.size(), location);
            List<ModelPrimitive> primitives = new ArrayList<>(primitivesJson.size());
            ModelBounds meshBounds = null;
            for (int primitiveIndex = 0; primitiveIndex < primitivesJson.size(); primitiveIndex++) {
                ModelPrimitive primitive = parsePrimitive(GlbJson.object(primitivesJson.get(primitiveIndex),
                        location + ".primitives[" + primitiveIndex + "]"), location + ".primitives[" + primitiveIndex + "]");
                primitives.add(primitive);
                meshBounds = GlbBounds.union(meshBounds, primitive.bounds());
            }
            meshes.add(new ModelMesh(GlbJson.string(mesh, "name", "", location), primitives,
                    Objects.requireNonNull(meshBounds)));
        }
        return List.copyOf(meshes);
    }

    private ModelPrimitive parsePrimitive(JsonObject primitive, String location) throws ModelImportException {
        int mode = GlbJson.optionalInt(primitive, "mode", 4, location);
        if (mode != 4) throw GlbFailures.unsupported(location + ".mode", "only TRIANGLES primitives are supported");
        if (primitive.has("targets")) throw GlbFailures.unsupported(location + ".targets", "morph targets are not supported in M2");
        JsonObject attributesJson = GlbJson.object(primitive.get("attributes"), location + ".attributes");
        if (!attributesJson.has("POSITION")) throw GlbFailures.attribute(location + ".attributes.POSITION", "POSITION is required");
        Map<ModelAttributeSemantic, ModelVertexAttribute> attributes = new LinkedHashMap<>();
        for (String key : attributesJson.keySet()) {
            ModelAttributeSemantic semantic = GlbAttributeSemanticParser.parse(key);
            if (semantic == null) throw GlbFailures.unsupported(location + ".attributes." + key,
                    "custom vertex attributes are not supported");
            if (attributes.put(semantic, decodeAttribute(attributesJson, key, semantic, location)) != null) {
                throw GlbFailures.attribute(location + ".attributes." + key, "duplicate canonical vertex semantic");
            }
        }
        ModelVertexAttribute positions = attributes.get(ModelAttributeSemantic.POSITION);
        validateConsecutiveSets(attributes.keySet(), location);
        for (var entry : attributes.entrySet()) {
            if (entry.getValue().elementCount() != positions.elementCount()) {
                throw GlbFailures.attribute(location + ".attributes." + entry.getKey(),
                        "all vertex attributes must have the same element count");
            }
        }
        Set<Integer> influenceSets = new HashSet<>();
        for (ModelAttributeSemantic semantic : attributes.keySet()) {
            if (semantic.is(ModelAttributeSemantic.Kind.JOINTS) || semantic.is(ModelAttributeSemantic.Kind.WEIGHTS)) {
                influenceSets.add(semantic.setIndex());
            }
        }
        for (int set : influenceSets) {
            ModelAttributeSemantic joints = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.JOINTS, set);
            ModelAttributeSemantic weights = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.WEIGHTS, set);
            if (attributes.containsKey(joints) != attributes.containsKey(weights)) {
                throw GlbFailures.attribute(location + ".attributes", joints + " and " + weights + " must appear together");
            }
        }
        normalizeInfluenceSets(attributes, influenceSets, location);
        int vertexCount = positions.elementCount();
        session.budgetTracker().claim(ModelBudgetResource.VERTICES, vertexCount, location);
        ModelIndexBuffer indices = primitive.has("indices")
                ? decoder.indices(GlbJson.requiredInt(primitive, "indices", location), location + ".indices")
                : sequentialIndices(vertexCount, location + ".indices");
        session.budgetTracker().claim(ModelBudgetResource.INDICES, indices.indexCount(), location);
        if (indices.indexCount() % 3 != 0) throw GlbFailures.attribute(location + ".indices", "triangle index count must be a multiple of three");
        session.budgetTracker().claim(ModelBudgetResource.TRIANGLES, indices.indexCount() / 3L, location);
        ensureIndexRange(indices, vertexCount, location);
        if (!attributes.containsKey(ModelAttributeSemantic.NORMAL)) {
            attributes.put(ModelAttributeSemantic.NORMAL, generateNormals(positions, indices, location));
        }
        int material = primitive.has("material") ? GlbJson.requiredInt(primitive, "material", location) : defaultMaterial;
        if (material >= 0 && material < materials.size()) {
            ModelTextureInfo normalTexture = materials.get(material).normalTexture().texture();
            if (normalTexture.textureIndex() >= 0 && !attributes.containsKey(ModelAttributeSemantic.TANGENT)) {
                GlbMikkTangentGenerator.Result generated = GlbMikkTangentGenerator.generate(
                        attributes, indices, normalTexture.texCoordSet(), session, location);
                attributes = new LinkedHashMap<>(generated.attributes());
                indices = generated.indices();
                positions = attributes.get(ModelAttributeSemantic.POSITION);
            }
        }
        for (var entry : attributes.entrySet()) if (entry.getKey().is(ModelAttributeSemantic.Kind.COLOR)) {
            validateColorRange(entry.getValue(), location + ".attributes." + entry.getKey());
        }
        ModelBounds bounds = GlbBounds.fromPositions(positions);
        return new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, attributes, indices, material, bounds);
    }

    private ModelVertexAttribute decodeAttribute(JsonObject attributes, String key, ModelAttributeSemantic semantic,
                                                 String location) throws ModelImportException {
        int accessor = GlbJson.requiredInt(attributes, key, location + ".attributes");
        return decoder.attribute(accessor, semantic, location + ".attributes." + key);
    }

    private void decodeOptionalAttribute(JsonObject source, String key, ModelAttributeSemantic semantic,
                                         String location, Map<ModelAttributeSemantic, ModelVertexAttribute> target)
            throws ModelImportException {
        if (source.has(key)) target.put(semantic, decodeAttribute(source, key, semantic, location));
    }

    private ModelIndexBuffer sequentialIndices(int vertexCount, String location) throws ModelImportException {
        ModelComponentType type = vertexCount <= 256 ? ModelComponentType.UINT8
                : vertexCount <= 65_536 ? ModelComponentType.UINT16 : ModelComponentType.UINT32;
        int byteCount = Math.multiplyExact(vertexCount, type.byteSize());
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES, byteCount, location);
        ByteBuffer data = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < vertexCount; i++) {
            switch (type) {
                case UINT8 -> data.put((byte) i);
                case UINT16 -> data.putShort((short) i);
                case UINT32 -> data.putInt(i);
                default -> throw new IllegalStateException();
            }
        }
        return new ModelIndexBuffer(type, vertexCount, data.array());
    }

    private void ensureIndexRange(ModelIndexBuffer indices, int vertexCount,
                                  String location) throws ModelImportException {
        for (int i = 0; i < indices.indexCount(); i++) {
            if ((i & 0x3FFF) == 0) session.checkpoint(location + ".indices[" + i + "]");
            if (indices.indexAt(i) >= vertexCount) throw GlbFailures.attribute(location + ".indices[" + i + "]", "index references a missing vertex");
        }
    }

    private ModelVertexAttribute generateNormals(ModelVertexAttribute positions, ModelIndexBuffer indices,
                                                 String location) throws ModelImportException {
        int count = positions.elementCount();
        int byteCount = Math.multiplyExact(count, 12);
        session.budgetTracker().claim(ModelBudgetResource.ATTRIBUTE_BYTES, byteCount, location + ".generatedNormals");
        float[] sums = new float[count * 3];
        ByteBuffer input = ByteBuffer.wrap(positions.data()).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < indices.indexCount(); i += 3) {
            if ((i & 0x3FFF) == 0) session.checkpoint(location + ".generatedNormals.indices[" + i + "]");
            int a = Math.toIntExact(indices.indexAt(i));
            int b = Math.toIntExact(indices.indexAt(i + 1));
            int c = Math.toIntExact(indices.indexAt(i + 2));
            float ax = input.getFloat(a * 12), ay = input.getFloat(a * 12 + 4), az = input.getFloat(a * 12 + 8);
            float abx = input.getFloat(b * 12) - ax, aby = input.getFloat(b * 12 + 4) - ay, abz = input.getFloat(b * 12 + 8) - az;
            float acx = input.getFloat(c * 12) - ax, acy = input.getFloat(c * 12 + 4) - ay, acz = input.getFloat(c * 12 + 8) - az;
            float nx = aby * acz - abz * acy, ny = abz * acx - abx * acz, nz = abx * acy - aby * acx;
            if (!Float.isFinite(nx) || !Float.isFinite(ny) || !Float.isFinite(nz)) throw GlbFailures.attribute(location, "generated normal overflowed");
            addNormal(sums, a, nx, ny, nz);
            addNormal(sums, b, nx, ny, nz);
            addNormal(sums, c, nx, ny, nz);
        }
        ByteBuffer output = ByteBuffer.allocate(byteCount).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < count; i++) {
            if ((i & 0x3FFF) == 0) session.checkpoint(location + ".generatedNormals.vertices[" + i + "]");
            float x = sums[i * 3], y = sums[i * 3 + 1], z = sums[i * 3 + 2];
            double length = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
            if (length < 1.0E-12D) { x = 0; y = 1; z = 0; length = 1; }
            output.putFloat((float) (x / length)).putFloat((float) (y / length)).putFloat((float) (z / length));
        }
        return new ModelVertexAttribute(ModelAttributeSemantic.NORMAL, ModelComponentType.FLOAT32,
                3, false, count, output.array());
    }

    private static void addNormal(float[] sums, int vertex, float x, float y, float z) {
        int offset = vertex * 3;
        sums[offset] += x;
        sums[offset + 1] += y;
        sums[offset + 2] += z;
    }

    private static void validateColorRange(ModelVertexAttribute color, String location) throws ModelImportException {
        ByteBuffer data = ByteBuffer.wrap(color.data()).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < color.elementCount() * color.componentCount(); i++) {
            float value = data.getFloat(i * 4);
            if (value < 0.0F || value > 1.0F) throw GlbFailures.attribute(location, "color components must be within [0, 1]");
        }
    }

    private static void validateConsecutiveSets(Set<ModelAttributeSemantic> semantics, String location)
            throws ModelImportException {
        for (ModelAttributeSemantic.Kind kind : List.of(ModelAttributeSemantic.Kind.TEXCOORD,
                ModelAttributeSemantic.Kind.COLOR, ModelAttributeSemantic.Kind.JOINTS,
                ModelAttributeSemantic.Kind.WEIGHTS)) {
            List<Integer> sets = semantics.stream().filter(semantic -> semantic.is(kind))
                    .map(ModelAttributeSemantic::setIndex).sorted().toList();
            for (int expected = 0; expected < sets.size(); expected++) {
                if (sets.get(expected) != expected) throw GlbFailures.attribute(location + ".attributes",
                        kind + " attribute sets must begin at zero and be consecutive");
            }
        }
    }

    private static void normalizeInfluenceSets(Map<ModelAttributeSemantic, ModelVertexAttribute> attributes,
                                               Set<Integer> sets, String location) throws ModelImportException {
        if (sets.isEmpty()) return;
        int count = attributes.get(ModelAttributeSemantic.POSITION).elementCount();
        Map<Integer, ByteBuffer> inputs = new LinkedHashMap<>();
        Map<Integer, ByteBuffer> outputs = new LinkedHashMap<>();
        for (int set : sets) {
            var semantic = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.WEIGHTS, set);
            inputs.put(set, attributes.get(semantic).readOnlyData().order(ByteOrder.LITTLE_ENDIAN));
            outputs.put(set, ByteBuffer.allocate(Math.multiplyExact(Math.multiplyExact(count, 4), Float.BYTES))
                    .order(ByteOrder.LITTLE_ENDIAN));
        }
        for (int vertex = 0; vertex < count; vertex++) {
            float sum = 0;
            for (ByteBuffer input : inputs.values()) for (int component = 0; component < 4; component++) {
                float value = input.getFloat((vertex * 4 + component) * Float.BYTES);
                if (!Float.isFinite(value) || value < 0) throw GlbFailures.attribute(location + ".attributes",
                        "skin weights must be finite and non-negative");
                sum += value;
            }
            if (!Float.isFinite(sum) || sum <= 1.0E-8F) throw GlbFailures.attribute(location + ".attributes",
                    "each vertex must have a positive skin-weight sum across all influence sets");
            for (int set : sets) for (int component = 0; component < 4; component++) {
                outputs.get(set).putFloat(inputs.get(set).getFloat((vertex * 4 + component) * Float.BYTES) / sum);
            }
        }
        for (int set : sets) {
            var semantic = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.WEIGHTS, set);
            attributes.put(semantic, new ModelVertexAttribute(semantic, ModelComponentType.FLOAT32, 4, false,
                    count, outputs.get(set).array()));
        }
    }

    private NodeAssembly parseNodes(List<ModelMesh> meshes) throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "nodes");
        if (array.isEmpty()) throw GlbFailures.invalid("nodes", "model must contain at least one node");
        session.budgetTracker().claim(ModelBudgetResource.NODES, array.size(), "nodes");
        List<NodeDraft> drafts = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) drafts.add(parseNode(GlbJson.object(array.get(i), "nodes[" + i + "]"), "nodes[" + i + "]"));
        Optional<ModelBounds>[] bounds = computeNodeBounds(drafts, meshes);
        List<ModelNode> nodes = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            NodeDraft draft = drafts.get(i);
            nodes.add(new ModelNode(draft.name, draft.transform, draft.mesh, draft.skin, draft.children, bounds[i]));
        }
        return new NodeAssembly(List.copyOf(nodes), drafts, bounds);
    }

    private NodeDraft parseNode(JsonObject node, String location) throws ModelImportException {
        int mesh = GlbJson.optionalInt(node, "mesh", -1, location);
        int skin = GlbJson.optionalInt(node, "skin", -1, location);
        if (skin >= 0 && mesh < 0) throw GlbFailures.invalid(location + ".skin", "a skinned node must reference a mesh");
        List<Integer> children = new ArrayList<>();
        JsonArray childArray = GlbJson.array(node, "children");
        for (int i = 0; i < childArray.size(); i++) children.add(integerElement(childArray.get(i), location + ".children[" + i + "]"));
        boolean matrix = node.has("matrix");
        boolean trs = node.has("translation") || node.has("rotation") || node.has("scale");
        if (matrix && trs) throw GlbFailures.invalid(location, "node matrix and TRS are mutually exclusive");
        ModelTransform transform;
        if (matrix) {
            float[] values = GlbJson.floatArray(node, "matrix", 16, new float[16], location);
            if (Math.abs(values[3]) > 1.0E-6F || Math.abs(values[7]) > 1.0E-6F
                    || Math.abs(values[11]) > 1.0E-6F || Math.abs(values[15] - 1.0F) > 1.0E-6F) {
                throw GlbFailures.unsupported(location + ".matrix", "node matrix must be affine");
            }
            transform = new ModelTransform.Matrix(new ModelMatrix4(values));
        } else {
            float[] t = GlbJson.floatArray(node, "translation", 3, new float[]{0, 0, 0}, location);
            float[] r = GlbJson.floatArray(node, "rotation", 4, new float[]{0, 0, 0, 1}, location);
            float[] s = GlbJson.floatArray(node, "scale", 3, new float[]{1, 1, 1}, location);
            double qLength = Math.sqrt((double) r[0] * r[0] + (double) r[1] * r[1] + (double) r[2] * r[2] + (double) r[3] * r[3]);
            if (Math.abs(qLength - 1.0D) > 1.0E-3D) throw GlbFailures.invalid(location + ".rotation", "node quaternion must be normalized");
            transform = new ModelTransform.Trs(new ModelVector3(t[0], t[1], t[2]),
                    new ModelQuaternion(r[0], r[1], r[2], r[3]), new ModelVector3(s[0], s[1], s[2]));
        }
        return new NodeDraft(GlbJson.string(node, "name", "", location), transform, mesh, skin, List.copyOf(children));
    }

    @SuppressWarnings("unchecked")
    private Optional<ModelBounds>[] computeNodeBounds(List<NodeDraft> nodes, List<ModelMesh> meshes)
            throws ModelImportException {
        Optional<ModelBounds>[] bounds = new Optional[nodes.size()];
        byte[] states = new byte[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) computeNodeBound(i, 1, nodes, meshes, bounds, states);
        return bounds;
    }

    private Optional<ModelBounds> computeNodeBound(int index, int depth, List<NodeDraft> nodes,
                                                   List<ModelMesh> meshes, Optional<ModelBounds>[] bounds,
                                                   byte[] states) throws ModelImportException {
        if (index < 0 || index >= nodes.size()) throw GlbFailures.reference("nodes", "child node index is out of range");
        if (depth > session.budget().maxNodeDepth()) throw GlbFailures.failure(ModelImportErrorCode.LIMIT_EXCEEDED, "nodes", "node hierarchy exceeds its depth budget");
        if (states[index] == 1) throw GlbFailures.failure(ModelImportErrorCode.INVALID_HIERARCHY, "nodes[" + index + "]", "node hierarchy contains a cycle");
        if (states[index] == 2) return bounds[index];
        states[index] = 1;
        NodeDraft node = nodes.get(index);
        ModelBounds accumulated = null;
        if (node.mesh >= 0) {
            if (node.mesh >= meshes.size()) throw GlbFailures.reference("nodes[" + index + "].mesh", "mesh index is out of range");
            accumulated = meshes.get(node.mesh).bounds();
        }
        for (int child : node.children) {
            Optional<ModelBounds> childBounds = computeNodeBound(child, depth + 1, nodes, meshes, bounds, states);
            if (childBounds.isPresent()) accumulated = GlbBounds.union(accumulated,
                    GlbBounds.transform(childBounds.get(), nodes.get(child).transform));
        }
        states[index] = 2;
        return bounds[index] = Optional.ofNullable(accumulated);
    }

    private SceneAssembly parseScenes(NodeAssembly nodes) throws ModelImportException {
        JsonArray array = GlbJson.array(document.root, "scenes");
        if (array.isEmpty()) throw GlbFailures.invalid("scenes", "GLB must define at least one scene");
        session.budgetTracker().claim(ModelBudgetResource.SCENES, array.size(), "scenes");
        int defaultScene = document.root.has("scene") ? GlbJson.requiredInt(document.root, "scene", "root")
                : array.size() == 1 ? 0 : -1;
        if (defaultScene < 0 || defaultScene >= array.size()) throw GlbFailures.reference("scene", "default scene is missing or out of range");
        List<ModelScene> scenes = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            String location = "scenes[" + i + "]";
            JsonObject scene = GlbJson.object(array.get(i), location);
            JsonArray rootsJson = GlbJson.array(scene, "nodes");
            List<Integer> roots = new ArrayList<>(rootsJson.size());
            ModelBounds sceneBounds = null;
            for (int rootIndex = 0; rootIndex < rootsJson.size(); rootIndex++) {
                int root = integerElement(rootsJson.get(rootIndex), location + ".nodes[" + rootIndex + "]");
                if (root < 0 || root >= nodes.nodes.size()) throw GlbFailures.reference(location + ".nodes", "root node index is out of range");
                roots.add(root);
                if (nodes.bounds[root].isPresent()) sceneBounds = GlbBounds.union(sceneBounds,
                        GlbBounds.transform(nodes.bounds[root].get(), nodes.drafts.get(root).transform));
            }
            scenes.add(new ModelScene(GlbJson.string(scene, "name", "", location), roots, Optional.ofNullable(sceneBounds)));
        }
        return new SceneAssembly(List.copyOf(scenes), defaultScene);
    }

    private static int integerElement(JsonElement element, String location) throws ModelImportException {
        JsonObject wrapper = new JsonObject();
        wrapper.add("value", element);
        return GlbJson.requiredInt(wrapper, "value", location);
    }

    private record NodeDraft(String name, ModelTransform transform, int mesh, int skin, List<Integer> children) { }
    private record NodeAssembly(List<ModelNode> nodes, List<NodeDraft> drafts, Optional<ModelBounds>[] bounds) { }
    private record SceneAssembly(List<ModelScene> scenes, int defaultScene) { }
}
