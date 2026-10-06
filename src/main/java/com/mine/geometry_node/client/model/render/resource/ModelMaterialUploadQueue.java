package com.mine.geometry_node.client.model.render.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/** Render-thread staging uploads; the caller retains ownership until either completion or failure. */
public final class ModelMaterialUploadQueue {
    public record Upload(GpuTexture target, ModelTextureBackend.Decoded data) {
        public Upload { Objects.requireNonNull(target); Objects.requireNonNull(data); }
    }
    private static final long BYTES_PER_TICK = 2L << 20, NANOS_PER_TICK = 2_000_000;
    private static final int MAX_JOBS = 32;
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static long uploadedBytes, completedJobs, failedJobs;
    private static String lastFailure = "";
    private static final class Job {
        final Object owner;
        final List<Upload> uploads;
        final Runnable complete;
        final Consumer<RuntimeException> failure;
        int texture, mip, row;
        Job(Object owner,List<Upload> uploads,Runnable complete,Consumer<RuntimeException> failure) {
            this.owner=owner;this.uploads=uploads;this.complete=complete;this.failure=failure;
        }
    }
    /** Rejected enqueue retains caller ownership; completion exceptions are forwarded to failure for cleanup. */
    public static void enqueue(Object owner,List<Upload> uploads,Runnable complete,Consumer<RuntimeException> failure) {
        RenderSystem.assertOnRenderThread();
        Objects.requireNonNull(owner);Objects.requireNonNull(complete);Objects.requireNonNull(failure);
        List<Upload> copy=List.copyOf(uploads);
        if(copy.isEmpty()||copy.size()>3)throw new IllegalArgumentException("material upload requires 1 to 3 textures");
        if(JOBS.size()>=MAX_JOBS)throw new IllegalStateException("material GPU upload queue full");
        for(Upload upload:copy) {
            if(upload.target.isClosed()||upload.data.levels.isEmpty())throw new IllegalArgumentException("closed or empty material texture");
            for(int mip=0;mip<upload.data.levels.size();mip++) {
                var image=upload.data.levels.get(mip);
                if(image.getWidth()!=upload.target.getWidth(mip)||image.getHeight()!=upload.target.getHeight(mip))
                    throw new IllegalArgumentException("material staging texture dimensions differ at mip "+mip);
            }
        }
        JOBS.addLast(new Job(owner,copy,complete,failure));
    }
    /** Call once per frame after IrisUploadBudget.beginFrame, outside an active host RenderPass. */
    public static void tick() {
        RenderSystem.assertOnRenderThread();
        long start=System.nanoTime(),bytes=0;
        var budget=ModelIrisUploadBudget.INSTANCE;
        while(!JOBS.isEmpty()&&bytes<BYTES_PER_TICK&&System.nanoTime()-start<NANOS_PER_TICK) {
            Job job=JOBS.peekFirst();Upload upload=job.uploads.get(job.texture);
            var image=upload.data.levels.get(job.mip);
            long rowBytes=(long)image.getWidth()*4;
            int rows=(int)Math.min(image.getHeight()-job.row,(BYTES_PER_TICK-bytes)/rowBytes);
            if(rows<=0)return;
            long chunk=rowBytes*rows;
            // Never consume staging data or advance offsets when another subsystem owns this frame's budget.
            if(!budget.tryAcquire(chunk))return;
            long uploadStart=System.nanoTime();
            RuntimeException problem=null;
            try {
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(upload.target,image,job.mip,0,
                        0,job.row,image.getWidth(),rows,0,job.row);
                job.row+=rows;bytes+=chunk;uploadedBytes+=chunk;
                if(job.row==image.getHeight()) {
                    job.row=0;job.mip++;
                    if(job.mip==upload.data.levels.size()) {job.mip=0;job.texture++;}
                }
            } catch(RuntimeException error) {problem=error;}
            finally {budget.recordElapsed(System.nanoTime()-uploadStart);}
            if(problem!=null) {JOBS.removeFirst();fail(job,problem);continue;}
            if(job.texture==job.uploads.size()) {
                JOBS.removeFirst();
                try {job.complete.run();completedJobs++;}
                catch(RuntimeException error) {fail(job,error);}
            }
        }
    }
    /** Removes all currently queued jobs for this owner by identity; caller failure callback releases staging resources. */
    public static void cancel(Object owner) {
        RenderSystem.assertOnRenderThread();
        List<Job> removed=new ArrayList<>();
        JOBS.removeIf(job->{if(job.owner!=owner)return false;removed.add(job);return true;});
        for(Job job:removed)fail(job,new CancellationException("material staging upload cancelled"));
    }
    private static void fail(Job job,RuntimeException error) {
        failedJobs++;lastFailure=error.toString();
        try {job.failure.accept(error);}
        catch(RuntimeException cleanupFailure) {lastFailure+="; cleanup: "+cleanupFailure;}
    }
    public static Map<String,Object> report() {
        long pendingBytes = 0;
        for (Job job : JOBS) for (int texture = job.texture; texture < job.uploads.size(); texture++) {
            var upload = job.uploads.get(texture);
            for (int mip = texture == job.texture ? job.mip : 0; mip < upload.data.levels.size(); mip++) {
                var image = upload.data.levels.get(mip);
                int row = texture == job.texture && mip == job.mip ? job.row : 0;
                pendingBytes = Math.addExact(pendingBytes, Math.max(0L, (long) (image.getHeight() - row) * image.getWidth() * 4));
            }
        }
        return Map.of("pendingJobs",JOBS.size(),"pendingBytes",pendingBytes,"uploadedBytes",uploadedBytes,"completedJobs",completedJobs,
                "failedJobs",failedJobs,"lastFailure",lastFailure,"maxJobs",MAX_JOBS,
                "status", JOBS.isEmpty() ? "IDLE" : "PENDING");
    }
    private ModelMaterialUploadQueue() {}
}
