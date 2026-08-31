package com.mine.geometry_node.client.asset.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Opens a local asset in the platform file manager without invoking a shell. */
public final class AssetSystemFileBrowser {
    private AssetSystemFileBrowser() {
    }

    public static void open(Path target) throws IOException {
        Path path = target.toAbsolutePath().normalize();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder process;
        if (os.contains("win")) {
            process = Files.isDirectory(path)
                    ? new ProcessBuilder("explorer.exe", path.toString())
                    : new ProcessBuilder("explorer.exe", "/select," + path);
        } else if (os.contains("mac")) {
            process = Files.isDirectory(path)
                    ? new ProcessBuilder("open", path.toString())
                    : new ProcessBuilder("open", "-R", path.toString());
        } else {
            Path directory = Files.isDirectory(path) ? path : path.getParent();
            if (directory == null) throw new IOException("Asset path has no parent: " + path);
            process = new ProcessBuilder("xdg-open", directory.toString());
        }
        process.start();
    }
}
