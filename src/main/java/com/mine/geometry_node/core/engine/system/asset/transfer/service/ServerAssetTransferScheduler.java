package com.mine.geometry_node.core.engine.system.asset.transfer.service;

import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferServerPolicy;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferDirection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Assigns bounded transfer slots fairly by rotating between players with queued work. */
final class ServerAssetTransferScheduler {
    private final Lane uploads = new Lane(AssetTransferDirection.UPLOAD);
    private final Lane downloads = new Lane(AssetTransferDirection.DOWNLOAD);
    private final Map<TransferKey, Ticket> tickets = new HashMap<>();
    private Limits limits = Limits.from(AssetTransferServerPolicy.defaults());
    private int queuedCount;

    synchronized Submission submit(TransferKey key, AssetTransferDirection direction,
                                   AssetTransferServerPolicy policy) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(direction, "direction");
        limits = Limits.from(policy);
        if (tickets.containsKey(key)) return new Submission(Disposition.DUPLICATE, List.of());

        Lane lane = lane(direction);
        Ticket ticket = new Ticket(key, direction);
        if (lane.waitingPlayers.isEmpty() && lane.canAdmit(ticket, limits)) {
            tickets.put(key, ticket);
            lane.admit(ticket);
            return new Submission(Disposition.ADMITTED, List.of(key));
        }
        tickets.put(key, ticket);
        lane.enqueue(ticket);
        queuedCount++;
        List<TransferKey> promoted = pump(lane);
        if (!ticket.active && queuedCount > limits.maxQueued()) {
            tickets.remove(key);
            if (lane.removeQueued(ticket)) queuedCount--;
            return new Submission(Disposition.FULL, promoted);
        }
        return new Submission(ticket.active ? Disposition.ADMITTED : Disposition.QUEUED, promoted);
    }

    synchronized List<TransferKey> remove(TransferKey key) {
        Ticket ticket = tickets.remove(key);
        if (ticket == null) return List.of();
        Lane lane = lane(ticket.direction);
        if (ticket.active) lane.release(ticket);
        else if (lane.removeQueued(ticket)) queuedCount--;
        return pump(lane);
    }

    synchronized List<TransferKey> removePlayer(UUID playerId) {
        List<Ticket> owned = tickets.entrySet().stream()
                .filter(entry -> entry.getKey().playerId.equals(playerId))
                .map(Map.Entry::getValue).toList();
        for (Ticket ticket : owned) {
            tickets.remove(ticket.key);
            Lane lane = lane(ticket.direction);
            if (ticket.active) lane.release(ticket);
            else if (lane.removeQueued(ticket)) queuedCount--;
        }
        List<TransferKey> promoted = new ArrayList<>(pump(uploads));
        promoted.addAll(pump(downloads));
        return List.copyOf(promoted);
    }

    synchronized void clear() {
        tickets.clear();
        uploads.clear();
        downloads.clear();
        queuedCount = 0;
    }

    private List<TransferKey> pump(Lane lane) {
        List<TransferKey> promoted = new ArrayList<>();
        int stalledPlayers = 0;
        while (!lane.waitingPlayers.isEmpty() && lane.active.size() < limits.global(lane.direction)) {
            UUID playerId = lane.waitingPlayers.removeFirst();
            ArrayDeque<Ticket> queue = lane.queuedByPlayer.get(playerId);
            if (queue == null || queue.isEmpty()) {
                lane.queuedByPlayer.remove(playerId);
                continue;
            }
            Ticket candidate = queue.peekFirst();
            if (!lane.canAdmit(candidate, limits)) {
                lane.waitingPlayers.addLast(playerId);
                if (++stalledPlayers >= lane.waitingPlayers.size()) break;
                continue;
            }

            stalledPlayers = 0;
            queue.removeFirst();
            queuedCount--;
            lane.admit(candidate);
            promoted.add(candidate.key);
            if (queue.isEmpty()) lane.queuedByPlayer.remove(playerId);
            else lane.waitingPlayers.addLast(playerId);
        }
        return List.copyOf(promoted);
    }

    private Lane lane(AssetTransferDirection direction) {
        return direction == AssetTransferDirection.UPLOAD ? uploads : downloads;
    }

    enum Disposition { ADMITTED, QUEUED, FULL, DUPLICATE }

    record TransferKey(UUID playerId, UUID transferId) {
        TransferKey {
            playerId = Objects.requireNonNull(playerId, "playerId");
            transferId = Objects.requireNonNull(transferId, "transferId");
        }
    }

    record Submission(Disposition disposition, List<TransferKey> promoted) { }

    private record Limits(int uploadsPerPlayer, int downloadsPerPlayer,
                          int uploadsGlobal, int downloadsGlobal, int maxQueued) {
        private static Limits from(AssetTransferServerPolicy policy) {
            return new Limits(policy.maxConcurrentUploadsPerPlayer(), policy.maxConcurrentDownloadsPerPlayer(),
                    policy.maxConcurrentUploadsGlobal(), policy.maxConcurrentDownloadsGlobal(),
                    policy.maxQueuedTransfers());
        }

        private int perPlayer(AssetTransferDirection direction) {
            return direction == AssetTransferDirection.UPLOAD ? uploadsPerPlayer : downloadsPerPlayer;
        }

        private int global(AssetTransferDirection direction) {
            return direction == AssetTransferDirection.UPLOAD ? uploadsGlobal : downloadsGlobal;
        }
    }

    private static final class Ticket {
        private final TransferKey key;
        private final AssetTransferDirection direction;
        private boolean active;

        private Ticket(TransferKey key, AssetTransferDirection direction) {
            this.key = key;
            this.direction = direction;
        }
    }

    private static final class Lane {
        private final AssetTransferDirection direction;
        private final Map<TransferKey, Ticket> active = new LinkedHashMap<>();
        private final Map<UUID, Integer> activeByPlayer = new HashMap<>();
        private final Map<UUID, ArrayDeque<Ticket>> queuedByPlayer = new LinkedHashMap<>();
        private final ArrayDeque<UUID> waitingPlayers = new ArrayDeque<>();

        private Lane(AssetTransferDirection direction) {
            this.direction = direction;
        }

        private boolean canAdmit(Ticket ticket, Limits limits) {
            return active.size() < limits.global(direction)
                    && activeByPlayer.getOrDefault(ticket.key.playerId, 0) < limits.perPlayer(direction);
        }

        private void admit(Ticket ticket) {
            ticket.active = true;
            active.put(ticket.key, ticket);
            activeByPlayer.merge(ticket.key.playerId, 1, Integer::sum);
        }

        private void enqueue(Ticket ticket) {
            ArrayDeque<Ticket> queue = queuedByPlayer.computeIfAbsent(ticket.key.playerId, ignored -> {
                waitingPlayers.addLast(ticket.key.playerId);
                return new ArrayDeque<>();
            });
            queue.addLast(ticket);
        }

        private void release(Ticket ticket) {
            if (active.remove(ticket.key) == null) return;
            activeByPlayer.computeIfPresent(ticket.key.playerId, (ignored, count) -> count > 1 ? count - 1 : null);
        }

        private boolean removeQueued(Ticket ticket) {
            ArrayDeque<Ticket> queue = queuedByPlayer.get(ticket.key.playerId);
            if (queue == null || !queue.remove(ticket)) return false;
            if (queue.isEmpty()) {
                queuedByPlayer.remove(ticket.key.playerId);
                waitingPlayers.remove(ticket.key.playerId);
            }
            return true;
        }

        private void clear() {
            active.clear();
            activeByPlayer.clear();
            queuedByPlayer.clear();
            waitingPlayers.clear();
        }
    }
}
