package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.api.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.importer.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GlbModelImporterTest {

    @Test
    void preservesTangentAndConsecutiveIndexedUvSets() {
        ByteBuffer data = ByteBuffer.allocate(36 + 6 + 48 + 24 + 24).order(ByteOrder.LITTLE_ENDIAN);
        data.put(triangleBinary(false, false));
        for (int vertex = 0; vertex < 3; vertex++) data.putFloat(1).putFloat(0).putFloat(0).putFloat(1);
        putTriangleUvs(data);
        data.putFloat(0.25F).putFloat(0.75F).putFloat(1).putFloat(0).putFloat(0).putFloat(1);
        String body = "\"bufferViews\":["
                + "{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6},"
                + "{\"buffer\":0,\"byteOffset\":42,\"byteLength\":48},"
                + "{\"buffer\":0,\"byteOffset\":90,\"byteLength\":24},"
                + "{\"buffer\":0,\"byteOffset\":114,\"byteLength\":24}],"
                + "\"accessors\":["
                + "{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":3,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"},"
                + "{\"bufferView\":4,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"TANGENT\":2,"
                + "\"TEXCOORD_0\":3,\"TEXCOORD_1\":4},\"indices\":1}]}],"
                + "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0";
        ModelPrimitive primitive = success(glb(baseJson(body, data.array().length), data.array()))
                .meshes().getFirst().primitives().getFirst();
        assertTrue(primitive.attributes().containsKey(ModelAttributeSemantic.TANGENT));
        assertTrue(primitive.attributes().containsKey(ModelAttributeSemantic.indexed(
                ModelAttributeSemantic.Kind.TEXCOORD, 1)));
        assertEquals(0.25F, attributeFloat(primitive, ModelAttributeSemantic.indexed(
                ModelAttributeSemantic.Kind.TEXCOORD, 1), 0));
    }

    @Test
    void rejectsNonConsecutiveIndexedUvSets() {
        byte[] binary = triangleBinary(true, false);
        String body = triangleBody(true, false, binary.length).replace("\"TEXCOORD_0\":2", "\"TEXCOORD_1\":2");
        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class,
                importResult(glb(baseJson(body, binary.length), binary)));
        assertEquals(ModelImportErrorCode.INVALID_ATTRIBUTE, failure.failure().code());
    }

    @Test
    void rejectsInvalidTangentHandedness() {
        ByteBuffer data = ByteBuffer.allocate(36 + 6 + 48).order(ByteOrder.LITTLE_ENDIAN);
        data.put(triangleBinary(false, false));
        for (int vertex = 0; vertex < 3; vertex++) data.putFloat(1).putFloat(0).putFloat(0).putFloat(0.5F);
        String body = triangleBody(false, false, data.array().length)
                .replace("{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6}",
                        "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6},"
                                + "{\"buffer\":0,\"byteOffset\":42,\"byteLength\":48}")
                .replace("{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"}",
                        "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"}")
                .replace("\"POSITION\":0", "\"POSITION\":0,\"TANGENT\":2");
        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class,
                importResult(glb(baseJson(body, data.array().length), data.array())));
        assertEquals(ModelImportErrorCode.INVALID_ATTRIBUTE, failure.failure().code());
        assertTrue(failure.failure().location().contains("TANGENT"));
    }

    @Test
    void rejectsMismatchedIndexedAttributeCountStructurallyBeforeCanonicalization() {
        byte[] binary = triangleBinary(false, false);
        String body = triangleBody(false, false, binary.length)
                .replace("{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"}",
                        "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                                + "{\"bufferView\":0,\"componentType\":5123,\"count\":2,\"type\":\"VEC4\"},"
                                + "{\"bufferView\":0,\"componentType\":5126,\"count\":2,\"type\":\"VEC4\"}")
                .replace("\"POSITION\":0", "\"POSITION\":0,\"JOINTS_0\":2,\"WEIGHTS_0\":3");

        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class,
                importResult(glb(baseJson(body, binary.length), binary)));
        assertEquals(ModelImportErrorCode.INVALID_ATTRIBUTE, failure.failure().code());
        assertTrue(failure.failure().location().contains("WEIGHTS_0")
                || failure.failure().location().contains("JOINTS_0"));
    }
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int BIN_CHUNK = 0x004E4942;
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    private static final byte[] JPEG_2X1 = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0,
            0, 17, 8, 0, 1, 0, 2, 3, 1, 17, 0, 2, 17, 0, 3, 17, 0, (byte) 0xFF, (byte) 0xD9};

    @Test
    void importsTriangleAndGeneratesNormalsAndBounds() {
        byte[] glb = triangleGlb(false, false, false);

        ModelDefinition model = success(glb);

        ModelPrimitive primitive = model.meshes().getFirst().primitives().getFirst();
        assertEquals(3, primitive.vertexCount());
        assertEquals(1, primitive.triangleCount());
        assertTrue(primitive.attributes().containsKey(ModelAttributeSemantic.NORMAL));
        assertEquals(new ModelVector3(2, 3, 4), model.bounds().min());
        assertEquals(new ModelVector3(3, 4, 4), model.bounds().max());
        assertEquals(model.bounds(), model.scenes().getFirst().bounds().orElseThrow());
        assertEquals(model.meshes().getFirst().bounds(), model.nodes().getFirst().bounds().orElseThrow());
    }

    @Test
    void importsEmbeddedPngAndBaseColorMaterial() {
        ModelDefinition model = success(triangleGlb(true, false, false));

        assertEquals(1, model.images().size());
        assertEquals("image/png", model.images().getFirst().mimeType());
        assertEquals(1, model.images().getFirst().width());
        assertEquals(1, model.images().getFirst().height());
        assertEquals(0, model.materials().getFirst().baseColorTexture().textureIndex());
    }

    @Test
    void importsM8SamplerTextureTransformBlendAndEmissive() {
        byte[] binary = triangleBinary(true, false);
        String body = "\"extensionsUsed\":[\"KHR_texture_transform\"]," + triangleBody(true, false, binary.length)
                .replace("\"textures\":[{\"source\":0}]",
                        "\"samplers\":[{\"magFilter\":9728,\"minFilter\":9985,\"wrapS\":33648,\"wrapT\":33071}],"
                                + "\"textures\":[{\"source\":0,\"sampler\":0}]")
                .replace("\"baseColorTexture\":{\"index\":0}",
                        "\"baseColorTexture\":{\"index\":0,\"extensions\":{\"KHR_texture_transform\":{"
                                + "\"offset\":[0.25,0.5],\"rotation\":0.75,\"scale\":[2,3]}}}")
                .replace("}}}]", "}},\"alphaMode\":\"BLEND\",\"alphaCutoff\":2,\"doubleSided\":true,"
                        + "\"emissiveFactor\":[0.1,0.2,0.3],\"emissiveTexture\":{\"index\":0}}]");
        ModelDefinition model = success(glb(baseJson(body, binary.length), binary));
        ModelTextureSampler sampler = model.textures().getFirst().sampler();
        assertEquals(ModelTextureWrap.MIRRORED_REPEAT, sampler.wrapS());
        assertEquals(ModelTextureWrap.CLAMP_TO_EDGE, sampler.wrapT());
        assertEquals(ModelTextureFilter.LINEAR_MIPMAP_NEAREST, sampler.minFilter());
        assertEquals(ModelTextureFilter.NEAREST, sampler.magFilter());
        ModelMaterial material = model.materials().getFirst();
        assertEquals(ModelAlphaMode.BLEND, material.alphaMode());
        assertEquals(2, material.alphaCutoff());
        assertTrue(material.doubleSided());
        assertEquals(0.25F, material.baseColorTexture().transform().offsetX());
        assertEquals(3, material.baseColorTexture().transform().scaleY());
        assertEquals(0.3F, material.emissiveBlue());
        assertEquals(0, material.emissiveTexture().textureIndex());
    }

    @Test
    void importsEveryGltfSamplerFilterAndWrapValue() {
        int[] minValues = {9728, 9729, 9984, 9985, 9986, 9987};
        ModelTextureFilter[] expectedMin = ModelTextureFilter.values();
        for (int index = 0; index < minValues.length; index++) {
            assertEquals(expectedMin[index], success(texturedSamplerGlb(minValues[index], 9729, 10497, 10497))
                    .textures().getFirst().sampler().minFilter());
        }
        assertEquals(ModelTextureWrap.MIRRORED_REPEAT,
                success(texturedSamplerGlb(9729, 9728, 33648, 33071)).textures().getFirst().sampler().wrapS());
    }

    @Test
    void rejectsNegativeM8TextureReferencesStructurally() {
        byte[] binary = triangleBinary(true, false);
        String negativeSampler = triangleBody(true, false, binary.length)
                .replace("{\"source\":0}", "{\"source\":0,\"sampler\":-1}");
        assertFailure(glb(baseJson(negativeSampler, binary.length), binary),
                ModelImportErrorCode.INVALID_DATA, "textures[0].sampler");
        String negativeTexture = triangleBody(true, false, binary.length)
                .replace("\"baseColorTexture\":{\"index\":0}", "\"baseColorTexture\":{\"index\":-1}");
        assertFailure(glb(baseJson(negativeTexture, binary.length), binary),
                ModelImportErrorCode.INVALID_DATA, "materials[0].pbrMetallicRoughness.baseColorTexture.index");
        String negativeSource = triangleBody(true, false, binary.length)
                .replace("{\"source\":0}", "{\"source\":-1}");
        assertFailure(glb(baseJson(negativeSource, binary.length), binary),
                ModelImportErrorCode.INVALID_DATA, "textures[0].source");
        assertFailure(texturedSamplerGlb(1234, 9729, 10497, 10497),
                ModelImportErrorCode.INVALID_DATA, "samplers[0].minFilter");
    }

    @Test
    void rejectsTexturedMaterialWithoutTexcoordZero() {
        byte[] binary = triangleBinary(true, false);
        String body = triangleBody(true, false, binary.length).replace(",\"TEXCOORD_0\":2", "");
        assertFailure(glb(baseJson(body, binary.length), binary),
                ModelImportErrorCode.INVALID_ATTRIBUTE, "meshes[0].primitives[0].TEXCOORD_0");
    }

    @Test
    void acceptsTextureTransformAsRequiredExtension() {
        byte[] binary = triangleBinary(true, false);
        String body = "\"extensionsUsed\":[\"KHR_texture_transform\"],"
                + "\"extensionsRequired\":[\"KHR_texture_transform\"],"
                + triangleBody(true, false, binary.length).replace("\"baseColorTexture\":{\"index\":0}",
                "\"baseColorTexture\":{\"index\":0,\"extensions\":{\"KHR_texture_transform\":{"
                        + "\"offset\":[0.1,0.2]}}}");
        assertEquals(0.1F, success(glb(baseJson(body, binary.length), binary))
                .materials().getFirst().baseColorTexture().transform().offsetX());
    }

    @Test
    void rejectsTextureTransformUseWithoutExtensionsUsedDeclaration() {
        byte[] binary = triangleBinary(true, false);
        String body = triangleBody(true, false, binary.length).replace("\"baseColorTexture\":{\"index\":0}",
                "\"baseColorTexture\":{\"index\":0,\"extensions\":{\"KHR_texture_transform\":{"
                        + "\"offset\":[0.1,0.2]}}}");
        assertFailure(glb(baseJson(body, binary.length), binary),
                ModelImportErrorCode.INVALID_DATA,
                "materials[0].pbrMetallicRoughness.baseColorTexture.extensions.KHR_texture_transform");
    }

    @Test
    void importsEmbeddedJpegMetadataWithoutDecodingPixels() {
        ModelImageSource image = success(triangleEmbeddedImageGlb(JPEG_2X1, "image/jpeg"))
                .images().getFirst();

        assertEquals("image/jpeg", image.mimeType());
        assertEquals(2, image.width());
        assertEquals(1, image.height());
    }

    @Test
    void importsEmbeddedPngDataUri() {
        String uri = "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG_1X1);
        ModelImageSource image = success(triangleDataUriGlb(uri)).images().getFirst();

        assertEquals(1, image.width());
        assertEquals(1, image.height());
    }

    @Test
    void importsWithoutIndicesAndCreatesCompactSequentialIndexBuffer() {
        ModelPrimitive primitive = success(triangleGlb(false, true, false))
                .meshes().getFirst().primitives().getFirst();

        assertEquals(ModelComponentType.UINT8, primitive.indices().componentType());
        assertArrayEquals(new long[]{0, 1, 2}, new long[]{primitive.indices().indexAt(0),
                primitive.indices().indexAt(1), primitive.indices().indexAt(2)});
    }

    @Test
    void decodesInterleavedNormalsNormalizedUvsAndVertexColors() {
        ModelPrimitive primitive = success(interleavedAttributeGlb())
                .meshes().getFirst().primitives().getFirst();

        assertEquals(4, primitive.vertexLayout().elements().size());
        assertEquals(1.0F, attributeFloat(primitive, ModelAttributeSemantic.NORMAL, 2), 1.0E-6F);
        assertEquals(1.0F, attributeFloat(primitive, ModelAttributeSemantic.TEXCOORD_0, 2), 1.0E-6F);
        assertEquals(128.0F / 255.0F,
                attributeFloat(primitive, ModelAttributeSemantic.COLOR_0, 1), 1.0E-6F);
    }

    @Test
    void appliesColumnMajorNodeMatrixToSceneBounds() {
        byte[] binary = triangleBinary(false, false);
        String body = triangleBody(false, false, binary.length).replace(
                "\"translation\":[2,3,4]",
                "\"matrix\":[0,1,0,0,-1,0,0,0,0,0,1,0,2,3,0,1]");

        ModelBounds bounds = success(glb(baseJson(body, binary.length), binary)).bounds();

        assertEquals(new ModelVector3(1, 3, 0), bounds.min());
        assertEquals(new ModelVector3(2, 4, 0), bounds.max());
    }

    @Test
    void importerRunsOnABackgroundThreadWithoutClientState() throws Exception {
        String caller = Thread.currentThread().getName();
        String[] worker = {caller};
        ModelImportResult result = CompletableFuture.supplyAsync(() -> {
            worker[0] = Thread.currentThread().getName();
            return importResult(triangleGlb(false, false, false));
        }).get(10, TimeUnit.SECONDS);

        assertNotEquals(caller, worker[0]);
        assertInstanceOf(ModelImportResult.Success.class, result);
    }

    @Test
    void rejectsWrongGlbVersion() {
        byte[] glb = triangleGlb(false, false, false);
        ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 1);

        assertFailure(glb, ModelImportErrorCode.UNSUPPORTED_FEATURE, "glb.header.version");
    }

    @Test
    void rejectsBufferViewOutsideBinChunk() {
        String json = baseJson("\"bufferViews\":[{\"buffer\":0,\"byteOffset\":10000,\"byteLength\":36}],"
                + "\"accessors\":[],\"meshes\":[],\"nodes\":[],\"scenes\":[]", 0);

        assertFailure(glb(json, new byte[0]), ModelImportErrorCode.INVALID_DATA, "bufferViews[0]");
    }

    @Test
    void rejectsIndexThatReferencesMissingVertex() {
        assertFailure(triangleGlb(false, false, true), ModelImportErrorCode.INVALID_ATTRIBUTE,
                "meshes[0].primitives[0].indices[2]");
    }

    @Test
    void importsM9LinearTranslationAnimation() {
        ModelDefinition definition = success(animatedTranslationGlb("LINEAR", "translation", 0, false));
        assertEquals(1, definition.animations().size());
        assertEquals(1, definition.animations().getFirst().durationSeconds());
    }

    @Test
    void rejectsUnsupportedAndMalformedM9Channels() {
        assertFailure(animatedTranslationGlb("CUBICSPLINE", "translation", 0, false),
                ModelImportErrorCode.UNSUPPORTED_FEATURE, "animations[0].samplers[0].interpolation");
        assertFailure(animatedTranslationGlb("LINEAR", "weights", 0, false),
                ModelImportErrorCode.UNSUPPORTED_FEATURE, "animations[0].channels[0].target.path");
        assertFailure(animatedTranslationGlb("LINEAR", "translation", 1, false),
                ModelImportErrorCode.INVALID_REFERENCE, "animations[0].channels[0].sampler");
        assertFailure(animatedTranslationGlb("LINEAR", "rotation", 0, false),
                ModelImportErrorCode.INVALID_DATA, "animations[0].channels[0].sampler");
        assertFailure(animatedTranslationGlb("LINEAR", "translation", 0, true),
                ModelImportErrorCode.INVALID_DATA, "animations[0].channels[1]");
    }

    @Test
    void importsM10SkinAndCanonicalizesJointWeights() {
        ModelDefinition model = success(skinnedTriangleGlb(0, true));

        assertEquals(1, model.skins().size());
        ModelSkin skin = model.skins().getFirst();
        assertEquals(java.util.List.of(1), skin.joints());
        assertEquals(1, skin.skeletonNodeIndex());
        assertEquals(new ModelMatrix4(new float[]{1, 0, 0, 0, 0, 1, 0, 0,
                0, 0, 1, 0, 0, 0, 0, 1}), skin.inverseBindMatrices().getFirst());
        ModelPrimitive primitive = model.meshes().getFirst().primitives().getFirst();
        ModelVertexAttribute joints = primitive.attributes().get(ModelAttributeSemantic.JOINTS_0);
        ModelVertexAttribute weights = primitive.attributes().get(ModelAttributeSemantic.WEIGHTS_0);
        assertEquals(ModelComponentType.UINT16, joints.componentType());
        assertEquals(ModelComponentType.FLOAT32, weights.componentType());
        assertEquals(1.0F, ByteBuffer.wrap(weights.data()).order(ByteOrder.LITTLE_ENDIAN).getFloat(), 1.0E-6F);
    }

    @Test
    void rejectsWeightedJointOutsideM10Skin() {
        assertFailure(skinnedTriangleGlb(1, false), ModelImportErrorCode.INVALID_REFERENCE,
                "meshes[0].primitives[0]@skins[0].JOINTS_0[0]");
    }

    private static byte[] skinnedTriangleGlb(int jointIndex, boolean explicitInverseBind) {
        ByteBuffer binary = ByteBuffer.allocate(42 + 2 + 12 + 12 + (explicitInverseBind ? 64 : 0))
                .order(ByteOrder.LITTLE_ENDIAN);
        binary.put(triangleBinary(false, false)).putShort((short) 0);
        for (int vertex = 0; vertex < 3; vertex++) {
            binary.put((byte) jointIndex).put((byte) 0).put((byte) 0).put((byte) 0);
        }
        for (int vertex = 0; vertex < 3; vertex++) {
            binary.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 0);
        }
        if (explicitInverseBind) {
            for (float value : new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}) {
                binary.putFloat(value);
            }
        }
        String ibmView = explicitInverseBind ? ",{\"buffer\":0,\"byteOffset\":68,\"byteLength\":64}" : "";
        String ibmAccessor = explicitInverseBind
                ? ",{\"bufferView\":4,\"componentType\":5126,\"count\":1,\"type\":\"MAT4\"}" : "";
        String ibmSkin = explicitInverseBind ? ",\"inverseBindMatrices\":4" : "";
        String body = "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6},"
                + "{\"buffer\":0,\"byteOffset\":44,\"byteLength\":12},"
                + "{\"buffer\":0,\"byteOffset\":56,\"byteLength\":12}" + ibmView + "],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":2,\"componentType\":5121,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":3,\"componentType\":5121,\"normalized\":true,\"count\":3,\"type\":\"VEC4\"}"
                + ibmAccessor + "],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"JOINTS_0\":2,\"WEIGHTS_0\":3},\"indices\":1}]}],"
                + "\"nodes\":[{\"mesh\":0,\"skin\":0},{\"name\":\"joint\"}],"
                + "\"skins\":[{\"joints\":[1],\"skeleton\":1" + ibmSkin + "}],"
                + "\"scenes\":[{\"nodes\":[0,1]}],\"scene\":0";
        return glb(baseJson(body, binary.array().length), binary.array());
    }

    private static byte[] animatedTranslationGlb(String interpolation, String path, int channelSampler,
                                                   boolean duplicateChannel) {
        byte[] geometry = triangleBinary(false, false);
        ByteBuffer buffer = ByteBuffer.allocate(geometry.length + 2 + 8 + 24).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(geometry).putShort((short) 0).putFloat(0).putFloat(1)
                .putFloat(0).putFloat(0).putFloat(0).putFloat(2).putFloat(0).putFloat(0);
        String body = triangleBody(false, false, buffer.array().length)
                .replace("],\"accessors\"", ",{\"buffer\":0,\"byteOffset\":44,\"byteLength\":8},"
                        + "{\"buffer\":0,\"byteOffset\":52,\"byteLength\":24}],\"accessors\"")
                .replace("],\"meshes\"", ",{\"bufferView\":2,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
                        + "{\"bufferView\":3,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"}],\"meshes\"")
                .replace("\"scene\":0", "\"animations\":[{\"name\":\"move\",\"samplers\":[{\"input\":2,\"output\":3,"
                        + "\"interpolation\":\"" + interpolation + "\"}],\"channels\":[{\"sampler\":" + channelSampler
                        + ",\"target\":{\"node\":0,\"path\":\"" + path + "\"}}"
                        + (duplicateChannel ? ",{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"translation\"}}" : "")
                        + "]}],\"scene\":0");
        return glb(baseJson(body, buffer.array().length), buffer.array());
    }

    @Test
    void rejectsRequiredExtensionsInsteadOfSilentlyDroppingThem() {
        byte[] binary = triangleBinary(false, false);
        String body = triangleBody(false, false, binary.length)
                .replace("\"scene\":0", "\"extensionsRequired\":[\"KHR_draco_mesh_compression\"],\"scene\":0");

        assertFailure(glb(baseJson(body, binary.length), binary),
                ModelImportErrorCode.UNSUPPORTED_FEATURE, "extensionsRequired");
    }

    private static ModelDefinition success(byte[] glb) {
        ModelImportResult.Success success = assertInstanceOf(ModelImportResult.Success.class, importResult(glb),
                () -> failureMessage(importResult(glb)));
        return success.definition();
    }

    private static void assertFailure(byte[] glb, ModelImportErrorCode code, String location) {
        ModelImportResult.Failure failure = assertInstanceOf(ModelImportResult.Failure.class, importResult(glb));
        assertEquals(code, failure.failure().code());
        assertEquals(location, failure.failure().location());
    }

    private static String failureMessage(ModelImportResult result) {
        return result instanceof ModelImportResult.Failure failure ? failure.failure().toString() : "unexpected result";
    }

    private static ModelImportResult importResult(byte[] glb) {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "fixture.glb",
                new ModelAssetRevision(glb.length, 0L, ""));
        return BuiltinModelImporters.createRegistry().importModel(GlbModelImporter.ID,
                new ModelImportSource(asset, glb), ModelImportContext.defaults());
    }

    private static byte[] triangleGlb(boolean textured, boolean noIndices, boolean invalidIndex) {
        byte[] binary = triangleBinary(textured, invalidIndex);
        return glb(baseJson(triangleBody(textured, noIndices, binary.length), binary.length), binary);
    }

    private static byte[] texturedSamplerGlb(int min, int mag, int wrapS, int wrapT) {
        byte[] binary = triangleBinary(true, false);
        String body = triangleBody(true, false, binary.length).replace("\"textures\":[{\"source\":0}]",
                "\"samplers\":[{\"minFilter\":" + min + ",\"magFilter\":" + mag
                        + ",\"wrapS\":" + wrapS + ",\"wrapT\":" + wrapT + "}],"
                        + "\"textures\":[{\"source\":0,\"sampler\":0}]");
        return glb(baseJson(body, binary.length), binary);
    }

    private static byte[] triangleBinary(boolean textured, boolean invalidIndex) {
        ByteBuffer buffer = ByteBuffer.allocate(36 + 6 + (textured ? 2 + 24 + PNG_1X1.length : 0))
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(0).putFloat(0).putFloat(0);
        buffer.putFloat(1).putFloat(0).putFloat(0);
        buffer.putFloat(0).putFloat(1).putFloat(0);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) (invalidIndex ? 3 : 2));
        if (textured) {
            buffer.putShort((short) 0);
            putTriangleUvs(buffer);
            buffer.put(PNG_1X1);
        }
        return buffer.array();
    }

    private static String triangleBody(boolean textured, boolean noIndices, int binaryLength) {
        String textureViews = textured
                ? ",{\"buffer\":0,\"byteOffset\":44,\"byteLength\":24},"
                + "{\"buffer\":0,\"byteOffset\":68,\"byteLength\":" + PNG_1X1.length + "}"
                : "";
        String textureData = textured
                ? ",\"images\":[{\"bufferView\":3,\"mimeType\":\"image/png\"}],"
                + "\"textures\":[{\"source\":0}],"
                + "\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[0.25,0.5,0.75,1],"
                + "\"baseColorTexture\":{\"index\":0}}}]"
                : "";
        String indices = noIndices ? "" : ",\"indices\":1";
        String material = textured ? ",\"material\":0" : "";
        String uvAccessor = textured
                ? ",{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"}"
                : "";
        String uvAttribute = textured ? ",\"TEXCOORD_0\":2" : "";
        return "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6}" + textureViews + "],"
                + "\"accessors\":["
                + "{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"}" + uvAccessor + "],"
                + "\"meshes\":[{\"name\":\"triangle\",\"primitives\":[{\"attributes\":{\"POSITION\":0" + uvAttribute + "}"
                + indices + material + "}]}],"
                + "\"nodes\":[{\"name\":\"mesh\",\"mesh\":0,\"translation\":[2,3,4]}],"
                + "\"scenes\":[{\"name\":\"main\",\"nodes\":[0]}],\"scene\":0" + textureData;
    }

    private static String baseJson(String body, int binaryLength) {
        return "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":"
                + binaryLength + "}]," + body + "}";
    }

    private static byte[] interleavedAttributeGlb() {
        ByteBuffer binary = ByteBuffer.allocate(3 * 32 + 6).order(ByteOrder.LITTLE_ENDIAN);
        putInterleavedVertex(binary, 0, 0, 0, 0, 0, 1, 0, 0, 255, 128, 0, 255);
        putInterleavedVertex(binary, 1, 0, 0, 0, 0, 1, 65_535, 0, 255, 128, 0, 255);
        putInterleavedVertex(binary, 0, 1, 0, 0, 0, 1, 0, 65_535, 255, 128, 0, 255);
        binary.putShort((short) 0).putShort((short) 1).putShort((short) 2);
        String body = "\"bufferViews\":["
                + "{\"buffer\":0,\"byteOffset\":0,\"byteLength\":96,\"byteStride\":32},"
                + "{\"buffer\":0,\"byteOffset\":96,\"byteLength\":6}],"
                + "\"accessors\":["
                + "{\"bufferView\":0,\"byteOffset\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":0,\"byteOffset\":12,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":0,\"byteOffset\":24,\"componentType\":5123,\"normalized\":true,\"count\":3,\"type\":\"VEC2\"},"
                + "{\"bufferView\":0,\"byteOffset\":28,\"componentType\":5121,\"normalized\":true,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,"
                + "\"TEXCOORD_0\":2,\"COLOR_0\":3},\"indices\":4}]}],"
                + "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0";
        return glb(baseJson(body, binary.array().length), binary.array());
    }

    private static byte[] triangleEmbeddedImageGlb(byte[] image, String mime) {
        ByteBuffer binary = texturedGeometryBinary(image.length);
        binary.put(image);
        String body = basicTexturedBody(68, image.length,
                "{\"bufferView\":3,\"mimeType\":\"" + mime + "\"}");
        return glb(baseJson(body, binary.array().length), binary.array());
    }

    private static byte[] triangleDataUriGlb(String uri) {
        byte[] binary = texturedGeometryBinary(0).array();
        String body = basicTexturedBody(-1, 0, "{\"uri\":\"" + uri + "\"}");
        return glb(baseJson(body, binary.length), binary);
    }

    private static String basicTexturedBody(int imageOffset, int imageLength, String imageJson) {
        String imageView = imageOffset >= 0
                ? ",{\"buffer\":0,\"byteOffset\":" + imageOffset + ",\"byteLength\":" + imageLength + "}"
                : "";
        return "\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6},"
                + "{\"buffer\":0,\"byteOffset\":44,\"byteLength\":24}" + imageView + "],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"}],"
                + "\"images\":[" + imageJson + "],\"textures\":[{\"source\":0}],"
                + "\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0}}}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"TEXCOORD_0\":2},\"indices\":1,\"material\":0}]}],"
                + "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0";
    }

    private static ByteBuffer texturedGeometryBinary(int trailingBytes) {
        ByteBuffer binary = ByteBuffer.allocate(68 + trailingBytes).order(ByteOrder.LITTLE_ENDIAN);
        binary.put(triangleBinary(false, false)).putShort((short) 0);
        putTriangleUvs(binary);
        return binary;
    }

    private static void putTriangleUvs(ByteBuffer output) {
        output.putFloat(0).putFloat(0);
        output.putFloat(1).putFloat(0);
        output.putFloat(0).putFloat(1);
    }

    private static void putInterleavedVertex(ByteBuffer output, float x, float y, float z,
                                             float nx, float ny, float nz, int u, int v,
                                             int r, int g, int b, int a) {
        output.putFloat(x).putFloat(y).putFloat(z).putFloat(nx).putFloat(ny).putFloat(nz);
        output.putShort((short) u).putShort((short) v);
        output.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
    }

    private static float attributeFloat(ModelPrimitive primitive, ModelAttributeSemantic semantic, int componentIndex) {
        return ByteBuffer.wrap(primitive.attributes().get(semantic).data())
                .order(ByteOrder.LITTLE_ENDIAN).getFloat(componentIndex * 4);
    }

    private static byte[] glb(String json, byte[] binary) {
        byte[] jsonBytes = pad(json.getBytes(StandardCharsets.UTF_8), (byte) 0x20);
        byte[] binBytes = pad(binary, (byte) 0);
        int length = 12 + 8 + jsonBytes.length + 8 + binBytes.length;
        ByteBuffer output = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        output.putInt(0x46546C67).putInt(2).putInt(length);
        output.putInt(jsonBytes.length).putInt(JSON_CHUNK).put(jsonBytes);
        output.putInt(binBytes.length).putInt(BIN_CHUNK).put(binBytes);
        return output.array();
    }

    private static byte[] pad(byte[] input, byte padding) {
        int size = (input.length + 3) & ~3;
        ByteArrayOutputStream output = new ByteArrayOutputStream(size);
        output.writeBytes(input);
        while (output.size() < size) output.write(padding);
        return output.toByteArray();
    }
}
