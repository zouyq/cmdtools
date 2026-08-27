package com.github.zouyq.actions;

import com.github.zouyq.utils.OperatingSystem;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

abstract class BasePlatformAction extends DumbAwareAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public final void update(@NotNull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setEnabled(OperatingSystem.current().isSupported() && isContextAvailable(event));
    }

    protected boolean isContextAvailable(@NotNull AnActionEvent event) {
        return true;
    }
}
