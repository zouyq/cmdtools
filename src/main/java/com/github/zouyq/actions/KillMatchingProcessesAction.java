package com.github.zouyq.actions;

import com.github.zouyq.config.CmdToolsSettings;
import com.github.zouyq.utils.ProcessKiller;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public final class KillMatchingProcessesAction extends BasePlatformAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        CmdToolsSettings.Data settings = CmdToolsSettings.getInstance().data();
        String regularExpression = settings.processNameRegex == null || settings.processNameRegex.isBlank()
                ? "(?i)java"
                : settings.processNameRegex;
        ProcessKiller.killMatching(regularExpression, event.getProject(), settings.confirmBeforeKilling);
    }
}
