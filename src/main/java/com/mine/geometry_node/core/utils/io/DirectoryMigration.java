package com.mine.geometry_node.core.utils.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Resumable directory moves: plan conflicts before changing files and never overwrite a target. */
public final class DirectoryMigration {
    private DirectoryMigration() {}

    public static synchronized void move(Path source, Path target) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        Path from = source.toAbsolutePath().normalize();
        Path to = target.toAbsolutePath().normalize();
        if (to.startsWith(from) || from.startsWith(to))
            throw new IOException("Migration directories must not overlap: " + source + " -> " + target);
        rejectLinkedAncestors(target);
        Plan plan = new Plan();
        plan.add(source, target);
        plan.execute();
    }

    /** Moves legacy repository entries while leaving sibling resource stores in place. */
    public static synchronized void moveContents(Path source, Path target, Set<String> retained) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        requireDirectory(source);
        rejectLinkedAncestors(target);
        Path destination = target.toAbsolutePath().normalize();
        Plan plan = new Plan();
        try (var entries = Files.list(source)) {
            for (Path entry : entries.toList()) {
                if (!retained.contains(entry.getFileName().toString())) {
                    if (destination.startsWith(entry.toAbsolutePath().normalize()))
                        throw new IOException("Migration target must be excluded from its source entries: " + target);
                    plan.add(entry, target.resolve(entry.getFileName()));
                }
            }
        }
        plan.execute();
    }

    private static void requireDirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Migration directory is not a regular directory: " + path);
    }

    private static void rejectLinkedAncestors(Path target) throws IOException {
        for (Path parent = target.toAbsolutePath().normalize(); parent != null; parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) throw new IOException("Migration target traverses a symbolic link: " + target);
        }
    }

    private record Move(Path source, Path target) {}

    private static final class Plan {
        private final List<Move> moves = new ArrayList<>();
        private final List<Path> emptyDirectories = new ArrayList<>();

        void add(Path source, Path target) throws IOException {
            if (Files.isSymbolicLink(source) || Files.isSymbolicLink(target))
                throw new IOException("Cannot migrate symbolic links: " + source + " -> " + target);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                moves.add(new Move(source, target));
                return;
            }
            if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Migration target already exists; neither file was replaced: " + target);
            try (var entries = Files.list(source)) {
                for (Path entry : entries.toList()) add(entry, target.resolve(entry.getFileName()));
            }
            emptyDirectories.add(source);
        }

        void execute() throws IOException {
            for (Move move : moves) {
                Files.createDirectories(move.target().getParent());
                Files.move(move.source(), move.target());
            }
            for (Path directory : emptyDirectories) Files.delete(directory);
        }
    }
}
