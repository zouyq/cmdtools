package com.github.zouyq.actions;

import com.github.zouyq.utils.PathResolver;
import com.github.zouyq.utils.TerminalLauncher;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public final class OpenCmdAction extends BasePlatformAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        PathResolver.resolveWorkingDirectory(event)
                .ifPresent(path -> TerminalLauncher.open(path, event.getProject()));
    }

    @Override
    protected boolean isContextAvailable(@NotNull AnActionEvent event) {
        return PathResolver.resolveWorkingDirectory(event).isPresent();
    }
}
