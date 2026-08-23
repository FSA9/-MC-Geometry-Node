package com.mine.geometry_node.client.terminal;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable argv-based launch description. It deliberately has no shell command string. */
public record ProcessLaunchSpec(
        List<String> argv,
        Map<String, String> environment,
        Path workingDirectory,
        TerminalSize initialSize) {
    private static final int MAX_ARGUMENTS = 256;
    private static final int MAX_ARGUMENT_LENGTH = 32_768;

    public ProcessLaunchSpec {
        Objects.requireNonNull(argv, "argv");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(initialSize, "initialSize");
        if (argv.isEmpty() || argv.size() > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("argv must contain 1 to " + MAX_ARGUMENTS + " entries");
        }
        argv = List.copyOf(argv);
        environment = Map.copyOf(environment);
        for (String argument : argv) {
            requireSafeValue(argument, "argv entry");
        }
        if (argv.getFirst().isBlank()) {
            throw new IllegalArgumentException("argv executable cannot be blank");
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            requireSafeValue(entry.getKey(), "environment name");
            requireSafeValue(entry.getValue(), "environment value");
            if (entry.getKey().isEmpty()) {
                throw new IllegalArgumentException("environment name cannot be empty");
            }
            if (entry.getKey().indexOf('=') >= 0) {
                throw new IllegalArgumentException("environment name cannot contain '='");
            }
        }
    }

    public static boolean isValidEnvironmentEntry(String name, String value) {
        return name != null && value != null && !name.isEmpty() && name.indexOf('=') < 0
                && name.length() <= MAX_ARGUMENT_LENGTH && value.length() <= MAX_ARGUMENT_LENGTH
                && name.indexOf('\0') < 0 && value.indexOf('\0') < 0;
    }

    private static void requireSafeValue(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > MAX_ARGUMENT_LENGTH || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(name + " is too long or contains NUL");
        }
    }
}
