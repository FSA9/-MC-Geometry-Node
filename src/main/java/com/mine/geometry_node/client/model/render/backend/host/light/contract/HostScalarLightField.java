package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import com.mine.geometry_node.client.model.render.backend.host.light.solve.HostLightingMemoryBudget;

import java.util.Arrays;
import java.util.Objects;

/** Compact immutable scalar field indexed by F3B receiver probe. */
public final class HostScalarLightField implements HostLocalLightField {
    private final HostLightFieldIdentity identity;
    private final int[] packed;
    private final HostLightingMemoryBudget.Reservation reservation;

    public HostScalarLightField(HostLightFieldIdentity identity, HostScalarLightSample[] samples) {
        this(identity, pack(samples), null);
    }

    public HostScalarLightField(HostLightFieldIdentity identity, int[] packed,
                                HostLightingMemoryBudget.Reservation reservation) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.packed = Arrays.copyOf(Objects.requireNonNull(packed, "packed"), packed.length);
        for (int value : this.packed) validatePacked(value);
        if (reservation != null && reservation.bytes() < residentBytes()) {
            reservation.close();
            throw new IllegalArgumentException("field reservation is smaller than retained storage");
        }
        this.reservation = reservation;
    }

    @Override public HostLightFieldIdentity identity() { return identity; }
    @Override public long residentBytes() { return (long) packed.length * Integer.BYTES; }
    public int size() { return packed.length; }
    public int packedLight(int probe) { return packed[check(probe)]; }
    public HostScalarLightSample sample(int probe) {
        int value = packedLight(probe);
        return new HostScalarLightSample((value >>> 4) & 15, (value >>> 20) & 15);
    }
    public HostVertexLightView probeView() { return this::packedLight; }
    public boolean sameSamples(HostScalarLightField other) {
        return other != null && Arrays.equals(packed, other.packed);
    }
    @Override public void close() { if (reservation != null) reservation.close(); }

    private static int[] pack(HostScalarLightSample[] samples) {
        Objects.requireNonNull(samples, "samples");
        int[] result = new int[samples.length];
        for (int index = 0; index < samples.length; index++) {
            result[index] = HostLightQuantizer.packUv2(
                    Objects.requireNonNull(samples[index], "samples[" + index + "]"));
        }
        return result;
    }

    private int check(int probe) {
        if (probe < 0 || probe >= packed.length) throw new IndexOutOfBoundsException(probe);
        return probe;
    }

    private static void validatePacked(int value) {
        int allowed = (15 << 4) | (15 << 20);
        if ((value & ~allowed) != 0) throw new IllegalArgumentException("packed field contains non-UV2 bits");
    }
}
