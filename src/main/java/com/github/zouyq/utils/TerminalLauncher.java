package com.github.zouyq.utils;

import com.github.zouyq.config.CmdToolsSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringTokenizer;

public final class TerminalLauncher {
    private static final Logger LOG = Logger.getInstance(TerminalLauncher.class);

    private TerminalLauncher() {
    }

    public static void open(Path directory, @Nullable Project project) {
        CmdToolsSettings.Data settings = CmdToolsSettings.getInstance().data();
        OperatingSystem operatingSystem = OperatingSystem.current();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                LaunchSpec spec = createLaunchSpec(operatingSystem, settings, directory);
                new ProcessBuilder(spec.command()).directory(spec.workingDirectory().toFile()).start();
            } catch (IOException | IllegalStateException exception) {
                LOG.warn("Unable to open terminal", exception);
                UiMessages.error(project, "Unable to open the configured terminal: " + exception.getMessage());
            }
        });
    }

    static LaunchSpec createLaunchSpec(OperatingSystem os, CmdToolsSettings.Data settings, Path directory) {
        settings.migrateLegacyFields();
        return switch (os) {
            case WINDOWS -> windowsSpec(settings, directory);
            case MAC -> macSpec(settings, directory);
            case LINUX -> linuxSpec(settings, directory);
            case UNSUPPORTED -> throw new IllegalStateException("Unsupported operating system");
        };
    }

    @VisibleForTesting
    static String resolveShell() {
        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            return shell.trim();
        }
        return "/bin/sh";
    }

    private static String shellLoginLine(String startup) {
        return startup + "; exec " + resolveShell();
    }

    private static LaunchSpec windowsSpec(CmdToolsSettings.Data settings, Path directory) {
        String executable = windowsExecutable(settings);
        String name = executableName(executable).toLowerCase(Locale.ROOT);
        String startup = startupCommand(settings);
        boolean customArgs = CmdToolsSettings.WINDOWS_CUSTOM.equals(settings.windowsTerminal)
                && settings.windowsCustomTerminalArgs != null
                && !settings.windowsCustomTerminalArgs.isBlank();

        if (customArgs) {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.addAll(tokenizeArgs(settings.windowsCustomTerminalArgs.trim(), directory));
            return new LaunchSpec(command, directory);
        }

        if (CmdToolsSettings.WINDOWS_CUSTOM.equals(settings.windowsTerminal)
                && (name.equals("wt") || name.equals("wt.exe"))) {
            if (startup.isEmpty()) {
                return new LaunchSpec(List.of(executable, "-d", directory.toString()), directory);
            }
            return new LaunchSpec(List.of(
                    executable, "-d", directory.toString(),
                    "cmd.exe", "/k", startup
            ), directory);
        }

        if (isWindowsConsoleShell(name)) {
            String dir = directory.toAbsolutePath().normalize().toString();
            StringBuilder startCommand = new StringBuilder("start \"\" /D \"")
                    .append(dir)
                    .append("\" ")
                    .append(quoteWindowsArg(executable));
            if (!startup.isEmpty()) {
                if (name.startsWith("powershell") || name.startsWith("pwsh")) {
                    startCommand.append(" -NoExit -Command ").append(quoteWindowsArg(startup));
                } else {
                    startCommand.append(" /k ").append(quoteWindowsArg(startup));
                }
            }
            return new LaunchSpec(List.of("cmd.exe", "/c", startCommand.toString()), directory);
        }

        if (!startup.isEmpty()) {
            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add(startup);
            return new LaunchSpec(command, directory);
        }
        return new LaunchSpec(List.of(executable), directory);
    }

    private static String quoteWindowsArg(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        if (value.indexOf(' ') < 0 && value.indexOf('\t') < 0 && value.indexOf('"') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }

    private static boolean isWindowsConsoleShell(String name) {
        return name.equals("cmd")
                || name.equals("cmd.exe")
                || name.equals("powershell")
                || name.equals("powershell.exe")
                || name.equals("pwsh")
                || name.equals("pwsh.exe");
    }

    private static String windowsExecutable(CmdToolsSettings.Data settings) {
        if (CmdToolsSettings.WINDOWS_CMD.equals(settings.windowsTerminal)) {
            return "cmd.exe";
        }
        if (CmdToolsSettings.WINDOWS_CUSTOM.equals(settings.windowsTerminal)) {
            if (settings.windowsCustomTerminalPath == null || settings.windowsCustomTerminalPath.isBlank()) {
                throw new IllegalStateException("Windows custom terminal path is empty");
            }
            return settings.windowsCustomTerminalPath.trim();
        }
        return "powershell.exe";
    }

    private static LaunchSpec macSpec(CmdToolsSettings.Data settings, Path directory) {
        String application = macApplication(settings);
        boolean customArgs = CmdToolsSettings.MAC_CUSTOM.equals(settings.macTerminal)
                && settings.macCustomTerminalArgs != null
                && !settings.macCustomTerminalArgs.isBlank();
        String startup = startupCommand(settings);

        if (customArgs) {
            List<String> command = new ArrayList<>();
            command.add("open");
            command.add("-na");
            command.add(application);
            command.add("--args");
            command.addAll(tokenizeArgs(settings.macCustomTerminalArgs.trim(), directory));
            return new LaunchSpec(command, directory);
        }

        if (!startup.isEmpty()) {
            return macStartupSpec(application, directory, startup);
        }
        return macOpenSpec(application, directory);
    }

    private static LaunchSpec macOpenSpec(String application, Path directory) {
        return macCliLaunch(application, directory, null)
                .orElseGet(() -> new LaunchSpec(
                        List.of("open", "-a", application, directory.toAbsolutePath().normalize().toString()),
                        directory));
    }

    private static LaunchSpec macStartupSpec(String application, Path directory, String startup) {
        Optional<LaunchSpec> cliLaunch = macCliLaunch(application, directory, startup);
        if (cliLaunch.isPresent()) {
            return cliLaunch.get();
        }

        MacTerminalKind kind = macTerminalKind(application);
        String dir = directory.toAbsolutePath().normalize().toString();
        String shellLine = "cd " + shellSingleQuote(dir) + " && " + startup;

        if (kind == MacTerminalKind.ITERM) {
            String script = "tell application " + appleQuote(displayApplicationName(application)) + "\n"
                    + "activate\n"
                    + "if (count of windows) = 0 then\n"
                    + "  create window with default profile\n"
                    + "else\n"
                    + "  tell current window to create tab with default profile\n"
                    + "end if\n"
                    + "tell current session of current window\n"
                    + "  write text " + appleQuote(shellLine) + "\n"
                    + "end tell\n"
                    + "end tell";
            return new LaunchSpec(List.of("osascript", "-e", script), directory);
        }

        if (kind == MacTerminalKind.TERMINAL) {
            String script = "tell application \"Terminal\"\n"
                    + "do script " + appleQuote(shellLine) + "\n"
                    + "activate\n"
                    + "end tell";
            return new LaunchSpec(List.of("osascript", "-e", script), directory);
        }

        Optional<String> fallbackCli = resolveMacCliExecutable(kind, application);
        if (fallbackCli.isEmpty() && (application.contains("/") || application.contains("\\"))) {
            fallbackCli = optionalExecutablePath(application);
        }
        if (fallbackCli.isPresent()) {
            return linuxStyleShellLaunch(fallbackCli.get(), directory, startup);
        }

        throw new IllegalStateException(
                "Unable to run the startup command in " + application
                        + ". Choose Terminal/iTerm, install the terminal CLI, or use Custom arguments.");
    }

    private static Optional<LaunchSpec> macCliLaunch(String application, Path directory, @Nullable String startup) {
        MacTerminalKind kind = macTerminalKind(application);
        Optional<String> executable = resolveMacCliExecutable(kind, application);
        if (executable.isEmpty()) {
            return Optional.empty();
        }

        String exe = executable.get();
        String dir = directory.toAbsolutePath().normalize().toString();
        String shell = resolveShell();
        boolean withStartup = startup != null && !startup.isBlank();

        return switch (kind) {
            case ALACRITTY -> Optional.of(withStartup
                    ? new LaunchSpec(List.of(
                    exe, "--working-directory", dir, "-e", shell, "-lc", shellLoginLine(startup)), directory)
                    : new LaunchSpec(List.of(exe, "--working-directory", dir), directory));
            case KITTY -> Optional.of(withStartup
                    ? new LaunchSpec(List.of(
                    exe, "--directory", dir, shell, "-lc", shellLoginLine(startup)), directory)
                    : new LaunchSpec(List.of(exe, "--directory", dir), directory));
            case WEZTERM -> Optional.of(withStartup
                    ? new LaunchSpec(List.of(
                    exe, "start", "--cwd", dir, "--", shell, "-lc", shellLoginLine(startup)), directory)
                    : new LaunchSpec(List.of(exe, "start", "--cwd", dir), directory));
            default -> Optional.empty();
        };
    }

    private static Optional<String> resolveMacCliExecutable(MacTerminalKind kind, String application) {
        String cliName = switch (kind) {
            case ALACRITTY -> "alacritty";
            case KITTY -> "kitty";
            case WEZTERM -> "wezterm";
            default -> null;
        };
        if (cliName != null) {
            Optional<String> fromPath = ExecutableFinder.firstAvailable(cliName);
            if (fromPath.isPresent()) {
                return fromPath;
            }
        }
        return optionalExecutablePath(application);
    }

    private static Optional<String> optionalExecutablePath(String application) {
        if (application == null || application.isBlank()) {
            return Optional.empty();
        }
        if (!application.contains("/") && !application.contains("\\")) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(application);
            if (Files.isRegularFile(path) && Files.isExecutable(path)) {
                return Optional.of(path.toString());
            }
        } catch (Exception ignored) {
            // Fall through.
        }
        return Optional.empty();
    }

    private static MacTerminalKind macTerminalKind(String application) {
        String app = displayApplicationName(application).toLowerCase(Locale.ROOT);
        if (app.contains("iterm")) {
            return MacTerminalKind.ITERM;
        }
        if (app.equals("terminal")) {
            return MacTerminalKind.TERMINAL;
        }
        if (app.contains("alacritty")) {
            return MacTerminalKind.ALACRITTY;
        }
        if (app.contains("kitty")) {
            return MacTerminalKind.KITTY;
        }
        if (app.contains("wezterm")) {
            return MacTerminalKind.WEZTERM;
        }
        if (app.contains("warp")) {
            return MacTerminalKind.WARP;
        }
        return MacTerminalKind.OTHER;
    }

    private static String displayApplicationName(String application) {
        String name = application;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(".app")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private static String macApplication(CmdToolsSettings.Data settings) {
        if (CmdToolsSettings.MAC_CUSTOM.equals(settings.macTerminal)) {
            if (settings.macCustomTerminalPath == null || settings.macCustomTerminalPath.isBlank()) {
                throw new IllegalStateException("macOS custom terminal application is empty");
            }
            return settings.macCustomTerminalPath.trim();
        }
        return settings.macTerminal == null || settings.macTerminal.isBlank()
                ? CmdToolsSettings.MAC_TERMINAL
                : settings.macTerminal.trim();
    }

    private static LaunchSpec linuxSpec(CmdToolsSettings.Data settings, Path directory) {
        String startup = startupCommand(settings);
        if (CmdToolsSettings.LINUX_CUSTOM.equals(settings.linuxTerminal)) {
            if (settings.linuxCustomTerminalPath == null || settings.linuxCustomTerminalPath.isBlank()) {
                throw new IllegalStateException("Linux custom terminal path is empty");
            }
            String executable = settings.linuxCustomTerminalPath.trim();
            String args = settings.linuxCustomTerminalArgs == null ? "" : settings.linuxCustomTerminalArgs.trim();
            if (!args.isEmpty()) {
                List<String> command = new ArrayList<>();
                command.add(executable);
                command.addAll(tokenizeArgs(args, directory));
                return new LaunchSpec(command, directory);
            }
            return linuxCommandFor(executable, directory, startup);
        }

        return linuxCommandFor(linuxExecutable(settings), directory, startup);
    }

    private static String linuxExecutable(CmdToolsSettings.Data settings) {
        String selected = settings.linuxTerminal == null || settings.linuxTerminal.isBlank()
                ? CmdToolsSettings.LINUX_AUTO
                : settings.linuxTerminal.trim();

        if (CmdToolsSettings.LINUX_AUTO.equals(selected)) {
            return ExecutableFinder.firstAvailable(
                            "x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal",
                            "mate-terminal", "tilix", "kitty", "wezterm", "alacritty", "xterm")
                    .orElseThrow(() -> new IllegalStateException(
                            "No supported terminal was found. Configure one in Settings | Tools | CmdTools."));
        }

        return ExecutableFinder.firstAvailable(selected).orElse(selected);
    }

    private static LaunchSpec linuxCommandFor(String executable, Path directory, String startup) {
        String name = resolveExecutableBaseName(executable).toLowerCase(Locale.ROOT);
        if (startup == null || startup.isBlank()) {
            return linuxCommandWithoutStartup(executable, name, directory);
        }

        String shell = resolveShell();
        String login = shellLoginLine(startup);
        if (name.equals("gnome-terminal") || name.equals("mate-terminal") || name.equals("tilix")) {
            return new LaunchSpec(List.of(
                    executable, "--working-directory=" + directory, "--", shell, "-lc", login
            ), directory);
        }
        if (name.equals("konsole")) {
            return new LaunchSpec(List.of(
                    executable, "--workdir", directory.toString(), "-e", shell, "-lc", login
            ), directory);
        }
        if (name.equals("xfce4-terminal")) {
            return new LaunchSpec(List.of(
                    executable, "--working-directory", directory.toString(),
                    "-x", shell, "-lc", login
            ), directory);
        }
        if (name.equals("kitty")) {
            return new LaunchSpec(List.of(
                    executable, "--directory", directory.toString(), shell, "-lc", login
            ), directory);
        }
        if (name.equals("wezterm")) {
            return new LaunchSpec(List.of(
                    executable, "start", "--cwd", directory.toString(), "--", shell, "-lc", login
            ), directory);
        }
        if (name.equals("alacritty")) {
            return new LaunchSpec(List.of(
                    executable, "--working-directory", directory.toString(),
                    "-e", shell, "-lc", login
            ), directory);
        }
        if (name.equals("xterm")) {
            return new LaunchSpec(List.of(
                    executable, "-e", shell, "-lc",
                    "cd " + shellSingleQuote(directory.toString()) + " && " + login
            ), directory);
        }
        return linuxStyleShellLaunch(executable, directory, startup);
    }

    private static LaunchSpec linuxStyleShellLaunch(String executable, Path directory, String startup) {
        String shell = resolveShell();
        String login = shellLoginLine(startup);
        return new LaunchSpec(List.of(
                executable, "-e", shell, "-lc",
                "cd " + shellSingleQuote(directory.toAbsolutePath().normalize().toString()) + " && " + login
        ), directory);
    }

    private static LaunchSpec linuxCommandWithoutStartup(String executable, String name, Path directory) {
        List<String> command;
        if (name.equals("gnome-terminal") || name.equals("mate-terminal") || name.equals("tilix")) {
            command = List.of(executable, "--working-directory=" + directory);
        } else if (name.equals("konsole")) {
            command = List.of(executable, "--workdir", directory.toString());
        } else if (name.equals("xfce4-terminal")) {
            command = List.of(executable, "--working-directory", directory.toString());
        } else if (name.equals("kitty")) {
            command = List.of(executable, "--directory", directory.toString());
        } else if (name.equals("wezterm")) {
            command = List.of(executable, "start", "--cwd", directory.toString());
        } else if (name.equals("alacritty")) {
            command = List.of(executable, "--working-directory", directory.toString());
        } else {
            command = List.of(executable);
        }
        return new LaunchSpec(command, directory);
    }

    private static String startupCommand(CmdToolsSettings.Data settings) {
        return settings.terminalStartupCommand == null ? "" : settings.terminalStartupCommand.trim();
    }

    private static String shellSingleQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static String appleQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String executableName(String executable) {
        int slash = Math.max(executable.lastIndexOf('/'), executable.lastIndexOf('\\'));
        return slash >= 0 ? executable.substring(slash + 1) : executable;
    }

    static String resolveExecutableBaseName(String executable) {
        try {
            Path path = Path.of(executable);
            if (Files.isSymbolicLink(path) || Files.isRegularFile(path)) {
                return executableName(path.toRealPath().toString());
            }
        } catch (Exception ignored) {
            // Fall back to the configured path basename.
        }
        return executableName(executable);
    }

    static List<String> tokenizeArgs(String args, Path directory) {
        String expanded = args.replace("$DIR", directory.toString());
        List<String> tokens = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(expanded, " \"", true);
        StringBuilder current = null;
        boolean inQuotes = false;
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if ("\"".equals(token)) {
                inQuotes = !inQuotes;
                if (current == null) {
                    current = new StringBuilder();
                }
                continue;
            }
            if (!inQuotes && " ".equals(token)) {
                if (current != null) {
                    tokens.add(current.toString());
                    current = null;
                }
                continue;
            }
            if (current == null) {
                current = new StringBuilder();
            }
            current.append(token);
        }
        if (current != null) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private enum MacTerminalKind {
        ITERM,
        TERMINAL,
        ALACRITTY,
        KITTY,
        WEZTERM,
        WARP,
        OTHER
    }

    record LaunchSpec(List<String> command, Path workingDirectory) {
    }
}
