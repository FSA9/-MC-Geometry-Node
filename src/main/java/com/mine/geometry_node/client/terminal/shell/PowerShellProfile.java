package com.mine.geometry_node.client.terminal.shell;

import com.mine.geometry_node.client.terminal.ProcessLaunchSpec;
import com.mine.geometry_node.client.terminal.TerminalSize;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Windows-first interactive PowerShell launch policy. It never modifies a user's shell config. */
public final class PowerShellProfile {
    public static final String ID = "powershell";

    private PowerShellProfile() {}

    public static ProcessLaunchSpec create(TerminalSize size) throws IOException {
        return create(size, Map.of());
    }

    public static ProcessLaunchSpec create(TerminalSize size, Map<String, String> environmentOverrides)
            throws IOException {
        if (!isWindows()) {
            throw new IOException("PowerShell SHELL is currently supported on Windows only");
        }
        Path executable = findExecutable("pwsh.exe");
        List<String> argv = new ArrayList<>();
        if (executable != null) {
            argv.add(executable.toString());
            argv.add("-NoLogo");
            argv.add("-NoProfile");
        } else {
            Path windowsPowerShell = windowsPowerShellPath();
            if (windowsPowerShell == null) {
                throw new IOException("PowerShell was not found. Install PowerShell 7 or enable Windows PowerShell");
            }
            argv.add(windowsPowerShell.toString());
            argv.add("-NoLogo");
            argv.add("-NoProfile");
        }
        Map<String, String> environment = copyValidEnvironment(System.getenv());
        if (environmentOverrides != null) environment.putAll(environmentOverrides);
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        environment.put("TERM_PROGRAM", "GeometryNode");
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return new ProcessLaunchSpec(argv, environment, workingDirectory, size);
    }

    static Map<String, String> copyValidEnvironment(Map<String, String> source) {
        Map<String, String> environment = new HashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (ProcessLaunchSpec.isValidEnvironmentEntry(entry.getKey(), entry.getValue())) {
                environment.put(entry.getKey(), entry.getValue());
            }
        }
        return environment;
    }

    static Path findExecutable(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return null;
        for (String entry : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (entry.isBlank()) continue;
            try {
                Path candidate = Path.of(entry, name);
                if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize();
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Path windowsPowerShellPath() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) return findExecutable("powershell.exe");
        Path candidate = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        return Files.isRegularFile(candidate) ? candidate.toAbsolutePath().normalize() : findExecutable("powershell.exe");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
