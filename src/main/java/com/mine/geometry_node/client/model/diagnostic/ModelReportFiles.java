package com.mine.geometry_node.client.model.diagnostic;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ModelReportFiles {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT);

    private ModelReportFiles() {}

    static Path write(Path directory, Instant capturedAt, String json) throws IOException {
        Files.createDirectories(directory);
        String basename = "report-" + TIMESTAMP.format(capturedAt.atZone(ZoneId.systemDefault()));
        for (long sequence = 1; ; sequence++) {
            String suffix = sequence == 1 ? "" : "-" + sequence;
            Path path = directory.resolve(basename + suffix + ".json");
            try {
                return Files.writeString(path, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException ignored) {
                // Another report owns this name; reserve the next available name atomically.
            }
        }
    }
}
