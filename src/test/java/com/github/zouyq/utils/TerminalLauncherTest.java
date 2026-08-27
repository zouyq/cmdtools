package com.github.zouyq.utils;

import com.github.zouyq.config.CmdToolsSettings;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TerminalLauncherTest {
    private static final Path DIRECTORY = Path.of("work", "project").toAbsolutePath();

    @Test
    public void selectsWindowsCmdViaStart() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.windowsTerminal = CmdToolsSettings.WINDOWS_CMD;

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.WINDOWS, settings, DIRECTORY);

        assertEquals(List.of("cmd.exe", "/c",
                        "start \"\" /D \"" + DIRECTORY.toAbsolutePath().normalize() + "\" cmd.exe"),
                spec.command());
        assertEquals(DIRECTORY, spec.workingDirectory());
    }

    @Test
    public void selectsWindowsPowerShellViaStart() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.windowsTerminal = CmdToolsSettings.WINDOWS_POWERSHELL;

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.WINDOWS, settings, DIRECTORY);

        assertEquals(List.of("cmd.exe", "/c",
                        "start \"\" /D \"" + DIRECTORY.toAbsolutePath().normalize() + "\" powershell.exe"),
                spec.command());
    }

    @Test
    public void opensWindowsTerminalWithDirectoryFlag() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.windowsTerminal = CmdToolsSettings.WINDOWS_CUSTOM;
        settings.windowsCustomTerminalPath = "C:\\Users\\me\\AppData\\Local\\Microsoft\\WindowsApps\\wt.exe";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.WINDOWS, settings, DIRECTORY);

        assertEquals(List.of(settings.windowsCustomTerminalPath, "-d", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void expandsCustomWindowsArguments() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.windowsTerminal = CmdToolsSettings.WINDOWS_CUSTOM;
        settings.windowsCustomTerminalPath = "C:\\tools\\term.exe";
        settings.windowsCustomTerminalArgs = "-start \"$DIR\"";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.WINDOWS, settings, DIRECTORY);

        assertEquals(List.of("C:\\tools\\term.exe", "-start", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void opensConfiguredMacTerminalAtDirectory() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.macTerminal = CmdToolsSettings.MAC_ITERM;

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.MAC, settings, DIRECTORY);

        assertEquals(List.of("open", "-a", "iTerm", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void opensCustomMacTerminalWithArguments() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.macTerminal = CmdToolsSettings.MAC_CUSTOM;
        settings.macCustomTerminalPath = "MyTerm";
        settings.macCustomTerminalArgs = "--cwd $DIR";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.MAC, settings, DIRECTORY);

        assertEquals(List.of("open", "-na", "MyTerm", "--args", "--cwd", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void usesConfiguredLinuxTerminalFlags() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.linuxTerminal = CmdToolsSettings.LINUX_CUSTOM;
        settings.linuxCustomTerminalPath = "/usr/bin/konsole";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.LINUX, settings, DIRECTORY);

        assertEquals(List.of("/usr/bin/konsole", "--workdir", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void usesLinuxPresetName() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.linuxTerminal = CmdToolsSettings.LINUX_ALACRITTY;

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.LINUX, settings, DIRECTORY);

        assertEquals(List.of("alacritty", "--working-directory", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void expandsCustomLinuxArguments() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.linuxTerminal = CmdToolsSettings.LINUX_CUSTOM;
        settings.linuxCustomTerminalPath = "/usr/local/bin/myterm";
        settings.linuxCustomTerminalArgs = "--dir \"$DIR\"";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.LINUX, settings, DIRECTORY);

        assertEquals(List.of("/usr/local/bin/myterm", "--dir", DIRECTORY.toString()), spec.command());
    }

    @Test
    public void migratesLegacyMacAndLinuxSettings() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.macTerminalApplication = "iTerm2";
        settings.linuxTerminalPath = "/usr/bin/kitty";
        settings.migrateLegacyFields();

        assertEquals(CmdToolsSettings.MAC_ITERM2, settings.macTerminal);
        assertEquals(CmdToolsSettings.LINUX_KITTY, settings.linuxTerminal);
    }

    @Test
    public void runsStartupCommandInWindowsPowerShell() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.windowsTerminal = CmdToolsSettings.WINDOWS_POWERSHELL;
        settings.terminalStartupCommand = "claude";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.WINDOWS, settings, DIRECTORY);

        assertEquals(List.of("cmd.exe", "/c",
                        "start \"\" /D \"" + DIRECTORY.toAbsolutePath().normalize()
                                + "\" powershell.exe -NoExit -Command claude"),
                spec.command());
    }

    @Test
    public void runsStartupCommandInLinuxKonsole() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.linuxTerminal = CmdToolsSettings.LINUX_CUSTOM;
        settings.linuxCustomTerminalPath = "/usr/bin/konsole";
        settings.terminalStartupCommand = "pi";
        String shell = TerminalLauncher.resolveShell();

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.LINUX, settings, DIRECTORY);

        assertEquals(List.of(
                        "/usr/bin/konsole", "--workdir", DIRECTORY.toString(),
                        "-e", shell, "-lc", "pi; exec " + shell),
                spec.command());
    }

    @Test
    public void runsStartupCommandOnMacAlacrittyViaCli() throws Exception {
        Path fakeAlacritty = Files.createTempFile("alacritty-", ".cmd");
        fakeAlacritty.toFile().setExecutable(true);
        try {
            CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
            settings.macTerminal = CmdToolsSettings.MAC_CUSTOM;
            settings.macCustomTerminalPath = fakeAlacritty.toString();
            settings.terminalStartupCommand = "claude";
            String shell = TerminalLauncher.resolveShell();

            TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                    OperatingSystem.MAC, settings, DIRECTORY);

            assertEquals(List.of(
                            fakeAlacritty.toString(),
                            "--working-directory", DIRECTORY.toAbsolutePath().normalize().toString(),
                            "-e", shell, "-lc", "claude; exec " + shell),
                    spec.command());
        } finally {
            Files.deleteIfExists(fakeAlacritty);
        }
    }

    @Test
    public void runsStartupCommandOnLinuxGenericTerminal() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.linuxTerminal = CmdToolsSettings.LINUX_CUSTOM;
        settings.linuxCustomTerminalPath = "/usr/bin/x-terminal-emulator";
        settings.terminalStartupCommand = "pi";
        String shell = TerminalLauncher.resolveShell();

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.LINUX, settings, DIRECTORY);

        assertEquals(List.of(
                        "/usr/bin/x-terminal-emulator", "-e", shell, "-lc",
                        "cd '" + DIRECTORY.toAbsolutePath().normalize() + "' && pi; exec " + shell),
                spec.command());
    }

    @Test
    public void opensMacAlacrittyPresetViaOpenWhenCliMissing() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.macTerminal = CmdToolsSettings.MAC_ALACRITTY;

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.MAC, settings, DIRECTORY);

        assertEquals(List.of("open", "-a", CmdToolsSettings.MAC_ALACRITTY,
                        DIRECTORY.toAbsolutePath().normalize().toString()),
                spec.command());
    }

    @Test
    public void runsStartupCommandInMacTerminalViaOsascript() {
        CmdToolsSettings.Data settings = new CmdToolsSettings.Data();
        settings.macTerminal = CmdToolsSettings.MAC_TERMINAL;
        settings.terminalStartupCommand = "claude";

        TerminalLauncher.LaunchSpec spec = TerminalLauncher.createLaunchSpec(
                OperatingSystem.MAC, settings, DIRECTORY);

        assertEquals("osascript", spec.command().get(0));
        assertEquals("-e", spec.command().get(1));
        String script = spec.command().get(2);
        org.junit.Assert.assertTrue(script.contains("claude"));
        org.junit.Assert.assertTrue(script.contains("Terminal"));
        org.junit.Assert.assertTrue(script.contains("do script"));
    }
}
