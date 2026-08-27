package com.github.zouyq.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class FileManagerLauncher {
    private static final Logger LOG = Logger.getInstance(FileManagerLauncher.class);

    private FileManagerLauncher() {
    }

    public static void open(Path path, @Nullable Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<List<String>> commands = commandsFor(OperatingSystem.current(), path);
                IOException lastFailure = null;
                for (int index = 0; index < commands.size(); index++) {
                    List<String> command = commands.get(index);
                    boolean lastCommand = index == commands.size() - 1;
                    try {
                        Process process = new ProcessBuilder(command).start();
                        if (lastCommand) {
                            return;
                        }
                        if (completedSuccessfully(process)) {
                            return;
                        }
                    } catch (IOException exception) {
                        lastFailure = exception;
                        LOG.warn("File manager command failed: " + command, exception);
                    }
                }
                if (lastFailure != null) {
                    throw lastFailure;
                }
                throw new IllegalStateException("No working file-manager command was available");
            } catch (IOException | IllegalStateException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LOG.warn("Unable to open file manager", exception);
                UiMessages.error(project, "Unable to open the system file manager: " + exception.getMessage());
            }
        });
    }

    static List<List<String>> commandsFor(OperatingSystem os, Path path) {
        boolean directory = Files.isDirectory(path);
        return switch (os) {
            case WINDOWS -> List.of(directory
                    ? List.of("explorer.exe", path.toString())
                    : List.of("explorer.exe", "/select," + path));
            case MAC -> List.of(directory
                    ? List.of("open", path.toString())
                    : List.of("open", "-R", path.toString()));
            case LINUX -> linuxCommands(path, directory);
            case UNSUPPORTED -> throw new IllegalStateException("Unsupported operating system");
        };
    }

    /** Convenience for tests that expect the preferred command. */
    static List<String> commandFor(OperatingSystem os, Path path) {
        return commandsFor(os, path).getFirst();
    }

    private static List<List<String>> linuxCommands(Path path, boolean directory) {
        List<List<String>> commands = new ArrayList<>();
        Path directoryToOpen = directory ? path : path.getParent();
        if (directoryToOpen == null) {
            directoryToOpen = path;
        }

        if (!directory) {
            URI uri = path.toAbsolutePath().normalize().toUri();
            ExecutableFinder.firstAvailable("dbus-send").ifPresent(dbus ->
                    commands.add(List.of(
                            dbus,
                            "--session",
                            "--dest=org.freedesktop.FileManager1",
                            "--type=method_call",
                            "/org/freedesktop/FileManager1",
                            "org.freedesktop.FileManager1.ShowItems",
                            "array:string:" + uri,
                            "string:"
                    )));
        }

        String opener = ExecutableFinder.firstAvailable("xdg-open").orElse("xdg-open");
        commands.add(List.of(opener, directoryToOpen.toString()));
        return commands;
    }

    private static boolean completedSuccessfully(Process process) throws InterruptedException {
        boolean finished = process.waitFor(3, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return false;
        }
        return process.exitValue() == 0;
    }
}
