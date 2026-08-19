package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldLightSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostLightQuantizer;
import com.mine.geometry_node.client.model.render.backend.host.light.contract.HostScalarLightSample;
import com.mine.geometry_node.client.model.render.backend.host.light.occlusion.HostModelOccluderInstance;
import com.mine.geometry_node.client.model.render.backend.host.light.source.HostLightSource;
import com.mine.geometry_node.client.model.render.backend.host.light.source.HostLightSourceSnapshot;

import java.util.Arrays;
import java.util.*;

/** Bounded F3B scalar solve: shape-aware direct visibility plus low-frequency transport. */
public final class HostUv2LightingSolver {
    private final Parameters parameters;

    public HostUv2LightingSolver(Parameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    public int[] solve(List<Receiver> receivers, HostLightSourceSnapshot sources,
                       WorldLightSnapshot world, WorldOccluderSnapshot worldOccluder,
                       HostModelOccluderInstance modelOccluder,
                       HostVoxelLightTransport.Cancellation cancellation) {
        Objects.requireNonNull(receivers, "receivers");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(worldOccluder, "worldOccluder");
        Objects.requireNonNull(cancellation, "cancellation");
        if (!sources.dimension().equals(world.dimension())
                || !worldOccluder.dimension().equals(world.dimension())) {
            throw new IllegalArgumentException("lighting inputs use different dimensions");
        }
        HostVoxelLightTransport.Result propagated = new HostVoxelLightTransport().propagate(
                world, worldOccluder, modelOccluder, parameters.voxelParameters(), cancellation);
        SourceIndex sourceIndex = SourceIndex.build(sources.sources(), parameters.sourceIndexCellSize(),
                parameters.maximumIndexedSourceRadius(), cancellation);
        int[] result = new int[receivers.size()];
        HostLightSource[] selected = new HostLightSource[parameters.maximumDirectSources()];
        double[] scores = new double[selected.length];
        for (int receiverIndex = 0; receiverIndex < receivers.size(); receiverIndex++) {
            if ((receiverIndex & 255) == 0) cancellation.check();
            Receiver receiver = Objects.requireNonNull(receivers.get(receiverIndex),
                    "receivers[" + receiverIndex + "]");
            if (!world.containsWorld(receiver.x(), receiver.y(), receiver.z())) {
                throw new IllegalArgumentException("receiver is outside the complete world capture");
            }
            int x = world.localX(receiver.x());
            int y = world.localY(receiver.y());
            int z = world.localZ(receiver.z());
            int block = propagated.block(x, y, z);
            int sky = propagated.sky(x, y, z);
            int selectedCount = selectSources(receiver, sourceIndex.candidates(receiver), selected, scores);
            for (int index = 0; index < selectedCount; index++) {
                HostLightSource source = selected[index];
                double score = scores[index];
                double dx = source.worldX() - receiver.x();
                double dy = source.worldY() - receiver.y();
                double dz = source.worldZ() - receiver.z();
                double side = dx * receiver.normalX() + dy * receiver.normalY() + dz * receiver.normalZ();
                double sign = side >= 0 ? 1 : -1;
                double fromX = receiver.x() + receiver.normalX() * parameters.rayOriginEpsilon() * sign;
                double fromY = receiver.y() + receiver.normalY() * parameters.rayOriginEpsilon() * sign;
                double fromZ = receiver.z() + receiver.normalZ() * parameters.rayOriginEpsilon() * sign;
                if (worldOccluder.blocksOpenSegmentToSource(fromX, fromY, fromZ,
                        source.worldX(), source.worldY(), source.worldZ())) continue;
                if (modelOccluder != null && modelOccluder.blocksOpenSegment(fromX, fromY, fromZ,
                        source.worldX(), source.worldY(), source.worldZ())) continue;
                block = Math.max(block, clamp((int) Math.round(score)));
            }
            result[receiverIndex] = HostLightQuantizer.packUv2(new HostScalarLightSample(block, sky));
        }
        return result;
    }

    public long estimatedScratchBytes(int worldCells) {
        return HostVoxelLightTransport.scratchBytes(worldCells);
    }

    public long estimatedReceiverBytes(int receivers) {
        if (receivers < 0) throw new IllegalArgumentException("receivers must not be negative");
        return Math.multiplyExact((long) receivers, 48L);
    }

    public long estimatedSourceIndexBytes(int sources) {
        if (sources < 0) throw new IllegalArgumentException("sources must not be negative");
        long axis = (long) Math.ceil(parameters.maximumIndexedSourceRadius() * 2.0
                / parameters.sourceIndexCellSize()) + 2L;
        long references = Math.multiplyExact(Math.multiplyExact(Math.multiplyExact(
                (long) sources, axis), axis), axis);
        return Math.addExact(Math.multiplyExact(references, 8L), Math.multiplyExact((long) sources, 32L));
    }

    public long estimatedFieldBytes(int receivers) {
        if (receivers < 0) throw new IllegalArgumentException("receivers must not be negative");
        return Math.multiplyExact((long) receivers, Integer.BYTES);
    }

    private int selectSources(Receiver receiver, List<HostLightSource> sources,
                              HostLightSource[] selected, double[] scores) {
        Arrays.fill(selected, null);
        Arrays.fill(scores, 0);
        int count = 0;
        for (HostLightSource source : sources) {
            double dx = source.worldX() - receiver.x();
            double dy = source.worldY() - receiver.y();
            double dz = source.worldZ() - receiver.z();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double radius = Math.min(source.radius(), parameters.maximumIndexedSourceRadius());
            if (distance > radius) continue;
            double score = source.intensity()
                    * Math.max(0.0, 1.0 - distance / Math.max(radius, 1.0e-6));
            if (!(score > 0)) continue;
            int insertion = count;
            while (insertion > 0 && better(source, score, selected[insertion - 1], scores[insertion - 1])) {
                if (insertion < selected.length) {
                    selected[insertion] = selected[insertion - 1];
                    scores[insertion] = scores[insertion - 1];
                }
                insertion--;
            }
            if (insertion < selected.length) {
                selected[insertion] = source;
                scores[insertion] = score;
                if (count < selected.length) count++;
            }
        }
        return count;
    }

    private static boolean better(HostLightSource candidate, double candidateScore,
                                  HostLightSource existing, double existingScore) {
        if (existing == null) return true;
        int scoreOrder = Double.compare(candidateScore, existingScore);
        return scoreOrder > 0 || scoreOrder == 0 && candidate.id().compareTo(existing.id()) < 0;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(15, value)); }

    public record Receiver(double x, double y, double z,
                           float normalX, float normalY, float normalZ) {
        public Receiver {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(normalX) || !Float.isFinite(normalY) || !Float.isFinite(normalZ)) {
                throw new IllegalArgumentException("receiver coordinates and normal must be finite");
            }
            float lengthSquared = normalX * normalX + normalY * normalY + normalZ * normalZ;
            if (!(lengthSquared > 1.0e-12F)) throw new IllegalArgumentException("receiver normal must be non-zero");
            float inverseLength = (float) (1.0 / Math.sqrt(lengthSquared));
            normalX *= inverseLength;
            normalY *= inverseLength;
            normalZ *= inverseLength;
        }
    }

    private record SourceCell(int x, int y, int z) {}

    private static final class SourceIndex {
        private final int cellSize;
        private final Map<SourceCell, List<HostLightSource>> cells;

        private SourceIndex(int cellSize, Map<SourceCell, List<HostLightSource>> cells) {
            this.cellSize = cellSize;
            this.cells = cells;
        }

        private static SourceIndex build(List<HostLightSource> sources, int cellSize, double maximumRadius,
                                         HostVoxelLightTransport.Cancellation cancellation) {
            Map<SourceCell, ArrayList<HostLightSource>> building = new HashMap<>();
            int sourceIndex = 0;
            for (HostLightSource source : sources) {
                if ((sourceIndex++ & 255) == 0) cancellation.check();
                double radius = Math.min(source.radius(), maximumRadius);
                if (!(radius > 0) || !(source.intensity() > 0)) continue;
                int minX = cell(source.worldX() - radius, cellSize);
                int minY = cell(source.worldY() - radius, cellSize);
                int minZ = cell(source.worldZ() - radius, cellSize);
                int maxX = cell(source.worldX() + radius, cellSize);
                int maxY = cell(source.worldY() + radius, cellSize);
                int maxZ = cell(source.worldZ() + radius, cellSize);
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            building.computeIfAbsent(new SourceCell(x, y, z), ignored -> new ArrayList<>())
                                    .add(source);
                        }
                    }
                }
            }
            Map<SourceCell, List<HostLightSource>> immutable = new HashMap<>(building.size());
            building.forEach((cell, values) -> immutable.put(cell, List.copyOf(values)));
            return new SourceIndex(cellSize, Map.copyOf(immutable));
        }

        private List<HostLightSource> candidates(Receiver receiver) {
            return cells.getOrDefault(new SourceCell(cell(receiver.x(), cellSize),
                    cell(receiver.y(), cellSize), cell(receiver.z(), cellSize)), List.of());
        }

        private static int cell(double coordinate, int cellSize) {
            double value = Math.floor(coordinate / cellSize);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("source index coordinate overflow");
            }
            return (int) value;
        }
    }

    public record Parameters(int maximumDirectSources, double rayOriginEpsilon,
                             int sourceIndexCellSize, double maximumIndexedSourceRadius,
                             HostVoxelLightTransport.Parameters voxelParameters) {
        public Parameters(int maximumDirectSources, double rayOriginEpsilon,
                          HostVoxelLightTransport.Parameters voxelParameters) {
            this(maximumDirectSources, rayOriginEpsilon, 16, 32, voxelParameters);
        }

        public Parameters {
            if (maximumDirectSources < 1 || maximumDirectSources > 256
                    || !(rayOriginEpsilon > 0) || !Double.isFinite(rayOriginEpsilon)
                    || sourceIndexCellSize < 1 || !(maximumIndexedSourceRadius > 0)
                    || !Double.isFinite(maximumIndexedSourceRadius)) {
                throw new IllegalArgumentException("invalid HOST UV2 solver parameters");
            }
            Objects.requireNonNull(voxelParameters, "voxelParameters");
        }

        public static Parameters defaults() {
            return new Parameters(32, 1.0e-4, 16, 32,
                    HostVoxelLightTransport.Parameters.defaults());
        }
    }
}
