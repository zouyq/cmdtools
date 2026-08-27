package com.github.zouyq.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ProcessKiller {
    private static final Logger LOG = Logger.getInstance(ProcessKiller.class);
    private static final int CONFIRM_LIST_LIMIT = 40;

    private ProcessKiller() {
    }

    public static void killMatching(String regularExpression, @Nullable Project project, boolean confirmBeforeKilling) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (regularExpression == null || regularExpression.isBlank()) {
                UiMessages.error(project, "The process-name regular expression is empty.");
                return;
            }
            final Pattern pattern;
            try {
                pattern = Pattern.compile(regularExpression);
            } catch (PatternSyntaxException exception) {
                UiMessages.error(project, "Invalid process-name regular expression: " + exception.getDescription());
                return;
            }

            List<MatchedProcess> targets = findMatching(pattern);
            if (targets.isEmpty()) {
                UiMessages.info(project, "No process names matched: " + regularExpression);
                return;
            }

            if (confirmBeforeKilling && !confirmTermination(project, regularExpression, targets)) {
                return;
            }

            int requested = 0;
            int failed = 0;
            for (MatchedProcess target : targets) {
                try {
                    ProcessHandle process = ProcessHandle.of(target.pid()).orElse(null);
                    if (process == null || !process.isAlive()) {
                        continue;
                    }
                    if (process.destroyForcibly()) {
                        requested++;
                    } else {
                        failed++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    LOG.warn("Unable to terminate process " + target.pid(), exception);
                }
            }

            if (failed == 0) {
                UiMessages.info(project, "Termination requested for " + requested + " matching process(es).");
            } else {
                UiMessages.error(project, "Termination requested for " + requested + " process(es); "
                        + failed + " could not be terminated. Check operating-system permissions.");
            }
        });
    }

    static List<MatchedProcess> findMatching(Pattern pattern) {
        Set<Long> protectedPids = protectedProcessIds();
        List<MatchedProcess> targets = new ArrayList<>();
        ProcessHandle.allProcesses()
                .filter(process -> !protectedPids.contains(process.pid()))
                .filter(process -> !isJetBrainsIdeJvm(process))
                .forEach(process -> process.info().command().ifPresent(command -> {
                    if (ProcessNameMatcher.matches(pattern, command)) {
                        targets.add(new MatchedProcess(
                                process.pid(),
                                ProcessNameMatcher.executableName(command),
                                command));
                    }
                }));
        targets.sort(Comparator.comparing(MatchedProcess::executableName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(MatchedProcess::pid));
        return targets;
    }

    private static boolean confirmTermination(
            @Nullable Project project,
            String regularExpression,
            List<MatchedProcess> targets) {
        AtomicBoolean confirmed = new AtomicBoolean(false);
        ApplicationManager.getApplication().invokeAndWait(() -> {
            int answer = Messages.showYesNoDialog(
                    project,
                    buildConfirmationMessage(regularExpression, targets),
                    "Kill Matching Processes",
                    Messages.getWarningIcon()
            );
            confirmed.set(answer == Messages.YES);
        });
        return confirmed.get();
    }

    static String buildConfirmationMessage(String regularExpression, List<MatchedProcess> targets) {
        StringBuilder message = new StringBuilder();
        message.append("Forcibly terminate these ").append(targets.size())
                .append(" process(es) matching:\n")
                .append(regularExpression)
                .append("\n\n");

        int shown = Math.min(targets.size(), CONFIRM_LIST_LIMIT);
        for (int index = 0; index < shown; index++) {
            MatchedProcess process = targets.get(index);
            message.append(process.pid())
                    .append('\t')
                    .append(process.executableName())
                    .append('\n');
        }
        if (targets.size() > CONFIRM_LIST_LIMIT) {
            message.append("… and ").append(targets.size() - CONFIRM_LIST_LIMIT).append(" more\n");
        }

        message.append("\nThe current IDE process, its parents, and other JetBrains IDE JVMs are always excluded.");
        return message.toString();
    }

    static Set<Long> protectedProcessIds() {
        Set<Long> protectedPids = new HashSet<>();
        ProcessHandle handle = ProcessHandle.current();
        while (true) {
            protectedPids.add(handle.pid());
            var parent = handle.parent();
            if (parent.isEmpty()) {
                break;
            }
            handle = parent.get();
        }
        return protectedPids;
    }

    static boolean isJetBrainsIdeJvm(ProcessHandle process) {
        return process.info().arguments()
                .map(args -> Arrays.stream(args).anyMatch(ProcessKiller::looksLikeIdeMainClass))
                .orElseGet(() -> process.info().command()
                        .map(ProcessKiller::looksLikeIdeLauncher)
                        .orElse(false));
    }

    private static boolean looksLikeIdeMainClass(String argument) {
        String value = argument.toLowerCase(Locale.ROOT);
        return value.contains("com.intellij.idea.main")
                || value.contains("com.intellij.idea.mainimpl")
                || value.contains("com.intellij.idea.jetbrainlessstarter");
    }

    private static boolean looksLikeIdeLauncher(String command) {
        String name = ProcessNameMatcher.executableName(command).toLowerCase(Locale.ROOT);
        return name.startsWith("idea")
                || name.startsWith("clion")
                || name.startsWith("pycharm")
                || name.startsWith("webstorm")
                || name.startsWith("phpstorm")
                || name.startsWith("goland")
                || name.startsWith("rider")
                || name.startsWith("rubymine")
                || name.startsWith("datagrip")
                || name.startsWith("dataspell")
                || name.startsWith("rustrover")
                || name.startsWith("aqua")
                || name.startsWith("writerside")
                || name.startsWith("studio");
    }

    record MatchedProcess(long pid, String executableName, String command) {
    }
}
