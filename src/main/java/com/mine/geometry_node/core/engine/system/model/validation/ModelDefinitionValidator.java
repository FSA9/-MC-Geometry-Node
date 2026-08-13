package com.mine.geometry_node.core.engine.system.model.validation;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.domain.animation.ModelAnimation;
import com.mine.geometry_node.core.engine.system.model.domain.animation.ModelAnimationChannel;
import com.mine.geometry_node.core.engine.system.model.domain.animation.ModelAnimationPath;
import com.mine.geometry_node.core.engine.system.model.domain.animation.ModelAnimationSampler;
import com.mine.geometry_node.core.engine.system.model.importer.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ModelDefinitionValidator {
    private ModelDefinitionValidator() {
    }

    public static void validate(ModelDefinition model, ModelImportBudget budget,
                                ModelCancellationToken cancellation) throws ModelImportException {
        if (model == null || budget == null) throw new IllegalArgumentException("model and budget must not be null");
        ModelCancellationToken token = cancellation == null ? ModelCancellationToken.NONE : cancellation;
        token.throwIfCancelled("validation");
        limit("scenes", model.scenes().size(), budget.maxScenes());
        limit("nodes", model.nodes().size(), budget.maxNodes());
        limit("meshes", model.meshes().size(), budget.maxMeshes());
        limit("materials", model.materials().size(), budget.maxMaterials());
        limit("textures", model.textures().size(), budget.maxTextures());
        limit("images", model.images().size(), budget.maxImages());
        limit("animations", model.animations().size(), budget.maxAnimations());

        validateHierarchy(model, budget.maxNodeDepth(), token);
        validateSkins(model, token);
        validateReferencesAndGeometry(model, budget, token);
        validateAnimations(model, budget, token);
    }

    private static void validateHierarchy(ModelDefinition model, int maxDepth,
                                          ModelCancellationToken token) throws ModelImportException {
        List<ModelNode> nodes = model.nodes();
        int[] parentCounts = new int[nodes.size()];
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            token.throwIfCancelled("nodes[" + nodeIndex + "]");
            for (int child : nodes.get(nodeIndex).children()) {
                requireIndex("nodes[" + nodeIndex + "].children", child, nodes.size());
                if (++parentCounts[child] > 1) {
                    fail(ModelImportErrorCode.INVALID_HIERARCHY, "nodes[" + child + "]",
                            "node has more than one parent");
                }
            }
        }
        byte[] states = new byte[nodes.size()];
        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            if (states[nodeIndex] == 0) visitNode(nodes, nodeIndex, 1, maxDepth, states, token);
        }
        for (int sceneIndex = 0; sceneIndex < model.scenes().size(); sceneIndex++) {
            ModelScene scene = model.scenes().get(sceneIndex);
            Set<Integer> roots = new HashSet<>();
            for (int root : scene.rootNodes()) {
                requireIndex("scenes[" + sceneIndex + "].rootNodes", root, nodes.size());
                if (!roots.add(root)) fail(ModelImportErrorCode.INVALID_HIERARCHY,
                        "scenes[" + sceneIndex + "]", "scene contains a duplicate root node");
                if (parentCounts[root] != 0) fail(ModelImportErrorCode.INVALID_HIERARCHY,
                        "scenes[" + sceneIndex + "].rootNodes", "scene root has a parent");
            }
        }
    }

    private static void visitNode(List<ModelNode> nodes, int index, int depth, int maxDepth,
                                  byte[] states, ModelCancellationToken token) throws ModelImportException {
        token.throwIfCancelled("nodes[" + index + "]");
        if (depth > maxDepth) limit("nodeDepth", depth, maxDepth);
        if (states[index] == 1) fail(ModelImportErrorCode.INVALID_HIERARCHY, "nodes[" + index + "]", "node hierarchy contains a cycle");
        if (states[index] == 2) return;
        states[index] = 1;
        for (int child : nodes.get(index).children()) visitNode(nodes, child, depth + 1, maxDepth, states, token);
        states[index] = 2;
    }

    private static void validateReferencesAndGeometry(ModelDefinition model, ModelImportBudget budget,
                                                      ModelCancellationToken token) throws ModelImportException {
        Set<SkinMeshKey> validatedSkinMeshes = new HashSet<>();
        for (int nodeIndex = 0; nodeIndex < model.nodes().size(); nodeIndex++) {
            ModelNode node = model.nodes().get(nodeIndex);
            int meshIndex = node.meshIndex();
            if (meshIndex >= 0) requireIndex("nodes[" + nodeIndex + "].meshIndex", meshIndex, model.meshes().size());
            if (node.skinIndex() >= 0) {
                requireIndex("nodes[" + nodeIndex + "].skinIndex", node.skinIndex(), model.skins().size());
                if (meshIndex < 0) fail(ModelImportErrorCode.INVALID_REFERENCE, "nodes[" + nodeIndex + "].skinIndex",
                        "a skinned node must reference a mesh");
                SkinMeshKey key = new SkinMeshKey(meshIndex, node.skinIndex());
                if (validatedSkinMeshes.add(key)) {
                    validateSkinAttributes(model.meshes().get(meshIndex), model.skins().get(node.skinIndex()), key, token);
                }
            } else if (meshIndex >= 0) {
                ModelMesh mesh = model.meshes().get(meshIndex);
                for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                    if (mesh.primitives().get(primitiveIndex).attributes().keySet().stream()
                            .anyMatch(semantic -> semantic.is(ModelAttributeSemantic.Kind.JOINTS))) {
                        fail(ModelImportErrorCode.INVALID_ATTRIBUTE,
                                "nodes[" + nodeIndex + "].mesh.primitives[" + primitiveIndex + "]",
                                "a primitive with skin attributes requires node.skinIndex");
                    }
                }
            }
        }
        for (int textureIndex = 0; textureIndex < model.textures().size(); textureIndex++) {
            requireIndex("textures[" + textureIndex + "].imageIndex",
                    model.textures().get(textureIndex).imageIndex(), model.images().size());
        }
        for (int materialIndex = 0; materialIndex < model.materials().size(); materialIndex++) {
            ModelMaterial material = model.materials().get(materialIndex);
            String location = "materials[" + materialIndex + "]";
            requireTextureReference(material.baseColorTexture(), location + ".baseColorTexture", model.textures().size());
            requireTextureReference(material.emissiveTexture(), location + ".emissiveTexture", model.textures().size());
            requireTextureReference(material.metallicRoughness().texture(),
                    location + ".metallicRoughness.texture", model.textures().size());
            requireTextureReference(material.normalTexture().texture(),
                    location + ".normalTexture.texture", model.textures().size());
            requireTextureReference(material.occlusionTexture().texture(),
                    location + ".occlusionTexture.texture", model.textures().size());
        }

        long primitiveCount = 0L;
        long vertexCount = 0L;
        long indexCount = 0L;
        long triangleCount = 0L;
        long attributeBytes = 0L;
        for (int meshIndex = 0; meshIndex < model.meshes().size(); meshIndex++) {
            ModelMesh mesh = model.meshes().get(meshIndex);
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                token.throwIfCancelled("meshes[" + meshIndex + "].primitives[" + primitiveIndex + "]");
                String location = "meshes[" + meshIndex + "].primitives[" + primitiveIndex + "]";
                ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
                primitiveCount = add(primitiveCount, 1L, location);
                validatePrimitive(primitive, location, model.materials(), token);
                vertexCount = add(vertexCount, primitive.vertexCount(), location);
                indexCount = add(indexCount, primitive.indices().indexCount(), location);
                triangleCount = add(triangleCount, primitive.triangleCount(), location);
                attributeBytes = add(attributeBytes, primitive.indices().byteSize(), location);
                for (ModelVertexAttribute attribute : primitive.attributes().values()) {
                    attributeBytes = add(attributeBytes, attribute.byteSize(), location);
                }
            }
        }
        limit("primitives", primitiveCount, budget.maxPrimitives());
        limit("vertices", vertexCount, budget.maxVertices());
        limit("indices", indexCount, budget.maxIndices());
        limit("triangles", triangleCount, budget.maxTriangles());
        limit("attributeBytes", attributeBytes, budget.maxAttributeBytes());

        long encodedImageBytes = 0L;
        for (ModelImageSource image : model.images()) encodedImageBytes = add(encodedImageBytes, image.byteSize(), "images");
        limit("encodedImageBytes", encodedImageBytes, budget.maxEncodedImageBytes());
    }

    private static void validateSkins(ModelDefinition model, ModelCancellationToken token)
            throws ModelImportException {
        for (int skinIndex = 0; skinIndex < model.skins().size(); skinIndex++) {
            token.throwIfCancelled("skins[" + skinIndex + "]");
            ModelSkin skin = model.skins().get(skinIndex);
            if (skin.joints().size() > ModelSkin.MAX_JOINTS) {
                limit("skins[" + skinIndex + "].joints", skin.joints().size(), ModelSkin.MAX_JOINTS);
            }
            Set<Integer> joints = new HashSet<>();
            for (int jointIndex = 0; jointIndex < skin.joints().size(); jointIndex++) {
                int nodeIndex = skin.joints().get(jointIndex);
                requireIndex("skins[" + skinIndex + "].joints[" + jointIndex + "]", nodeIndex, model.nodes().size());
                if (!joints.add(nodeIndex)) fail(ModelImportErrorCode.INVALID_HIERARCHY,
                        "skins[" + skinIndex + "].joints", "skin contains a duplicate joint node");
            }
            if (skin.skeletonNodeIndex() >= 0) requireIndex("skins[" + skinIndex + "].skeletonNodeIndex",
                    skin.skeletonNodeIndex(), model.nodes().size());
            if (skin.inverseBindMatrices().size() != skin.joints().size()) {
                fail(ModelImportErrorCode.INVALID_ATTRIBUTE, "skins[" + skinIndex + "].inverseBindMatrices",
                        "inverse-bind matrix count must match joint count");
            }
        }
    }

    private static void validateSkinAttributes(ModelMesh mesh, ModelSkin skin, SkinMeshKey key,
                                               ModelCancellationToken token)
            throws ModelImportException {
        for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
            ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
            String location = "meshes[" + key.meshIndex + "].primitives[" + primitiveIndex + "]"
                    + "@skins[" + key.skinIndex + "]";
            List<Integer> sets = primitive.attributes().keySet().stream()
                    .filter(semantic -> semantic.is(ModelAttributeSemantic.Kind.JOINTS))
                    .map(ModelAttributeSemantic::setIndex).sorted().toList();
            if (sets.isEmpty()) {
                fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location,
                        "every primitive of a skinned mesh requires at least one JOINTS_n and WEIGHTS_n pair");
            }
            int vertexCount = primitive.vertexCount();
            for (int vertex = 0; vertex < vertexCount; vertex++) {
                if ((vertex & 0x3FFF) == 0) token.throwIfCancelled(location + ".skinWeights[" + vertex + "]");
                float weightSum = 0.0F;
                for (int set : sets) {
                    String jointsName = "JOINTS_" + set, weightsName = "WEIGHTS_" + set;
                    ByteBuffer jointData = primitive.attributes().get(ModelAttributeSemantic.indexed(
                            ModelAttributeSemantic.Kind.JOINTS, set)).readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
                    ByteBuffer weightData = primitive.attributes().get(ModelAttributeSemantic.indexed(
                            ModelAttributeSemantic.Kind.WEIGHTS, set)).readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
                    for (int component = 0; component < 4; component++) {
                    int joint = Short.toUnsignedInt(jointData.getShort((vertex * 4 + component) * Short.BYTES));
                    float weight = weightData.getFloat((vertex * 4 + component) * Float.BYTES);
                    if (!Float.isFinite(weight) || weight < 0.0F) {
                        fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + "." + weightsName + "[" + vertex + "]",
                                "canonical skin weights must be finite and non-negative");
                    }
                    if (weight > 0.0F && joint >= skin.joints().size()) {
                        throw new ModelImportException(new ModelImportFailure(ModelImportErrorCode.INVALID_REFERENCE,
                                location + "." + jointsName + "[" + vertex + "]", "weighted joint index is out of range",
                                joint, skin.joints().size() - 1L));
                    }
                    weightSum += weight;
                    }
                }
                if (!Float.isFinite(weightSum) || Math.abs(weightSum - 1.0F) > 1.0E-4F) {
                    fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + ".skinWeights[" + vertex + "]",
                            "canonical skin weights must sum to one across all influence sets");
                }
            }
        }
    }

    private record SkinMeshKey(int meshIndex, int skinIndex) { }

    private static void validatePrimitive(ModelPrimitive primitive, String location,
                                          List<ModelMaterial> materials, ModelCancellationToken token)
            throws ModelImportException {
        if (primitive.topology() != ModelPrimitiveTopology.TRIANGLES) {
            fail(ModelImportErrorCode.UNSUPPORTED_FEATURE, location + ".topology", "only triangle primitives are supported");
        }
        ModelVertexAttribute position = primitive.attributes().get(ModelAttributeSemantic.POSITION);
        if (position == null) fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + ".POSITION", "position attribute is required");
        validateAttribute(position, location + ".POSITION", ModelComponentType.FLOAT32, 3, false);
        int vertexCount = position.elementCount();
        if (vertexCount < 3) fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + ".POSITION", "primitive requires at least three vertices");
        for (ModelVertexAttribute attribute : primitive.attributes().values()) {
            if (attribute.elementCount() != vertexCount) fail(ModelImportErrorCode.INVALID_ATTRIBUTE,
                    location + "." + attribute.semantic(), "all vertex attributes must have the same element count");
            switch (attribute.semantic().kind()) {
                case POSITION -> { }
                case NORMAL -> validateAttribute(attribute, location + ".NORMAL", ModelComponentType.FLOAT32, 3, false);
                case TANGENT -> validateAttribute(attribute, location + ".TANGENT", ModelComponentType.FLOAT32, 4, false);
                case TEXCOORD -> validateAttribute(attribute, location + "." + attribute.semantic(), ModelComponentType.FLOAT32, 2, false);
                case COLOR -> {
                    if (attribute.componentType() != ModelComponentType.FLOAT32
                            || (attribute.componentCount() != 3 && attribute.componentCount() != 4)
                            || attribute.normalized()) {
                        fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + "." + attribute.semantic(),
                                "canonical color must use non-normalized FLOAT32 x3/x4");
                    }
                }
                case JOINTS -> {
                    if (attribute.componentType() != ModelComponentType.UINT16
                            || attribute.componentCount() != 4 || attribute.normalized()) {
                        fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + "." + attribute.semantic(),
                                "canonical joints must use non-normalized UINT16 x4");
                    }
                }
                case WEIGHTS -> validateAttribute(attribute, location + "." + attribute.semantic(), ModelComponentType.FLOAT32, 4, false);
            }
            validateCanonicalValues(attribute, location + "." + attribute.semantic(), token);
        }
        Set<Integer> jointSets = new HashSet<>(), weightSets = new HashSet<>();
        for (ModelAttributeSemantic semantic : primitive.attributes().keySet()) {
            if (semantic.is(ModelAttributeSemantic.Kind.JOINTS)) jointSets.add(semantic.setIndex());
            if (semantic.is(ModelAttributeSemantic.Kind.WEIGHTS)) weightSets.add(semantic.setIndex());
        }
        if (!jointSets.equals(weightSets)) {
            fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location, "JOINTS_n and WEIGHTS_n sets must be paired");
        }
        if (primitive.indices().indexCount() < 3 || primitive.indices().indexCount() % 3 != 0) {
            fail(ModelImportErrorCode.INVALID_INDEX, location + ".indices", "triangle index count must be a positive multiple of three");
        }
        for (int index = 0; index < primitive.indices().indexCount(); index++) {
            if ((index & 0x3FFF) == 0) token.throwIfCancelled(location + ".indices[" + index + "]");
            long value = primitive.indices().indexAt(index);
            if (value >= vertexCount) {
                throw new ModelImportException(new ModelImportFailure(ModelImportErrorCode.INVALID_INDEX,
                        location + ".indices[" + index + "]", "index references a missing vertex", value, vertexCount - 1L));
            }
        }
        requireIndex(location + ".materialIndex", primitive.materialIndex(), materials.size());
        ModelMaterial material = materials.get(primitive.materialIndex());
        requireTextureCoordinates(primitive, material.baseColorTexture(), location);
        requireTextureCoordinates(primitive, material.emissiveTexture(), location);
        requireTextureCoordinates(primitive, material.metallicRoughness().texture(), location);
        requireTextureCoordinates(primitive, material.normalTexture().texture(), location);
        requireTextureCoordinates(primitive, material.occlusionTexture().texture(), location);
    }

    private static void requireTextureReference(ModelTextureInfo texture, String location, int textureCount)
            throws ModelImportException {
        if (texture.textureIndex() >= 0) requireIndex(location, texture.textureIndex(), textureCount);
    }

    private static void requireTextureCoordinates(ModelPrimitive primitive, ModelTextureInfo texture, String location)
            throws ModelImportException {
        if (texture.textureIndex() < 0) return;
        ModelAttributeSemantic semantic = ModelAttributeSemantic.indexed(ModelAttributeSemantic.Kind.TEXCOORD,
                texture.texCoordSet());
        if (!primitive.attributes().containsKey(semantic)) fail(ModelImportErrorCode.INVALID_ATTRIBUTE,
                location + "." + semantic, "textured material requires " + semantic);
    }

    private static void validateCanonicalValues(ModelVertexAttribute attribute, String location,
                                                ModelCancellationToken token) throws ModelImportException {
        if (attribute.componentType() != ModelComponentType.FLOAT32) return;
        ByteBuffer data = attribute.readOnlyData().order(ByteOrder.LITTLE_ENDIAN);
        int values = Math.multiplyExact(attribute.elementCount(), attribute.componentCount());
        for (int index = 0; index < values; index++) {
            if ((index & 0x3FFF) == 0) token.throwIfCancelled(location + "[" + index + "]");
            float value = data.getFloat(index * Float.BYTES);
            if (!Float.isFinite(value)) {
                fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + "[" + index + "]",
                        "canonical floating-point attributes must be finite");
            }
            if (attribute.semantic().is(ModelAttributeSemantic.Kind.COLOR)
                    && (value < 0.0F || value > 1.0F)) {
                fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + "[" + index + "]",
                        "canonical color components must be within [0, 1]");
            }
            if (attribute.semantic().equals(ModelAttributeSemantic.TANGENT) && index % 4 == 3
                    && value != -1.0F && value != 1.0F) {
                fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location + "[" + index + "]",
                        "canonical tangent handedness must be -1 or +1");
            }
        }
    }

    private static void validateAttribute(ModelVertexAttribute attribute, String location,
                                          ModelComponentType type, int components,
                                          boolean normalized) throws ModelImportException {
        if (attribute.componentType() != type || attribute.componentCount() != components
                || attribute.normalized() != normalized) {
            fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location,
                    "attribute does not use the canonical component layout");
        }
    }

    private static void validateAnimations(ModelDefinition model, ModelImportBudget budget,
                                           ModelCancellationToken token) throws ModelImportException {
        long channelCount = 0L;
        long keyframeCount = 0L;
        for (int animationIndex = 0; animationIndex < model.animations().size(); animationIndex++) {
            token.throwIfCancelled("animations[" + animationIndex + "]");
            ModelAnimation animation = model.animations().get(animationIndex);
            channelCount = add(channelCount, animation.channels().size(), "animations");
            for (ModelAnimationSampler sampler : animation.samplers()) {
                if (sampler.interpolation() == com.mine.geometry_node.core.engine.system.model.domain.animation.ModelAnimationInterpolation.CUBIC_SPLINE) {
                    fail(ModelImportErrorCode.UNSUPPORTED_FEATURE, "animations[" + animationIndex + "].samplers",
                            "CUBIC_SPLINE animation is not supported in M9");
                }
                keyframeCount = add(keyframeCount, sampler.keyCount(), "animations");
            }
            Set<String> targets = new HashSet<>();
            for (int channelIndex = 0; channelIndex < animation.channels().size(); channelIndex++) {
                ModelAnimationChannel channel = animation.channels().get(channelIndex);
                String location = "animations[" + animationIndex + "].channels[" + channelIndex + "]";
                if (channel.path() == ModelAnimationPath.WEIGHTS) {
                    fail(ModelImportErrorCode.UNSUPPORTED_FEATURE, location,
                            "morph-weight animation is not supported in M9");
                }
                requireIndex(location + ".nodeIndex", channel.nodeIndex(), model.nodes().size());
                requireIndex(location + ".samplerIndex", channel.samplerIndex(), animation.samplers().size());
                ModelAnimationSampler sampler = animation.samplers().get(channel.samplerIndex());
                if (channel.path() != ModelAnimationPath.WEIGHTS
                        && sampler.outputComponentCount() != channel.path().componentCount()) {
                    fail(ModelImportErrorCode.INVALID_ATTRIBUTE, location, "animation sampler component count does not match its path");
                }
                if (channel.path() == ModelAnimationPath.ROTATION) {
                    for (int key = 0; key < sampler.keyCount(); key++) {
                        double lengthSquared = 0.0D;
                        for (int component = 0; component < 4; component++) {
                            double value = sampler.outputValue(key, component);
                            lengthSquared += value * value;
                        }
                        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0E-12D) {
                            fail(ModelImportErrorCode.INVALID_DATA, location,
                                    "rotation quaternion must have non-zero finite length");
                        }
                    }
                }
                if (model.nodes().get(channel.nodeIndex()).transform() instanceof ModelTransform.Matrix) {
                    fail(ModelImportErrorCode.UNSUPPORTED_FEATURE, location, "matrix nodes cannot be targeted by TRS animation");
                }
                if (!targets.add(channel.nodeIndex() + ":" + channel.path())) {
                    fail(ModelImportErrorCode.INVALID_DATA, location, "animation contains duplicate channels for one node path");
                }
            }
        }
        limit("animationChannels", channelCount, budget.maxAnimationChannels());
        limit("animationKeyframes", keyframeCount, budget.maxAnimationKeyframes());
    }

    private static long add(long left, long right, String location) throws ModelImportException {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            fail(ModelImportErrorCode.LIMIT_EXCEEDED, location, "model counter overflow");
            return 0L;
        }
    }

    private static void requireIndex(String location, int index, int size) throws ModelImportException {
        if (index < 0 || index >= size) {
            throw new ModelImportException(new ModelImportFailure(ModelImportErrorCode.INVALID_REFERENCE,
                    location, "reference is out of range", index, size - 1L));
        }
    }

    private static void limit(String location, long actual, long maximum) throws ModelImportException {
        if (actual > maximum) {
            throw new ModelImportException(new ModelImportFailure(ModelImportErrorCode.LIMIT_EXCEEDED,
                    location, "model import budget exceeded", actual, maximum));
        }
    }

    private static void fail(ModelImportErrorCode code, String location, String message) throws ModelImportException {
        throw new ModelImportException(ModelImportFailure.simple(code, location, message));
    }
}
