package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class StaticModelRenderMetadata {
    private final List<Matrix4f> nodeWorldTransforms;
    private final List<StaticModelMaterial> materials;
    private final ModelBounds bounds;
    private final List<ModelBounds> nodeWorldBounds;

    private StaticModelRenderMetadata(List<Matrix4f> nodeWorldTransforms, List<StaticModelMaterial> materials,
                                      ModelBounds bounds, List<ModelBounds> nodeWorldBounds) {
        List<Matrix4f> transformCopy = new ArrayList<>(nodeWorldTransforms.size());
        for (Matrix4f transform : nodeWorldTransforms) transformCopy.add(transform == null ? null : new Matrix4f(transform));
        this.nodeWorldTransforms = java.util.Collections.unmodifiableList(transformCopy);
        this.materials = List.copyOf(materials);
        this.bounds = bounds;
        this.nodeWorldBounds = java.util.Collections.unmodifiableList(new ArrayList<>(nodeWorldBounds));
    }

    public static StaticModelRenderMetadata from(ModelDefinition definition) {
        List<Matrix4f> world = new ArrayList<>(definition.nodes().size());
        for (int index = 0; index < definition.nodes().size(); index++) world.add(null);
        for (int root : definition.scenes().get(definition.defaultScene()).rootNodes()) {
            resolve(definition, root, new Matrix4f(), world);
        }
        List<StaticModelMaterial> materials = new ArrayList<>(definition.materials().size());
        for (ModelMaterial material : definition.materials()) {
            materials.add(new StaticModelMaterial(material.red(), material.green(), material.blue(), material.alpha(),
                    texture(definition, material.baseColorTexture()), material.alphaMode(), material.alphaCutoff(),
                    material.doubleSided(), material.emissiveRed(), material.emissiveGreen(), material.emissiveBlue(),
                    texture(definition, material.emissiveTexture())));
        }
        List<ModelBounds> nodeBounds = new ArrayList<>(definition.nodes().size());
        for (int index = 0; index < definition.nodes().size(); index++) {
            Matrix4f transform = world.get(index);
            ModelNode node = definition.nodes().get(index);
            nodeBounds.add(transform == null || node.meshIndex() < 0 ? null
                    : transformBounds(definition.meshes().get(node.meshIndex()).bounds(), transform));
        }
        return new StaticModelRenderMetadata(world, materials, definition.bounds(), nodeBounds);
    }

    public boolean nodeVisible(int index) { return nodeWorldTransforms.get(index) != null; }
    public int nodeCount() { return nodeWorldTransforms.size(); }
    public boolean nodeDrawable(int index) {
        return nodeVisible(index) && nodeWorldBounds.get(index) != null;
    }
    public Matrix4f nodeWorldTransform(int index) {
        Matrix4f transform = nodeWorldTransforms.get(index);
        if (transform == null) throw new IllegalArgumentException("node is outside the selected scene");
        return new Matrix4f(transform);
    }
    public StaticModelMaterial material(int index) { return materials.get(index); }
    public ModelBounds bounds() { return bounds; }
    public ModelBounds nodeWorldBounds(int index) { return nodeWorldBounds.get(index); }

    private static StaticModelTexture texture(ModelDefinition definition, ModelTextureInfo info) {
        if (info.textureIndex() < 0) return StaticModelTexture.absent();
        ModelTexture texture = definition.textures().get(info.textureIndex());
        return new StaticModelTexture(texture.imageIndex(), texture.sampler(), info.transform());
    }

    private static ModelBounds transformBounds(ModelBounds bounds, Matrix4f transform) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            ModelVector3 min = bounds.min(), max = bounds.max();
            org.joml.Vector3f point = transform.transformPosition(new org.joml.Vector3f(
                    (float) ((corner & 1) == 0 ? min.x() : max.x()),
                    (float) ((corner & 2) == 0 ? min.y() : max.y()),
                    (float) ((corner & 4) == 0 ? min.z() : max.z())));
            minX = java.lang.Math.min(minX, point.x); minY = java.lang.Math.min(minY, point.y); minZ = java.lang.Math.min(minZ, point.z);
            maxX = java.lang.Math.max(maxX, point.x); maxY = java.lang.Math.max(maxY, point.y); maxZ = java.lang.Math.max(maxZ, point.z);
        }
        return new ModelBounds(new ModelVector3((float) minX, (float) minY, (float) minZ),
                new ModelVector3((float) maxX, (float) maxY, (float) maxZ));
    }

    private static void resolve(ModelDefinition definition, int index, Matrix4f parent, List<Matrix4f> output) {
        if (output.get(index) != null) return;
        Matrix4f world = new Matrix4f(parent).mul(local(definition.nodes().get(index).transform()));
        output.set(index, world);
        for (int child : definition.nodes().get(index).children()) resolve(definition, child, world, output);
    }

    private static Matrix4f local(ModelTransform transform) {
        if (transform instanceof ModelTransform.Matrix matrix) return new Matrix4f().set(matrix.value().elements());
        ModelTransform.Trs trs = (ModelTransform.Trs) transform;
        ModelVector3 t = trs.translation(); ModelQuaternion r = trs.rotation(); ModelVector3 s = trs.scale();
        return new Matrix4f().translation((float) t.x(), (float) t.y(), (float) t.z())
                .rotate((float) r.x(), (float) r.y(), (float) r.z(), (float) r.w())
                .scale((float) s.x(), (float) s.y(), (float) s.z());
    }
}
