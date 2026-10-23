package com.mine.geometry_node.core.engine.system.model.collision.generation;

import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionMesh;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import org.joml.Vector3d;

import java.io.IOException;
import java.util.*;

/** Surface-driven BSP decomposition of a consistently wound closed volume. */
final class ModelCollisionVolumeDecomposition {
    private static final double EPS = 1e-9;
    private static final int MAX_CELLS = 1024, MAX_PARTS = 256;
    private static final long MAX_WORK = 8_000_000;

    private record Plane(Vector3d normal, double distance) {
        double distance(Vector3d p) {
            return normal.dot(p) - distance;
        }

        Plane flipped() {
            return new Plane(new Vector3d(normal).negate(), -distance);
        }
    }

    private record Cell(ModelCollisionMesh.Part shape, int[] triangles, int depth) {}

    private final List<Vector3d> vertices;
    private final int[] triangles;
    private final ModelCancellation token;
    private final long deadline;
    private final double fittingError;
    private long work;
    private int cells;

    private ModelCollisionVolumeDecomposition(
            List<Vector3d> vertices,
            int[] triangles,
            double fittingError,
            ModelCancellation token,
            long deadline) {
        this.vertices = vertices;
        this.triangles = triangles;
        this.fittingError = fittingError;
        this.token = token;
        this.deadline = deadline;
    }

    static List<ModelCollisionMesh.Part> decompose(
            List<Vector3d> vertices,
            int[] triangles,
            double fittingError,
            ModelCancellation token,
            long deadline)
            throws IOException {
        var solver =
                new ModelCollisionVolumeDecomposition(
                        vertices, triangles, fittingError, token, deadline);
        ModelCollisionMesh.Part hull =
                vertices.size() > 12000 ? null : ModelCollisionSolids.hull(vertices);
        if (hull == null) hull = box(vertices);
        int[] source = new int[triangles.length / 3];
        Arrays.setAll(source, i -> i);
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        pending.push(new Cell(hull, source, 0));
        List<ModelCollisionMesh.Part> inside = new ArrayList<>();
        while (!pending.isEmpty()) {
            solver.check();
            Cell cell = pending.pop();
            if (++solver.cells > MAX_CELLS || cell.depth > 96)
                throw new IOException("closed collision volume decomposition exceeds cell budget (cells="+solver.cells+", depth="+cell.depth+", size="+(cell.shape.maxX()-cell.shape.minX())+","+(cell.shape.maxY()-cell.shape.minY())+","+(cell.shape.maxZ()-cell.shape.minZ())+")");
            Plane splitter = solver.splitter(cell);
            if (splitter == null) {
                if (solver.inside(center(cell.shape))) {
                    inside.add(cell.shape);
                }
                continue;
            }
            double dx = cell.shape.maxX() - cell.shape.minX(),
                    dy = cell.shape.maxY() - cell.shape.minY(),
                    dz = cell.shape.maxZ() - cell.shape.minZ();
            if (dx * dx + dy * dy + dz * dz <= fittingError * fittingError) {
                // A source boundary crosses this cell. Its entire closed volume
                // lies within the fitting budget of that boundary, so retain it
                // conservatively instead of constructing unstable microscopic cuts.
                inside.add(cell.shape);
                continue;
            }
            ModelCollisionMesh.Part negative = clip(cell.shape, splitter),
                    positive = clip(cell.shape, splitter.flipped());
            if (negative == null || positive == null) {
                // A detailed curved boundary may exhaust one convex cell's face
                // capacity. Subdivide space, retaining both halves, rather than
                // dropping the boundary or approximating it with a surface shell.
                double x = cell.shape.maxX() - cell.shape.minX(),
                        y = cell.shape.maxY() - cell.shape.minY(),
                        z = cell.shape.maxZ() - cell.shape.minZ();
                Vector3d axis =
                        x >= y && x >= z
                                ? new Vector3d(1, 0, 0)
                                : y >= z ? new Vector3d(0, 1, 0) : new Vector3d(0, 0, 1);
                Plane middle = new Plane(axis, axis.dot(center(cell.shape)));
                negative = clip(cell.shape, middle);
                positive = clip(cell.shape, middle.flipped());
                if (negative == null || positive == null)
                    throw new IOException(
                            "closed collision volume split is numerically degenerate (depth="
                                    + cell.depth
                                    + ", triangles="
                                    + cell.triangles.length
                                    + ", size="
                                    + x
                                    + ","
                                    + y
                                    + ","
                                    + z
                                    + ")");
            }
            pending.push(
                    new Cell(
                            positive, solver.candidates(cell.triangles, positive), cell.depth + 1));
            pending.push(
                    new Cell(
                            negative, solver.candidates(cell.triangles, negative), cell.depth + 1));
        }
        if (inside.isEmpty())
            throw new IOException("closed collision volume contains no reliable interior cells");
        solver.merge(inside);
        if(inside.size()>MAX_PARTS)throw new IOException("closed collision volume decomposition exceeds part budget after convex merging");
        return inside;
    }

    private static ModelCollisionMesh.Part box(List<Vector3d> vertices) throws IOException {
        double x0 = Double.POSITIVE_INFINITY,
                y0 = x0,
                z0 = x0,
                x1 = Double.NEGATIVE_INFINITY,
                y1 = x1,
                z1 = x1;
        for (Vector3d v : vertices) {
            x0 = Math.min(x0, v.x);
            y0 = Math.min(y0, v.y);
            z0 = Math.min(z0, v.z);
            x1 = Math.max(x1, v.x);
            y1 = Math.max(y1, v.y);
            z1 = Math.max(z1, v.z);
        }
        List<Vector3d> corners = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            corners.add(
                    new Vector3d(
                            (i & 1) == 0 ? x0 : x1,
                            (i & 2) == 0 ? y0 : y1,
                            (i & 4) == 0 ? z0 : z1));
        var box = ModelCollisionSolids.hull(corners);
        if (box == null) throw new IOException("closed collision volume has degenerate bounds");
        return box;
    }

    private Plane splitter(Cell cell) throws IOException {
        double[] cv = cell.shape.vertices(), planes = cell.shape.planes();
        double[] boundaryTolerances = new double[planes.length / 4];
        for (int p = 0; p < planes.length; p += 4) {
            double span = 0;
            for (int i = 0; i < cv.length; i += 3)
                span = Math.max(span, planes[p+3] - planes[p]*cv[i] - planes[p+1]*cv[i+1] - planes[p+2]*cv[i+2]);
            // A thin axis must not hide a large concavity in another axis.
            boundaryTolerances[p / 4] = Math.min(1e-10, span * 1e-4);
        }
        Plane best = null;
        double bestBalance = -1;
        for (int triangle : cell.triangles) {
            check();
            int t = triangle * 3;
            Vector3d a = vertices.get(triangles[t]),
                    b = vertices.get(triangles[t + 1]),
                    c = vertices.get(triangles[t + 2]);
            Vector3d n = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a));
            if (n.lengthSquared() < 1e-24) continue;
            n.normalize();
            Plane plane = new Plane(n, n.dot(a));
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < cv.length; i += 3) {
                double d = n.x * cv[i] + n.y * cv[i + 1] + n.z * cv[i + 2] - plane.distance;
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            if (min >= -EPS*4 || max <= EPS*4) continue;
            List<Vector3d> polygon = clipTriangle(a, b, c, planes);
            if (polygon.size() < 3) continue;
            Vector3d area=new Vector3d(),origin=polygon.getFirst();for(int i=1;i<polygon.size()-1;i++)area.add(new Vector3d(polygon.get(i)).sub(origin).cross(new Vector3d(polygon.get(i+1)).sub(origin)));
            if(area.lengthSquared()<1e-28)continue;
            boolean interior = true;
            for (int p = 0; p < planes.length; p += 4) {
                double nearest=Double.POSITIVE_INFINITY;
                for(Vector3d point:polygon)nearest=Math.min(nearest,planes[p]*point.x+planes[p+1]*point.y+planes[p+2]*point.z-planes[p+3]);
                if(nearest>=-boundaryTolerances[p / 4]) {
                    interior = false;
                    break;
                }
            }
            if (!interior) continue;
            double balance = Math.min(-min, max) / Math.max(-min, max);
            if (balance > bestBalance) {
                bestBalance = balance;
                best = plane;
            }
            if (balance > .8) break;
        }
        return best;
    }

    private int[] candidates(int[] source, ModelCollisionMesh.Part shape) throws IOException {
        int[] selected = new int[source.length];
        int count = 0;
        double[] planes = shape.planes();
        for (int triangle : source) {
            check();
            int t = triangle * 3;
            if (clipTriangle(
                                    vertices.get(triangles[t]),
                                    vertices.get(triangles[t + 1]),
                                    vertices.get(triangles[t + 2]),
                                    planes)
                            .size()
                    >= 3) selected[count++] = triangle;
        }
        return Arrays.copyOf(selected, count);
    }

    private List<Vector3d> clipTriangle(Vector3d a, Vector3d b, Vector3d c, double[] planes)
            throws IOException {
        List<Vector3d> polygon = new ArrayList<>(List.of(a, b, c));
        for (int p = 0; p < planes.length && !polygon.isEmpty(); p += 4) {
            spend(polygon.size());
            Plane plane =
                    new Plane(new Vector3d(planes[p], planes[p + 1], planes[p + 2]), planes[p + 3]);
            polygon = clipPolygon(polygon, plane);
        }
        return polygon;
    }

    private static List<Vector3d> clipPolygon(List<Vector3d> polygon, Plane plane) {
        List<Vector3d> out = new ArrayList<>();
        Vector3d previous = polygon.getLast();
        double pd = plane.distance(previous);
        for (Vector3d current : polygon) {
            double cd = plane.distance(current);
            if ((pd <= EPS) != (cd <= EPS))
                out.add(new Vector3d(previous).lerp(current, Math.clamp((pd-EPS) / (pd - cd),0,1)));
            if (cd <= EPS) out.add(current);
            previous = current;
            pd = cd;
        }
        return out;
    }

    private static ModelCollisionMesh.Part clip(ModelCollisionMesh.Part body, Plane plane) {
        List<Vector3d> points = clippedPoints(body, plane);
        return ModelCollisionSolids.hull(points);
    }

    private static List<Vector3d> clippedPoints(ModelCollisionMesh.Part body, Plane plane) {
        double[] v = body.vertices();
        int[] triangles = body.triangles();
        List<Vector3d> points = new ArrayList<>();
        for (int i = 0; i < v.length; i += 3) {
            Vector3d p = new Vector3d(v[i], v[i + 1], v[i + 2]);
            if (plane.distance(p) <= EPS) points.add(p);
        }
        Set<Long> seen = new HashSet<>();
        for (int t = 0; t < triangles.length; t += 3)
            for (int j = 0; j < 3; j++) {
                int a = triangles[t + j], b = triangles[t + (j + 1) % 3];
                long edge = ((long) Math.min(a, b) << 32) | Math.max(a, b);
                if (!seen.add(edge)) continue;
                Vector3d p = new Vector3d(v[a * 3], v[a * 3 + 1], v[a * 3 + 2]),
                        q = new Vector3d(v[b * 3], v[b * 3 + 1], v[b * 3 + 2]);
                double pd = plane.distance(p), qd = plane.distance(q);
                if (pd < -EPS && qd > EPS || pd > EPS && qd < -EPS)
                    points.add(p.lerp(q, pd / (pd - qd)));
            }
        return points;
    }

    private boolean inside(Vector3d point) throws IOException {
        double angle = 0;
        for (int t = 0; t < triangles.length; t += 3) {
            spend(1);
            Vector3d a = new Vector3d(vertices.get(triangles[t])).sub(point),
                    b = new Vector3d(vertices.get(triangles[t + 1])).sub(point),
                    c = new Vector3d(vertices.get(triangles[t + 2])).sub(point);
            double la = a.length(), lb = b.length(), lc = c.length();
            double numerator = a.dot(new Vector3d(b).cross(c));
            double denominator = la * lb * lc + a.dot(b) * lc + b.dot(c) * la + c.dot(a) * lb;
            angle += 2 * Math.atan2(numerator, denominator);
        }
        double winding = angle / (4 * Math.PI);
        if (winding < -.25)
            throw new IOException("closed collision volume has an unmatched inward shell");
        if (winding > .75) return true;
        if (winding < .25) return false;
        throw new IOException("closed collision volume has ambiguous winding");
    }

    private void merge(List<ModelCollisionMesh.Part> parts) throws IOException {
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < parts.size(); i++)
                for (int j = i + 1; j < parts.size(); j++) {
                    check();
                    var a = parts.get(i);
                    var b = parts.get(j);
                    Plane shared = shared(a, b);
                    if (shared == null) continue;
                    List<Vector3d> points = new ArrayList<>();
                    add(points, a);
                    add(points, b);
                    var hull = ModelCollisionSolids.hull(points);
                    if (hull == null) continue;
                    if (contained(clippedPoints(hull, shared), a)
                            && contained(clippedPoints(hull, shared.flipped()), b)) {
                        parts.set(i, hull);
                        parts.remove(j--);
                    }
                }
    }

    private static Plane shared(ModelCollisionMesh.Part a, ModelCollisionMesh.Part b) {
        double[] ap = a.planes(), bp = b.planes();
        for (int i = 0; i < ap.length; i += 4)
            for (int j = 0; j < bp.length; j += 4)
                if (Math.abs(ap[i] + bp[j])
                                        + Math.abs(ap[i + 1] + bp[j + 1])
                                        + Math.abs(ap[i + 2] + bp[j + 2])
                                < EPS
                        && Math.abs(ap[i + 3] + bp[j + 3]) < EPS)
                    return new Plane(new Vector3d(ap[i], ap[i + 1], ap[i + 2]), ap[i + 3]);
        return null;
    }

    private static boolean contained(List<Vector3d> points, ModelCollisionMesh.Part body) {
        double[] planes = body.planes();
        for (Vector3d v : points)
            for (int p = 0; p < planes.length; p += 4)
                if (planes[p] * v.x + planes[p + 1] * v.y + planes[p + 2] * v.z - planes[p + 3]
                        > EPS) return false;
        return true;
    }

    private static void add(List<Vector3d> points, ModelCollisionMesh.Part part) {
        double[] v = part.vertices();
        for (int i = 0; i < v.length; i += 3) points.add(new Vector3d(v[i], v[i + 1], v[i + 2]));
    }

    private static Vector3d center(ModelCollisionMesh.Part shape) {
        double[] v = shape.vertices();
        Vector3d center = new Vector3d();
        for (int i = 0; i < v.length; i += 3) center.add(v[i], v[i + 1], v[i + 2]);
        return center.div(v.length / 3);
    }

    private void spend(int n) throws IOException {
        work += n;
        if (work > MAX_WORK)
            throw new IOException("closed collision volume decomposition exceeds work budget");
        check();
    }

    private void check() throws IOException {
        ModelCollisionOptimizationBudget.check(token, deadline);
    }
}
