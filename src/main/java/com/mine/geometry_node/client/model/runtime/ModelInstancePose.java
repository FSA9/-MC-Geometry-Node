package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mine.geometry_node.core.engine.system.model.domain.animation.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/** Mutable render-thread-confined pose owned by exactly one model instance. */
public final class ModelInstancePose {
    private final ModelDefinition definition;
    private final Vector3f[] translations;
    private final Quaternionf[] rotations;
    private final Vector3f[] scales;
    private final Matrix4f[] worldMatrices;
    private final ModelBounds[] worldBounds;
    private final int[] parents;
    private final BitSet dirty;
    private final BitSet sceneNodes;
    private final Matrix4f[][] inverseBindMatrices;
    private final float[][] skinPalettes;
    private final long[] skinPaletteRevisions;
    private int animationIndex = -1;
    private ModelAnimationPlaybackState playbackState = ModelAnimationPlaybackState.STOPPED;
    private float timeSeconds;
    private float speed = 1.0F;
    private boolean looping;
    private boolean reverse;
    private long lastTickNanos = Long.MIN_VALUE;
    private long revision;
    private long modelBoundsRevision = Long.MIN_VALUE;
    private ModelBounds cachedModelBounds;

    public ModelInstancePose(ModelDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "definition");
        int count = definition.nodes().size();
        translations = new Vector3f[count]; rotations = new Quaternionf[count]; scales = new Vector3f[count];
        worldMatrices = new Matrix4f[count]; worldBounds = new ModelBounds[count]; parents = new int[count];
        Arrays.fill(parents, -1);
        for (int parent = 0; parent < count; parent++) {
            for (int child : definition.nodes().get(parent).children()) parents[child] = parent;
        }
        dirty = new BitSet(count);
        sceneNodes = new BitSet(count);
        for (int root : definition.scenes().get(definition.defaultScene()).rootNodes()) collectSceneNodes(root, sceneNodes);
        inverseBindMatrices = new Matrix4f[definition.skins().size()][];
        for (int skinIndex = 0; skinIndex < definition.skins().size(); skinIndex++) {
            ModelSkin skin = definition.skins().get(skinIndex);
            inverseBindMatrices[skinIndex] = new Matrix4f[skin.inverseBindMatrices().size()];
            for (int joint = 0; joint < skin.inverseBindMatrices().size(); joint++) {
                inverseBindMatrices[skinIndex][joint] =
                        new Matrix4f().set(skin.inverseBindMatrices().get(joint).elements());
            }
        }
        skinPalettes = new float[count][];
        skinPaletteRevisions = new long[count];
        Arrays.fill(skinPaletteRevisions, Long.MIN_VALUE);
        resetPose();
    }

    public List<ModelAnimation> animations() { return definition.animations(); }
    public int animationIndex() { return animationIndex; }
    public ModelAnimationPlaybackState playbackState() { return playbackState; }
    public float timeSeconds() { return timeSeconds; }
    public float speed() { return speed; }
    public boolean looping() { return looping; }
    public boolean reverse() { return reverse; }
    public float durationSeconds() { return animationIndex < 0 ? 0.0F : duration(); }
    public long revision() { return revision; }
    public boolean animated() { return animationIndex >= 0; }

    public void select(int index) {
        if (index < 0 || index >= definition.animations().size()) throw new IllegalArgumentException("animation index is out of range");
        animationIndex = index;
        playbackState = ModelAnimationPlaybackState.STOPPED;
        timeSeconds = reverse ? duration() : 0.0F;
        lastTickNanos = Long.MIN_VALUE;
        resetPose();
        sample();
    }

    public void play() {
        requireSelection();
        if (playbackState == ModelAnimationPlaybackState.STOPPED) {
            timeSeconds = reverse ? duration() : 0.0F;
            resetPose();
            sample();
        }
        playbackState = ModelAnimationPlaybackState.PLAYING;
        lastTickNanos = Long.MIN_VALUE;
    }

    public void pause() {
        if (playbackState == ModelAnimationPlaybackState.PLAYING) playbackState = ModelAnimationPlaybackState.PAUSED;
        lastTickNanos = Long.MIN_VALUE;
    }

    /** Stops playback and returns the selected animation to its directional start pose. */
    public void stop() {
        playbackState = ModelAnimationPlaybackState.STOPPED;
        timeSeconds = reverse && animationIndex >= 0 ? duration() : 0.0F;
        lastTickNanos = Long.MIN_VALUE;
        resetPose();
        if (animationIndex >= 0) sample();
    }

    /** Restores the authored rest pose and clears animation selection and all playback options. */
    public void reset() {
        animationIndex = -1;
        playbackState = ModelAnimationPlaybackState.STOPPED;
        timeSeconds = 0.0F; speed = 1.0F; looping = false; reverse = false;
        lastTickNanos = Long.MIN_VALUE;
        resetPose();
    }

    public void seek(float seconds) {
        requireSelection();
        if (!Float.isFinite(seconds)) throw new IllegalArgumentException("seek time must be finite");
        timeSeconds = Math.clamp(seconds, 0.0F, duration());
        lastTickNanos = Long.MIN_VALUE;
        restoreAnimatedTargets();
        sample();
    }

    public void setSpeed(float speed) {
        if (!Float.isFinite(speed) || speed <= 0.0F) throw new IllegalArgumentException("animation speed must be finite and positive");
        this.speed = speed;
    }
    public void setLooping(boolean looping) { this.looping = looping; }
    public void setReverse(boolean reverse) { this.reverse = reverse; lastTickNanos = Long.MIN_VALUE; }

    public void tick(long nowNanos) {
        if (playbackState != ModelAnimationPlaybackState.PLAYING) return;
        if (lastTickNanos == Long.MIN_VALUE) { lastTickNanos = nowNanos; return; }
        long elapsed = Math.max(0L, nowNanos - lastTickNanos);
        lastTickNanos = nowNanos;
        float duration = duration();
        double next = timeSeconds + elapsed * 1.0E-9D * speed * (reverse ? -1.0D : 1.0D);
        if (looping && duration > 0.0F) {
            next %= duration;
            if (next < 0.0D) next += duration;
        } else if (next <= 0.0D || next >= duration) {
            next = Math.clamp(next, 0.0D, duration);
            playbackState = ModelAnimationPlaybackState.PAUSED;
        }
        timeSeconds = (float) next;
        restoreAnimatedTargets();
        sample();
    }

    public Matrix4f worldMatrix(int nodeIndex) {
        resolve(nodeIndex);
        return new Matrix4f(worldMatrices[nodeIndex]);
    }

    public ModelBounds nodeWorldBounds(int nodeIndex) {
        resolve(nodeIndex);
        return worldBounds[nodeIndex];
    }

    public ModelBounds modelBounds() {
        if (modelBoundsRevision == revision) return cachedModelBounds;
        ModelBounds result = null;
        for (int node = sceneNodes.nextSetBit(0); node >= 0; node = sceneNodes.nextSetBit(node + 1)) {
            ModelBounds bounds = nodeWorldBounds(node);
            if (bounds == null) continue;
            result = result == null ? bounds : union(result, bounds);
        }
        cachedModelBounds = result == null ? definition.bounds() : result;
        modelBoundsRevision = revision;
        return cachedModelBounds;
    }

    /** Returns one fixed-size std140 palette in mesh-node local space for the selected skin. */
    public float[] skinPalette(int meshNodeIndex) {
        ModelNode meshNode = definition.nodes().get(meshNodeIndex);
        if (meshNode.skinIndex() < 0) throw new IllegalArgumentException("node does not reference a skin");
        ModelSkin skin = definition.skins().get(meshNode.skinIndex());
        if (skinPaletteRevisions[meshNodeIndex] == revision) return skinPalettes[meshNodeIndex];
        Matrix4f inverseMesh = worldMatrix(meshNodeIndex).invert();
        float[] palette = new float[ModelSkin.MAX_JOINTS * 16 * 2];
        for (int index = 0; index < ModelSkin.MAX_JOINTS; index++) {
            Matrix4f matrix = index < skin.joints().size()
                    ? new Matrix4f(inverseMesh).mul(worldMatrix(skin.joints().get(index)))
                    .mul(inverseBindMatrices[meshNode.skinIndex()][index])
                    : new Matrix4f();
            matrix.get(palette, index * 16);
            float determinant = matrix.determinant3x3();
            if (!Float.isFinite(determinant) || Math.abs(determinant) <= 1.0E-8F) {
                throw new IllegalStateException("skin joint matrix is singular");
            }
            new Matrix4f().set3x3(new Matrix3f(matrix).invert().transpose())
                    .get(palette, ModelSkin.MAX_JOINTS * 16 + index * 16);
        }
        skinPalettes[meshNodeIndex] = palette;
        skinPaletteRevisions[meshNodeIndex] = revision;
        return palette;
    }

    private void collectSceneNodes(int node, BitSet output) {
        if (output.get(node)) return;
        output.set(node);
        for (int child : definition.nodes().get(node).children()) collectSceneNodes(child, output);
    }

    private float duration() { return definition.animations().get(animationIndex).durationSeconds(); }
    private void requireSelection() { if (animationIndex < 0) throw new IllegalStateException("no animation is selected"); }

    private void resetPose() {
        for (int i = 0; i < definition.nodes().size(); i++) {
            ModelTransform transform = definition.nodes().get(i).transform();
            if (transform instanceof ModelTransform.Trs trs) {
                translations[i] = vector(trs.translation()); rotations[i] = quaternion(trs.rotation()); scales[i] = vector(trs.scale());
            } else {
                translations[i] = null; rotations[i] = null; scales[i] = null;
            }
        }
        dirty.set(0, definition.nodes().size());
        revision++;
    }

    private void sample() {
        ModelAnimation animation = definition.animations().get(animationIndex);
        for (ModelAnimationChannel channel : animation.channels()) {
            ModelAnimationSampler sampler = animation.samplers().get(channel.samplerIndex());
            int upper = upperBound(sampler, timeSeconds);
            int left = Math.max(0, upper - 1);
            int right = Math.min(sampler.keyCount() - 1, upper);
            float amount = 0.0F;
            if (right != left && sampler.interpolation() == ModelAnimationInterpolation.LINEAR) {
                amount = (timeSeconds - sampler.keyTime(left)) / (sampler.keyTime(right) - sampler.keyTime(left));
                amount = Math.clamp(amount, 0.0F, 1.0F);
            }
            int node = channel.nodeIndex();
            switch (channel.path()) {
                case TRANSLATION -> translations[node].set(lerp3(sampler, left, right, amount));
                case SCALE -> scales[node].set(lerp3(sampler, left, right, amount));
                case ROTATION -> rotations[node].set(sampleRotation(sampler, left, right, amount));
                case WEIGHTS -> throw new IllegalStateException("validated M9 animations cannot contain weights");
            }
            markSubtreeDirty(node);
        }
    }

    private void restoreAnimatedTargets() {
        ModelAnimation animation = definition.animations().get(animationIndex);
        for (ModelAnimationChannel channel : animation.channels()) {
            int node = channel.nodeIndex();
            ModelTransform.Trs rest = (ModelTransform.Trs) definition.nodes().get(node).transform();
            switch (channel.path()) {
                case TRANSLATION -> translations[node].set(vector(rest.translation()));
                case ROTATION -> rotations[node].set(quaternion(rest.rotation()));
                case SCALE -> scales[node].set(vector(rest.scale()));
                case WEIGHTS -> throw new IllegalStateException("validated M9 animations cannot contain weights");
            }
            markSubtreeDirty(node);
        }
        revision++;
    }

    private void resolve(int node) {
        if (!dirty.get(node)) return;
        int parent = parents[node];
        Matrix4f local = local(node);
        worldMatrices[node] = parent < 0 ? local : new Matrix4f(worldMatrix(parent)).mul(local);
        ModelNode definitionNode = definition.nodes().get(node);
        worldBounds[node] = definitionNode.meshIndex() < 0 ? null
                : transformBounds(definition.meshes().get(definitionNode.meshIndex()).bounds(), worldMatrices[node]);
        dirty.clear(node);
    }

    private Matrix4f local(int node) {
        ModelTransform transform = definition.nodes().get(node).transform();
        if (transform instanceof ModelTransform.Matrix matrix) return new Matrix4f().set(matrix.value().elements());
        return new Matrix4f().translation(translations[node]).rotate(rotations[node]).scale(scales[node]);
    }

    private void markSubtreeDirty(int node) {
        dirty.set(node);
        for (int child : definition.nodes().get(node).children()) markSubtreeDirty(child);
    }

    private static int upperBound(ModelAnimationSampler sampler, float time) {
        int low = 0, high = sampler.keyCount();
        while (low < high) { int mid = (low + high) >>> 1; if (sampler.keyTime(mid) <= time) low = mid + 1; else high = mid; }
        return low;
    }

    private static Vector3f lerp3(ModelAnimationSampler sampler, int left, int right, float amount) {
        return new Vector3f(sampler.outputValue(left, 0), sampler.outputValue(left, 1), sampler.outputValue(left, 2))
                .lerp(new Vector3f(sampler.outputValue(right, 0), sampler.outputValue(right, 1), sampler.outputValue(right, 2)), amount);
    }

    private static Quaternionf sampleRotation(ModelAnimationSampler sampler, int left, int right, float amount) {
        Quaternionf a = new Quaternionf(sampler.outputValue(left, 0), sampler.outputValue(left, 1),
                sampler.outputValue(left, 2), sampler.outputValue(left, 3));
        Quaternionf b = new Quaternionf(sampler.outputValue(right, 0), sampler.outputValue(right, 1),
                sampler.outputValue(right, 2), sampler.outputValue(right, 3));
        if (a.lengthSquared() < 1.0E-12F || b.lengthSquared() < 1.0E-12F) {
            throw new IllegalStateException("validated animation contains a zero-length quaternion");
        }
        a.normalize(); b.normalize();
        if (a.dot(b) < 0.0F) b.set(-b.x, -b.y, -b.z, -b.w);
        return amount == 0.0F ? a : a.slerp(b, amount).normalize();
    }

    private static Vector3f vector(ModelVector3 value) { return new Vector3f((float) value.x(), (float) value.y(), (float) value.z()); }
    private static Quaternionf quaternion(ModelQuaternion value) { return new Quaternionf((float) value.x(), (float) value.y(), (float) value.z(), (float) value.w()); }

    private static ModelBounds transformBounds(ModelBounds bounds, Matrix4f matrix) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY), max = new Vector3f(Float.NEGATIVE_INFINITY);
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = matrix.transformPosition(new Vector3f(
                    (float) ((corner & 1) == 0 ? bounds.min().x() : bounds.max().x()),
                    (float) ((corner & 2) == 0 ? bounds.min().y() : bounds.max().y()),
                    (float) ((corner & 4) == 0 ? bounds.min().z() : bounds.max().z())));
            min.min(point); max.max(point);
        }
        return new ModelBounds(new ModelVector3(min.x, min.y, min.z), new ModelVector3(max.x, max.y, max.z));
    }

    private static ModelBounds union(ModelBounds left, ModelBounds right) {
        return new ModelBounds(new ModelVector3(
                Math.min(left.min().x(), right.min().x()), Math.min(left.min().y(), right.min().y()), Math.min(left.min().z(), right.min().z())),
                new ModelVector3(Math.max(left.max().x(), right.max().x()), Math.max(left.max().y(), right.max().y()), Math.max(left.max().z(), right.max().z())));
    }
}
