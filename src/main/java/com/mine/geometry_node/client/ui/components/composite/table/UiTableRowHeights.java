package com.mine.geometry_node.client.ui.components.composite.table;

/** Pixel heights and prefix offsets for virtual table rows, with logarithmic updates and hit lookup. */
public final class UiTableRowHeights {
    private final int[] heights;
    private final int[] sums;

    public UiTableRowHeights(int count, int estimate) {
        if (count < 0 || estimate <= 0) throw new IllegalArgumentException("Invalid row geometry");
        heights = new int[count];
        sums = new int[count + 1];
        java.util.Arrays.fill(heights, estimate);
        for (int i = 1; i <= count; i++) sums[i] = (i & -i) * estimate;
    }

    public int size() { return heights.length; }
    public int height(int index) { return heights[index]; }
    public int totalHeight() { return top(heights.length); }

    /** Returns the prefix height for an index in [0, size]; size selects the content bottom. */
    public int top(int index) {
        if (index < 0 || index > heights.length) throw new IndexOutOfBoundsException(index);
        int result = 0;
        for (int i = index; i > 0; i -= i & -i) result += sums[i];
        return result;
    }

    public boolean setHeight(int index, int height) {
        if (height <= 0) throw new IllegalArgumentException("Invalid row height");
        int delta = height - heights[index];
        if (delta == 0) return false;
        heights[index] = height;
        for (int i = index + 1; i < sums.length; i += i & -i) sums[i] += delta;
        return true;
    }

    /** Finds the containing row in logarithmic time, clamping to the nearest row at either end. */
    public int indexAt(float offset) {
        if (heights.length == 0) return -1;
        int index = 0, sum = 0;
        for (int step = Integer.highestOneBit(heights.length); step != 0; step >>= 1) {
            int next = index + step;
            if (next < sums.length && sum + sums[next] <= offset) {
                sum += sums[next];
                index = next;
            }
        }
        return Math.min(index, heights.length - 1);
    }
}
