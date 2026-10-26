package com.mine.geometry_node.client.runtime.model.render.geometry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Disjoint triangle ranges for a captured resident prefix and its immediate remainder. */
public final class ModelGeometryUploadRanges {
    private ModelGeometryUploadRanges() {}

    public record IndexRange(int firstIndex, int count) {
        public IndexRange {
            if (firstIndex < 0 || count <= 0 || firstIndex % 3 != 0 || count % 3 != 0
                    || (long) firstIndex + count > Integer.MAX_VALUE)
                throw new IllegalArgumentException("index range must contain complete triangles");
        }
    }

    public record Split(List<IndexRange> resident, List<IndexRange> immediate) {
        public Split {
            resident = List.copyOf(resident);
            immediate = List.copyOf(immediate);
        }
    }

    public static Split split(List<IndexRange> visible, int totalIndices, int uploadedIndices) {
        Objects.requireNonNull(visible);
        if (totalIndices < 0 || totalIndices % 3 != 0 || uploadedIndices < 0
                || uploadedIndices > totalIndices || uploadedIndices % 3 != 0)
            throw new IllegalArgumentException("upload prefix must contain complete source triangles");
        int previousEnd = 0;
        for (IndexRange range : visible) {
            int end = range.firstIndex() + range.count();
            if (range.firstIndex() < previousEnd || end > totalIndices)
                throw new IllegalArgumentException("visible ranges must be ordered, disjoint, and inside the source");
            previousEnd = end;
        }
        if (uploadedIndices == 0) return new Split(List.of(), visible);
        if (uploadedIndices == totalIndices) return new Split(visible, List.of());
        List<IndexRange> resident = new ArrayList<>(), immediate = new ArrayList<>();
        for (IndexRange range : visible) {
            int end = range.firstIndex() + range.count();
            if (end <= uploadedIndices) { resident.add(range); continue; }
            if (range.firstIndex() >= uploadedIndices) { immediate.add(range); continue; }
            int residentEnd = Math.min(end, uploadedIndices);
            if (residentEnd > range.firstIndex())
                resident.add(new IndexRange(range.firstIndex(), residentEnd - range.firstIndex()));
            int immediateStart = Math.max(range.firstIndex(), uploadedIndices);
            if (immediateStart < end)
                immediate.add(new IndexRange(immediateStart, end - immediateStart));
        }
        return new Split(resident, immediate);
    }
}
