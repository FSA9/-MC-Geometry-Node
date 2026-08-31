package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Extends committed repository mutations without coupling the file store to asset-specific services. */
public final class RemoteAssetMutationRegistry {
    public static final RemoteAssetMutationRegistry INSTANCE = new RemoteAssetMutationRegistry();

    private final List<Participant> participants = new CopyOnWriteArrayList<>();

    private RemoteAssetMutationRegistry() {
    }

    public void register(Participant participant) {
        if (participant == null) throw new IllegalArgumentException("participant must not be null");
        if (!participants.contains(participant)) participants.add(participant);
    }

    public PreparedMutation prepare(MinecraftServer server, Operation operation, Path source, Path target)
            throws Exception {
        List<PreparedMutation> prepared = new java.util.ArrayList<>(participants.size());
        try {
            for (Participant participant : participants) {
                prepared.add(participant.prepare(server, operation, source, target));
            }
            return new CompositePreparedMutation(List.copyOf(prepared));
        } catch (Exception exception) {
            rollbackReverse(prepared, exception);
            throw exception;
        }
    }

    private static void rollbackReverse(List<PreparedMutation> prepared, Exception primary) {
        for (int i = prepared.size() - 1; i >= 0; i--) {
            try {
                prepared.get(i).rollback();
            } catch (RuntimeException rollbackException) {
                primary.addSuppressed(rollbackException);
            }
        }
    }

    public enum Operation {
        COPY,
        MOVE,
        RENAME,
        DELETE
    }

    @FunctionalInterface
    public interface Participant {
        PreparedMutation prepare(MinecraftServer server, Operation operation, Path source, Path target)
                throws Exception;
    }

    public interface PreparedMutation {
        PreparedMutation NONE = new PreparedMutation() {
        };

        default void commit() {
        }

        default void rollback() {
        }
    }

    private record CompositePreparedMutation(List<PreparedMutation> delegates) implements PreparedMutation {
        @Override
        public void commit() {
            for (PreparedMutation delegate : delegates) {
                try {
                    delegate.commit();
                } catch (RuntimeException exception) {
                    System.err.println("[RemoteAsset] Mutation participant commit failed: "
                            + exception.getMessage());
                }
            }
        }

        @Override
        public void rollback() {
            RuntimeException failure = null;
            for (int i = delegates.size() - 1; i >= 0; i--) {
                try {
                    delegates.get(i).rollback();
                } catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
