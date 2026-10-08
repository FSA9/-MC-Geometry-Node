package com.mine.geometry_node.core.engine.system.model.collision;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import java.io.IOException;
import java.util.*;

/** Repairs only unambiguous reversed boundary edges; closed surfaces are never snapped. */
final class ModelCollisionBoundaryWeld {
    record Boundary(int from, int to) {}

    record Result(int[] representatives, double displacement, int edges) {}

    private record Cell(long x, long y, long z) {}

    private ModelCollisionBoundaryWeld() {}

    static Result weld(
            double[] positions,
            List<Boundary> edges,
            double tolerance,
            ModelCancellation token,
            long deadline)
            throws IOException {
        int vertices = positions.length / 3;
        ModelCollisionOptimizationBudget.check(token, deadline);
        int[] parent = new int[vertices];
        Arrays.setAll(parent, i -> i);
        if (edges.isEmpty() || edges.size() > 300_000 || tolerance <= 0)
            return new Result(parent, 0, 0);
        Map<Cell, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            if ((i & 1023) == 0) ModelCollisionOptimizationBudget.check(token, deadline);
            cells.computeIfAbsent(
                            cell(positions, edges.get(i), tolerance), ignored -> new ArrayList<>())
                    .add(i);
        }
        int[] partner = new int[edges.size()];
        Arrays.fill(partner, -1);
        double squared = tolerance * tolerance;
        for (int i = 0; i < edges.size(); i++) {
            if ((i & 1023) == 0) ModelCollisionOptimizationBudget.check(token, deadline);
            Boundary edge = edges.get(i);
            Cell cell = cell(positions, edge, tolerance);
            int match = -1;
            boolean ambiguous = false;
            for (int x = -1; x <= 1; x++)
                for (int y = -1; y <= 1; y++)
                    for (int z = -1; z <= 1; z++) {
                        List<Integer> candidates =
                                cells.get(new Cell(cell.x + x, cell.y + y, cell.z + z));
                        if (candidates == null) continue;
                        if (candidates.size() > 64) {
                            ambiguous = true;
                            continue;
                        }
                        for (int candidate : candidates)
                            if (candidate != i) {
                                Boundary other = edges.get(candidate);
                                if (distance(positions, edge.from, other.to) <= squared
                                        && distance(positions, edge.to, other.from) <= squared) {
                                    if (match >= 0 && match != candidate) ambiguous = true;
                                    match = candidate;
                                }
                            }
                    }
            if (!ambiguous) partner[i] = match;
        }
        Map<Integer, int[]> groups = new HashMap<>();
        int repaired = 0;
        double maximum = 0;
        for (int i = 0; i < edges.size(); i++) {
            if ((i & 255) == 0) ModelCollisionOptimizationBudget.check(token, deadline);
            int j = partner[i];
            if (j <= i || partner[j] != i) continue;
            Boundary a = edges.get(i), b = edges.get(j);
            int a0 = root(parent, a.from),
                    a1 = root(parent, a.to),
                    b0 = root(parent, b.to),
                    b1 = root(parent, b.from);
            if (a0 == a1 || b0 == b1 || a0 == b1 || a1 == b0) continue;
            if (!within(positions, group(groups, a0), group(groups, b0), squared)
                    || !within(positions, group(groups, a1), group(groups, b1), squared)) continue;
            merge(parent, groups, a0, b0);
            merge(parent, groups, root(parent, a1), root(parent, b1));
            repaired++;
        }
        for (int i = 0; i < vertices; i++) {
            if ((i & 4095) == 0) ModelCollisionOptimizationBudget.check(token, deadline);
            parent[i] = root(parent, i);
            maximum = Math.max(maximum, Math.sqrt(distance(positions, i, parent[i])));
        }
        return new Result(parent, maximum, repaired);
    }

    private static boolean within(double[] points, int[] a, int[] b, double tolerance) {
        if (a.length + b.length > 32) return false;
        for (int i : a) for (int j : b) if (distance(points, i, j) > tolerance) return false;
        return true;
    }

    private static int[] group(Map<Integer, int[]> groups, int id) {
        return groups.computeIfAbsent(id, ignored -> new int[] {id});
    }

    private static void merge(int[] parent, Map<Integer, int[]> groups, int a, int b) {
        if (a == b) return;
        int keep = Math.min(a, b), drop = Math.max(a, b);
        int[] first = group(groups, keep),
                second = group(groups, drop),
                merged = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        groups.put(keep, merged);
        groups.remove(drop);
        parent[drop] = keep;
    }

    private static int root(int[] parents, int i) {
        while (parents[i] != i) {
            parents[i] = parents[parents[i]];
            i = parents[i];
        }
        return i;
    }

    private static double distance(double[] p, int a, int b) {
        double x = p[a * 3] - p[b * 3],
                y = p[a * 3 + 1] - p[b * 3 + 1],
                z = p[a * 3 + 2] - p[b * 3 + 2];
        return x * x + y * y + z * z;
    }

    private static Cell cell(double[] p, Boundary edge, double size) {
        return new Cell(
                (long) Math.floor((p[edge.from * 3] + p[edge.to * 3]) * .5 / size),
                (long) Math.floor((p[edge.from * 3 + 1] + p[edge.to * 3 + 1]) * .5 / size),
                (long) Math.floor((p[edge.from * 3 + 2] + p[edge.to * 3 + 2]) * .5 / size));
    }
}
