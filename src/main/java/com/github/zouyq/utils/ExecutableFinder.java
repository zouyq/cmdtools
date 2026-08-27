package com.github.zouyq.utils;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

final class ExecutableFinder {
    private ExecutableFinder() {
    }

    static Optional<String> firstAvailable(String... candidates) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return Optional.empty();
        }

        for (String candidate : candidates) {
            if (candidate.contains("/") || candidate.contains("\\")) {
                if (isExecutable(candidate)) {
                    return Optional.of(candidate);
                }
                continue;
            }
            for (String directory : pathValue.split(java.io.File.pathSeparator)) {
                try {
                    Path path = Path.of(directory, candidate);
                    if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                        return Optional.of(path.toString());
                    }
                } catch (InvalidPathException ignored) {
                    // Ignore malformed PATH entries and continue searching.
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isExecutable(String value) {
        try {
            Path path = Path.of(value);
            return Files.isRegularFile(path) && Files.isExecutable(path);
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}
