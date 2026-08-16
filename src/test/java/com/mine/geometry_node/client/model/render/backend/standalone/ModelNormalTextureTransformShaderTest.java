package com.mine.geometry_node.client.model.render.backend.standalone;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelNormalTextureTransformShaderTest {
    @Test
    void shaderAppliesInverseFullUvJacobianToMappedNormal() throws IOException {
        String shader = Files.readString(Path.of(
                "src/main/resources/assets/geometry_node/shaders/model/native/standalone/static_model.fsh"));
        assertTrue(shader.contains("mat2(cosine, -sine, sine, cosine) * mapped"));
        assertTrue(shader.contains("return inverseRotated / scale"));
        assertTrue(shader.contains("mapped.xy = normalMapVectorForSourceUv(mapped.xy, 2)"));
    }

    @Test
    void inverseJacobianHandlesRotationNegativeAndNonUniformScale() {
        double angle = Math.PI / 3.0;
        double[] transformed = inverseJacobian(new double[]{0.4, -0.7}, angle, -2.0, 0.25);
        double cosine = Math.cos(angle), sine = Math.sin(angle);
        double[] reconstructed = {
                cosine * (-2.0 * transformed[0]) - sine * (0.25 * transformed[1]),
                sine * (-2.0 * transformed[0]) + cosine * (0.25 * transformed[1])
        };
        assertArrayEquals(new double[]{0.4, -0.7}, reconstructed, 1.0E-12);
        assertTrue((-2.0 * 0.25) < 0.0, "negative determinant preserves mirrored UV handedness in the Jacobian");
    }

    @Test
    void inverseJacobianHandlesPureRotation() {
        double[] transformed = inverseJacobian(new double[]{1, 0}, Math.PI / 2.0, 1, 1);
        assertArrayEquals(new double[]{0, -1}, transformed, 1.0E-12);
    }

    private static double[] inverseJacobian(double[] mapped, double rotation, double scaleX, double scaleY) {
        double cosine = Math.cos(rotation), sine = Math.sin(rotation);
        return new double[]{(cosine * mapped[0] + sine * mapped[1]) / scaleX,
                (-sine * mapped[0] + cosine * mapped[1]) / scaleY};
    }
}
