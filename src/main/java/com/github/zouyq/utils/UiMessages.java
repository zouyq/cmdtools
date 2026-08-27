package com.github.zouyq.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

public final class UiMessages {
    private UiMessages() {
    }

    public static void info(@Nullable Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showInfoMessage(project, message, "CmdTools"));
    }

    public static void error(@Nullable Project project, String message) {
        ApplicationManager.getApplication().invokeLater(() ->
                Messages.showErrorDialog(project, message, "CmdTools"));
    }
}
