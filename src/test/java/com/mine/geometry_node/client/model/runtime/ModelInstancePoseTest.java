package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.api.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.domain.animation.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ModelInstancePoseTest {
    @Test
    void instancesOwnIndependentLinearAndStepPoses() {
        ModelDefinition definition = definition(List.of(animation(ModelAnimationInterpolation.LINEAR)));
        ModelInstancePose first = new ModelInstancePose(definition);
        ModelInstancePose second = new ModelInstancePose(definition);
        first.select(0); first.seek(0.5F);
        second.select(0); second.seek(1.0F);
        assertEquals(1.0F, first.worldMatrix(0).m30(), 1.0E-5F);
        assertEquals(2.0F, second.worldMatrix(0).m30(), 1.0E-5F);

        ModelInstancePose step = new ModelInstancePose(definition(List.of(animation(ModelAnimationInterpolation.STEP))));
        step.select(0); step.seek(0.75F);
        assertEquals(0.0F, step.worldMatrix(0).m30(), 1.0E-5F);
    }

    @Test
    void pauseStopResetLoopReverseAndSpeedHaveDistinctSemantics() {
        ModelInstancePose pose = new ModelInstancePose(definition(List.of(animation(ModelAnimationInterpolation.LINEAR))));
        pose.select(0); pose.setSpeed(2); pose.setLooping(true); pose.play();
        pose.tick(1_000_000_000L); pose.tick(1_750_000_000L);
        assertEquals(0.5F, pose.timeSeconds(), 1.0E-5F);
        pose.pause(); pose.tick(2_000_000_000L);
        assertEquals(0.5F, pose.timeSeconds(), 1.0E-5F);
        pose.setReverse(true); pose.stop();
        assertEquals(1.0F, pose.timeSeconds(), 1.0E-5F);
        assertEquals(2.0F, pose.worldMatrix(0).m30(), 1.0E-5F);
        pose.reset();
        assertEquals(-1, pose.animationIndex());
        assertEquals(0.0F, pose.worldMatrix(0).m30(), 1.0E-5F);
    }

    @Test
    void shortestArcQuaternionInterpolationRemainsNormalized() {
        ModelAnimationSampler sampler = new ModelAnimationSampler(ModelAnimationInterpolation.LINEAR, 4,
                new float[]{0, 1}, new float[]{0, 0, 0, 1, 0, 0, 0, -1});
        ModelAnimation animation = new ModelAnimation("rotation", List.of(sampler),
                List.of(new ModelAnimationChannel(0, ModelAnimationPath.ROTATION, 0)));
        ModelInstancePose pose = new ModelInstancePose(definition(List.of(animation)));
        pose.select(0); pose.seek(0.5F);
        assertEquals(1.0F, pose.worldMatrix(0).m00(), 1.0E-5F);
    }

    @Test
    void animatedParentPropagatesToChildBoundsWithoutChangingOtherInstance() {
        ModelDefinition definition = hierarchyDefinition();
        ModelInstancePose animated = new ModelInstancePose(definition);
        ModelInstancePose resting = new ModelInstancePose(definition);
        animated.select(0); animated.seek(0.5F);
        assertEquals(2.0F, animated.worldMatrix(1).m30(), 1.0E-5F);
        assertEquals(1.0F, resting.worldMatrix(1).m30(), 1.0E-5F);
        assertEquals(2.0, animated.nodeWorldBounds(1).min().x(), 1.0E-5);
        assertEquals(1.0, resting.nodeWorldBounds(1).min().x(), 1.0E-5);
    }

    @Test
    void skinPaletteIsMeshLocalAndIndependentPerInstance() {
        ModelDefinition base = definition(List.of(new ModelAnimation("jointMove",
                List.of(new ModelAnimationSampler(ModelAnimationInterpolation.LINEAR, 3,
                        new float[]{0, 1}, new float[]{0, 0, 0, 2, 0, 0})),
                List.of(new ModelAnimationChannel(0, ModelAnimationPath.TRANSLATION, 0)))));
        ModelBounds bounds = base.bounds();
        ModelSkin skin = new ModelSkin("skin", List.of(0), 0,
                List.of(new ModelMatrix4(new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1})));
        ModelDefinition definition = new ModelDefinition(base.source(),
                List.of(new ModelScene("scene", List.of(0, 1), Optional.of(bounds))), 0,
                List.of(new ModelNode("joint", ModelTransform.Trs.IDENTITY, -1, -1, List.of(), Optional.empty()),
                        new ModelNode("mesh", ModelTransform.Trs.IDENTITY, 0, 0, List.of(), Optional.of(bounds))),
                base.meshes(), base.materials(), base.textures(), base.images(), base.animations(), List.of(skin), bounds);
        ModelInstancePose animated = new ModelInstancePose(definition);
        ModelInstancePose resting = new ModelInstancePose(definition);
        animated.select(0); animated.seek(0.5F);

        assertEquals(1.0F, animated.skinPalette(1)[12], 1.0E-5F);
        assertEquals(0.0F, resting.skinPalette(1)[12], 1.0E-5F);
        assertNotEquals(animated.revision(), resting.revision());
    }

    private static ModelAnimation animation(ModelAnimationInterpolation interpolation) {
        return new ModelAnimation("move", List.of(new ModelAnimationSampler(interpolation, 3,
                new float[]{0, 1}, new float[]{0, 0, 0, 2, 0, 0})),
                List.of(new ModelAnimationChannel(0, ModelAnimationPath.TRANSLATION, 0)));
    }

    private static ModelDefinition definition(List<ModelAnimation> animations) {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "animated",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, ModelVector3.ONE);
        ModelPrimitive primitive = new ModelPrimitive(ModelPrimitiveTopology.TRIANGLES, Map.of(
                ModelAttributeSemantic.POSITION, new ModelVertexAttribute(ModelAttributeSemantic.POSITION,
                        ModelComponentType.FLOAT32, 3, false, 3, new byte[36])),
                new ModelIndexBuffer(ModelComponentType.UINT8, 3, new byte[]{0, 1, 2}), 0, bounds);
        return new ModelDefinition(asset, List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("node", ModelTransform.Trs.IDENTITY, 0, List.of(), Optional.of(bounds))),
                List.of(new ModelMesh("mesh", List.of(primitive), bounds)), List.of(ModelMaterial.defaultMaterial()),
                List.of(), List.of(), animations, bounds);
    }

    private static ModelDefinition hierarchyDefinition() {
        ModelDefinition base = definition(List.of(animation(ModelAnimationInterpolation.LINEAR)));
        ModelBounds bounds = base.bounds();
        return new ModelDefinition(base.source(), List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("parent", ModelTransform.Trs.IDENTITY, -1, List.of(1), Optional.empty()),
                        new ModelNode("child", new ModelTransform.Trs(new ModelVector3(1, 0, 0),
                                ModelQuaternion.IDENTITY, ModelVector3.ONE), 0, List.of(), Optional.of(bounds))),
                base.meshes(), base.materials(), base.textures(), base.images(), base.animations(), bounds);
    }
}
