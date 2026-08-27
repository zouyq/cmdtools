package com.github.zouyq.utils;

import java.util.regex.Pattern;

final class ProcessNameMatcher {
    private ProcessNameMatcher() {
    }

    static boolean matches(Pattern pattern, String command) {
        return pattern.matcher(executableName(command)).find();
    }

    static String executableName(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        int slash = Math.max(command.lastIndexOf('/'), command.lastIndexOf('\\'));
        return slash >= 0 ? command.substring(slash + 1) : command;
    }
}
