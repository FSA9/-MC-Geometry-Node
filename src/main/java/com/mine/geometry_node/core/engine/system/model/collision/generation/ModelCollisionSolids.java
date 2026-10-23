package com.mine.geometry_node.core.engine.system.model.collision.generation;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;

import com.github.quickhull3d.QuickHull3D;
import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Closed convex volume extraction and decomposition. Unresolved surfaces fail explicitly. */
final class ModelCollisionSolids {
    record Input(double[] positions, int[] indices) {
        Input(float[] positions, int[] indices) {
            this(doubles(positions), indices);
        }

        private static double[] doubles(float[] values) {
            double[] out = new double[values.length];
            for (int i = 0; i < values.length; i++) out[i] = values[i];
            return out;
        }
    }

    record Extracted(
            List<ModelCollisionMesh.Part> solids,
            int[] remaining,
            int weldedEdges,
            List<String> issues) {}

    private record Position(long x, long y, long z) {}

    private record Triangle(int a, int b, int c) {}

    private record TriangleUse(int offset, int orientation) {}

    private record Sample(long x, long y, long z) {}

    private ModelCollisionSolids() {}

    static Extracted extractVolume(
            Input input, ModelCollisionSettings settings, ModelCancellation token, long deadline)
            throws IOException {
        return extractVolume(input, settings, token, deadline, false);
    }

    static Extracted extractVolume(
            Input input,
            ModelCollisionSettings settings,
            ModelCancellation token,
            long deadline,
            boolean allowPartial)
            throws IOException {
        int[] indices = input.indices;
        double[] positions = input.positions;
        double fittingError = settings.fitError() * 0.5;
        int triangles = indices.length / 3;
        if (triangles > ModelCollisionBuilder.MAX_TRIANGLES)
            throw new IOException("collision source triangle budget exceeded");
        int[] weld = new int[positions.length / 3], parent = new int[weld.length];
        Map<Position, Integer> unique = new HashMap<>();
        int count = 0;
        for (int i = 0; i < weld.length; i++) {
            if ((i & 4095) == 0) check(token, deadline);
            Position key =
                    new Position(
                            Double.doubleToLongBits(positions[i * 3] + 0d),
                            Double.doubleToLongBits(positions[i * 3 + 1] + 0d),
                            Double.doubleToLongBits(positions[i * 3 + 2] + 0d));
            Integer vertex = unique.get(key);
            if (vertex == null) {
                vertex = count++;
                unique.put(key, vertex);
                parent[vertex] = vertex;
            }
            weld[i] = vertex;
        }
        unique.clear();
        Map<Triangle, TriangleUse> seen = new HashMap<>();
        int[] clean = new int[indices.length];
        int used = 0;
        for (int t = 0; t < indices.length; t += 3) {
            if ((t & 4095) == 0) check(token, deadline);
            int a = weld[indices[t]], b = weld[indices[t + 1]], c = weld[indices[t + 2]];
            if (a == b || b == c || a == c) continue;
            int low = Math.min(a, Math.min(b, c)), high = Math.max(a, Math.max(b, c));
            Triangle key = new Triangle(low, a + b + c - low - high, high);
            int orientation = (a < b && b < c || b < c && c < a || c < a && a < b) ? 1 : -1;
            TriangleUse previous = seen.putIfAbsent(key, new TriangleUse(used, orientation));
            if (previous != null) {
                if (previous.orientation != orientation) clean[previous.offset] = -1;
                continue;
            }
            clean[used++] = indices[t];
            clean[used++] = indices[t + 1];
            clean[used++] = indices[t + 2];
        }
        seen.clear();
        int cleaned = 0;
        for (int t = 0; t < used; t += 3)
            if (clean[t] >= 0) {
                clean[cleaned++] = clean[t];
                clean[cleaned++] = clean[t + 1];
                clean[cleaned++] = clean[t + 2];
            }
        indices = Arrays.copyOf(clean, cleaned);
        triangles = indices.length / 3;
        for (int t = 0; t < indices.length; t += 3) {
            if ((t & 4095) == 0) check(token, deadline);
            union(parent, weld[indices[t]], weld[indices[t + 1]]);
            union(parent, weld[indices[t]], weld[indices[t + 2]]);
        }
        Map<Long, Edge> edges = new HashMap<>();
        for (int t = 0; t < indices.length; t += 3) {
            if ((t & 4095) == 0) check(token, deadline);
            for (int j = 0; j < 3; j++) {
                int a = weld[indices[t + j]], b = weld[indices[t + (j + 1) % 3]];
                long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                Edge edge = edges.computeIfAbsent(key, ignored -> new Edge());
                edge.count++;
                edge.direction += a < b ? 1 : -1;
            }
        }
        double[] representatives = new double[count * 3];
        for (int i = 0; i < weld.length; i++) {
            int w = weld[i] * 3;
            representatives[w] = positions[i * 3];
            representatives[w + 1] = positions[i * 3 + 1];
            representatives[w + 2] = positions[i * 3 + 2];
        }
        List<ModelCollisionBoundaryWeld.Boundary> boundary = new ArrayList<>();
        for (var entry : edges.entrySet())
            if (entry.getValue().count == 1) {
                int a = (int) (entry.getKey() >>> 32), b = (int) (long) entry.getKey();
                boundary.add(
                        entry.getValue().direction > 0
                                ? new ModelCollisionBoundaryWeld.Boundary(a, b)
                                : new ModelCollisionBoundaryWeld.Boundary(b, a));
            }
        var stitched =
                ModelCollisionBoundaryWeld.weld(
                        representatives,
                        boundary,
                        Math.min(1e-5, settings.fitError() * .01),
                        token,
                        deadline);
        int weldedEdges = 0;
        if (stitched.edges() > 0
                && validWeld(
                        representatives,
                        weld,
                        indices,
                        stitched.representatives(),
                        token,
                        deadline)) {
            positions = positions.clone();
            weldedEdges = stitched.edges();
            fittingError -= stitched.displacement();
            for (int i = 0; i < weld.length; i++) {
                if ((i & 4095) == 0) check(token, deadline);
                weld[i] = stitched.representatives()[weld[i]];
                int w = weld[i] * 3;
                positions[i * 3] = representatives[w];
                positions[i * 3 + 1] = representatives[w + 1];
                positions[i * 3 + 2] = representatives[w + 2];
            }
            for (int i = 0; i < count; i++) parent[i] = i;
            edges.clear();
            for (int t = 0; t < indices.length; t += 3) {
                if ((t & 4095) == 0) check(token, deadline);
                union(parent, weld[indices[t]], weld[indices[t + 1]]);
                union(parent, weld[indices[t]], weld[indices[t + 2]]);
                for (int j = 0; j < 3; j++) {
                    int a = weld[indices[t + j]], b = weld[indices[t + (j + 1) % 3]];
                    long key = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                    Edge edge = edges.computeIfAbsent(key, ignored -> new Edge());
                    edge.count++;
                    edge.direction += a < b ? 1 : -1;
                }
            }
        }
        boolean[] open = new boolean[count], balanced = new boolean[count];
        Arrays.fill(balanced, true);
        int[] boundaryEdges = new int[count],
                nonmanifoldEdges = new int[count],
                inconsistentEdges = new int[count];
        for (var entry : edges.entrySet())
            if (entry.getValue().count != 2 || entry.getValue().direction != 0) {
                int component = root(parent, (int) (entry.getKey() >>> 32));
                if (entry.getValue().count == 1) boundaryEdges[component]++;
                else if (entry.getValue().count > 2) nonmanifoldEdges[component]++;
                else inconsistentEdges[component]++;
                open[root(parent, (int) (entry.getKey() >>> 32))] = true;
                if (entry.getValue().direction != 0)
                    balanced[root(parent, (int) (entry.getKey() >>> 32))] = false;
            }
        edges.clear();
        int[] head = new int[count], next = new int[triangles], sizes = new int[count];
        Arrays.fill(head, -1);
        for (int t = 0; t < triangles; t++) {
            int r = root(parent, weld[indices[t * 3]]);
            next[t] = head[r];
            head[r] = t;
            sizes[r]++;
        }
        // Nested disconnected shells may describe a hollow wall or room. Treating
        // either shell as an independent solid would silently fill that cavity.
        double[] componentVolume = new double[count];
        for (int t = 0; t < triangles; t++) {
            int r = root(parent, weld[indices[t * 3]]), o = indices[head[r] * 3] * 3;
            Vector3d origin = new Vector3d(positions[o], positions[o + 1], positions[o + 2]);
            Vector3d[] p = new Vector3d[3];
            for (int j = 0; j < 3; j++) {
                int v = indices[t * 3 + j] * 3;
                p[j] = new Vector3d(positions[v], positions[v + 1], positions[v + 2]).sub(origin);
            }
            componentVolume[r] += p[0].dot(p[1].cross(p[2]));
        }
        Map<Integer, Bounds> bounds = new HashMap<>();
        for (int i = 0; i < indices.length; i++) {
            if ((i & 4095) == 0) check(token, deadline);
            int source = indices[i], r = root(parent, weld[source]);
            bounds.computeIfAbsent(r, ignored -> new Bounds())
                    .include(
                            positions[source * 3],
                            positions[source * 3 + 1],
                            positions[source * 3 + 2]);
        }
        List<Map.Entry<Integer, Bounds>> bounded = new ArrayList<>(bounds.entrySet());
        if (bounded.size() <= 10_000)
            for (int i = 0; i < bounded.size(); i++) {
                if ((i & 31) == 0) check(token, deadline);
                for (int j = i + 1; j < bounded.size(); j++)
                    if (componentVolume[bounded.get(j).getKey()] < 0
                                    && bounded.get(i).getValue().contains(bounded.get(j).getValue())
                                    && !open[bounded.get(i).getKey()]
                                    && containsComponent(
                                            bounded.get(i).getKey(),
                                            bounded.get(j).getKey(),
                                            positions,
                                            indices,
                                            head,
                                            next,
                                            token,
                                            deadline)
                            || componentVolume[bounded.get(i).getKey()] < 0
                                    && bounded.get(j).getValue().contains(bounded.get(i).getValue())
                                    && !open[bounded.get(j).getKey()]
                                    && containsComponent(
                                            bounded.get(j).getKey(),
                                            bounded.get(i).getKey(),
                                            positions,
                                            indices,
                                            head,
                                            next,
                                            token,
                                            deadline)) {
                        union(parent, bounded.get(i).getKey(), bounded.get(j).getKey());
                    }
            }
        else throw new IOException("collision volume component topology exceeds grouping budget");
        boolean[] cavityGroup = new boolean[count];
        for (int i = 0; i < count; i++)
            if (componentVolume[i] < 0) cavityGroup[root(parent, i)] = true;
        {
            boolean[] groupedOpen = new boolean[count];
            for (int i = 0; i < count; i++) if (open[i]) groupedOpen[root(parent, i)] = true;
            boolean[] groupedBalanced = new boolean[count];
            Arrays.fill(groupedBalanced, true);
            for (int i = 0; i < count; i++)
                if (!balanced[i]) groupedBalanced[root(parent, i)] = false;
            balanced = groupedBalanced;
            open = groupedOpen;
            Arrays.fill(head, -1);
            Arrays.fill(sizes, 0);
            for (int t = 0; t < triangles; t++) {
                int r = root(parent, weld[indices[t * 3]]);
                next[t] = head[r];
                head[r] = t;
                sizes[r]++;
            }
        }
        boolean[] consumed = new boolean[triangles];
        List<ModelCollisionMesh.Part> solids = new ArrayList<>();
        int retained = indices.length;
        List<String> issues = new ArrayList<>();
        List<Integer> order = new ArrayList<>();
        for (int r = 0; r < count; r++) if (head[r] >= 0) order.add(r);
        order.sort(java.util.Comparator.comparingInt(r -> sizes[r]));
        long objectDeadline = deadline;
        for (int r : order) {
            token.check();
            if (allowPartial && System.nanoTime() >= objectDeadline) {
                if (issues.size() < 8)
                    issues.add("object time budget exhausted; remaining components skipped");
                break;
            }
            deadline = objectDeadline;
            int previousParts = solids.size();
            try {
                check(token, deadline);
                if (sizes[r] < 4) {
                    if (issues.size() < 8)
                        issues.add("component has fewer than four triangles: " + sizes[r]);
                    continue;
                }
                if (sizes[r] > 20_000)
                    throw new IOException(
                            "closed collision component exceeds 20000 triangle decomposition"
                                + " budget: "
                                    + sizes[r]);
                Map<Integer, Integer> compact = new HashMap<>();
                List<Vector3d> points = new ArrayList<>();
                int[] local = new int[sizes[r] * 3];
                int k = 0;
                double signedVolume = 0;
                int originIndex = indices[head[r] * 3] * 3;
                Vector3d origin =
                        new Vector3d(
                                positions[originIndex],
                                positions[originIndex + 1],
                                positions[originIndex + 2]);
                for (int t = head[r]; t >= 0; t = next[t]) {
                    Vector3d[] triangle = new Vector3d[3];
                    for (int j = 0; j < 3; j++) {
                        int v = indices[t * 3 + j] * 3;
                        triangle[j] =
                                new Vector3d(positions[v], positions[v + 1], positions[v + 2])
                                        .sub(origin);
                    }
                    signedVolume += triangle[0].dot(triangle[1].cross(triangle[2]));
                }
                if (signedVolume <= 1e-12) {
                    throw new IOException(
                            "collision component has no outward enclosed volume (triangles="
                                    + sizes[r]
                                    + ")");
                }
                for (int t = head[r]; t >= 0; t = next[t])
                    for (int j = 0; j < 3; j++) {
                        int source = indices[t * 3 + j], w = weld[source];
                        Integer id = compact.get(w);
                        if (id == null) {
                            id = points.size();
                            compact.put(w, id);
                            Vector3d point =
                                    new Vector3d(
                                            positions[source * 3],
                                            positions[source * 3 + 1],
                                            positions[source * 3 + 2]);
                            if (!point.isFinite() || point.lengthSquared() > 1e16)
                                throw new IOException(
                                        "collision geometry exceeds coordinate budget");
                            points.add(point);
                        }
                        local[k++] = id;
                    }
                double remainingError = fittingError;
                // Already-convex low-complexity components need no native simplification or BSP.
                // Library initialization is covered by the global budget, not a tiny component
                // slice.
                if (!open[r]
                        && !cavityGroup[r]
                        && points.size() <= 128
                        && convexSurface(points, local, token, deadline)) {
                    var exact = closedHull(points, local, remainingError, token, deadline);
                    if (exact != null) {
                        solids.add(exact);
                        for (int t = head[r]; t >= 0; t = next[t]) {
                            consumed[t] = true;
                            retained -= 3;
                        }
                        continue;
                    }
                }
                deadline =
                        allowPartial
                                ? Math.min(objectDeadline, System.nanoTime() + 250_000_000L)
                                : objectDeadline;
                if (open[r] && !cavityGroup[r]) {
                    int[] repaired =
                            ModelCollisionVolumeRepair.repair(
                                    points,
                                    local,
                                    settings.mergeScale(),
                                    remainingError * .25,
                                    token,
                                    deadline);
                    if (repaired != local) {
                        local = repaired;
                        // Cap vertices retain their original positions. Added material
                        // belongs to the separately bounded merge gap, not a displaced source
                        // surface.
                        open[r] = false;
                        balanced[r] = true;
                    }
                }
                if (balanced[r] && !cavityGroup[r]) {
                    int[] reduced =
                            ModelCollisionVolumeSimplifier.simplify(
                                    points, local, remainingError * .5, token, deadline);
                    // An optional simplifier exhausting its slice must not discard the original
                    // solid.
                    if (allowPartial)
                        deadline = Math.min(objectDeadline, System.nanoTime() + 250_000_000L);
                    if (reduced != local) {
                        int[] remap = new int[points.size()];
                        Arrays.fill(remap, -1);
                        List<Vector3d> retainedPoints = new ArrayList<>();
                        local = reduced.clone();
                        for (int i = 0; i < local.length; i++) {
                            int index = local[i];
                            if (remap[index] < 0) {
                                remap[index] = retainedPoints.size();
                                retainedPoints.add(points.get(index));
                            }
                            local[i] = remap[index];
                        }
                        points = retainedPoints;
                        remainingError *= .5;
                    }
                }
                if (cavityGroup[r]) {
                    if (!balanced[r])
                        throw new IOException(
                                "nested collision shells do not enclose a consistent volume");
                    solids.addAll(decompose(points, local, remainingError, token, deadline));
                    for (int t = head[r]; t >= 0; t = next[t]) {
                        consumed[t] = true;
                        retained -= 3;
                    }
                    continue;
                }
                ModelCollisionMesh.Part body =
                        closedHull(points, local, remainingError, token, deadline);
                if (body == null && !open[r]) {
                    solids.addAll(decompose(points, local, remainingError, token, deadline));
                    for (int t = head[r]; t >= 0; t = next[t]) {
                        consumed[t] = true;
                        retained -= 3;
                    }
                    continue;
                }
                if (body == null) {
                    if (balanced[r]) {
                        solids.addAll(decompose(points, local, remainingError, token, deadline));
                        for (int t = head[r]; t >= 0; t = next[t]) {
                            consumed[t] = true;
                            retained -= 3;
                        }
                        continue;
                    }
                    throw new IOException(
                            "open/nonmanifold collision component cannot be certified as a closed"
                                    + " volume (triangles="
                                    + sizes[r]
                                    + ", boundary="
                                    + boundaryEdges[r]
                                    + ", nonmanifold="
                                    + nonmanifoldEdges[r]
                                    + ", inconsistent="
                                    + inconsistentEdges[r]
                                    + ", bounds="
                                    + describe(points)
                                    + ")");
                }
                // Reject oversized/non-fitting hulls before the more expensive convexity
                // certificate.
                boolean convex = !open[r] && convexSurface(points, local, token, deadline);
                if (!convex) {
                    List<Vector3d> hullPoints = new ArrayList<>();
                    double[] hv = body.vertices();
                    for (int i = 0; i < hv.length; i += 3)
                        hullPoints.add(new Vector3d(hv[i], hv[i + 1], hv[i + 2]));
                    int[] hullTriangles = body.triangles();
                    if (!ModelCollisionSurfaceCertificate.covers(
                                    points,
                                    local,
                                    hullPoints,
                                    hullTriangles,
                                    remainingError * 0.5,
                                    token,
                                    deadline)
                            || !ModelCollisionSurfaceCertificate.covers(
                                    hullPoints,
                                    hullTriangles,
                                    points,
                                    local,
                                    remainingError * 0.5,
                                    token,
                                    deadline)) {
                        if (balanced[r]) {
                            solids.addAll(
                                    decompose(points, local, remainingError, token, deadline));
                            for (int t = head[r]; t >= 0; t = next[t]) {
                                consumed[t] = true;
                                retained -= 3;
                            }
                            continue;
                        }
                        throw new IOException(
                                "open/nonmanifold collision component has uncovered surfaces"
                                        + " (triangles="
                                        + sizes[r]
                                        + ", boundary="
                                        + boundaryEdges[r]
                                        + ", nonmanifold="
                                        + nonmanifoldEdges[r]
                                        + ", inconsistent="
                                        + inconsistentEdges[r]
                                        + ")");
                    }
                }
                solids.add(body);
                for (int t = head[r]; t >= 0; t = next[t]) {
                    consumed[t] = true;
                    retained -= 3;
                }
            } catch (IOException | IllegalArgumentException failure) {
                token.check();
                if (!allowPartial) throw failure;
                if (issues.size() < 8)
                    issues.add("triangles=" + sizes[r] + ": " + failure.getMessage());
                solids.subList(previousParts, solids.size()).clear();
            }
        }
        int[] remaining = new int[retained];
        int k = 0;
        for (int t = 0; t < triangles; t++)
            if (!consumed[t]) for (int j = 0; j < 3; j++) remaining[k++] = indices[t * 3 + j];
        if (!allowPartial && remaining.length != 0)
            throw new IOException(
                    "collision source contains open or degenerate surfaces without enclosed volume"
                            + " (triangles="
                            + remaining.length / 3
                            + ")");
        return new Extracted(solids, remaining, weldedEdges, List.copyOf(issues));
    }

    private static boolean validWeld(
            double[] positions,
            int[] weld,
            int[] indices,
            int[] mapped,
            ModelCancellation token,
            long deadline)
            throws IOException {
        for (int t = 0; t < indices.length; t += 3) {
            if ((t & 1023) == 0) check(token, deadline);
            int a = weld[indices[t]],
                    b = weld[indices[t + 1]],
                    c = weld[indices[t + 2]],
                    ma = mapped[a],
                    mb = mapped[b],
                    mc = mapped[c];
            if (a == ma && b == mb && c == mc) continue;
            if (ma == mb || mb == mc || ma == mc) return false;
            Vector3d origin = point(positions, a), moved = point(positions, ma);
            Vector3d
                    before = point(positions, b).sub(origin).cross(point(positions, c).sub(origin)),
                    after = point(positions, mb).sub(moved).cross(point(positions, mc).sub(moved));
            if (after.lengthSquared() < 1e-24 || before.dot(after) <= 0) return false;
        }
        return true;
    }

    private record SurfacePlane(long x, long y, long z, long d) {}

    private static String describe(List<Vector3d> points) {
        Bounds b = new Bounds();
        for (Vector3d p : points) b.include(p.x, p.y, p.z);
        return b.minX + "," + b.minY + "," + b.minZ + ".." + b.maxX + "," + b.maxY + "," + b.maxZ;
    }

    private static boolean containsComponent(
            int outer,
            int inner,
            double[] positions,
            int[] indices,
            int[] head,
            int[] next,
            ModelCancellation token,
            long deadline)
            throws IOException {
        Vector3d origin = new Vector3d();
        int sample = head[inner] * 3;
        for (int j = 0; j < 3; j++) {
            int v = indices[sample + j] * 3;
            origin.add(positions[v], positions[v + 1], positions[v + 2]);
        }
        origin.div(3);
        Vector3d direction = new Vector3d(1, .3713906763541037, .119582418317971);
        List<Double> hits = new ArrayList<>();
        int work = 0;
        for (int t = head[outer]; t >= 0; t = next[t]) {
            if ((work++ & 255) == 0) check(token, deadline);
            int a = indices[t * 3] * 3, b = indices[t * 3 + 1] * 3, c = indices[t * 3 + 2] * 3;
            double distance =
                    org.joml.Intersectiond.intersectRayTriangle(
                            origin.x,
                            origin.y,
                            origin.z,
                            direction.x,
                            direction.y,
                            direction.z,
                            positions[a],
                            positions[a + 1],
                            positions[a + 2],
                            positions[b],
                            positions[b + 1],
                            positions[b + 2],
                            positions[c],
                            positions[c + 1],
                            positions[c + 2],
                            1e-10);
            if (distance >= 0 && distance <= 1e-7) return true;
            if (distance > 0) hits.add(distance);
        }
        hits.sort(Double::compare);
        int crossings = 0;
        double last = Double.NEGATIVE_INFINITY;
        for (double hit : hits)
            if (hit - last > 1e-7) {
                crossings++;
                last = hit;
            }
        return (crossings & 1) != 0;
    }

    private static List<ModelCollisionMesh.Part> decompose(
            List<Vector3d> points,
            int[] triangles,
            double fittingError,
            ModelCancellation token,
            long deadline)
            throws IOException {
        try {
            var extruded =
                    ModelCollisionExtrusions.decompose(
                            points, triangles, fittingError, token, deadline);
            if (extruded != null) return extruded;
            Bounds bounds = new Bounds();
            for (Vector3d point : points) bounds.include(point.x, point.y, point.z);
            Vector3d origin =
                    new Vector3d(
                            (bounds.minX + bounds.maxX) * .5,
                            (bounds.minY + bounds.maxY) * .5,
                            (bounds.minZ + bounds.maxZ) * .5);
            List<Vector3d> local = new ArrayList<>(points.size());
            for (Vector3d point : points) local.add(new Vector3d(point).sub(origin));
            List<ModelCollisionMesh.Part> decomposed =
                    ModelCollisionVolumeDecomposition.decompose(
                            local, triangles, fittingError, token, deadline);
            List<ModelCollisionMesh.Part> translated = new ArrayList<>(decomposed.size());
            for (var part : decomposed) {
                double[] vertices = part.vertices(), planes = part.planes();
                for (int i = 0; i < vertices.length; i += 3) {
                    vertices[i] += origin.x;
                    vertices[i + 1] += origin.y;
                    vertices[i + 2] += origin.z;
                }
                for (int i = 0; i < planes.length; i += 4)
                    planes[i + 3] +=
                            planes[i] * origin.x
                                    + planes[i + 1] * origin.y
                                    + planes[i + 2] * origin.z;
                translated.add(new ModelCollisionMesh.Part(vertices, part.triangles(), planes));
            }
            return translated;
        } catch (IOException failure) {
            Bounds b = new Bounds();
            for (Vector3d p : points) b.include(p.x, p.y, p.z);
            throw new IOException(
                    failure.getMessage()
                            + "; componentTriangles="
                            + triangles.length / 3
                            + ", points="
                            + points.size()
                            + ", bounds="
                            + b.minX
                            + ","
                            + b.minY
                            + ","
                            + b.minZ
                            + ".."
                            + b.maxX
                            + ","
                            + b.maxY
                            + ","
                            + b.maxZ,
                    failure);
        }
    }

    private static boolean convexSurface(
            List<Vector3d> points, int[] triangles, ModelCancellation token, long deadline)
            throws IOException {
        Set<SurfacePlane> checked = new HashSet<>();
        long tests = 0;
        Vector3d center = new Vector3d();
        for (Vector3d v : points) center.add(v);
        center.div(points.size());
        for (int t = 0; t < triangles.length; t += 3) {
            if ((t & 255) == 0) check(token, deadline);
            Vector3d a = points.get(triangles[t]),
                    b = points.get(triangles[t + 1]),
                    c = points.get(triangles[t + 2]);
            Vector3d n = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a));
            if (n.lengthSquared() < 1e-24) continue;
            n.normalize();
            double d = n.dot(a);
            // A reflection reverses all outward normals. Orient using a convex
            // candidate's mean, then require every source vertex in the halfspace.
            if (n.dot(center) > d) {
                n.negate();
                d = -d;
            }
            SurfacePlane plane =
                    new SurfacePlane(
                            Double.doubleToLongBits(n.x + 0d),
                            Double.doubleToLongBits(n.y + 0d),
                            Double.doubleToLongBits(n.z + 0d),
                            Double.doubleToLongBits(d + 0d));
            if (!checked.add(plane)) continue;
            for (Vector3d v : points) {
                if (++tests > 8_000_000) return false;
                if (n.dot(v) - d > 1e-8) return false;
            }
        }
        return checked.size() >= 4;
    }

    private static ModelCollisionMesh.Part closedHull(
            List<Vector3d> points,
            int[] triangles,
            double error,
            ModelCancellation token,
            long deadline)
            throws IOException {
        if (points.size() < 4 || points.size() > 12_000) return null;
        check(token, deadline);
        ModelCollisionMesh.Part body = hull(points);
        check(token, deadline);
        if (body == null) {
            body = approximateHull(points, error * 0.25, token, deadline);
        }
        if (body == null) {
            // Removing points has a known Hausdorff bound on their convex hull, unlike
            // an unconstrained face-count target or a volume-only simplification test.
            Map<Sample, Vector3d> samples = new LinkedHashMap<>();
            double cell = error / (4 * Math.sqrt(3));
            for (Vector3d p : points)
                samples.putIfAbsent(
                        new Sample(
                                (long) Math.floor(p.x / cell),
                                (long) Math.floor(p.y / cell),
                                (long) Math.floor(p.z / cell)),
                        p);
            check(token, deadline);
            body = hull(new ArrayList<>(samples.values()));
            check(token, deadline);
            if (body == null) return null;
        }
        double[] planes = body.planes();
        // Every original surface triangle must lie within one hull facet's error
        // slab. Concave rooms and door frames fail this test and require volume decomposition.
        for (int i = 0; i < triangles.length; i += 3) {
            if ((i & 255) == 0) check(token, deadline);
            boolean surface = false;
            for (int p = 0; p < planes.length && !surface; p += 4) {
                double maximum = 0;
                for (int j = 0; j < 3; j++) {
                    Vector3d v = points.get(triangles[i + j]);
                    maximum =
                            Math.max(
                                    maximum,
                                    Math.abs(
                                            planes[p] * v.x
                                                    + planes[p + 1] * v.y
                                                    + planes[p + 2] * v.z
                                                    - planes[p + 3]));
                }
                if (maximum <= error * 0.5) surface = true;
            }
            if (!surface) return null;
        }
        return body;
    }

    private static ModelCollisionMesh.Part approximateHull(
            List<Vector3d> points, double error, ModelCancellation token, long deadline)
            throws IOException {
        Set<Integer> selected = new LinkedHashSet<>();
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) {
                        double maximum = Double.NEGATIVE_INFINITY;
                        int best = 0;
                        for (int i = 0; i < points.size(); i++) {
                            Vector3d p = points.get(i);
                            double projection = x * p.x + y * p.y + z * p.z;
                            if (projection > maximum) {
                                maximum = projection;
                                best = i;
                            }
                        }
                        selected.add(best);
                    }
        long work = 0;
        Vector3d closest = new Vector3d();
        for (int iteration = 0; iteration < 64; iteration++) {
            check(token, deadline);
            List<Vector3d> chosen = new ArrayList<>();
            for (int index : selected) chosen.add(points.get(index));
            var body = hull(chosen);
            if (body == null) return null;
            double[] planes = body.planes(), vertices = body.vertices();
            int[] triangles = body.triangles();
            List<Vector3d> hv = new ArrayList<>();
            for (int i = 0; i < vertices.length; i += 3)
                hv.add(new Vector3d(vertices[i], vertices[i + 1], vertices[i + 2]));
            double maximum = error * error;
            int farthest = -1;
            for (int i = 0; i < points.size(); i++) {
                if ((i & 255) == 0) check(token, deadline);
                Vector3d point = points.get(i);
                boolean inside = true;
                for (int p = 0; p < planes.length; p += 4)
                    if (planes[p] * point.x
                                    + planes[p + 1] * point.y
                                    + planes[p + 2] * point.z
                                    - planes[p + 3]
                            > 1e-10) {
                        inside = false;
                        break;
                    }
                if (inside) continue;
                double distance = Double.POSITIVE_INFINITY;
                for (int t = 0; t < triangles.length; t += 3) {
                    if (++work > 2_000_000) return null;
                    org.joml.Intersectiond.findClosestPointOnTriangle(
                            hv.get(triangles[t]),
                            hv.get(triangles[t + 1]),
                            hv.get(triangles[t + 2]),
                            point,
                            closest);
                    distance = Math.min(distance, closest.distanceSquared(point));
                }
                if (distance > maximum) {
                    maximum = distance;
                    farthest = i;
                }
            }
            if (farthest < 0) return body;
            if (!selected.add(farthest)) return null;
        }
        return null;
    }

    static ModelCollisionMesh.Part hull(List<Vector3d> points) {
        if (points.size() < 4) return null;
        double[] source = new double[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            Vector3d p = points.get(i);
            source[i * 3] = p.x;
            source[i * 3 + 1] = p.y;
            source[i * 3 + 2] = p.z;
        }
        try {
            QuickHull3D hull = new QuickHull3D(source);
            if (hull.getNumVertices() > 128 || hull.getNumFaces() > 128) return null;
            var hullVertices = hull.getVertices();
            int[][] faces = hull.getFaces();
            double[] vertices = new double[hullVertices.length * 3],
                    planes = new double[faces.length * 4];
            int indexCount = 0;
            for (int[] face : faces) indexCount += (face.length - 2) * 3;
            if (indexCount > 768) return null;
            for (int i = 0; i < hullVertices.length; i++) {
                var p = hullVertices[i];
                vertices[i * 3] = p.x;
                vertices[i * 3 + 1] = p.y;
                vertices[i * 3 + 2] = p.z;
            }
            int[] indices = new int[indexCount];
            int index = 0;
            for (int f = 0; f < faces.length; f++) {
                int[] face = faces[f];
                Vector3d origin = point(vertices, face[0]), normal = new Vector3d();
                for (int i = 1; i < face.length - 1; i++) {
                    normal.add(
                            point(vertices, face[i])
                                    .sub(origin)
                                    .cross(point(vertices, face[i + 1]).sub(origin)));
                    indices[index++] = face[0];
                    indices[index++] = face[i];
                    indices[index++] = face[i + 1];
                }
                if (normal.lengthSquared() < 1e-24) return null;
                normal.normalize();
                planes[f * 4] = normal.x;
                planes[f * 4 + 1] = normal.y;
                planes[f * 4 + 2] = normal.z;
                planes[f * 4 + 3] = normal.dot(origin);
            }
            return new ModelCollisionMesh.Part(vertices, indices, planes);
        } catch (IllegalArgumentException | com.github.quickhull3d.InternalErrorException invalid) {
            return null;
        }
    }

    private static Vector3d point(double[] vertices, int i) {
        return new Vector3d(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]);
    }

    private static int root(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        a = root(parent, a);
        b = root(parent, b);
        if (a != b) parent[b] = a;
    }

    private static void check(ModelCancellation token, long deadline) throws IOException {
        ModelCollisionOptimizationBudget.check(token, deadline);
    }

    private static final class Edge {
        int count, direction;
    }

    private static final class Bounds {
        double minX = Double.POSITIVE_INFINITY,
                minY = minX,
                minZ = minX,
                maxX = Double.NEGATIVE_INFINITY,
                maxY = maxX,
                maxZ = maxX;

        void include(double x, double y, double z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean contains(Bounds b) {
            return minX <= b.minX + 1e-7
                    && minY <= b.minY + 1e-7
                    && minZ <= b.minZ + 1e-7
                    && maxX >= b.maxX - 1e-7
                    && maxY >= b.maxY - 1e-7
                    && maxZ >= b.maxZ - 1e-7
                    && (minX < b.minX - 1e-6
                            || minY < b.minY - 1e-6
                            || minZ < b.minZ - 1e-6
                            || maxX > b.maxX + 1e-6
                            || maxY > b.maxY + 1e-6
                            || maxZ > b.maxZ + 1e-6);
        }
    }
}
