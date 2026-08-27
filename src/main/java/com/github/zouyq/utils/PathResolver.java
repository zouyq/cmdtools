package com.github.zouyq.utils;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public final class PathResolver {
    private PathResolver() {
    }

    public static Optional<Path> resolveSelectedPath(@NotNull AnActionEvent event) {
        VirtualFile selected = event.getData(CommonDataKeys.VIRTUAL_FILE);
        Optional<Path> selectedPath = toLocalPath(selected);
        if (selectedPath.isPresent()) {
            return selectedPath;
        }

        Project project = event.getProject();
        if (project == null || project.getBasePath() == null) {
            return Optional.empty();
        }
        return safePath(project.getBasePath());
    }

    public static Optional<Path> resolveWorkingDirectory(@NotNull AnActionEvent event) {
        return resolveSelectedPath(event).map(path -> path.toFile().isDirectory()
                ? path
                : Optional.ofNullable(path.getParent()).orElse(path));
    }

    static Optional<Path> toLocalPath(VirtualFile file) {
        if (file == null) {
            return Optional.empty();
        }

        String path = file.getCanonicalPath();
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }

        int archiveSeparator = path.indexOf("!/");
        if (archiveSeparator >= 0) {
            path = path.substring(0, archiveSeparator);
        }
        return safePath(FileUtil.toSystemDependentName(path));
    }

    private static Optional<Path> safePath(String value) {
        try {
            return Optional.of(Path.of(value).toAbsolutePath().normalize());
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }
}
