package com.mine.geometry_node.client.asset.file;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Coordinates local asset structure changes with open asset sessions. */
public final class AssetFileTransactionManager {
    public static final AssetFileTransactionManager INSTANCE = new AssetFileTransactionManager();

    private final ReentrantReadWriteLock mStructureLock = new ReentrantReadWriteLock(true);
    private final CopyOnWriteArrayList<MutationParticipant> mParticipants = new CopyOnWriteArrayList<>();

    private AssetFileTransactionManager() {
    }

    public void registerParticipant(MutationParticipant participant) {
        if (participant != null) mParticipants.addIfAbsent(participant);
    }

    public <T> T mutateStructure(IOCallable<T> action) throws IOException {
        Lock lock = mStructureLock.writeLock();
        lock.lock();
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    public <T> T mutate(MutationPlan plan, IOCallable<T> action) throws IOException {
        MutationPlan resolvedPlan = plan != null ? plan : MutationPlan.empty();
        if (resolvedPlan.isEmpty()) return mutateStructure(action);
        Lock lock = mStructureLock.writeLock();
        lock.lock();
        List<PreparedMutation> prepared = new ArrayList<>();
        try {
            try {
                for (MutationParticipant participant : mParticipants) {
                    PreparedMutation mutation = participant.prepare(resolvedPlan);
                    if (mutation != null) prepared.add(mutation);
                }
                T result = action.call();
                for (PreparedMutation mutation : prepared) mutation.commit();
                return result;
            } catch (IOException | RuntimeException e) {
                for (int i = prepared.size() - 1; i >= 0; i--) {
                    try {
                        prepared.get(i).rollback();
                    } catch (RuntimeException rollbackError) {
                        e.addSuppressed(rollbackError);
                    }
                }
                throw e;
            }
        } finally {
            lock.unlock();
        }
    }

    public <T> T readStructure(Supplier<T> action) {
        Lock lock = mStructureLock.readLock();
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public <T> T readStructureIO(IOCallable<T> action) throws IOException {
        Lock lock = mStructureLock.readLock();
        lock.lock();
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    public static MutationPlan moves(Map<Path, Path> moves) {
        return new MutationPlan(moves, List.of());
    }

    public static MutationPlan deletes(List<Path> deletes) {
        return new MutationPlan(Map.of(), deletes);
    }

    public record MutationPlan(Map<Path, Path> moves, List<Path> deletes) {
        public MutationPlan {
            Map<Path, Path> normalizedMoves = new LinkedHashMap<>();
            if (moves != null) {
                moves.forEach((source, destination) -> {
                    if (source != null && destination != null) {
                        normalizedMoves.put(normalize(source), normalize(destination));
                    }
                });
            }
            moves = Map.copyOf(normalizedMoves);
            deletes = deletes == null ? List.of() : deletes.stream()
                    .filter(path -> path != null)
                    .map(AssetFileTransactionManager::normalize)
                    .toList();
        }

        public static MutationPlan empty() {
            return new MutationPlan(Map.of(), List.of());
        }

        public boolean isEmpty() {
            return moves.isEmpty() && deletes.isEmpty();
        }
    }

    @FunctionalInterface
    public interface MutationParticipant {
        PreparedMutation prepare(MutationPlan plan) throws IOException;
    }

    public interface PreparedMutation {
        void commit();

        void rollback();
    }

    @FunctionalInterface
    public interface IOCallable<T> {
        T call() throws IOException;
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
