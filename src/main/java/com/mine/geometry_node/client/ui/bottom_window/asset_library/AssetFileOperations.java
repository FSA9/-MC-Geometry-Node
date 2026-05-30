package com.mine.geometry_node.client.ui.bottom_window.asset_library;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class AssetFileOperations {

    private AssetFileOperations() {
    }

    static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        Path path = file.toPath();
        if (Files.isDirectory(path) && !Files.isSymbolicLink(path)) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Unable to list directory: " + file.getAbsolutePath());
            }
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        Files.deleteIfExists(path);
    }

    static File copyRecursively(File source, File destination) throws IOException {
        validateSource(source);
        File actualDestination = resolveDestinationFor(source, destination);
        ensureNotNestedInSource(source, actualDestination);
        copyRecursivelyInternal(source, actualDestination);
        return actualDestination;
    }

    static File moveRecursively(File source, File destination) throws IOException {
        validateSource(source);
        File actualDestination = resolveDestinationFor(source, destination);
        ensureNotNestedInSource(source, actualDestination);
        ensureParentDirectory(actualDestination);

        if (source.renameTo(actualDestination)) {
            return actualDestination;
        }

        copyRecursivelyInternal(source, actualDestination);
        try {
            deleteRecursively(source);
        } catch (IOException deleteException) {
            IOException moveException = new IOException(
                    "Copied but failed to delete source: " + source.getAbsolutePath(), deleteException);
            throw moveException;
        }
        return actualDestination;
    }

    static File resolveAvailableDestination(File directory, String sourceName) {
        validateSourceName(sourceName);
        File candidate = new File(directory, sourceName);
        boolean directoryName = candidate.exists() && candidate.isDirectory();
        return resolveAvailableDestination(directory, sourceName, directoryName);
    }

    static File resolveAvailableDestination(File directory, String sourceName, boolean directoryName) {
        if (directory == null) {
            directory = new File(".");
        }
        validateSourceName(sourceName);

        File candidate = new File(directory, sourceName);
        if (!candidate.exists()) {
            return candidate;
        }

        String baseName = sourceName;
        String extension = "";
        if (!directoryName) {
            int dotIndex = sourceName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = sourceName.substring(0, dotIndex);
                extension = sourceName.substring(dotIndex);
            }
        }

        int counter = 1;
        File resolved;
        do {
            resolved = new File(directory, baseName + "_" + counter + extension);
            counter++;
        } while (resolved.exists());
        return resolved;
    }

    private static void copyRecursivelyInternal(File source, File destination) throws IOException {
        ensureParentDirectory(destination);

        Path sourcePath = source.toPath();
        Path destinationPath = destination.toPath();
        if (Files.isDirectory(sourcePath) && !Files.isSymbolicLink(sourcePath)) {
            Files.createDirectory(destinationPath);
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Unable to list directory: " + source.getAbsolutePath());
            }
            for (File child : children) {
                copyRecursivelyInternal(child, new File(destination, child.getName()));
            }
        } else {
            Files.copy(sourcePath, destinationPath, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static File resolveDestinationFor(File source, File destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (destination.exists()) {
            return resolveAvailableDestination(destination.getParentFile(), destination.getName(), source.isDirectory());
        }
        return destination;
    }

    private static void validateSource(File source) throws IOException {
        if (source == null) {
            throw new IOException("Source file is null");
        }
        if (!source.exists()) {
            throw new IOException("Source file does not exist: " + source.getAbsolutePath());
        }
    }

    private static void validateSourceName(String sourceName) {
        if (sourceName == null || sourceName.isEmpty()) {
            throw new IllegalArgumentException("sourceName must not be empty");
        }
    }

    private static void ensureParentDirectory(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
    }

    private static void ensureNotNestedInSource(File source, File destination) throws IOException {
        Path sourcePath = source.toPath().toAbsolutePath().normalize();
        Path destinationPath = destination.toPath().toAbsolutePath().normalize();
        if (source.isDirectory() && destinationPath.startsWith(sourcePath)) {
            throw new IOException("Destination is inside source directory: " + destination.getAbsolutePath());
        }
    }
}
