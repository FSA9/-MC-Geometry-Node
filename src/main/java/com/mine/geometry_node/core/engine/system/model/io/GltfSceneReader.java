package com.mine.geometry_node.core.engine.system.model.io;

import com.google.gson.*;
import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import org.joml.*;
import java.io.IOException;
import java.util.*;
import static com.mine.geometry_node.core.engine.system.model.io.GltfJson.*;

/** Preserves source node indices while validating a forest independently of node names. */
final class GltfSceneReader {
    record Result(List<GltfAsset.Node> nodes, List<GltfAsset.Scene> scenes, int defaultScene) { }
    static Result read(JsonObject doc, int meshCount, GltfImportBudget budget, ModelCancellation token, List<String> notes) throws IOException {
        JsonArray source = array(doc, "nodes", ""); int count = source.size(); budget.reserve((long)count * 192, "/nodes");
        int[] parents = new int[count]; Arrays.fill(parents, -1); ArrayList<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < count; i++) children.add(new ArrayList<>());
        for (int i = 0; i < count; i++) {
            token.check(); String path = "/nodes/" + i; JsonObject node = at(source, i, "/nodes");
            JsonArray cs = array(node, "children", path);
            for (int j = 0; j < cs.size(); j++) {
                int child = index(cs.get(j), path + "/children/" + j);
                if (child >= count || child == i || parents[child] != -1) throw invalid(path + "/children/" + j, "child is outside the node table, self-referencing, or has multiple parents");
                parents[child] = i; children.get(i).add(child);
            }
        }
        ArrayDeque<Integer> pending = new ArrayDeque<>(); ArrayList<Integer> roots = new ArrayList<>();
        for (int i = 0; i < count; i++) if (parents[i] < 0) { pending.add(i); roots.add(i); }
        int reached = 0;
        while (!pending.isEmpty()) { token.check(); int i = pending.removeFirst(); reached++; pending.addAll(children.get(i)); }
        if (reached != count) throw invalid("/nodes", "node hierarchy contains a cycle");
        ArrayList<GltfAsset.Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            token.check(); JsonObject node = at(source, i, "/nodes"); String path = "/nodes/" + i;
            int mesh = index(node, "mesh", -1, path); if (mesh >= meshCount) throw invalid(path + "/mesh", "mesh index out of range");
            if (node.has("skin")) notes.add(path + ": skin ignored by STATIC_BASIC; bind/default geometry shown");
            if (node.has("weights")) notes.add(path + ": morph weights ignored by STATIC_BASIC");
            if (node.has("camera")) notes.add(path + ": glTF camera retained only in source; world camera remains active");
            nodes.add(new GltfAsset.Node(i, parents[i], string(node, "name", "", path), transform(node, path, notes), mesh, children.get(i)));
        }
        JsonArray sceneSource = array(doc, "scenes", ""); ArrayList<GltfAsset.Scene> scenes = new ArrayList<>();
        for (int i = 0; i < sceneSource.size(); i++) {
            token.check(); String path = "/scenes/" + i; JsonObject scene = at(sceneSource, i, "/scenes");
            JsonArray sourceRoots = array(scene, "nodes", path); ArrayList<Integer> sceneRoots = new ArrayList<>(); HashSet<Integer> seen = new HashSet<>();
            for (int j = 0; j < sourceRoots.size(); j++) {
                int root = index(sourceRoots.get(j), path + "/nodes/" + j);
                if (root >= count || parents[root] != -1 || !seen.add(root)) throw invalid(path + "/nodes/" + j, "scene must reference distinct hierarchy roots");
                sceneRoots.add(root);
            }
            budget.reserve((long)sceneRoots.size() * 4, path); scenes.add(new GltfAsset.Scene(string(scene, "name", "", path), sceneRoots));
        }
        int selected = index(doc, "scene", -1, "");
        if (selected >= scenes.size()) throw invalid("/scene", "default scene index out of range");
        if (scenes.isEmpty() && !nodes.isEmpty()) {
            scenes.add(new GltfAsset.Scene("implicit", roots)); selected = 0;
            notes.add("/scenes: absent; STATIC_BASIC exposes all hierarchy roots as an implicit selectable scene");
        } else if (selected < 0 && !scenes.isEmpty()) {
            selected = 0; notes.add("/scene: no glTF default; STATIC_BASIC selects scene 0 unless explicitly overridden");
        }
        return new Result(List.copyOf(nodes), List.copyOf(scenes), selected);
    }
    private static Matrix4f transform(JsonObject node, String path, List<String> notes) throws ModelProblem {
        Matrix4f matrix;
        if (node.has("matrix")) {
            if (node.has("translation") || node.has("rotation") || node.has("scale")) throw invalid(path, "matrix and TRS are mutually exclusive");
            matrix = new Matrix4f().set(vector(node, "matrix", new float[16], path));
            if (matrix.m03() != 0 || matrix.m13() != 0 || matrix.m23() != 0 || matrix.m33() != 1)
                throw invalid(path + "/matrix", "node matrix must be affine");
        } else {
            float[] t = vector(node, "translation", new float[]{0,0,0}, path), s = vector(node, "scale", new float[]{1,1,1}, path);
            float[] r = vector(node, "rotation", new float[]{0,0,0,1}, path); Quaternionf rotation = new Quaternionf(r[0],r[1],r[2],r[3]);
            float squared = rotation.lengthSquared(); if (!Float.isFinite(squared) || squared < 1e-20f) throw invalid(path + "/rotation", "nonzero finite quaternion required");
            if (java.lang.Math.abs(squared - 1) > 1e-5f) notes.add(path + "/rotation: non-unit quaternion normalized");
            matrix = new Matrix4f().translation(t[0],t[1],t[2]).rotate(rotation.normalize()).scale(s[0],s[1],s[2]);
        }
        if (!matrix.isFinite() || !Float.isFinite(matrix.determinant3x3()) || matrix.determinant3x3() == 0)
            throw invalid(path, "STATIC_BASIC requires finite nonsingular node transforms for normal transformation");
        return matrix;
    }
}
