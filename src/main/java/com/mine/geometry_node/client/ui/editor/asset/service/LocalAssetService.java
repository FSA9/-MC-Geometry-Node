package com.mine.geometry_node.client.ui.editor.asset.service;

import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskContext;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphUploadFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LocalAssetService {

    public FileOperationResult deleteFiles(List<File> files, AssetTaskContext context) throws InterruptedException {
        List<File> sources = snapshotFiles(files);
        List<File> successfulFiles = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        int total = sources.size();
        int processed = 0;

        for (File file : sources) {
            context.checkCancelled();
            context.progress("删除 " + displayName(file), processed, total);
            try {
                deleteRecursively(file, context);
                successfulFiles.add(file);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                failedPaths.add(pathLabel(file));
                e.printStackTrace();
            }
            processed++;
            context.progress("删除中", processed, total);
        }

        return new FileOperationResult(successfulFiles, failedPaths);
    }

    public PasteResult pasteFiles(
            List<File> sources,
            File targetDirectory,
            boolean cutOperation,
            AssetTaskContext context
    ) throws InterruptedException {
        List<File> sourceSnapshot = snapshotFiles(sources);
        List<FileMove> movedFiles = new ArrayList<>();
        List<File> successfulFiles = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        if (targetDirectory == null) {
            return new PasteResult(successfulFiles, movedFiles, List.of("目标目录为空"));
        }

        int total = sourceSnapshot.size();
        int processed = 0;
        for (File source : sourceSnapshot) {
            context.checkCancelled();
            context.progress((cutOperation ? "移动 " : "复制 ") + displayName(source), processed, total);
            try {
                if (source == null || !source.exists()) {
                    failedPaths.add(pathLabel(source));
                    processed++;
                    context.progress(cutOperation ? "移动中" : "复制中", processed, total);
                    continue;
                }
                File destination = resolveAvailableDestination(targetDirectory, source.getName(), source.isDirectory());
                if (source.isDirectory() && isDescendantOrSelf(destination, source)) {
                    failedPaths.add(pathLabel(source));
                    processed++;
                    context.progress(cutOperation ? "移动中" : "复制中", processed, total);
                    continue;
                }
                if (cutOperation) {
                    File actualDestination = moveRecursively(source, destination, context);
                    movedFiles.add(new FileMove(source, actualDestination));
                    successfulFiles.add(source);
                } else {
                    copyRecursively(source, destination, context);
                    successfulFiles.add(source);
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                failedPaths.add(pathLabel(source));
                e.printStackTrace();
            }
            processed++;
            context.progress(cutOperation ? "移动中" : "复制中", processed, total);
        }

        return new PasteResult(successfulFiles, movedFiles, failedPaths);
    }

    public MoveResult moveFilesToDirectory(
            List<File> sources,
            File targetDirectory,
            AssetTaskContext context
    ) throws InterruptedException {
        List<File> sourceSnapshot = snapshotFiles(sources);
        List<FileMove> movedFiles = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        if (targetDirectory == null || !targetDirectory.isDirectory()) {
            return new MoveResult(movedFiles, List.of("目标目录无效"));
        }

        int total = sourceSnapshot.size();
        int processed = 0;
        for (File source : sourceSnapshot) {
            context.checkCancelled();
            context.progress("移动 " + displayName(source), processed, total);
            try {
                if (source == null
                        || !source.exists()
                        || source.equals(targetDirectory)
                        || isDescendantOrSelf(targetDirectory, source)
                        || (source.getParentFile() != null && source.getParentFile().equals(targetDirectory))) {
                    failedPaths.add(pathLabel(source));
                    processed++;
                    context.progress("移动中", processed, total);
                    continue;
                }
                File destination = resolveAvailableDestination(targetDirectory, source.getName(), source.isDirectory());
                File actualDestination = moveRecursively(source, destination, context);
                movedFiles.add(new FileMove(source, actualDestination));
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                failedPaths.add(pathLabel(source));
                e.printStackTrace();
            }
            processed++;
            context.progress("移动中", processed, total);
        }

        return new MoveResult(movedFiles, failedPaths);
    }

    public DownloadSaveResult saveDownloadedFiles(
            List<RemoteGraphUploadFile> files,
            File targetDirectory,
            AssetTaskContext context
    ) throws InterruptedException {
        List<RemoteGraphUploadFile> fileSnapshot = files == null ? List.of() : List.copyOf(files);
        List<String> failedPaths = new ArrayList<>();
        if (targetDirectory == null) {
            return new DownloadSaveResult(0, List.of("目标目录为空"));
        }

        Path root = targetDirectory.toPath().toAbsolutePath().normalize();
        int total = fileSnapshot.size();
        int processed = 0;
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            return new DownloadSaveResult(0, List.of(targetDirectory.getAbsolutePath()));
        }

        for (RemoteGraphUploadFile file : fileSnapshot) {
            context.checkCancelled();
            context.progress("保存 " + file.targetPath(), processed, total);
            try {
                String relative = AssetPathUtils.normalizeRemoteFilePath(file.targetPath());
                Path target = root.resolve(relative).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalArgumentException("invalid download path: " + file.targetPath());
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                context.checkCancelled();
                Files.writeString(target, file.jsonContent());
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                failedPaths.add(file.targetPath());
                e.printStackTrace();
            }
            processed++;
            context.progress("保存中", processed, total);
        }

        return new DownloadSaveResult(total - failedPaths.size(), failedPaths);
    }

    public UploadCollectionResult collectUploadFiles(
            List<File> selectedFiles,
            String targetDirectory,
            AssetTaskContext context
    ) throws InterruptedException {
        List<RemoteGraphUploadFile> files = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        String targetPrefix = AssetPathUtils.normalizeRemoteDirectory(targetDirectory);
        List<File> selections = snapshotFiles(selectedFiles);
        int processed = 0;

        for (File selected : selections) {
            context.checkCancelled();
            context.progress("扫描 " + displayName(selected), processed, selections.size());
            if (selected == null || !selected.exists()) {
                failedPaths.add(pathLabel(selected));
                processed++;
                context.progress("扫描中", processed, selections.size());
                continue;
            }

            Path selectedPath = selected.toPath().toAbsolutePath().normalize();
            Path base = selectedPath.getParent();
            if (base == null) {
                failedPaths.add(pathLabel(selected));
                processed++;
                context.progress("扫描中", processed, selections.size());
                continue;
            }
            collectUploadFile(files, failedPaths, base, selectedPath, targetPrefix, context);
            processed++;
            context.progress("扫描中", processed, selections.size());
        }

        return new UploadCollectionResult(files, failedPaths);
    }

    public CreatedAssetItem createAssetItem(
            File directory,
            String sourceName,
            boolean directoryItem,
            AssetTaskContext context
    ) throws Exception {
        context.checkCancelled();
        if (directory == null || !directory.isDirectory()) {
            throw new IOException("目标目录无效");
        }

        context.progress("创建 " + sourceName, 0, 1);
        File newFile = resolveAvailableDestination(directory, sourceName, directoryItem);
        context.checkCancelled();
        if (directoryItem) {
            Files.createDirectory(newFile.toPath());
        } else {
            Files.createFile(newFile.toPath());
        }
        context.progress("创建中", 1, 1);
        return new CreatedAssetItem(newFile);
    }

    public RenameResult renameFile(File source, String newName, AssetTaskContext context) throws Exception {
        context.checkCancelled();
        validateSource(source);
        validateSourceName(newName);

        File parent = source.getParentFile();
        if (parent == null) {
            throw new IOException("源文件没有父目录: " + source.getAbsolutePath());
        }

        if (newName.equals(source.getName())) {
            return new RenameResult(source, source, false);
        }

        Path parentPath = parent.toPath().toAbsolutePath().normalize();
        Path destinationPath = parentPath.resolve(newName).normalize();
        if (!parentPath.equals(destinationPath.getParent())) {
            throw new IllegalArgumentException("newName must stay within the source directory");
        }
        File destination = destinationPath.toFile();
        if (Files.exists(destinationPath)) {
            return new RenameResult(source, source, false);
        }

        context.progress("重命名 " + displayName(source), 0, 1);
        context.checkCancelled();
        Files.move(source.toPath(), destinationPath);
        context.progress("重命名中", 1, 1);
        return new RenameResult(source, destination, true);
    }

    public static File resolveAvailableDestination(File directory, String sourceName, boolean directoryName) {
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

    private void collectUploadFile(
            List<RemoteGraphUploadFile> out,
            List<String> failedPaths,
            Path base,
            Path path,
            String targetPrefix,
            AssetTaskContext context
    ) throws InterruptedException {
        context.checkCancelled();
        try {
            if (Files.isSymbolicLink(path)) {
                return;
            }
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    var iterator = stream.iterator();
                    while (iterator.hasNext()) {
                        Path child = iterator.next();
                        collectUploadFile(out, failedPaths, base, child, targetPrefix, context);
                    }
                }
                return;
            }
            if (!Files.isRegularFile(path)) {
                return;
            }
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                return;
            }

            String relative = base.relativize(path).toString().replace('\\', '/');
            String targetPath = targetPrefix.isEmpty() ? relative : targetPrefix + "/" + relative;
            targetPath = AssetPathUtils.normalizeRemoteFilePath(targetPath);
            context.progress("读取 " + path.getFileName(), out.size(), 0);
            String json = Files.readString(path);
            context.checkCancelled();
            out.add(new RemoteGraphUploadFile(targetPath, json));
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            failedPaths.add(path.toString());
            e.printStackTrace();
        }
    }

    private static File copyRecursively(File source, File destination, AssetTaskContext context)
            throws IOException, InterruptedException {
        validateSource(source);
        File actualDestination = resolveDestinationFor(source, destination);
        ensureNotNestedInSource(source, actualDestination);
        copyRecursivelyInternal(source, actualDestination, context);
        return actualDestination;
    }

    private static File moveRecursively(File source, File destination, AssetTaskContext context)
            throws IOException, InterruptedException {
        validateSource(source);
        File actualDestination = resolveDestinationFor(source, destination);
        ensureNotNestedInSource(source, actualDestination);
        ensureParentDirectory(actualDestination);

        context.checkCancelled();
        if (source.renameTo(actualDestination)) {
            return actualDestination;
        }

        copyRecursivelyInternal(source, actualDestination, context);
        context.checkCancelled();
        try {
            deleteRecursively(source, context);
        } catch (IOException deleteException) {
            throw new IOException("Copied but failed to delete source: " + source.getAbsolutePath(), deleteException);
        }
        return actualDestination;
    }

    private static void deleteRecursively(File file, AssetTaskContext context) throws IOException, InterruptedException {
        context.checkCancelled();
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
                deleteRecursively(child, context);
            }
        }

        context.checkCancelled();
        Files.deleteIfExists(path);
    }

    private static void copyRecursivelyInternal(File source, File destination, AssetTaskContext context)
            throws IOException, InterruptedException {
        context.checkCancelled();
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
                copyRecursivelyInternal(child, new File(destination, child.getName()), context);
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
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("sourceName must not be blank");
        }
        if (sourceName.equals(".") || sourceName.equals("..")
                || sourceName.indexOf('/') >= 0 || sourceName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("sourceName must be a single file name");
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

    private static boolean isDescendantOrSelf(File candidate, File root) {
        try {
            String candidatePath = candidate.getCanonicalPath();
            String rootPath = root.getCanonicalPath();
            return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private static List<File> snapshotFiles(List<File> files) {
        return files == null ? List.of() : List.copyOf(files);
    }

    private static String displayName(File file) {
        if (file == null) return "未知文件";
        String name = file.getName();
        return name == null || name.isEmpty() ? file.getAbsolutePath() : name;
    }

    private static String pathLabel(File file) {
        return file == null ? "未知文件" : file.getAbsolutePath();
    }

    public record FileMove(File source, File destination) {
    }

    public record CreatedAssetItem(File file) {
    }

    public record RenameResult(File source, File destination, boolean renamed) {
    }

    public record FileOperationResult(List<File> successfulFiles, List<String> failedPaths) {
        public FileOperationResult {
            successfulFiles = successfulFiles == null ? List.of() : List.copyOf(successfulFiles);
            failedPaths = failedPaths == null ? List.of() : List.copyOf(failedPaths);
        }
    }

    public record PasteResult(List<File> successfulFiles, List<FileMove> movedFiles, List<String> failedPaths) {
        public PasteResult {
            successfulFiles = successfulFiles == null ? List.of() : List.copyOf(successfulFiles);
            movedFiles = movedFiles == null ? List.of() : List.copyOf(movedFiles);
            failedPaths = failedPaths == null ? List.of() : List.copyOf(failedPaths);
        }
    }

    public record MoveResult(List<FileMove> movedFiles, List<String> failedPaths) {
        public MoveResult {
            movedFiles = movedFiles == null ? List.of() : List.copyOf(movedFiles);
            failedPaths = failedPaths == null ? List.of() : List.copyOf(failedPaths);
        }
    }

    public record DownloadSaveResult(int successCount, List<String> failedPaths) {
        public DownloadSaveResult {
            successCount = Math.max(0, successCount);
            failedPaths = failedPaths == null ? List.of() : List.copyOf(failedPaths);
        }
    }

    public record UploadCollectionResult(List<RemoteGraphUploadFile> files, List<String> failedPaths) {
        public UploadCollectionResult {
            files = files == null ? List.of() : List.copyOf(files);
            failedPaths = failedPaths == null ? List.of() : List.copyOf(failedPaths);
        }
    }
}
