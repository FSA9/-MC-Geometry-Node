package com.mine.geometry_node.client.ui.editor.asset.service;

import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.task.AssetTaskContext;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphDocumentStore;
import com.mine.geometry_node.client.ui.persistence.graphfile.GraphFileRegistry;
import com.mine.geometry_node.core.engine.system.asset.AssetTransferPolicy;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LocalAssetService {

    public FileOperationResult deleteFiles(List<File> files, AssetTaskContext context) throws InterruptedException {
        List<File> sources = topLevelFiles(files);
        List<String> failedPaths = new ArrayList<>();
        List<DeletePlan> plans = new ArrayList<>();
        for (File source : sources) {
            context.checkCancelled();
            context.progress("准备删除 " + displayName(source), plans.size(), sources.size());
            try {
                validateSource(source);
                Path sourcePath = normalize(source.toPath());
                plans.add(new DeletePlan(source, sourcePath,
                        GraphDocumentStore.siblingTemporary(sourcePath, "delete")));
            } catch (Exception e) {
                failedPaths.add(pathLabel(source));
            }
        }
        if (plans.isEmpty()) return new FileOperationResult(List.of(), failedPaths);

        try {
            context.enterCommitPhase();
        } catch (InterruptedException e) {
            throw e;
        }
        try {
            GraphFileRegistry.Mutation mutation = GraphFileRegistry.INSTANCE.beginDelete(
                    plans.stream().map(DeletePlan::sourcePath).toList());
            mutation.commit(() -> {
                commitDeletes(plans);
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            for (DeletePlan plan : plans) failedPaths.add(pathLabel(plan.source()));
            return new FileOperationResult(List.of(), failedPaths);
        }

        for (DeletePlan plan : plans) {
            try {
                deleteRecursively(plan.trashPath());
            } catch (IOException e) {
                System.err.println("[AssetLibrary] Failed to clean deleted asset: " + plan.trashPath());
                e.printStackTrace();
            }
        }
        context.progress("删除完成", plans.size(), plans.size());
        return new FileOperationResult(plans.stream().map(DeletePlan::source).toList(), failedPaths);
    }

    public PasteResult pasteFiles(List<File> sources, File targetDirectory, boolean cutOperation,
                                  AssetTaskContext context) throws InterruptedException {
        if (cutOperation) {
            MoveResult moved = moveFilesToDirectory(sources, targetDirectory, context);
            return new PasteResult(
                    moved.movedFiles().stream().map(FileMove::source).toList(),
                    moved.movedFiles(), moved.failedPaths());
        }
        return copyFiles(sources, targetDirectory, context);
    }

    private PasteResult copyFiles(List<File> files, File targetDirectory, AssetTaskContext context)
            throws InterruptedException {
        List<File> sources = topLevelFiles(files);
        List<CopyPlan> plans = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        if (targetDirectory == null || !targetDirectory.isDirectory()) {
            return new PasteResult(List.of(), List.of(), List.of("目标目录无效"));
        }

        for (File source : sources) {
            context.checkCancelled();
            context.progress("准备复制 " + displayName(source), plans.size(), sources.size());
            Path temporary = null;
            try {
                validateSource(source);
                File destination = resolveAvailableDestination(targetDirectory, source.getName(), source.isDirectory());
                ensureNotNestedInSource(source, destination);
                temporary = GraphDocumentStore.siblingTemporary(destination.toPath(), "copy");
                copyRecursively(source.toPath(), temporary, context);
                plans.add(new CopyPlan(source, normalize(destination.toPath()), temporary));
            } catch (InterruptedException e) {
                cleanupQuietly(temporary);
                cleanupCopyPlans(plans);
                throw e;
            } catch (Exception e) {
                cleanupQuietly(temporary);
                failedPaths.add(pathLabel(source));
                e.printStackTrace();
            }
        }
        if (plans.isEmpty()) return new PasteResult(List.of(), List.of(), failedPaths);

        try {
            context.enterCommitPhase();
        } catch (InterruptedException e) {
            cleanupCopyPlans(plans);
            throw e;
        }
        try {
            GraphDocumentStore.INSTANCE.withStructureMutation(() -> {
                commitCopies(plans);
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            cleanupCopyPlans(plans);
            for (CopyPlan plan : plans) failedPaths.add(pathLabel(plan.source()));
            return new PasteResult(List.of(), List.of(), failedPaths);
        }
        context.progress("复制完成", plans.size(), plans.size());
        return new PasteResult(plans.stream().map(CopyPlan::source).toList(), List.of(), failedPaths);
    }

    public MoveResult moveFilesToDirectory(List<File> files, File targetDirectory, AssetTaskContext context)
            throws InterruptedException {
        List<File> sources = topLevelFiles(files);
        List<MovePlan> plans = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        if (targetDirectory == null || !targetDirectory.isDirectory()) {
            return new MoveResult(List.of(), List.of("目标目录无效"));
        }

        for (File source : sources) {
            context.checkCancelled();
            context.progress("准备移动 " + displayName(source), plans.size(), sources.size());
            Path temporary = null;
            try {
                validateSource(source);
                if (source.equals(targetDirectory) || isDescendantOrSelf(targetDirectory, source)
                        || targetDirectory.equals(source.getParentFile())) {
                    throw new IOException("无效的移动目标");
                }
                File destination = resolveAvailableDestination(targetDirectory, source.getName(), source.isDirectory());
                ensureNotNestedInSource(source, destination);
                Path sourcePath = normalize(source.toPath());
                Path destinationPath = normalize(destination.toPath());
                boolean direct = sameFileStore(sourcePath, normalize(targetDirectory.toPath()));
                Path trash = GraphDocumentStore.siblingTemporary(sourcePath, "move-source");
                if (!direct) {
                    temporary = GraphDocumentStore.siblingTemporary(destinationPath, "move-target");
                    copyRecursively(sourcePath, temporary, context);
                }
                plans.add(new MovePlan(source, sourcePath, destinationPath, temporary, trash, direct));
            } catch (InterruptedException e) {
                cleanupQuietly(temporary);
                cleanupMovePlans(plans);
                throw e;
            } catch (Exception e) {
                cleanupQuietly(temporary);
                failedPaths.add(pathLabel(source));
                e.printStackTrace();
            }
        }
        if (plans.isEmpty()) return new MoveResult(List.of(), failedPaths);

        try {
            context.enterCommitPhase();
        } catch (InterruptedException e) {
            cleanupMovePlans(plans);
            throw e;
        }
        Map<Path, Path> pathChanges = new LinkedHashMap<>();
        for (MovePlan plan : plans) pathChanges.put(plan.sourcePath(), plan.destinationPath());
        try {
            GraphFileRegistry.Mutation mutation = GraphFileRegistry.INSTANCE.beginMoves(pathChanges);
            mutation.commit(() -> {
                commitMoves(plans);
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
            for (MovePlan plan : plans) cleanupQuietly(plan.temporaryPath());
            for (MovePlan plan : plans) failedPaths.add(pathLabel(plan.source()));
            return new MoveResult(List.of(), failedPaths);
        }

        for (MovePlan plan : plans) {
            if (!plan.direct()) cleanupQuietly(plan.trashPath());
        }
        context.progress("移动完成", plans.size(), plans.size());
        return new MoveResult(plans.stream()
                .map(plan -> new FileMove(plan.source(), plan.destinationPath().toFile())).toList(), failedPaths);
    }

    public UploadCollectionResult collectUploadSources(List<File> selectedFiles, String targetDirectory,
                                                        AssetTaskContext context) throws InterruptedException {
        List<UploadSource> files = new ArrayList<>();
        List<String> failedPaths = new ArrayList<>();
        String targetPrefix = AssetPathUtils.normalizeRemoteDirectory(targetDirectory);
        List<File> selections = topLevelFiles(selectedFiles);
        int processed = 0;
        for (File selected : selections) {
            context.checkCancelled();
            context.progress("扫描 " + displayName(selected), processed, selections.size());
            if (selected == null || !selected.exists() || selected.toPath().getParent() == null) {
                failedPaths.add(pathLabel(selected));
            } else {
                Path selectedPath = normalize(selected.toPath());
                collectUploadFile(files, failedPaths, selectedPath.getParent(), selectedPath, targetPrefix, context);
            }
            processed++;
        }
        return new UploadCollectionResult(files, failedPaths);
    }

    public CreatedAssetItem createAssetItem(File directory, String sourceName, boolean directoryItem,
                                            AssetTaskContext context) throws Exception {
        context.checkCancelled();
        if (directory == null || !directory.isDirectory()) throw new IOException("目标目录无效");
        validateSourceName(sourceName);
        context.enterCommitPhase();
        Path created = GraphDocumentStore.INSTANCE.withStructureMutation(() -> {
            File destination = resolveAvailableDestination(directory, sourceName, directoryItem);
            if (directoryItem) Files.createDirectory(destination.toPath());
            else Files.createFile(destination.toPath());
            return normalize(destination.toPath());
        });
        context.progress("创建完成", 1, 1);
        return new CreatedAssetItem(created.toFile());
    }

    public RenameResult renameFile(File source, String newName, AssetTaskContext context) throws Exception {
        context.checkCancelled();
        validateSource(source);
        validateSourceName(newName);
        File parent = source.getParentFile();
        if (parent == null) throw new IOException("源文件没有父目录: " + source.getAbsolutePath());
        if (newName.equals(source.getName())) return new RenameResult(source, source, false);

        Path sourcePath = normalize(source.toPath());
        Path destinationPath = normalize(parent.toPath().resolve(newName));
        if (!normalize(parent.toPath()).equals(destinationPath.getParent())) {
            throw new IllegalArgumentException("newName must stay within the source directory");
        }
        if (Files.exists(destinationPath)) return new RenameResult(source, source, false);

        context.enterCommitPhase();
        GraphFileRegistry.Mutation mutation = GraphFileRegistry.INSTANCE.beginMove(sourcePath, destinationPath);
        mutation.commit(() -> {
            GraphDocumentStore.moveNew(sourcePath, destinationPath);
            return null;
        });
        context.progress("重命名完成", 1, 1);
        return new RenameResult(source, destinationPath.toFile(), true);
    }

    public static File resolveAvailableDestination(File directory, String sourceName, boolean directoryName) {
        if (directory == null) directory = new File(".");
        validateSourceName(sourceName);
        File candidate = new File(directory, sourceName);
        if (!candidate.exists()) return candidate;

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
            resolved = new File(directory, baseName + "_" + counter++ + extension);
        } while (resolved.exists());
        return resolved;
    }

    private void collectUploadFile(List<UploadSource> out, List<String> failedPaths, Path base,
                                   Path path, String targetPrefix, AssetTaskContext context)
            throws InterruptedException {
        context.checkCancelled();
        try {
            if (Files.isSymbolicLink(path)) return;
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    for (Path child : stream.toList()) {
                        collectUploadFile(out, failedPaths, base, child, targetPrefix, context);
                    }
                }
                return;
            }
            if (!Files.isRegularFile(path) || !AssetTransferPolicy.isTransferablePath(path.toString())) return;
            String relative = base.relativize(path).toString().replace('\\', '/');
            String targetPath = targetPrefix.isEmpty() ? relative : targetPrefix + "/" + relative;
            targetPath = AssetPathUtils.normalizeRemoteFilePath(targetPath);
            out.add(new UploadSource(path, targetPath));
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            failedPaths.add(path.toString());
            e.printStackTrace();
        }
    }

    private static void commitCopies(List<CopyPlan> plans) throws IOException {
        List<CopyPlan> committed = new ArrayList<>();
        try {
            for (CopyPlan plan : plans) {
                GraphDocumentStore.moveNew(plan.temporaryPath(), plan.destinationPath());
                committed.add(plan);
            }
        } catch (IOException e) {
            for (int i = committed.size() - 1; i >= 0; i--) {
                CopyPlan plan = committed.get(i);
                try {
                    GraphDocumentStore.moveNew(plan.destinationPath(), plan.temporaryPath());
                } catch (IOException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            throw e;
        }
    }

    private static void commitDeletes(List<DeletePlan> plans) throws IOException {
        List<DeletePlan> committed = new ArrayList<>();
        try {
            for (DeletePlan plan : plans) {
                GraphDocumentStore.moveNew(plan.sourcePath(), plan.trashPath());
                committed.add(plan);
            }
        } catch (IOException e) {
            for (int i = committed.size() - 1; i >= 0; i--) {
                DeletePlan plan = committed.get(i);
                try {
                    GraphDocumentStore.moveNew(plan.trashPath(), plan.sourcePath());
                } catch (IOException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            throw e;
        }
    }

    private static void commitMoves(List<MovePlan> plans) throws IOException {
        List<MovePlan> committed = new ArrayList<>();
        try {
            for (MovePlan plan : plans) {
                if (plan.direct()) {
                    GraphDocumentStore.moveNew(plan.sourcePath(), plan.destinationPath());
                } else {
                    GraphDocumentStore.moveNew(plan.sourcePath(), plan.trashPath());
                    try {
                        GraphDocumentStore.moveNew(plan.temporaryPath(), plan.destinationPath());
                    } catch (IOException e) {
                        try {
                            GraphDocumentStore.moveNew(plan.trashPath(), plan.sourcePath());
                        } catch (IOException rollbackError) {
                            e.addSuppressed(rollbackError);
                        }
                        throw e;
                    }
                }
                committed.add(plan);
            }
        } catch (IOException e) {
            for (int i = committed.size() - 1; i >= 0; i--) {
                MovePlan plan = committed.get(i);
                try {
                    if (plan.direct()) {
                        GraphDocumentStore.moveNew(plan.destinationPath(), plan.sourcePath());
                    } else {
                        try {
                            GraphDocumentStore.moveNew(plan.destinationPath(), plan.temporaryPath());
                        } catch (IOException rollbackError) {
                            e.addSuppressed(rollbackError);
                        }
                        GraphDocumentStore.moveNew(plan.trashPath(), plan.sourcePath());
                    }
                } catch (IOException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }
            throw e;
        }
    }

    private static void copyRecursively(Path source, Path destination, AssetTaskContext context)
            throws IOException, InterruptedException {
        context.checkCancelled();
        Path parent = destination.getParent();
        if (parent != null) Files.createDirectories(parent);
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(destination);
            try (var stream = Files.list(source)) {
                for (Path child : stream.toList()) {
                    copyRecursively(child, destination.resolve(child.getFileName()), context);
                }
            }
        } else {
            Files.copy(source, destination, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.list(path)) {
                List<Path> children = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path child : children) deleteRecursively(child);
            }
        }
        Files.deleteIfExists(path);
    }

    private static boolean sameFileStore(Path source, Path destinationDirectory) {
        try {
            FileStore sourceStore = Files.getFileStore(source);
            FileStore destinationStore = Files.getFileStore(destinationDirectory);
            return sourceStore.equals(destinationStore);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void validateSource(File source) throws IOException {
        if (source == null) throw new IOException("Source file is null");
        if (!source.exists()) throw new IOException("Source file does not exist: " + source.getAbsolutePath());
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

    private static void ensureNotNestedInSource(File source, File destination) throws IOException {
        Path sourcePath = normalize(source.toPath());
        Path destinationPath = normalize(destination.toPath());
        if (source.isDirectory() && destinationPath.startsWith(sourcePath)) {
            throw new IOException("Destination is inside source directory: " + destination.getAbsolutePath());
        }
    }

    private static boolean isDescendantOrSelf(File candidate, File root) {
        if (candidate == null || root == null) return false;
        Path candidatePath = normalize(candidate.toPath());
        Path rootPath = normalize(root.toPath());
        return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath);
    }

    private static List<File> topLevelFiles(List<File> files) {
        if (files == null || files.isEmpty()) return List.of();
        Set<Path> unique = new LinkedHashSet<>();
        Map<Path, File> byPath = new LinkedHashMap<>();
        for (File file : files) {
            if (file == null) continue;
            Path path = normalize(file.toPath());
            unique.add(path);
            byPath.putIfAbsent(path, file);
        }
        List<Path> sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.comparingInt(Path::getNameCount));
        List<Path> topLevel = new ArrayList<>();
        for (Path path : sorted) {
            if (topLevel.stream().noneMatch(path::startsWith)) topLevel.add(path);
        }
        return topLevel.stream().map(byPath::get).toList();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static void cleanupCopyPlans(List<CopyPlan> plans) {
        for (CopyPlan plan : plans) cleanupQuietly(plan.temporaryPath());
    }

    private static void cleanupMovePlans(List<MovePlan> plans) {
        for (MovePlan plan : plans) {
            cleanupQuietly(plan.temporaryPath());
            cleanupQuietly(plan.trashPath());
        }
    }

    private static void cleanupQuietly(Path path) {
        if (path == null) return;
        try {
            deleteRecursively(path);
        } catch (IOException ignored) {
        }
    }

    private static String displayName(File file) {
        if (file == null) return "未知文件";
        return file.getName().isEmpty() ? file.getAbsolutePath() : file.getName();
    }

    private static String pathLabel(File file) {
        return file == null ? "未知文件" : file.getAbsolutePath();
    }

    private record CopyPlan(File source, Path destinationPath, Path temporaryPath) {
    }

    private record DeletePlan(File source, Path sourcePath, Path trashPath) {
    }

    private record MovePlan(File source, Path sourcePath, Path destinationPath, Path temporaryPath,
                            Path trashPath, boolean direct) {
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

    public record UploadSource(Path sourcePath, String targetPath) {
        public UploadSource {
            sourcePath = normalize(sourcePath);
            targetPath = AssetPathUtils.normalizeRemoteFilePath(targetPath);
        }
    }

    public record UploadCollectionResult(List<UploadSource> files, List<String> failedPaths) {
        public UploadCollectionResult {
            files = files == null ? List.of() : List.copyOf(files);
            failedPaths = failedPaths == null ? List.of() : List.copyOf(failedPaths);
        }
    }
}
