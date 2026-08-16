package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.domain.*;

final class GlbBounds {
    private GlbBounds() {
    }

    static ModelBounds fromPositions(ModelVertexAttribute positions) throws com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException {
        java.nio.ByteBuffer data = java.nio.ByteBuffer.wrap(positions.data()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        Accumulator bounds = new Accumulator();
        for (int i = 0; i < positions.elementCount(); i++) {
            int offset = i * 12;
            bounds.include(data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8));
        }
        return bounds.build("POSITION");
    }

    static ModelBounds union(ModelBounds left, ModelBounds right) {
        if (left == null) return right;
        if (right == null) return left;
        return new ModelBounds(
                new ModelVector3(Math.min(left.min().x(), right.min().x()),
                        Math.min(left.min().y(), right.min().y()), Math.min(left.min().z(), right.min().z())),
                new ModelVector3(Math.max(left.max().x(), right.max().x()),
                        Math.max(left.max().y(), right.max().y()), Math.max(left.max().z(), right.max().z())));
    }

    static ModelBounds transform(ModelBounds bounds, ModelTransform transform)
            throws com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException {
        return transform(bounds, matrix(transform));
    }

    static float[] matrix(ModelTransform transform) {
        if (transform instanceof ModelTransform.Matrix matrix) return matrix.value().elements();
        ModelTransform.Trs trs = (ModelTransform.Trs) transform;
        ModelQuaternion q = trs.rotation();
        float x = q.x(), y = q.y(), z = q.z(), w = q.w();
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float xw = x * w, yw = y * w, zw = z * w;
        float sx = trs.scale().x(), sy = trs.scale().y(), sz = trs.scale().z();
        return new float[]{
                (1 - 2 * (yy + zz)) * sx, 2 * (xy + zw) * sx, 2 * (xz - yw) * sx, 0,
                2 * (xy - zw) * sy, (1 - 2 * (xx + zz)) * sy, 2 * (yz + xw) * sy, 0,
                2 * (xz + yw) * sz, 2 * (yz - xw) * sz, (1 - 2 * (xx + yy)) * sz, 0,
                trs.translation().x(), trs.translation().y(), trs.translation().z(), 1
        };
    }

    static float[] multiply(float[] left, float[] right) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float value = 0.0F;
                for (int k = 0; k < 4; k++) value += left[k * 4 + row] * right[column * 4 + k];
                result[column * 4 + row] = value;
            }
        }
        return result;
    }

    private static ModelBounds transform(ModelBounds bounds, float[] matrix)
            throws com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException {
        Accumulator result = new Accumulator();
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? bounds.min().x() : bounds.max().x();
            float y = (corner & 2) == 0 ? bounds.min().y() : bounds.max().y();
            float z = (corner & 4) == 0 ? bounds.min().z() : bounds.max().z();
            result.include(matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                    matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                    matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
        }
        return result.build("bounds");
    }

    static final class Accumulator {
        private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        void include(float x, float y, float z) throws com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw GlbFailures.attribute("POSITION", "position contains a non-finite value");
            }
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }

        ModelBounds build(String location) throws com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException {
            if (minX == Float.POSITIVE_INFINITY) throw GlbFailures.invalid(location, "bounds cannot be computed from empty geometry");
            return new ModelBounds(new ModelVector3(minX, minY, minZ), new ModelVector3(maxX, maxY, maxZ));
        }
    }
}
