package com.mine.geometry_node.client.asset.transfer;

import com.mine.geometry_node.core.engine.system.asset.transfer.AssetTransferLimits;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AssetTransferIoExecutor;
import com.mine.geometry_node.core.engine.system.asset.transfer.io.AtomicAssetCommitter;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferErrorCode;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFailure;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferFileSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferJobSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferSnapshot;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferState;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetFileDownloadRequest;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetFileResponse;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetFileUpload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Client-side file queue. Each request is one complete Minecraft/NeoForge payload. */
public final class ClientAssetTransferService implements AutoCloseable {
    public static final ClientAssetTransferService INSTANCE = new ClientAssetTransferService();
    private static final int HISTORY_LIMIT = 50;

    private final AssetTransferIoExecutor io =
            new AssetTransferIoExecutor("GeometryNode-AssetTransfer-ClientIO", 1, 64);
    private final Map<UUID, ClientJob> jobs = new LinkedHashMap<>();
    private final Map<UUID, ClientFile> filesByTransferId = new LinkedHashMap<>();
    private final Map<UUID, ClientAssetTransferRequest> retryRequestsByTransferId = new LinkedHashMap<>();
    private final Deque<ClientJob> pendingJobs = new ArrayDeque<>();
    private final Deque<AssetTransferFileSnapshot> completedHistory = new ArrayDeque<>();
    private final Deque<AssetTransferFileSnapshot> failedHistory = new ArrayDeque<>();
    private final List<Consumer<AssetTransferSnapshot>> observers = new CopyOnWriteArrayList<>();
    private ClientFile activeFile;
    private long revision;

    private ClientAssetTransferService() {
    }

    public synchronized UUID submit(List<ClientAssetTransferRequest> requests) {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Transfer job cannot be empty");
        AssetTransferDirection direction = requests.getFirst().direction();
        if (requests.stream().anyMatch(request -> request.direction() != direction)) {
            throw new IllegalArgumentException("A transfer job cannot mix upload and download directions");
        }
        ClientJob job = new ClientJob(UUID.randomUUID(), direction, List.copyOf(requests));
        jobs.put(job.jobId, job);
        pendingJobs.addLast(job);
        for (ClientFile file : job.files) filesByTransferId.put(file.transferId, file);
        publish();
        pump();
        return job.jobId;
    }

    public synchronized CompletableFuture<AssetTransferJobSnapshot> completion(UUID jobId) {
        ClientJob job = jobs.get(jobId);
        if (job == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown asset transfer job: " + jobId));
        }
        return job.completion;
    }

    public synchronized UUID retry(UUID transferId) {
        ClientAssetTransferRequest request = retryRequestsByTransferId.get(transferId);
        return request != null ? submit(List.of(request)) : null;
    }

    public synchronized List<UUID> retryAll() {
        Map<AssetTransferDirection, List<ClientAssetTransferRequest>> requests = new LinkedHashMap<>();
        for (AssetTransferFileSnapshot failed : failedHistory) {
            ClientAssetTransferRequest request = retryRequestsByTransferId.get(failed.transferId());
            if (request != null) requests.computeIfAbsent(request.direction(), ignored -> new ArrayList<>()).add(request);
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
        retryRequestsByTransferId.clear();
        publish();
    }

    public void handle(PacketAssetFileResponse packet) {
        ClientFile file;
        synchronized (this) {
            file = filesByTransferId.get(packet.transferId());
            if (file == null || file != activeFile || file.state.isTerminal()) return;
            if (packet.state() != AssetTransferState.COMPLETED) {
                finish(file, packet.state(), failure(packet));
                publish();
                pump();
                return;
            }
            if (file.direction == AssetTransferDirection.UPLOAD) {
                finish(file, AssetTransferState.COMPLETED, warning(packet));
                publish();
                pump();
                return;
            }
            if (packet.sourceSize() != packet.content().length) {
                failLocal(file, AssetTransferErrorCode.IO_FAILURE, "invalid_download_size", "");
                return;
            }
            file.state = AssetTransferState.COMMITTING;
            publish();
        }
        commitDownload(file, packet.content(), warning(packet));
    }

    public synchronized void resetConnection() {
        for (ClientJob job : List.copyOf(jobs.values())) {
            for (ClientFile file : job.files) {
                if (!file.state.isTerminal()) {
                    file.state = AssetTransferState.FAILED;
                    file.failure = new AssetTransferFailure(AssetTransferErrorCode.DISCONNECTED,
                            "geometry_node.asset_transfer.error.disconnected", List.of(), "");
                }
            }
            if (!job.completion.isDone()) job.completion.complete(job.snapshot());
        }
        jobs.clear();
        filesByTransferId.clear();
        retryRequestsByTransferId.clear();
        pendingJobs.clear();
        completedHistory.clear();
        failedHistory.clear();
        activeFile = null;
        revision++;
        publishSnapshot(AssetTransferSnapshot.empty());
    }

    private synchronized void pump() {
        if (activeFile != null) return;
        while (!pendingJobs.isEmpty()) {
            ClientJob job = pendingJobs.removeFirst();
            ClientFile file = job.nextQueuedFile();
            if (file == null) continue;
            activeFile = file;
            file.state = AssetTransferState.TRANSFERRING;
            if (job.hasQueuedFiles()) pendingJobs.addLast(job);
            publish();
            start(file);
            return;
        }
    }

    private void start(ClientFile file) {
        if (file.direction == AssetTransferDirection.DOWNLOAD) {
            send(file, () -> new PacketAssetFileDownloadRequest(
                    file.transferId, file.request.remotePath(), file.request.purpose()));
            return;
        }
        io.submit(() -> readUpload(file.request.localPath())).whenComplete((content, throwable) -> post(() -> {
            synchronized (this) {
                if (file != activeFile || file.state.isTerminal()) return;
                if (throwable != null) {
                    failLocal(file, AssetTransferErrorCode.IO_FAILURE, "read_failed", rootMessage(throwable));
                    return;
                }
            }
            send(file, () -> new PacketAssetFileUpload(file.transferId, file.request.remotePath(),
                    file.request.conflictPolicy(), file.request.purpose(), content));
        }));
    }

    private void send(ClientFile file, Supplier<? extends CustomPacketPayload> payloadFactory) {
        try {
            NetworkHandler.sendToServer(payloadFactory.get());
        } catch (RuntimeException exception) {
            synchronized (this) {
                if (file == activeFile && !file.state.isTerminal()) {
                    failLocal(file, AssetTransferErrorCode.IO_FAILURE,
                            "send_failed", rootMessage(exception));
                }
            }
        }
    }

    private void commitDownload(ClientFile file, byte[] content, AssetTransferFailure warning) {
        io.run(() -> {
            Path target = file.request.localPath();
            Path parent = target.getParent();
            if (parent == null) throw new IOException("Download target has no parent directory");
            Files.createDirectories(parent);
            Path temporary = Files.createTempFile(parent, ".geometrynode-transfer-", ".tmp");
            try {
                Files.write(temporary, content);
                AtomicAssetCommitter.commit(temporary, target, file.request.conflictPolicy());
            } catch (Throwable throwable) {
                Files.deleteIfExists(temporary);
                throw throwable;
            }
        }).whenComplete((ignored, throwable) -> post(() -> {
            synchronized (this) {
                if (file != activeFile || file.state.isTerminal()) return;
                if (throwable != null) {
                    failLocal(file, AssetTransferErrorCode.IO_FAILURE, "commit_failed", rootMessage(throwable));
                    return;
                }
                finish(file, AssetTransferState.COMPLETED, warning);
                publish();
                pump();
            }
        }));
    }

    private static byte[] readUpload(Path source) throws IOException {
        Path normalized = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IOException("Upload source is not a regular file");
        }
        BasicFileAttributes before = Files.readAttributes(
                normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.size() > AssetTransferLimits.MAX_FILE_BYTES) {
            throw new IOException("Asset file exceeds the 64 MiB protocol limit");
        }
        byte[] content = Files.readAllBytes(normalized);
        if (content.length > AssetTransferLimits.MAX_FILE_BYTES) {
            throw new IOException("Asset file exceeds the 64 MiB protocol limit");
        }
        BasicFileAttributes after = Files.readAttributes(
                normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !Objects.equals(before.fileKey(), after.fileKey())) {
            throw new IOException("Upload source changed while it was being read");
        }
        return content;
    }

    private synchronized void failLocal(ClientFile file, AssetTransferErrorCode code, String message, String detail) {
        if (file.state.isTerminal()) return;
        finish(file, AssetTransferState.FAILED, new AssetTransferFailure(
                code, "geometry_node.asset_transfer.error." + message, List.of(), detail));
        publish();
        pump();
    }

    private void finish(ClientFile file, AssetTransferState state, AssetTransferFailure failure) {
        file.state = state;
        file.failure = failure;
        if (activeFile == file) activeFile = null;
        AssetTransferFileSnapshot terminal = file.snapshot();
        if (state == AssetTransferState.COMPLETED) {
            completedHistory.addFirst(terminal);
            trimHistory(completedHistory);
        } else if (state == AssetTransferState.FAILED) {
            failedHistory.addFirst(terminal);
            if (failure != null && failure.isRetryable()) retryRequestsByTransferId.put(file.transferId, file.request);
            trimFailedHistory();
        }
        if (file.job.isTerminal()) {
            AssetTransferJobSnapshot result = file.job.snapshot();
            file.job.completion.complete(result);
            jobs.remove(file.job.jobId, file.job);
            pendingJobs.remove(file.job);
            for (ClientFile completed : file.job.files) filesByTransferId.remove(completed.transferId, completed);
        }
    }

    private static AssetTransferFailure failure(PacketAssetFileResponse packet) {
        AssetTransferErrorCode code = packet.errorCode() == AssetTransferErrorCode.NONE
                ? AssetTransferErrorCode.UNKNOWN : packet.errorCode();
        String key = packet.messageKey().isBlank()
                ? "geometry_node.asset_transfer.error.unknown" : packet.messageKey();
        return new AssetTransferFailure(code, key, List.of(), packet.detail());
    }

    private static AssetTransferFailure warning(PacketAssetFileResponse packet) {
        return packet.errorCode() == AssetTransferErrorCode.NONE ? null : failure(packet);
    }

    private static void trimHistory(Deque<?> history) {
        while (history.size() > HISTORY_LIMIT) history.removeLast();
    }

    private void trimFailedHistory() {
        while (failedHistory.size() > HISTORY_LIMIT) {
            AssetTransferFileSnapshot removed = failedHistory.removeLast();
            retryRequestsByTransferId.remove(removed.transferId());
        }
    }

    private void publish() {
        revision++;
        publishSnapshot(snapshot());
    }

    private void publishSnapshot(AssetTransferSnapshot snapshot) {
        for (Consumer<AssetTransferSnapshot> observer : observers) observer.accept(snapshot);
    }

    private static void post(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null ? "" : current.getMessage() != null
                ? current.getMessage() : current.getClass().getSimpleName();
    }

    @Override
    public void close() {
        resetConnection();
        io.close();
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

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

        private boolean isTerminal() {
            return files.stream().allMatch(file -> file.state.isTerminal());
        }

        private boolean hasQueuedFiles() {
            return files.stream().anyMatch(file -> file.state == AssetTransferState.QUEUED);
        }

        private ClientFile nextQueuedFile() {
            return files.stream().filter(file -> file.state == AssetTransferState.QUEUED).findFirst().orElse(null);
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
        private AssetTransferState state = AssetTransferState.QUEUED;
        private AssetTransferFailure failure;

        private ClientFile(ClientJob job, ClientAssetTransferRequest request) {
            this.job = job;
            this.request = request;
            direction = request.direction();
        }

        private AssetTransferFileSnapshot snapshot() {
            return new AssetTransferFileSnapshot(transferId, direction, request.localPath().toString(),
                    request.remotePath(), state, failure);
        }
    }
}
