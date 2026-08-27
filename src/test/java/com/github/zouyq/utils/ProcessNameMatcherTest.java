package com.github.zouyq.utils;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProcessNameMatcherTest {
    @Test
    public void matchesOnlyExecutableBaseName() {
        Pattern pattern = Pattern.compile("(?i)java|gradle");

        assertTrue(ProcessNameMatcher.matches(pattern, "C:\\Program Files\\Java\\bin\\java.exe"));
        assertTrue(ProcessNameMatcher.matches(pattern, "/usr/bin/gradle"));
        assertFalse(ProcessNameMatcher.matches(pattern, "/opt/java-tools/unrelated"));
    }

    @Test
    public void supportsFuzzyRegularExpressionSearch() {
        assertTrue(ProcessNameMatcher.matches(Pattern.compile("idea"), "/opt/idea/bin/idea64"));
        assertFalse(ProcessNameMatcher.matches(Pattern.compile("^java$"), "/usr/bin/java.exe"));
    }

    @Test
    public void protectsCurrentProcessTreeIds() {
        assertTrue(ProcessKiller.protectedProcessIds().contains(ProcessHandle.current().pid()));
    }

    @Test
    public void confirmationMessageListsMatchedProcesses() {
        String message = ProcessKiller.buildConfirmationMessage(
                "(?i)java",
                java.util.List.of(
                        new ProcessKiller.MatchedProcess(101, "java.exe", "C:\\java.exe"),
                        new ProcessKiller.MatchedProcess(202, "javaw.exe", "C:\\javaw.exe")
                ));

        assertTrue(message.contains("2 process(es)"));
        assertTrue(message.contains("(?i)java"));
        assertTrue(message.contains("101\tjava.exe"));
        assertTrue(message.contains("202\tjavaw.exe"));
    }
}
