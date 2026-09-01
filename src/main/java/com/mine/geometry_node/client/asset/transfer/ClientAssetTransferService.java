package com.mine.geometry_node.client.asset.transfer;

import com.mine.geometry_node.client.ui.persistence.config.AssetTransferConfigAdapter;
import com.mine.geometry_node.client.ui.persistence.config.ConfigManager;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferClientPreferences;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.IncomingAssetTransferFile;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.OutgoingAssetTransferFile;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.VerifiedAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFailure;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFileSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import com.mine.geometry_node.core.engine.system.asset.transfer.service.ByteRateLimiter;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferAccepted;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferQueued;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferAck;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferCancel;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferChunk;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferComplete;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferDownloadChunk;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferDownloadComplete;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferOpen;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferResult;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferServerResult;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferUploadAck;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ClientAssetTransferService implements AutoCloseable {
    public static final ClientAssetTransferService INSTANCE = new ClientAssetTransferService();
    private static final int MAX_ACTIVE_PER_DIRECTION = 2;

    private final AssetTransferIoExecutor io = new AssetTransferIoExecutor("GeometryNode-AssetTransfer-ClientIO", 2, 128);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "GeometryNode-AssetTransfer-ClientRate");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<UUID, ClientJob> jobs = new LinkedHashMap<>();
    private final Map<UUID, ClientFile> filesByTransferId = new LinkedHashMap<>();
    private final Deque<ClientJob> uploadRoundRobin = new ArrayDeque<>();
    private final Deque<ClientJob> downloadRoundRobin = new ArrayDeque<>();
    private final Deque<AssetTransferFileSnapshot> completedHistory = new ArrayDeque<>();
    private final Deque<AssetTransferFileSnapshot> failedHistory = new ArrayDeque<>();
    private final List<Consumer<AssetTransferSnapshot>> observers = new CopyOnWriteArrayList<>();
    private long revision;

    private ClientAssetTransferService() {
        scheduler.scheduleAtFixedRate(() -> post(this::expireIdleFiles), 1L, 1L, TimeUnit.SECONDS);
    }

    public synchronized UUID submit(List<ClientAssetTransferRequest> requests) {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Transfer job cannot be empty");
        AssetTransferDirection direction = requests.getFirst().direction();
        if (requests.stream().anyMatch(request -> request.direction() != direction)) {
            throw new IllegalArgumentException("A transfer job cannot mix upload and download directions");
        }
        ClientJob job = new ClientJob(UUID.randomUUID(), direction, List.copyOf(requests));
        jobs.put(job.jobId, job);
        roundRobin(direction).addLast(job);
        for (ClientFile file : job.files) filesByTransferId.put(file.transferId, file);
        publish();
        pump(direction);
        return job.jobId;
    }

    public synchronized CompletableFuture<AssetTransferJobSnapshot> completion(UUID jobId) {
        ClientJob job = jobs.get(jobId);
        if (job == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown asset transfer job: " + jobId));
        return job.completion;
    }

    public synchronized void cancel(UUID jobId) {
        ClientJob job = jobs.get(jobId);
        if (job == null) return;
        for (ClientFile file : job.files) {
            if (file.state.isTerminal()) continue;
            if (file.started) {
                if (file.state == AssetTransferState.COMMITTING) continue;
                if (!file.cancelRequested) {
                    file.cancelRequested = true;
                    NetworkHandler.sendToServer(new PacketAssetTransferCancel(file.transferId, "cancelled_by_user"));
                }
            } else {
                finish(file, AssetTransferState.CANCELLED, new AssetTransferFailure(
                        AssetTransferErrorCode.CANCELLED, "geometry_node.asset_transfer.error.cancelled", List.of(), ""));
            }
        }
        publish();
        pump(job.direction);
    }

    public synchronized UUID retry(UUID transferId) {
        ClientFile previous = filesByTransferId.get(transferId);
        if (previous == null || previous.failure == null || !previous.failure.isRetryable()) return null;
        return submit(List.of(previous.request));
    }

    public synchronized List<UUID> retryAll() {
        Map<AssetTransferDirection, List<ClientAssetTransferRequest>> requests = new LinkedHashMap<>();
        for (AssetTransferFileSnapshot failed : failedHistory) {
            ClientFile file = filesByTransferId.get(failed.transferId());
            if (file != null && file.failure != null && file.failure.isRetryable()) {
                requests.computeIfAbsent(file.direction, ignored -> new ArrayList<>()).add(file.request);
            }
        }
        List<UUID> jobIds = new ArrayList<>();
        for (List<ClientAssetTransferRequest> directionRequests : requests.values()) {
            jobIds.add(submit(directionRequests));
        }
        return List.copyOf(jobIds);
    }

    public synchronized Subscription subscribe(Consumer<AssetTransferSnapshot> observer) {
        if (observer == null) return () -> { };
        observers.add(observer);
        observer.accept(snapshot());
        return () -> observers.remove(observer);
    }

    public synchronized AssetTransferSnapshot snapshot() {
        List<AssetTransferJobSnapshot> active = jobs.values().stream()
                .filter(job -> !job.isTerminal()).map(ClientJob::snapshot).toList();
        return new AssetTransferSnapshot(revision, active, List.copyOf(completedHistory), List.copyOf(failedHistory));
    }

    public synchronized void clearCompletedHistory() {
        completedHistory.clear();
        publish();
    }

    public synchronized void clearFailedHistory() {
        failedHistory.clear();
        publish();
    }

    public void handle(PacketAssetTransferAccepted packet) {
        ClientFile file;
        synchronized (this) {
            file = filesByTransferId.get(packet.transferId());
            if (file == null || file.state.isTerminal() || file.cancelRequested) return;
            if (file.direction == AssetTransferDirection.DOWNLOAD
                    && packet.totalBytes() > preferences().maxDownloadFileBytes()) {
                failLocal(file, AssetTransferErrorCode.FILE_TOO_LARGE, "file_too_large", "");
                return;
            }
            file.totalBytes = packet.totalBytes();
            file.sha256 = packet.sha256();
            file.chunkBytes = packet.acceptedChunkBytes();
            file.state = AssetTransferState.TRANSFERRING;
            file.touch();
            publish();
        }
        if (file.direction == AssetTransferDirection.UPLOAD) sendNextUploadChunk(file);
        else prepareIncomingDownload(file);
    }

    public synchronized void handle(PacketAssetTransferQueued packet) {
        ClientFile file = filesByTransferId.get(packet.transferId());
        if (file == null || file.state.isTerminal() || file.cancelRequested) return;
        file.state = AssetTransferState.QUEUED;
        publish();
    }

    public void handle(PacketAssetTransferUploadAck packet) {
        ClientFile file;
        synchronized (this) {
            file = filesByTransferId.get(packet.transferId());
            if (file == null || file.direction != AssetTransferDirection.UPLOAD
                    || file.state.isTerminal() || file.cancelRequested) return;
            if (packet.nextSequence() != file.nextSequence || packet.nextOffset() != file.transferredBytes) {
                failLocal(file, AssetTransferErrorCode.INVALID_SEQUENCE, "invalid_acknowledgement", "");
                return;
            }
            file.sendInProgress = false;
            file.touch();
            publish();
        }
        sendNextUploadChunk(file);
    }

    public void handle(PacketAssetTransferDownloadChunk packet) {
        ClientFile file;
        synchronized (this) {
            file = filesByTransferId.get(packet.transferId());
            if (file == null || file.direction != AssetTransferDirection.DOWNLOAD || file.state.isTerminal()
                    || file.cancelRequested || file.incoming == null) return;
            file.touch();
        }
        io.submit(() -> file.incoming.writeChunk(packet.sequence(), packet.offset(), packet.content()))
                .whenComplete((ignored, throwable) -> post(() -> {
                    if (throwable != null) {
                        failLocal(file, AssetTransferErrorCode.INVALID_SEQUENCE, "invalid_sequence", rootMessage(throwable));
                        return;
                    }
                    synchronized (this) {
                        if (file.cancelRequested || file.state.isTerminal()) return;
                        file.transferredBytes = file.incoming.nextOffset();
                        file.nextSequence = file.incoming.nextSequence();
                        publish();
                    }
                    long delay = preferences().downloadRateBytesPerSecond() == 0L ? 0L
                            : file.downloadLimiter.reserveDelayNanos(packet.content().length);
                    scheduler.schedule(() -> post(() -> {
                        synchronized (this) {
                            if (file.cancelRequested || file.state.isTerminal()) return;
                        }
                        NetworkHandler.sendToServer(new PacketAssetTransferAck(
                                file.transferId, file.nextSequence, file.transferredBytes));
                    }), delay, TimeUnit.NANOSECONDS);
                }));
    }

    public void handle(PacketAssetTransferDownloadComplete packet) {
        ClientFile file;
        synchronized (this) {
            file = filesByTransferId.get(packet.transferId());
            if (file == null || file.direction != AssetTransferDirection.DOWNLOAD
                    || file.state.isTerminal() || file.cancelRequested) return;
            file.state = AssetTransferState.VERIFYING;
            publish();
        }
        io.run(() -> {
            file.incoming.verifyAndClose();
            Path temporary = file.incoming.retainVerifiedFile();
            try {
                synchronized (this) {
                    if (file.cancelRequested || file.state.isTerminal()) {
                        Files.deleteIfExists(temporary);
                        return;
                    }
                    file.state = AssetTransferState.COMMITTING;
                }
                VerifiedAssetCommitter.commit(temporary, file.request.localPath(), file.request.conflictPolicy());
            } catch (Throwable throwable) {
                Files.deleteIfExists(temporary);
                throw throwable;
            }
        }).whenComplete((ignored, throwable) -> post(() -> {
            if (throwable != null) {
                failLocal(file, AssetTransferErrorCode.HASH_MISMATCH, "verification_or_commit_failed", rootMessage(throwable));
                return;
            }
            synchronized (this) {
                if (file.cancelRequested || file.state.isTerminal()) return;
                finish(file, AssetTransferState.COMPLETED, null);
                publish();
                pump(file.direction);
            }
            NetworkHandler.sendToServer(new PacketAssetTransferResult(file.transferId, AssetTransferState.COMPLETED,
                    AssetTransferErrorCode.NONE, "", ""));
        }));
    }

    public synchronized void handle(PacketAssetTransferServerResult packet) {
        ClientFile file = filesByTransferId.get(packet.transferId());
        if (file == null || file.state.isTerminal()) return;
        AssetTransferFailure failure = packet.state() == AssetTransferState.COMPLETED ? null : new AssetTransferFailure(
                packet.errorCode(), packet.messageKey(), List.of(), packet.detail());
        finish(file, packet.state(), failure);
        publish();
        pump(file.direction);
    }

    public synchronized void resetConnection() {
        for (ClientJob job : jobs.values()) {
            for (ClientFile file : job.files) {
                if (!file.state.isTerminal()) finish(file, AssetTransferState.CANCELLED,
                        new AssetTransferFailure(AssetTransferErrorCode.DISCONNECTED,
                                "geometry_node.asset_transfer.error.disconnected", List.of(), ""));
            }
        }
        jobs.clear();
        filesByTransferId.clear();
        uploadRoundRobin.clear();
        downloadRoundRobin.clear();
        completedHistory.clear();
        failedHistory.clear();
        revision++;
        publishSnapshot(AssetTransferSnapshot.empty());
    }

    private synchronized void pump(AssetTransferDirection direction) {
        long active = filesByTransferId.values().stream()
                .filter(file -> file.direction == direction && file.started && !file.state.isTerminal()).count();
        if (active >= MAX_ACTIVE_PER_DIRECTION) return;
        Deque<ClientJob> roundRobin = roundRobin(direction);
        while (active < MAX_ACTIVE_PER_DIRECTION && !roundRobin.isEmpty()) {
            ClientJob job = roundRobin.removeFirst();
            ClientFile file = job.nextQueuedFile();
            if (file != null) {
                file.started = true;
                file.startedAtNanos = System.nanoTime();
                file.state = AssetTransferState.PREFLIGHT;
                active++;
                start(file);
            }
            if (job.hasQueuedFiles()) roundRobin.addLast(job);
        }
        publish();
    }

    private void start(ClientFile file) {
        if (file.direction == AssetTransferDirection.DOWNLOAD) {
            NetworkHandler.sendToServer(new PacketAssetTransferOpen(file.transferId, file.direction,
                    file.request.remotePath(), 0L, "", preferences().preferredChunkBytes(),
                    file.request.conflictPolicy(), file.request.purpose()));
            return;
        }
        AssetTransferClientPreferences preferences = preferences();
        io.submit(() -> OutgoingAssetTransferFile.open(
                        file.request.localPath(), preferences.maxUploadFileBytes()))
                .whenComplete((outgoing, throwable) -> post(() -> {
                    if (throwable != null) {
                        failLocal(file, AssetTransferErrorCode.IO_FAILURE, "open_failed", rootMessage(throwable));
                        return;
                    }
                    synchronized (this) {
                        if (file.state.isTerminal()) { closeQuietly(outgoing); return; }
                        file.outgoing = outgoing;
                        file.totalBytes = outgoing.totalBytes();
                        file.sourceLastModified = outgoing.lastModifiedMillis();
                        file.sha256 = outgoing.sha256();
                        publish();
                    }
                    NetworkHandler.sendToServer(new PacketAssetTransferOpen(file.transferId, file.direction,
                            file.request.remotePath(), file.totalBytes, file.sha256,
                            preferences.preferredChunkBytes(), file.request.conflictPolicy(), file.request.purpose()));
                }));
    }

    private void prepareIncomingDownload(ClientFile file) {
        Path parent = file.request.localPath().getParent();
        Path temporaryDirectory = parent != null ? parent.resolve(".geometrynode-transfer")
                : file.request.localPath().toAbsolutePath().getParent().resolve(".geometrynode-transfer");
        io.submit(() -> IncomingAssetTransferFile.create(temporaryDirectory, file.totalBytes, file.sha256))
                .whenComplete((incoming, throwable) -> post(() -> {
                    if (throwable != null) {
                        failLocal(file, AssetTransferErrorCode.IO_FAILURE, "open_failed", rootMessage(throwable));
                        return;
                    }
                    synchronized (this) {
                        if (file.state.isTerminal() || file.cancelRequested) { closeQuietly(incoming); return; }
                        file.incoming = incoming;
                    }
                    NetworkHandler.sendToServer(new PacketAssetTransferAck(file.transferId, 0, 0L));
                }));
    }

    private void sendNextUploadChunk(ClientFile file) {
        synchronized (this) {
            if (file.state.isTerminal() || file.cancelRequested || file.sendInProgress) return;
            if (file.transferredBytes >= file.totalBytes) {
                file.state = AssetTransferState.VERIFYING;
                publish();
                NetworkHandler.sendToServer(new PacketAssetTransferComplete(file.transferId));
                return;
            }
            file.sendInProgress = true;
        }
        long offset = file.transferredBytes;
        int sequence = file.nextSequence;
        io.submit(() -> file.outgoing.readChunk(offset, file.chunkBytes)).whenComplete((content, throwable) -> {
            if (throwable != null) {
                post(() -> failLocal(file, AssetTransferErrorCode.IO_FAILURE, "read_failed", rootMessage(throwable)));
                return;
            }
            long delay = file.uploadLimiter.reserveDelayNanos(content.length);
            scheduler.schedule(() -> post(() -> {
                synchronized (this) {
                    if (file.state.isTerminal() || file.cancelRequested) return;
                    file.nextSequence++;
                    file.transferredBytes += content.length;
                    publish();
                }
                NetworkHandler.sendToServer(new PacketAssetTransferChunk(file.transferId, sequence, offset, content));
            }), delay, TimeUnit.NANOSECONDS);
        });
    }

    private synchronized void failLocal(ClientFile file, AssetTransferErrorCode code, String message, String detail) {
        if (file.state.isTerminal()) return;
        NetworkHandler.sendToServer(new PacketAssetTransferCancel(file.transferId, message));
        finish(file, AssetTransferState.FAILED, new AssetTransferFailure(code,
                "geometry_node.asset_transfer.error." + message, List.of(), detail));
        publish();
        pump(file.direction);
    }

    private void finish(ClientFile file, AssetTransferState state, AssetTransferFailure failure) {
        file.state = state;
        file.failure = failure;
        closeQuietly(file.incoming);
        closeQuietly(file.outgoing);
        AssetTransferFileSnapshot terminal = file.snapshot();
        AssetTransferClientPreferences preferences = preferences();
        if (state == AssetTransferState.COMPLETED) {
            completedHistory.addFirst(terminal);
            trim(completedHistory, preferences.completedHistoryLimit());
        } else if (state == AssetTransferState.FAILED) {
            failedHistory.addFirst(terminal);
            trim(failedHistory, preferences.failedHistoryLimit());
        }
        if (file.job.isTerminal()) file.job.completion.complete(file.job.snapshot());
    }

    private void publish() {
        revision++;
        publishSnapshot(snapshot());
    }

    private void publishSnapshot(AssetTransferSnapshot snapshot) {
        for (Consumer<AssetTransferSnapshot> observer : observers) observer.accept(snapshot);
    }

    private synchronized void expireIdleFiles() {
        long deadline = System.nanoTime() - TimeUnit.SECONDS.toNanos(30L);
        List<ClientFile> expired = filesByTransferId.values().stream()
                .filter(file -> file.started && file.state != AssetTransferState.PREFLIGHT
                        && file.state != AssetTransferState.QUEUED
                        && !file.state.isTerminal() && file.lastActivityNanos < deadline).toList();
        for (ClientFile file : expired) {
            failLocal(file, AssetTransferErrorCode.TIMEOUT, "timeout", "");
        }
    }

    private Deque<ClientJob> roundRobin(AssetTransferDirection direction) {
        return direction == AssetTransferDirection.UPLOAD ? uploadRoundRobin : downloadRoundRobin;
    }

    private AssetTransferClientPreferences preferences() {
        return AssetTransferConfigAdapter.from(ConfigManager.INSTANCE.getConfig().networkTransfer);
    }

    private static void trim(Deque<?> values, int limit) {
        while (values.size() > limit) values.removeLast();
    }

    private static void post(Runnable task) { Minecraft.getInstance().execute(task); }
    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null ? "" : (current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName());
    }
    private static void closeQuietly(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Exception ignored) { }
    }

    @Override public void close() {
        resetConnection();
        scheduler.shutdownNow();
        io.close();
    }

    @FunctionalInterface public interface Subscription extends AutoCloseable { @Override void close(); }

    private final class ClientJob {
        private final UUID jobId;
        private final AssetTransferDirection direction;
        private final List<ClientFile> files;
        private final long createdAt = System.currentTimeMillis();
        private final CompletableFuture<AssetTransferJobSnapshot> completion = new CompletableFuture<>();
        private ClientJob(UUID jobId, AssetTransferDirection direction, List<ClientAssetTransferRequest> requests) {
            this.jobId = jobId;
            this.direction = direction;
            files = requests.stream().map(request -> new ClientFile(this, request)).toList();
        }
        private boolean isTerminal() { return files.stream().allMatch(file -> file.state.isTerminal()); }
        private boolean hasQueuedFiles() {
            return files.stream().anyMatch(file -> !file.started && file.state == AssetTransferState.QUEUED);
        }
        private ClientFile nextQueuedFile() {
            return files.stream().filter(file -> !file.started && file.state == AssetTransferState.QUEUED)
                    .findFirst().orElse(null);
        }
        private AssetTransferJobSnapshot snapshot() {
            return new AssetTransferJobSnapshot(jobId, direction, files.stream().map(ClientFile::snapshot).toList(), createdAt);
        }
    }

    private final class ClientFile {
        private final ClientJob job;
        private final ClientAssetTransferRequest request;
        private final UUID transferId = UUID.randomUUID();
        private final AssetTransferDirection direction;
        private final ByteRateLimiter uploadLimiter;
        private final ByteRateLimiter downloadLimiter;
        private AssetTransferState state = AssetTransferState.QUEUED;
        private AssetTransferFailure failure;
        private OutgoingAssetTransferFile outgoing;
        private IncomingAssetTransferFile incoming;
        private String sha256 = "";
        private long totalBytes;
        private long sourceLastModified;
        private long transferredBytes;
        private int chunkBytes;
        private int nextSequence;
        private boolean started;
        private boolean sendInProgress;
        private boolean cancelRequested;
        private long startedAtNanos;
        private long lastActivityNanos = System.nanoTime();
        private ClientFile(ClientJob job, ClientAssetTransferRequest request) {
            this.job = job;
            this.request = request;
            direction = request.direction();
            AssetTransferClientPreferences preferences = preferences();
            chunkBytes = preferences.preferredChunkBytes();
            uploadLimiter = new ByteRateLimiter(preferences.uploadRateBytesPerSecond());
            downloadLimiter = new ByteRateLimiter(preferences.downloadRateBytesPerSecond());
        }
        private void touch() { lastActivityNanos = System.nanoTime(); }
        private AssetTransferFileSnapshot snapshot() {
            long elapsed = startedAtNanos == 0L ? 0L : Math.max(1L, System.nanoTime() - startedAtNanos);
            long speed = elapsed == 0L ? 0L : (long) (transferredBytes * 1_000_000_000.0 / elapsed);
            return new AssetTransferFileSnapshot(transferId, direction, request.localPath().toString(),
                    request.remotePath(), state, totalBytes, transferredBytes, speed, failure);
        }
    }

}
