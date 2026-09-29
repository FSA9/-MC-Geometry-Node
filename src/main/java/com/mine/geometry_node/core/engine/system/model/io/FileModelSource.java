package com.mine.geometry_node.core.engine.system.model.io;
import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
/** Range-backed GLB/BIN source. Close invalidates subsequent reads. */
public final class FileModelSource implements ModelByteSource {
    private final FileChannel channel;
    private final long size;
    private final int maxSlice;
    private long bytesRead;
    private int largestRead;
    public FileModelSource(Path file, ModelLimits limits) throws IOException {
        channel=FileChannel.open(file,StandardOpenOption.READ); maxSlice=limits.readSliceBytes();
        try { size=channel.size(); ModelLimits.requireWithin(size,limits.packageBytes(),file.toString()); }
        catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }
    public long size() { return size; }
    public long bytesRead() { return bytesRead; }
    public int largestRead() { return largestRead; }
    public ByteBuffer read(long offset,int length,ModelCancellation cancellation) throws IOException {
        cancellation.check();
        if (offset<0 || length<0 || offset>size || length>size-offset)
            throw new ModelProblem(ModelProblem.Code.INVALID_CONTAINER,"byte["+offset+"]","range exceeds source");
        ModelLimits.requireWithin(length,maxSlice,"read slice");
        ByteBuffer data=ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        while(data.hasRemaining()) {
            cancellation.check(); int n=channel.read(data,offset+data.position());
            if(n<0) throw new EOFException("source changed/truncated while reading");
            bytesRead+=n;
        }
        largestRead=Math.max(largestRead,length);
        return data.flip().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
    }
    public void close() throws IOException { channel.close(); }
}
