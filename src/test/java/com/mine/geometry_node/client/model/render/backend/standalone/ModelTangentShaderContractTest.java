package com.mine.geometry_node.client.model.render.backend.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelTangentShaderContractTest {
    private static final Path SHADER = Path.of(
            "src/main/resources/assets/geometry_node/shaders/model/native/standalone/static_model.vsh");

    @Test
    void shaderUsesForwardLinearTransformAndGramSchmidtForTangents() throws IOException {
        String source = Files.readString(SHADER);
        assertTrue(source.contains("mat3(ModelViewMat) * mat3(skin) * Tangent.xyz"));
        assertTrue(source.contains("mat3(ModelViewMat) * Tangent.xyz"));
        assertTrue(source.contains("transformedTangent - normalView * dot(normalView, transformedTangent)"));
        assertTrue(source.contains("vec4(normalize(orthogonalTangent), Tangent.w)"));
        assertFalse(source.contains("skinNormal * Tangent.xyz"));
        assertFalse(source.contains("inverse(mat3(ModelViewMat))) * Tangent.xyz"));
    }

    @Test
    void nonUniformModelAndSkinScaleKeepTangentOrthogonalToNormal() {
        double[] normal = normalize(inverseTransposeScale(new double[]{1, 1, 0}, 2, 3, 4));
        double[] tangent = forwardScale(new double[]{1, -1, 0}, 2, 3, 4);
        tangent = normalize(subtract(tangent, multiply(normal, dot(normal, tangent))));
        assertEquals(0.0, dot(normal, tangent), 1.0E-12);

        double[] skinnedNormal = normalize(inverseTransposeScale(new double[]{0, 1, 1}, 5, 2, 0.5));
        double[] skinnedTangent = forwardScale(new double[]{0, 1, -1}, 5, 2, 0.5);
        skinnedTangent = normalize(subtract(skinnedTangent,
                multiply(skinnedNormal, dot(skinnedNormal, skinnedTangent))));
        assertEquals(0.0, dot(skinnedNormal, skinnedTangent), 1.0E-12);
    }

    private static double[] forwardScale(double[] value, double x, double y, double z) {
        return new double[]{value[0] * x, value[1] * y, value[2] * z};
    }

    private static double[] inverseTransposeScale(double[] value, double x, double y, double z) {
        return new double[]{value[0] / x, value[1] / y, value[2] / z};
    }

    private static double dot(double[] left, double[] right) {
        return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
    }

    private static double[] multiply(double[] value, double scalar) {
        return new double[]{value[0] * scalar, value[1] * scalar, value[2] * scalar};
    }

    private static double[] subtract(double[] left, double[] right) {
        return new double[]{left[0] - right[0], left[1] - right[1], left[2] - right[2]};
    }

    private static double[] normalize(double[] value) {
        double inverseLength = 1.0 / Math.sqrt(dot(value, value));
        return multiply(value, inverseLength);
    }
}
