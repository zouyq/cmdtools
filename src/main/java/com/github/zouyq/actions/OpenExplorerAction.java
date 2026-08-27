package com.github.zouyq.actions;

import com.github.zouyq.utils.FileManagerLauncher;
import com.github.zouyq.utils.PathResolver;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public final class OpenExplorerAction extends BasePlatformAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        PathResolver.resolveSelectedPath(event)
                .ifPresent(path -> FileManagerLauncher.open(path, event.getProject()));
    }

    @Override
    protected boolean isContextAvailable(@NotNull AnActionEvent event) {
        return PathResolver.resolveSelectedPath(event).isPresent();
    }
}
